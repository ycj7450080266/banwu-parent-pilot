package cn.banwu.parentpilot;

import org.json.JSONException;
import org.json.JSONArray;
import org.json.JSONObject;

public final class TodoItem {
    public static final String CATEGORY_TASK = "task";
    public static final String CATEGORY_CHECKIN = "checkin";
    public static final String CATEGORY_NOTICE = "notice";
    // Legacy aliases retained so existing parsers and saved data remain compatible.
    public static final String CATEGORY_TODO = CATEGORY_TASK;
    public static final String CATEGORY_REPLY = CATEGORY_TASK;
    public static final String CATEGORY_OPTIONAL = CATEGORY_TASK;
    public String id;
    public String title;
    public String due;
    public String sender;
    public String source;
    public long createdAt;
    public long updatedAt;
    public boolean done;
    public long completedAt;
    public String category;
    public long dueAt;
    public String eventKey;
    public boolean overdueDeferred;
    public String groupName;
    public String summary;
    public boolean homeworkPack;
    public JSONArray homeworkSteps;

    public TodoItem(String id, String title, String due, String sender, String source, long createdAt, boolean done, long completedAt) {
        this(id, title, due, sender, source, createdAt, done, completedAt, CATEGORY_TODO);
    }

    public TodoItem(String id, String title, String due, String sender, String source, long createdAt, boolean done, long completedAt, String category) {
        this(id, title, due, sender, source, createdAt, done, completedAt, category, 0L, "", false, "");
    }

    public TodoItem(String id, String title, String due, String sender, String source, long createdAt, boolean done,
                    long completedAt, String category, long dueAt, String eventKey, boolean overdueDeferred) {
        this(id, title, due, sender, source, createdAt, done, completedAt, category, dueAt, eventKey, overdueDeferred, "");
    }

    public TodoItem(String id, String title, String due, String sender, String source, long createdAt, boolean done,
                    long completedAt, String category, long dueAt, String eventKey, boolean overdueDeferred, String groupName) {
        this(id, title, due, sender, source, createdAt, done, completedAt, category, dueAt, eventKey, overdueDeferred, groupName, createdAt);
    }

    public TodoItem(String id, String title, String due, String sender, String source, long createdAt, boolean done,
                    long completedAt, String category, long dueAt, String eventKey, boolean overdueDeferred, String groupName, long updatedAt) {
        this(id, title, due, sender, source, createdAt, done, completedAt, category, dueAt, eventKey, overdueDeferred, groupName, updatedAt, "");
    }

    public TodoItem(String id, String title, String due, String sender, String source, long createdAt, boolean done,
                    long completedAt, String category, long dueAt, String eventKey, boolean overdueDeferred, String groupName, long updatedAt, String summary) {
        this(id, title, due, sender, source, createdAt, done, completedAt, category, dueAt, eventKey,
                overdueDeferred, groupName, updatedAt, summary, false, new JSONArray());
    }

    public TodoItem(String id, String title, String due, String sender, String source, long createdAt, boolean done,
                    long completedAt, String category, long dueAt, String eventKey, boolean overdueDeferred,
                    String groupName, long updatedAt, String summary, boolean homeworkPack, JSONArray homeworkSteps) {
        this.id = id;
        this.title = title;
        this.due = due;
        this.sender = sender;
        this.source = source;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt > 0 ? updatedAt : createdAt;
        this.done = done;
        this.completedAt = completedAt;
        this.category = normalizeCategory(category);
        this.dueAt = dueAt;
        this.eventKey = eventKey == null ? "" : eventKey;
        this.overdueDeferred = overdueDeferred;
        this.groupName = groupName == null ? "" : groupName;
        this.summary = summary == null ? "" : summary;
        this.homeworkPack = homeworkPack;
        this.homeworkSteps = homeworkSteps == null ? new JSONArray() : homeworkSteps;
    }

    public boolean allHomeworkStepsDone() {
        if (!homeworkPack || homeworkSteps == null || homeworkSteps.length() == 0) return false;
        for (int i = 0; i < homeworkSteps.length(); i++) {
            JSONObject step = homeworkSteps.optJSONObject(i);
            if (step != null && !step.optBoolean("done", false)) return false;
        }
        return true;
    }

    public static String normalizeCategory(String category) {
        if (CATEGORY_NOTICE.equals(category)) return CATEGORY_NOTICE;
        if (CATEGORY_CHECKIN.equals(category) || "check-in".equalsIgnoreCase(category) || "\u6253\u5361".equals(category)) return CATEGORY_CHECKIN;
        // Existing reply/optional/todo values become the unified Task category.
        return CATEGORY_TASK;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject j = new JSONObject();
        j.put("id", id); j.put("title", title); j.put("due", due); j.put("sender", sender);
        j.put("source", source); j.put("createdAt", createdAt); j.put("updatedAt", updatedAt); j.put("done", done); j.put("completedAt", completedAt);
        j.put("category", category);
        j.put("dueAt", dueAt); j.put("eventKey", eventKey); j.put("overdueDeferred", overdueDeferred); j.put("groupName", groupName); j.put("summary", summary); j.put("homeworkPack", homeworkPack); j.put("homeworkSteps", homeworkSteps == null ? new JSONArray() : homeworkSteps);
        return j;
    }

    public static TodoItem fromJson(JSONObject j) throws JSONException {
        boolean done = j.optBoolean("done");
        return new TodoItem(j.getString("id"), j.getString("title"), j.optString("due", "未注明时间"),
                j.optString("sender", "微信群通知"), j.optString("source", ""), j.optLong("createdAt"), done,
                j.optLong("completedAt", 0L), j.optString("category", CATEGORY_TODO),
                j.optLong("dueAt", 0L), j.optString("eventKey", ""), j.optBoolean("overdueDeferred", false), j.optString("groupName", ""), j.optLong("updatedAt", j.optLong("createdAt")), j.optString("summary", ""), j.optBoolean("homeworkPack", false), j.optJSONArray("homeworkSteps"));
    }
}
