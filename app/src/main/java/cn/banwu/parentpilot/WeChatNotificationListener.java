package cn.banwu.parentpilot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;

public class WeChatNotificationListener extends NotificationListenerService {
    private static final String WECHAT_PACKAGE = "com.tencent.mm";
    private static final String CHANNEL = "new_todos";
    private static final long BATCH_WINDOW_MS = 5000L;
    private static final long MAX_BATCH_MS = 15000L;
    private static volatile boolean runtimeConnected;
    private static volatile long lastRebindRequestAt;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService batchScheduler = Executors.newSingleThreadScheduledExecutor();
    private final Object batchLock = new Object();
    private final Map<String, PendingBatch> pendingBatches = new HashMap<>();

    private static final class PendingBatch {
        String title, subtitle, conversation;
        long startedAt, receivedAt;
        final LinkedHashSet<String> messages = new LinkedHashSet<>();
        ScheduledFuture<?> flush;
    }

    @Override public void onListenerConnected() {
        super.onListenerConnected();
        runtimeConnected = true;
        lastRebindRequestAt = 0L;
        TodoStore.recordDiagnostic(this, "\u901a\u77e5\u76d1\u542c\u670d\u52a1\u5df2\u8fde\u63a5");
        sendBroadcast(new android.content.Intent(MainActivity.ACTION_REFRESH).setPackage(getPackageName()));
    }

    @Override public void onListenerDisconnected() {
        runtimeConnected = false;
        TodoStore.recordDiagnostic(this, "\u901a\u77e5\u76d1\u542c\u670d\u52a1\u5df2\u65ad\u5f00\uff0c\u5df2\u8bf7\u6c42\u91cd\u8fde");
        super.onListenerDisconnected();
        requestRebind(new ComponentName(this, WeChatNotificationListener.class));
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || !WECHAT_PACKAGE.equals(sbn.getPackageName())) return;
        if (TodoStore.paused(this)) {
            TodoStore.recordDiagnostic(this, "收到微信通知，但整理处于暂停状态");
            return;
        }

