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
        SharedPreferences p = prefs(c);
        return p.contains(PREF_GROUP) ? decode(p.getString(PREF_GROUP, "")) : DEFAULT_GROUP;
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
        prefs(c).edit().putString(PREF_CHILD, encode(child.trim())).putString(PREF_GROUP, encode(group.trim())).apply();
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
        StringBuilder out = new StringBuilder("班务清单｜通知处理诊断\n记录条数：").append(events.length()).append("\n");
        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("M月d日 HH:mm:ss", java.util.Locale.CHINA);
        for (int i = 0; i < events.length(); i++) {
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
        Collections.sort(result, (x, y) -> Long.compare(y.createdAt, x.createdAt));
        try {
            String raw = decode(prefs(c).getString(ITEMS, ""));
            JSONArray stored = new JSONArray(raw.isEmpty() ? "[]" : raw);
            if (stored.length() != result.size()) save(c, result);
        } catch (JSONException ignored) { }
        return result;
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

    public static synchronized void toggle(Context c, String id) {
        List<TodoItem> items = all(c);
        for (TodoItem item : items) {
            if (item.id.equals(id)) {
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
