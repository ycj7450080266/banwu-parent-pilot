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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WeChatNotificationListener extends NotificationListenerService {
    private static final String WECHAT_PACKAGE = "com.tencent.mm";
    private static final String CHANNEL = "new_todos";
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    @Override public void onListenerConnected() {
        super.onListenerConnected();
        TodoStore.recordDiagnostic(this, "通知监听服务已连接，等待微信通知");
    }

    @Override public void onListenerDisconnected() {
        TodoStore.recordDiagnostic(this, "通知监听服务已断开；请重新开启通知访问");
        super.onListenerDisconnected();
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
        TodoStore.recordDiagnostic(this, "已收到微信通知正文，正在匹配群名和关键词");
        final String messageBody = body;
        worker.execute(() -> process(title, subtitle, conversation, messageBody));
    }

    private void process(String title, String subtitle, String conversation, String body) {
        if (TodoStore.paused(this)) {
            TodoStore.recordDiagnostic(this, "收到通知后整理被暂停，未加入待办");
            return;
        }
        String child = TodoStore.child(this);
        String group = TodoStore.group(this);
        if (child.isEmpty() || group.isEmpty()) {
            TodoStore.recordDiagnostic(this, "未设置孩子姓名或班级群名");
            return;
        }
        String visibleHeader = title + " " + subtitle + " " + conversation;
        if (!matchesGroup(visibleHeader, group)) {
            TodoStore.recordDiagnostic(this, "收到微信通知，但标题群名与设置不匹配；已兼容空格和末尾“群”字，请核对群名");
            return;
        }
        TodoStore.recordDiagnostic(this, "群名匹配成功，开始整理消息正文");

        String sender = title.isEmpty() || title.equals(group) ? "微信群通知" : title;
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
        TodoStore.recordDiagnostic(this, "正文处理完成；" + MessageTaskParser.diagnosticSummary(body, child, scopeKeywords, taskKeywords));
        MessageTaskParser.Candidate candidate = MessageTaskParser.parse(body, child, scopeKeywords, taskKeywords);
        if (candidate == null) {
            TodoStore.recordDiagnostic(this, "群名已匹配，但正文未通过词库筛选（任务词、孩子姓名或范围词）");
            return;
        }

        if (TodoStore.add(this, candidate.title, candidate.due, sender, body, candidate.category)) {
            TodoStore.recordDiagnostic(this, "已识别并加入“" + categoryLabel(candidate.category) + "”");
            showLocalNotice(candidate.title, candidate.due);
        } else {
            TodoStore.recordDiagnostic(this, "已识别，但同标题任务 6 小时内重复，已跳过");
        }
    }

    @Override public void onDestroy() { worker.shutdown(); super.onDestroy(); }

    private String value(CharSequence s) { return s == null ? "" : s.toString().trim(); }

    private String normalizeGroup(String value) {
        return value == null ? "" : value.replaceAll("[\\s\\u00A0\\u3000]+", "");
    }

    private boolean matchesGroup(String visibleHeader, String configuredGroup) {
        String header = canonicalGroup(visibleHeader);
        String group = canonicalGroup(configuredGroup);
        if (header.isEmpty() || group.isEmpty()) return false;
        return header.contains(group) || (group.length() >= 4 && group.contains(header));
    }

    private String canonicalGroup(String value) {
        if (value == null) return "";
        String normalized = normalizeGroup(value).replaceAll("[·•,，。:：;；_\\-—【】\\[\\]（）()]+", "");
        return normalized.replaceAll("(微信群|班级群|群聊|群)$", "");
    }

    private String categoryLabel(String category) {
        if (TodoItem.CATEGORY_REPLY.equals(category)) return "必须回复";
        if (TodoItem.CATEGORY_NOTICE.equals(category)) return "通知";
        if (TodoItem.CATEGORY_OPTIONAL.equals(category)) return "自愿事项";
        return "待办";
    }

    private void showLocalNotice(String title, String due) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(new NotificationChannel(CHANNEL, "新待办提醒", NotificationManager.IMPORTANCE_DEFAULT));
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_menu_agenda)
                .setContentTitle("群消息整理出一项待办")
                .setContentText(title + " · " + due)
                .setAutoCancel(true);
        manager.notify((title + due).hashCode(), builder.build());
    }

    public static boolean isEnabled(Context context) {
        String enabled = android.provider.Settings.Secure.getString(context.getContentResolver(), "enabled_notification_listeners");
        ComponentName self = new ComponentName(context, WeChatNotificationListener.class);
        return enabled != null && enabled.contains(self.flattenToString());
    }
}