        Notification n = sbn.getNotification();
        if (n == null || n.extras == null) {
            TodoStore.recordDiagnostic(this, "收到微信通知，但系统未提供通知内容");
            return;
        }
        Bundle extras = n.extras;
        String title = value(extras.getCharSequence(Notification.EXTRA_TITLE));
        String subtitle = value(extras.getCharSequence(Notification.EXTRA_SUB_TEXT));
        String conversation = value(extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE));
        String body = value(extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        if (body.isEmpty()) body = value(extras.getCharSequence(Notification.EXTRA_TEXT));
        if (body.isEmpty()) {
            CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            if (lines != null) {
                StringBuilder combined = new StringBuilder();
                for (CharSequence line : lines) if (line != null && line.length() > 0) {
                    if (combined.length() > 0) combined.append('\n');
                    combined.append(line);
                }
                body = combined.toString().trim();
            }
        }
        if (body.isEmpty()) {
            TodoStore.recordDiagnostic(this, "收到微信通知，但系统没有提供可读取的正文");
            return;
        }
        long postedAt = sbn.getPostTime() > 0 ? sbn.getPostTime() : System.currentTimeMillis();
        enqueueForBatch(title, subtitle, conversation, body, postedAt);
    }

    private void enqueueForBatch(String title, String subtitle, String conversation, String body, long receivedAt) {
        java.util.List<String> groups = TodoStore.groups(this);
        String matchedGroup = findMatchedGroup(title + " " + subtitle + " " + conversation, groups);
        if (matchedGroup == null) {
            TodoStore.recordDiagnostic(this, "收到微信通知正文，但标题群名与已设置群聊均不匹配；请核对群聊名称");
            return;
        }
        String key = canonicalGroup(matchedGroup);
        long now = System.currentTimeMillis();
        synchronized (batchLock) {
            PendingBatch batch = pendingBatches.get(key);
            if (batch == null) {
                batch = new PendingBatch();
                batch.title = title;
                batch.subtitle = subtitle;
                batch.conversation = conversation;
                batch.startedAt = now;
                pendingBatches.put(key, batch);
            }
            batch.receivedAt = Math.max(batch.receivedAt, receivedAt);
            if (body != null && !body.trim().isEmpty()) batch.messages.add(body.trim());
            if (batch.flush != null) batch.flush.cancel(false);
            long remaining = Math.max(0L, MAX_BATCH_MS - (now - batch.startedAt));
            long delay = Math.min(BATCH_WINDOW_MS, remaining);
            PendingBatch expected = batch;
            batch.flush = batchScheduler.schedule(() -> flushBatch(key, expected), delay, TimeUnit.MILLISECONDS);
            TodoStore.recordDiagnostic(this, "已收到匹配群聊通知，正在合并连续消息片段");
        }
    }

    private void flushBatch(String key, PendingBatch expected) {
        synchronized (batchLock) {
            if (pendingBatches.get(key) != expected) return;
            pendingBatches.remove(key);
        }
        StringBuilder combined = new StringBuilder();
        for (String message : expected.messages) {
            if (combined.length() > 0) combined.append((char)10).append((char)10);
            combined.append(message);
        }
        String body = combined.toString().trim();
        if (body.isEmpty()) return;
        TodoStore.recordDiagnostic(this, "连续消息片段合并完成，开始进行语义整理");
        worker.execute(() -> process(expected.title, expected.subtitle, expected.conversation, body, expected.receivedAt));
    }

    private void process(String title, String subtitle, String conversation, String body, long receivedAt) {
        if (TodoStore.paused(this)) {
            TodoStore.recordDiagnostic(this, "收到通知后整理被暂停，未加入待办");
            return;
        }
        String child = TodoStore.child(this);
        java.util.List<String> groups = TodoStore.groups(this);
        if (child.isEmpty() || groups.isEmpty()) {
            TodoStore.recordDiagnostic(this, "未设置孩子姓名或群聊名称");
            return;
        }
        String visibleHeader = title + " " + subtitle + " " + conversation;
        String matchedGroup = findMatchedGroup(visibleHeader, groups);
        if (matchedGroup == null) {
            TodoStore.recordDiagnostic(this, "收到微信通知，但标题群名与已设置群聊均不匹配；请核对群聊名称");
            return;
        }
        TodoStore.recordDiagnostic(this, "命中已设置的群聊之一，开始整理消息正文");

        String sender = title.isEmpty() || matchesGroup(title, groups) ? "爸爸" : title;
        String scopeKeywords = TodoStore.scopeKeywords(this);
        String taskKeywords = TodoStore.taskKeywords(this);
        int separator = body.indexOf('：');
        int asciiSeparator = body.indexOf(':');
        if (separator < 0 || (asciiSeparator >= 0 && asciiSeparator < separator)) separator = asciiSeparator;
        if (separator > 0 && separator <= 20) {
            String leading = body.substring(0, separator).trim();
            // A WeChat group notification may contain both a sender separator and a
            // task separator, e.g. "[6条]家长: 孩子 今天数学作业：……". Use the
            // earliest separator above so only the sender label is removed.
            boolean leadingIsMessage = MessageTaskParser.parse(leading, child, scopeKeywords, taskKeywords) != null;
            if (!leading.isEmpty() && !leading.contains("@") && !leadingIsMessage) {
                sender = leading;
                body = body.substring(separator + 1).trim();
            }
        }
        if (MessageTaskParser.isExplicitlyDirectedToOtherFamily(body, child, scopeKeywords)) {
            TodoStore.recordDiagnostic(this, "消息明确@其他家长且未提及当前孩子或全体家长，已忽略");
            return;
        }
        boolean useAi = TodoStore.aiEnabled(this) && TodoStore.aiConsent(this)
                && !TodoStore.aiKey(this).isEmpty() && !TodoStore.aiModel(this).isEmpty();
        java.util.List<AiTaskAnalyzer.Task> aiTasks = null;
        if (useAi) {
            try {
                AiTaskAnalyzer.Result result = AiTaskAnalyzer.analyze(this, body, TodoStore.activeContext(this, matchedGroup));
                aiTasks = result.tasks;
                TodoStore.recordDiagnostic(this, "API 语义分析成功；返回事项数=" + aiTasks.size() + "（API 响应正文不写入诊断）");
                if (aiTasks.isEmpty()) { TodoStore.recordDiagnostic(this, "模型判断本批新消息无需新增或更新事项"); return; }
            } catch (Exception error) {
                TodoStore.recordDiagnostic(this, "API 分析失败，已回退本地关键词规则：" + safeError(error));
            }
        } else if (TodoStore.aiEnabled(this) && TodoStore.aiConsent(this)) {
            TodoStore.recordDiagnostic(this, "模型识别已开启但 URL、模型名或 API Key 未填；本次回退本地规则");
        }

        java.util.ArrayList<AiTaskAnalyzer.Task> tasks = new java.util.ArrayList<>();
        if (aiTasks != null) tasks.addAll(aiTasks);
        else {
            TodoStore.recordDiagnostic(this, "正在应用本地关键词规则；" + MessageTaskParser.diagnosticSummary(body, child, scopeKeywords, taskKeywords));
            MessageTaskParser.Candidate candidate = MessageTaskParser.parse(body, child, scopeKeywords, taskKeywords);
            if (candidate != null) {
                boolean homework = DeadlineRules.looksLikeHomework(body);
                org.json.JSONArray checklist = homework ? HomeworkChecklist.extract(body) : new org.json.JSONArray();
                String titleForTask = homework ? HomeworkChecklist.title(body) : candidate.title;
                tasks.add(new AiTaskAnalyzer.Task(titleForTask, titleForTask, candidate.category, candidate.due, "", homework,
                        "new", "", checklist));
            }
        }
        if (tasks.isEmpty()) {
            TodoStore.recordDiagnostic(this, "群名已匹配，但正文未通过本地词库筛选（任务词、孩子姓名或范围词）");
            return;
        }

        int added=0, merged=0, skipped=0;
        for (AiTaskAnalyzer.Task task : tasks) {
            DeadlineRules.Result deadline = DeadlineRules.resolve(task.due, task.due, receivedAt, task.homework || DeadlineRules.looksLikeHomework(body));
            String dueLabel=deadline.at>0?deadline.label:(task.due==null||task.due.isEmpty()?"未注明时间":task.due);
            org.json.JSONArray checklist = task.checklist;
            if (task.homework && checklist.length() == 0) checklist.put(task.summary == null || task.summary.isEmpty() ? task.title : task.summary);
            int result=TodoStore.addStructured(this,task.title,dueLabel,deadline.at,sender,body,task.category,task.eventKey,
                    matchedGroup,task.targetId,task.summary,task.homework,checklist);
            if(result==1){added++;showLocalNotice(task.title,dueLabel,task.category);}
            else if(result==2)merged++;
            else skipped++;
        }
        TodoStore.recordDiagnostic(this, "整理完成：新增="+added+"，合并/更新同一事项="+merged+"，重复跳过="+skipped);
    }

    private String safeError(Exception error) {
        String message=error.getMessage();
        if(message==null||message.isEmpty())return error.getClass().getSimpleName();
        String safe=message.replaceAll("(?i)(bearer\\s+)[^\\s]+", "$1[隐藏]");
        return safe.substring(0,Math.min(safe.length(),180));
    }

    @Override public void onDestroy() {
        runtimeConnected = false;
        ArrayList<PendingBatch> remaining;
        synchronized (batchLock) {
            remaining = new ArrayList<>(pendingBatches.values());
            pendingBatches.clear();
        }
        for (PendingBatch batch : remaining) {
            if (batch.flush != null) batch.flush.cancel(false);
            StringBuilder combined = new StringBuilder();
            for (String message : batch.messages) {
                if (combined.length() > 0) combined.append((char)10).append((char)10);
                combined.append(message);
            }
            String body = combined.toString().trim();
            if (!body.isEmpty()) worker.execute(() -> process(batch.title, batch.subtitle, batch.conversation, body, batch.receivedAt));
        }
        batchScheduler.shutdownNow();
        worker.shutdown();
        super.onDestroy();
    }

    private String value(CharSequence s) { return s == null ? "" : s.toString().trim(); }

    private String normalizeGroup(String value) {
        return value == null ? "" : value.replaceAll("[\\s\\u00A0\\u3000]+", "");
    }

    private boolean matchesGroup(String visibleHeader, java.util.List<String> configuredGroups) {
        return findMatchedGroup(visibleHeader, configuredGroups) != null;
    }

    private String findMatchedGroup(String visibleHeader, java.util.List<String> configuredGroups) {
        if (configuredGroups == null || configuredGroups.isEmpty()) return null;
        String header = canonicalGroup(visibleHeader);
        if (header.isEmpty()) return null;
        for (String configuredGroup : configuredGroups) {
            String group = canonicalGroup(configuredGroup);
            if (!group.isEmpty() && (header.contains(group) || (group.length() >= 4 && group.contains(header)))) return configuredGroup;
        }
        return null;
    }

    private String canonicalGroup(String value) {
        if (value == null) return "";
        String normalized = normalizeGroup(value).replaceAll("[·•,，。:：;；_\\-—【】\\[\\]（）()]+", "");
        return normalized.replaceAll("(微信群|班级群|群聊|群)$", "");
    }

    private String categoryLabel(String category) {
        if (TodoItem.CATEGORY_CHECKIN.equals(category)) return "打卡";
        if (TodoItem.CATEGORY_NOTICE.equals(category)) return "通知";
        return "任务";
    }

    private void showLocalNotice(String title, String due, String category) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(new NotificationChannel(CHANNEL, "新待办提醒", NotificationManager.IMPORTANCE_DEFAULT));
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_menu_agenda)
                .setContentTitle("群消息整理出一项" + categoryLabel(category))
                .setContentText(title + " · " + due)
                .setAutoCancel(true);
        manager.notify((title + due).hashCode(), builder.build());
    }

    public static boolean isRuntimeConnected() { return runtimeConnected; }

    public static void ensureBound(Context context) {
        if (!isEnabled(context) || runtimeConnected) return;
        long now = System.currentTimeMillis();
        if (now - lastRebindRequestAt < 15000L) return;
        lastRebindRequestAt = now;
        try {
            requestRebind(new ComponentName(context, WeChatNotificationListener.class));
            TodoStore.recordDiagnostic(context, "\u901a\u77e5\u8bbf\u95ee\u5df2\u6388\u6743\uff0c\u6b63\u5728\u8bf7\u6c42\u7cfb\u7edf\u91cd\u8fde\u76d1\u542c\u670d\u52a1");
        } catch (RuntimeException error) {
            TodoStore.recordDiagnostic(context, "\u901a\u77e5\u76d1\u542c\u91cd\u8fde\u5931\u8d25\uff1a" + error.getClass().getSimpleName());
        }
    }

    public static void reconnectAfterPackageUpdate(Context context) {
        runtimeConnected = false;
        lastRebindRequestAt = 0L;
        ensureBound(context);
    }

    public static boolean isEnabled(Context context) {
        ComponentName self = new ComponentName(context, WeChatNotificationListener.class);
        if (Build.VERSION.SDK_INT >= 27) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) return manager.isNotificationListenerAccessGranted(self);
        }
        String enabled = android.provider.Settings.Secure.getString(context.getContentResolver(), "enabled_notification_listeners");
        return enabled != null && enabled.contains(self.flattenToString());
    }
}

