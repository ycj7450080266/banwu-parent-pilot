package cn.banwu.parentpilot;

import org.json.JSONException;
import org.json.JSONObject;

public final class TodoItem {
    public static final String CATEGORY_TODO = "todo";
    public static final String CATEGORY_REPLY = "reply";
    public static final String CATEGORY_NOTICE = "notice";
    public static final String CATEGORY_OPTIONAL = "optional";
    public String id;
    public String title;
    public String due;
    public String sender;
    public String source;
    public long createdAt;
    public boolean done;
    public long completedAt;
    public String category;

    public TodoItem(String id, String title, String due, String sender, String source, long createdAt, boolean done, long completedAt) {
        this(id, title, due, sender, source, createdAt, done, completedAt, CATEGORY_TODO);
    }

    public TodoItem(String id, String title, String due, String sender, String source, long createdAt, boolean done, long completedAt, String category) {
        this.id = id;
        this.title = title;
        this.due = due;
        this.sender = sender;
        this.source = source;
        this.createdAt = createdAt;
        this.done = done;
        this.completedAt = completedAt;
        this.category = normalizeCategory(category);
    }

    public static String normalizeCategory(String category) {
        if (CATEGORY_REPLY.equals(category) || CATEGORY_NOTICE.equals(category) || CATEGORY_OPTIONAL.equals(category)) return category;
        return CATEGORY_TODO;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject j = new JSONObject();
        j.put("id", id); j.put("title", title); j.put("due", due); j.put("sender", sender);
        j.put("source", source); j.put("createdAt", createdAt); j.put("done", done); j.put("completedAt", completedAt);
        j.put("category", category);
        return j;
    }

    public static TodoItem fromJson(JSONObject j) throws JSONException {
        boolean done = j.optBoolean("done");
        return new TodoItem(j.getString("id"), j.getString("title"), j.optString("due", "未注明时间"),
                j.optString("sender", "微信群通知"), j.optString("source", ""), j.optLong("createdAt"), done,
                j.optLong("completedAt", 0L), j.optString("category", CATEGORY_TODO));
    }
}
