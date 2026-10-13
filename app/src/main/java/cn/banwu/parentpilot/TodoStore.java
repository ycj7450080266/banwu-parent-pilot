package cn.banwu.parentpilot;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class TodoStore {
    private static final String PREFS = "pilot_local_data";
    private static final String ITEMS = "items";
    private static final String PREF_CHILD = "child_name";
    private static final String PREF_GROUP = "group_name";
    private static final String PREF_SCOPE_KEYWORDS = "scope_keywords";
    private static final String PREF_TASK_KEYWORDS = "task_keywords";
    private static final String PREF_LAST_DIAGNOSTIC = "last_notification_diagnostic";
    private static final String PREF_DIAGNOSTIC_HISTORY = "notification_diagnostic_history";
    private static final int MAX_DIAGNOSTIC_EVENTS = 400;
    private static final String PREF_PAUSED = "paused";
    private static final String PREF_AI_ENABLED = "ai_enabled";
    private static final String PREF_AI_URL = "ai_url";
    private static final String PREF_AI_MODEL = "ai_model";
    private static final String PREF_AI_KEY = "ai_key";
    private static final String PREF_AI_CONSENT = "ai_cloud_consent";
    private static final String DEFAULT_SCOPE_KEYWORDS = "家长,家长们,亲爱的家长,亲爱的家长们,各位家长,各位家长们,全体家长,请家长,@所有人,@全体成员,@全体家长,@全体同学,全体同学,同学们,各位同学,全班同学,全班,班级统一,孩子们,小朋友们";
    private static final String PREVIOUS_DEFAULT_SCOPE_KEYWORDS = "家长,家长们,亲爱的家长们,各位家长,各位家长们,全体家长,请家长,@所有人,全体同学,同学们,各位同学,全班,班级统一";
    private static final String DEFAULT_TASK_KEYWORDS = "作业,练习,试卷,卷子,考试,测验,听写,默写,背诵,朗读,阅读,抄写,订正,预习,复习,生字,课文,绘画,画画,手工,答题,做题,提交,上交,上传,补交,补做,填写,填报,签字,签署,签名,回执,回复,确认,反馈,报数,接龙,打卡,缴费,报名,申请书,请假条,带回,带去,带好,带上,带来,准备好,穿校服,领取,归还,收集,统计,检查,请假";
    private static final String PREVIOUS_DEFAULT_TASK_KEYWORDS = "提交,上交,上传,准备,带上,带来,带,交,作业,练习,试卷,卷子,考试,测验,听写,默写,背诵,朗读,阅读,抄写,订正,预习,复习,签字,回执,缴费,报名,接龙,打卡,收集,领取,归还,统计,检查,请假,回复,确认,反馈,报数";
    // Do not ship personal child or school information as repository defaults.
    private static final String DEFAULT_CHILD = "";
    private static final String DEFAULT_GROUP = "";
    private TodoStore() {}

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    public static String child(Context c) {
        SharedPreferences p = prefs(c);
        return p.contains(PREF_CHILD) ? decode(p.getString(PREF_CHILD, "")) : DEFAULT_CHILD;
    }
    public static String group(Context c) {
        List<String> groups = groups(c);
        StringBuilder joined = new StringBuilder();
        for (String group : groups) {
            if (joined.length() > 0) joined.append("、");
            joined.append(group);
        }
        return joined.length() == 0 ? DEFAULT_GROUP : joined.toString();
    }
    public static List<String> groups(Context c) {
        SharedPreferences p = prefs(c);
        String saved = p.contains(PREF_GROUP) ? decode(p.getString(PREF_GROUP, "")) : DEFAULT_GROUP;
        ArrayList<String> result = new ArrayList<>();
        for (String part : saved.split("[,，、;；\\n\\r]+")) {
            String group = part.trim();
            if (group.isEmpty()) continue;
            boolean duplicate = false;
            for (String existing : result) if (existing.equalsIgnoreCase(group)) { duplicate = true; break; }
            if (!duplicate) result.add(group);
        }
        return result;
    }
    public static String scopeKeywords(Context c) {
        SharedPreferences p = prefs(c);
        String saved = p.contains(PREF_SCOPE_KEYWORDS) ? decode(p.getString(PREF_SCOPE_KEYWORDS, "")) : "";
        if (saved.isEmpty() || saved.equals(PREVIOUS_DEFAULT_SCOPE_KEYWORDS)) {
            if (!saved.isEmpty()) p.edit().putString(PREF_SCOPE_KEYWORDS, encode(DEFAULT_SCOPE_KEYWORDS)).apply();
            return DEFAULT_SCOPE_KEYWORDS;
        }
        return saved;
    }
    public static String taskKeywords(Context c) {
        SharedPreferences p = prefs(c);
        String saved = p.contains(PREF_TASK_KEYWORDS) ? decode(p.getString(PREF_TASK_KEYWORDS, "")) : "";
        if (saved.isEmpty() || saved.equals(PREVIOUS_DEFAULT_TASK_KEYWORDS)) {
            if (!saved.isEmpty()) p.edit().putString(PREF_TASK_KEYWORDS, encode(DEFAULT_TASK_KEYWORDS)).apply();
            return DEFAULT_TASK_KEYWORDS;
        }
        return saved;
    }
    public static String defaultScopeKeywords() { return DEFAULT_SCOPE_KEYWORDS; }
    public static String defaultTaskKeywords() { return DEFAULT_TASK_KEYWORDS; }
    public static boolean paused(Context c) { return prefs(c).getBoolean(PREF_PAUSED, false); }
    public static void saveSetup(Context c, String child, String group) {
        ArrayList<String> groups = new ArrayList<>();
        for (String part : (group == null ? "" : group).split("[,，、;；\\n\\r]+")) {
            String value = part.trim();
            if (value.isEmpty()) continue;
            boolean duplicate = false;
            for (String existing : groups) if (existing.equalsIgnoreCase(value)) { duplicate = true; break; }
            if (!duplicate) groups.add(value);
        }
        StringBuilder storedGroups = new StringBuilder();
        for (String value : groups) {
            if (storedGroups.length() > 0) storedGroups.append('\n');
            storedGroups.append(value);
        }
        prefs(c).edit().putString(PREF_CHILD, encode(child.trim())).putString(PREF_GROUP, encode(storedGroups.toString())).apply();
        TodoWidgetProvider.refresh(c);
    }
    public static void saveKeywordSettings(Context c, String scope, String task) {
        prefs(c).edit().putString(PREF_SCOPE_KEYWORDS, encode(scope.trim()))
                .putString(PREF_TASK_KEYWORDS, encode(task.trim())).apply();
    }
    public static synchronized void recordDiagnostic(Context c, String status) {
        SharedPreferences p = prefs(c);
        long at = System.currentTimeMillis();
        JSONArray events = new JSONArray();
        try { events = new JSONArray(p.getString(PREF_DIAGNOSTIC_HISTORY, "[]")); }
        catch (JSONException ignored) { }
        JSONObject event = new JSONObject();
        try {
            event.put("at", at);
            event.put("status", status == null ? "" : status);
            events.put(event);
        } catch (JSONException ignored) { }
        while (events.length() > MAX_DIAGNOSTIC_EVENTS) events.remove(0);
        p.edit().putString(PREF_LAST_DIAGNOSTIC, status == null ? "" : status)
                .putLong("last_notification_diagnostic_at", at)
                .putString(PREF_DIAGNOSTIC_HISTORY, events.toString()).apply();
    }
    public static synchronized String diagnosticHistory(Context c) {
        SharedPreferences p = prefs(c);
        JSONArray events;
        try { events = new JSONArray(p.getString(PREF_DIAGNOSTIC_HISTORY, "[]")); }
        catch (JSONException ignored) { events = new JSONArray(); }
        if (events.length() == 0) {
            String previous = p.getString(PREF_LAST_DIAGNOSTIC, "");
            long previousAt = p.getLong("last_notification_diagnostic_at", 0L);
            if (!previous.isEmpty() && previousAt > 0) {
                JSONObject event = new JSONObject();
                try {
                    event.put("at", previousAt);
                    event.put("status", previous);
                    events.put(event);
                    p.edit().putString(PREF_DIAGNOSTIC_HISTORY, events.toString()).apply();
                } catch (JSONException ignored) { }
            }
        }
        StringBuilder out = new StringBuilder("班级消息待办｜通知处理诊断\n记录条数：").append(events.length()).append("\n");
        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("M月d日 HH:mm:ss", java.util.Locale.CHINA);
        for (int i = events.length() - 1; i >= 0; i--) {
            JSONObject event = events.optJSONObject(i);
            if (event == null) continue;
            out.append(format.format(new java.util.Date(event.optLong("at"))))
                    .append("  ").append(event.optString("status", "")).append('\n');
        }
        if (events.length() == 0) out.append("尚无通知处理记录\n");
        out.append("\n说明：仅记录处理步骤与关键词匹配结果，不保存群消息正文。");
        return out.toString();
    }
    public static String lastDiagnostic(Context c) {
        SharedPreferences p = prefs(c);
        String status = p.getString(PREF_LAST_DIAGNOSTIC, "尚未收到微信通知");
        long at = p.getLong("last_notification_diagnostic_at", 0L);
        if (at == 0L) return status;
        return new java.text.SimpleDateFormat("M月d日 HH:mm:ss", java.util.Locale.CHINA).format(new java.util.Date(at)) + "\n" + status;
    }
    public static void setPaused(Context c, boolean paused) { prefs(c).edit().putBoolean(PREF_PAUSED, paused).apply(); }

    public static boolean aiEnabled(Context c) { return prefs(c).getBoolean(PREF_AI_ENABLED, false); }
    public static boolean aiConsent(Context c) { return prefs(c).getBoolean(PREF_AI_CONSENT, false); }
    public static String aiUrl(Context c) { return decode(prefs(c).getString(PREF_AI_URL, "https://api.openai.com/v1")); }
    public static String aiModel(Context c) { return decode(prefs(c).getString(PREF_AI_MODEL, "")); }
    public static String aiKey(Context c) { return decode(prefs(c).getString(PREF_AI_KEY, "")); }
    public static void saveAiSettings(Context c, String url, String model, String newKey, boolean enabled, boolean consent, boolean clearKey) {
        SharedPreferences p = prefs(c);
        SharedPreferences.Editor e = p.edit().putString(PREF_AI_URL, encode(url.trim()))
                .putString(PREF_AI_MODEL, encode(model.trim())).putBoolean(PREF_AI_ENABLED, enabled)
                .putBoolean(PREF_AI_CONSENT, consent);
        if (clearKey) e.putString(PREF_AI_KEY, encode(""));
        else if (newKey != null && !newKey.trim().isEmpty()) e.putString(PREF_AI_KEY, encode(newKey.trim()));
        e.apply();
    }

    public static synchronized List<TodoItem> all(Context c) {
        ArrayList<TodoItem> result = new ArrayList<>();
        try {
            String raw = decode(prefs(c).getString(ITEMS, ""));
            JSONArray a = new JSONArray(raw.isEmpty() ? "[]" : raw);
            for (int i = 0; i < a.length(); i++) {
                TodoItem item = TodoItem.fromJson(a.getJSONObject(i));
                result.add(item);
            }
        } catch (JSONException ignored) { }
        boolean mergedMisclassified = mergeDuplicateOneOffCheckins(result);
        boolean correctedHomeworkDeadlines = correctSameDayMorningHomeworkDeadline(result);
        Collections.sort(result, (x, y) -> Long.compare(y.createdAt, x.createdAt));
        if (mergedMisclassified || correctedHomeworkDeadlines) save(c, result);
        try {
            String raw = decode(prefs(c).getString(ITEMS, ""));
            JSONArray stored = new JSONArray(raw.isEmpty() ? "[]" : raw);
            if (stored.length() != result.size()) save(c, result);
        } catch (JSONException ignored) { }
        return result;
    }

    private static boolean correctSameDayMorningHomeworkDeadline(List<TodoItem> items) {
        boolean changed=false;
        java.text.SimpleDateFormat label=new java.text.SimpleDateFormat("M\u6708d\u65e5 HH:mm",java.util.Locale.CHINA);
        java.util.Calendar created=java.util.Calendar.getInstance(), due=java.util.Calendar.getInstance();
        for(TodoItem item:items) {
            if(!item.homeworkPack || item.createdAt<=0 || item.dueAt<=0 || item.dueAt>=item.createdAt) continue;
            created.setTimeInMillis(item.createdAt); due.setTimeInMillis(item.dueAt);
            boolean sameDay=created.get(java.util.Calendar.YEAR)==due.get(java.util.Calendar.YEAR)
                    && created.get(java.util.Calendar.DAY_OF_YEAR)==due.get(java.util.Calendar.DAY_OF_YEAR);
            if(!sameDay || due.get(java.util.Calendar.HOUR_OF_DAY)!=9 || due.get(java.util.Calendar.MINUTE)!=0) continue;
            due.set(java.util.Calendar.HOUR_OF_DAY,21); due.set(java.util.Calendar.MINUTE,0);
            due.set(java.util.Calendar.SECOND,0); due.set(java.util.Calendar.MILLISECOND,0);
            item.dueAt=due.getTimeInMillis(); item.due=label.format(due.getTime()); changed=true;
        }
        return changed;
    }

    public static synchronized boolean add(Context c, String title, String due, String sender, String source) {
        return add(c, title, due, sender, source, TodoItem.CATEGORY_TODO);
    }

    public static synchronized boolean add(Context c, String title, String due, String sender, String source, String category) {
        return addAt(c, title, due, sender, source, System.currentTimeMillis(), category);
    }

    public static synchronized boolean addAt(Context c, String title, String due, String sender, String source, long assignedAt) {
        return addAt(c, title, due, sender, source, assignedAt, TodoItem.CATEGORY_TODO);
    }

    public static synchronized boolean addAt(Context c, String title, String due, String sender, String source, long assignedAt, String category) {
        List<TodoItem> items = all(c);
        String normalized = title.trim().toLowerCase();
        for (TodoItem item : items) {
            if (item.title.trim().toLowerCase().equals(normalized) && item.category.equals(TodoItem.normalizeCategory(category))
                    && Math.abs(System.currentTimeMillis() - item.createdAt) < 6 * 60 * 60 * 1000L) return false;
        }
        items.add(0, new TodoItem(UUID.randomUUID().toString(), title, due, sender, source,
                assignedAt, false, 0L, category));
        save(c, items);
        return true;
    }

    /** Compact, group-scoped context for associating new notifications with active events. */
    public static synchronized JSONArray activeContext(Context c, String groupName) {
        JSONArray context = new JSONArray();
        String key = normalizeGroupName(groupName);
        if (key.isEmpty()) return context;
        long now = System.currentTimeMillis();
        int included = 0;
        for (TodoItem item : all(c)) {
            if (item.done || !key.equals(normalizeGroupName(item.groupName))) continue;
            boolean recent = now - (item.updatedAt > 0 ? item.updatedAt : item.createdAt) <= 7L * 24 * 60 * 60 * 1000;
            boolean deadlineOpen = item.dueAt > now;
            if (!recent && !deadlineOpen) continue;
            JSONObject row = new JSONObject();
            try {
                row.put("id", item.id);
                row.put("title", item.title);
                row.put("summary", item.summary);
                row.put("category", item.category);
                row.put("due", item.due);
                row.put("event_key", item.eventKey);
                row.put("homework_pack", item.homeworkPack);
                row.put("checklist", item.homeworkSteps);
                context.put(row);
            } catch (JSONException ignored) { }
            if (++included >= 20) break;
        }
        return context;
    }

    /** Safely collapse a one-time chain item and a task when both came from the same notification text. */
    private static boolean mergeDuplicateOneOffCheckins(ArrayList<TodoItem> items) {
        boolean changed = false;
        for (int i = 0; i < items.size(); i++) {
            TodoItem a = items.get(i);
            for (int j = items.size() - 1; j > i; j--) {
                TodoItem b = items.get(j);
                boolean mismatch = (TodoItem.CATEGORY_CHECKIN.equals(a.category) && TodoItem.CATEGORY_TASK.equals(b.category))
                        || (TodoItem.CATEGORY_TASK.equals(a.category) && TodoItem.CATEGORY_CHECKIN.equals(b.category));
                if (!mismatch || Math.abs(a.createdAt - b.createdAt) > 24L * 60 * 60 * 1000) continue;
                String groupA = normalizeGroupName(a.groupName), groupB = normalizeGroupName(b.groupName);
                if (groupA.isEmpty() || !groupA.equals(groupB)) continue;
                String sa = normalizeTitle(a.source), sb = normalizeTitle(b.source);
                if (sa.length() < 40 || sb.length() < 40 || !(sa.equals(sb) || sa.contains(sb) || sb.contains(sa))) continue;
                String all = (a.title + " " + a.summary + " " + b.title + " " + b.summary + " " + a.source).toLowerCase(java.util.Locale.ROOT);
                if (containsAny(all, "每天", "每日", "每周", "每星期", "连续打卡", "连续签到", "周期打卡")) continue;
                TodoItem task = TodoItem.CATEGORY_TASK.equals(a.category) ? a : b;
                TodoItem chain = task == a ? b : a;
                task.category = TodoItem.CATEGORY_TASK;
                if (chain.title.length() < task.title.length()) task.title = chain.title;
                if (chain.summary.length() > task.summary.length()) task.summary = chain.summary;
                if (chain.source.length() > task.source.length()) task.source = chain.source;
                task.done = a.done && b.done;
                task.completedAt = task.done ? Math.max(a.completedAt, b.completedAt) : 0L;
                task.updatedAt = Math.max(a.updatedAt, b.updatedAt);
                items.remove(chain);
                changed = true;
                if (chain == a) { i--; break; }
            }
        }
        return changed;
    }

    private static boolean containsAny(String text, String... terms) {
        for (String term : terms) if (text.contains(term)) return true;
        return false;
    }

    private static String normalizeGroupName(String value) {
        if (value == null) return "";
        return value.replaceAll("[\\s\\u00A0\\u3000]+", "").replaceAll("(微信群|班级群|群聊|群)$", "");
    }

    /** Adds a classified task or updates an existing semantically identical reminder. */
    public static synchronized int addStructured(Context c, String title, String due, long dueAt, String sender,
                                                   String source, String category, String eventKey) {
        return addStructured(c, title, due, dueAt, sender, source, category, eventKey, "", "", "");
    }

    public static synchronized int addStructured(Context c, String title, String due, long dueAt, String sender,
                                                   String source, String category, String eventKey,
                                                   String groupName, String targetId) {
        return addStructured(c, title, due, dueAt, sender, source, category, eventKey, groupName, targetId, "");
    }

    public static synchronized int addStructured(Context c, String title, String due, long dueAt, String sender,
                                                   String source, String category, String eventKey,
                                                   String groupName, String targetId, String summary) {
        return addStructured(c, title, due, dueAt, sender, source, category, eventKey, groupName, targetId,
                summary, false, new JSONArray());
    }

    public static synchronized int addStructured(Context c, String title, String due, long dueAt, String sender,
                                                   String source, String category, String eventKey,
                                                   String groupName, String targetId, String summary,
                                                   boolean homeworkPack, JSONArray homeworkSteps) {
        if (title == null || title.trim().isEmpty()) return 0;
        List<TodoItem> items = all(c);
        String normalized = normalizeTitle(title);
        String groupKey = normalizeGroupName(groupName);
        long now = System.currentTimeMillis();

        boolean crossDayHomeworkUpdate = false;
        if (targetId != null && !targetId.trim().isEmpty()) {
            for (TodoItem item : items) {
                if (!targetId.equals(item.id) || item.done) continue;
                if (homeworkPack && item.homeworkPack && !sameLocalDay(dueAt, item.dueAt)) {
                    crossDayHomeworkUpdate = true;
                    break;
                }
                if (!groupKey.isEmpty() && !normalizeGroupName(item.groupName).isEmpty()
                        && !groupKey.equals(normalizeGroupName(item.groupName))) return 0;
                boolean changed = item.updatedAt != now;
                item.updatedAt = now;
                if (!item.title.equals(title.trim())) { item.title = title.trim(); changed = true; }
                if (summary != null && !summary.trim().isEmpty() && !summary.trim().equals(item.summary)) { item.summary = summary.trim(); changed = true; }
                String normalizedCategory = TodoItem.normalizeCategory(category);
                if (!item.category.equals(normalizedCategory)) { item.category = normalizedCategory; changed = true; }
                if (dueAt > 0 && dueAt != item.dueAt) { item.dueAt = dueAt; item.due = due; item.overdueDeferred = false; changed = true; }
                if ((item.eventKey == null || item.eventKey.isEmpty()) && eventKey != null && !eventKey.isEmpty()) { item.eventKey = eventKey; changed = true; }
                if ((item.groupName == null || item.groupName.isEmpty()) && !groupName.isEmpty()) { item.groupName = groupName; changed = true; }
                if (mergeHomeworkSteps(item, homeworkPack, homeworkSteps)) changed = true;
                if (appendSource(item, source, "后续补充")) changed = true;
                if (changed) { save(c, items); return 2; }
                return 0;
            }
            if (!crossDayHomeworkUpdate) return 0;
        }

        for (TodoItem item : items) {
            if (item.done || groupKey.isEmpty() || !groupKey.equals(normalizeGroupName(item.groupName))) continue;
            if (homeworkPack && item.homeworkPack && !sameLocalDay(dueAt, item.dueAt)) continue;
            boolean active = (item.dueAt > now) || (now - (item.updatedAt > 0 ? item.updatedAt : item.createdAt) <= 7L * 24 * 60 * 60 * 1000);
            boolean sameHomeworkUnit = active && homeworkPack && item.homeworkPack && sameLocalDay(dueAt, item.dueAt);
            boolean sameEvent = active && eventKey != null && !eventKey.trim().isEmpty() && eventKey.equals(item.eventKey) && normalizeTitle(item.title).equals(normalized);
            boolean sameTitle = normalizeTitle(item.title).equals(normalized)
                    && item.category.equals(TodoItem.normalizeCategory(category))
                    && Math.abs(now - item.createdAt) < 72L * 60 * 60 * 1000;
            if (!sameHomeworkUnit && !sameEvent && !sameTitle) continue;
            boolean changed = sameEvent || sameTitle || sameHomeworkUnit;
            item.updatedAt = now;
            if (!sameHomeworkUnit && sameEvent && !item.title.equals(title.trim())) { item.title = title.trim(); changed = true; }
            if ((sameEvent || sameTitle || sameHomeworkUnit) && summary != null && !summary.trim().isEmpty() && !summary.trim().equals(item.summary)) { item.summary = summary.trim(); changed = true; }
            if (mergeHomeworkSteps(item, homeworkPack, homeworkSteps)) changed = true;
            if (sameHomeworkUnit) {
                String groupedTitle = HomeworkChecklist.title((item.source == null ? "" : item.source) + " " + (source == null ? "" : source));
                if (!groupedTitle.equals("今日学习任务") && !groupedTitle.equals(item.title)) { item.title = groupedTitle; changed = true; }
            }
            if (sameEvent && !item.category.equals(TodoItem.normalizeCategory(category))) { item.category = TodoItem.normalizeCategory(category); changed = true; }
            if (dueAt > 0 && dueAt != item.dueAt) { item.dueAt = dueAt; item.due = due; item.overdueDeferred = false; changed = true; }
            if (eventKey != null && !eventKey.isEmpty() && (item.eventKey == null || item.eventKey.isEmpty())) { item.eventKey = eventKey; changed = true; }
            if (appendSource(item, source, "后续提醒")) changed = true;
            if (changed) { save(c, items); return 2; }
            return 0;
        }
        JSONArray storedSteps = buildHomeworkSteps(homeworkSteps);
        boolean isHomeworkPack = homeworkPack && storedSteps.length() > 0;
        items.add(0, new TodoItem(UUID.randomUUID().toString(), title.trim(), due == null ? "未注明时间" : due,
                sender == null || sender.trim().isEmpty() ? "爸爸" : sender.trim(), source == null ? "" : source,
                now, false, 0L, category, dueAt, eventKey, false, groupName, now, summary, isHomeworkPack, storedSteps));
        save(c, items);
        return 1;
    }

    private static boolean sameLocalDay(long first, long second) {
        if (first <= 0 || second <= 0) return false;
        java.util.Calendar a = java.util.Calendar.getInstance(); a.setTimeInMillis(first);
        java.util.Calendar b = java.util.Calendar.getInstance(); b.setTimeInMillis(second);
        return a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR)
                && a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR);
    }

    private static JSONArray buildHomeworkSteps(JSONArray input) {
        JSONArray result = new JSONArray();
        if (input == null) return result;
        for (int i = 0; i < input.length(); i++) {
            String text = input.optString(i, "").trim();
            if (text.isEmpty()) continue;
            try { result.put(new JSONObject().put("text", text).put("done", false)); }
            catch (JSONException ignored) { }
        }
        return result;
    }

    private static boolean mergeHomeworkSteps(TodoItem item, boolean homeworkPack, JSONArray incoming) {
        if (!homeworkPack || incoming == null || incoming.length() == 0) return false;
        if (item.homeworkSteps == null) item.homeworkSteps = new JSONArray();
        item.homeworkPack = true;
        boolean changed = false;
        for (int i = 0; i < incoming.length(); i++) {
            String text = incoming.optString(i, "").trim();
            if (text.isEmpty()) continue;
            String key = normalizeTitle(text);
            boolean exists = false;
            for (int j = 0; j < item.homeworkSteps.length(); j++) {
                JSONObject step = item.homeworkSteps.optJSONObject(j);
                if (step != null && key.equals(normalizeTitle(step.optString("text", "")))) { exists = true; break; }
            }
            if (!exists) {
                try { item.homeworkSteps.put(new JSONObject().put("text", text).put("done", false)); changed = true; }
                catch (JSONException ignored) { }
            }
        }
        if (changed && item.allHomeworkStepsDone()) { item.done = true; item.completedAt = System.currentTimeMillis(); }
        return changed;
    }

    public static synchronized void toggleHomeworkStep(Context c, String id, int index) {
        List<TodoItem> items = all(c);
        long now = System.currentTimeMillis();
        for (TodoItem item : items) if (item.id.equals(id) && item.homeworkPack && item.homeworkSteps != null) {
            JSONObject step = item.homeworkSteps.optJSONObject(index);
            if (step == null) return;
            try { step.put("done", !step.optBoolean("done", false)); }
            catch (JSONException ignored) { return; }
            item.done = item.allHomeworkStepsDone();
            item.completedAt = item.done ? now : 0L;
            item.updatedAt = now;
            save(c, items);
            return;
        }
    }

    private static boolean appendSource(TodoItem item, String source, String label) {
        if (source == null || source.trim().isEmpty() || item.source.contains(source.trim())) return false;
        item.source = item.source.isEmpty() ? source.trim() : item.source + "\n\n" + label + "：" + source.trim();
        return true;
    }
    private static String normalizeTitle(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT).replaceAll("[\\s，。！？!?、；;：:（）()【】\\[\\]]+", "").trim();
    }

    public static synchronized void defer(Context c, String id, long newDueAt) {
        List<TodoItem> items = all(c);
        for (TodoItem item : items) if (item.id.equals(id)) {
            item.dueAt = newDueAt;
            item.due = new java.text.SimpleDateFormat("M月d日 HH:mm", java.util.Locale.CHINA).format(new java.util.Date(newDueAt));
            item.overdueDeferred = true;
        }
        save(c, items);
    }

    public static synchronized void toggle(Context c, String id) {
        List<TodoItem> items = all(c);
        for (TodoItem item : items) {
            if (item.id.equals(id)) {
                if (item.homeworkPack) continue;
                item.done = !item.done;
                item.completedAt = item.done ? System.currentTimeMillis() : 0L;
            }
        }
        save(c, items);
    }

    public static synchronized void remove(Context c, String id) {
        List<TodoItem> items = all(c);
        items.removeIf(item -> item.id.equals(id));
        save(c, items);
    }

    private static void save(Context c, List<TodoItem> items) {
        JSONArray a = new JSONArray();
        try { for (TodoItem item : items) a.put(item.toJson()); }
        catch (JSONException ignored) { }
        prefs(c).edit().putString(ITEMS, encode(a.toString())).apply();
        TodoWidgetProvider.refresh(c);
        c.sendBroadcast(new android.content.Intent(MainActivity.ACTION_REFRESH).setPackage(c.getPackageName()));
    }

    private static String encode(String value) { return "enc:" + LocalCrypto.encrypt(value); }
    private static String decode(String value) {
        if (value == null || value.isEmpty()) return "";
        return value.startsWith("enc:") ? LocalCrypto.decrypt(value.substring(4)) : value;
    }
}
