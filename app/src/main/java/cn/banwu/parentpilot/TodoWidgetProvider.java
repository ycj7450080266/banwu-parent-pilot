package cn.banwu.parentpilot;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.widget.RemoteViews;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TodoWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_COMPLETE_TASK = "cn.banwu.parentpilot.widget.COMPLETE_TASK";
    public static final String ACTION_COMPLETE_HOMEWORK_STEP = "cn.banwu.parentpilot.widget.COMPLETE_HOMEWORK_STEP";
    private static final String EXTRA_TASK_ID = "task_id";
    private static final String EXTRA_STEP_INDEX = "step_index";
    private static final int MAX_ROWS = 16;
    private static final int[] ROW_IDS = {R.id.widget_row_1, R.id.widget_row_2, R.id.widget_row_3, R.id.widget_row_4, R.id.widget_row_5, R.id.widget_row_6, R.id.widget_row_7, R.id.widget_row_8, R.id.widget_row_9, R.id.widget_row_10, R.id.widget_row_11, R.id.widget_row_12, R.id.widget_row_13, R.id.widget_row_14, R.id.widget_row_15, R.id.widget_row_16};
    private static final int[] MARK_IDS = {R.id.widget_mark_1, R.id.widget_mark_2, R.id.widget_mark_3, R.id.widget_mark_4, R.id.widget_mark_5, R.id.widget_mark_6, R.id.widget_mark_7, R.id.widget_mark_8, R.id.widget_mark_9, R.id.widget_mark_10, R.id.widget_mark_11, R.id.widget_mark_12, R.id.widget_mark_13, R.id.widget_mark_14, R.id.widget_mark_15, R.id.widget_mark_16};
    private static final int[] TITLE_IDS = {R.id.widget_task_1, R.id.widget_task_2, R.id.widget_task_3, R.id.widget_task_4, R.id.widget_task_5, R.id.widget_task_6, R.id.widget_task_7, R.id.widget_task_8, R.id.widget_task_9, R.id.widget_task_10, R.id.widget_task_11, R.id.widget_task_12, R.id.widget_task_13, R.id.widget_task_14, R.id.widget_task_15, R.id.widget_task_16};
    private static final int[] META_IDS = {R.id.widget_meta_1, R.id.widget_meta_2, R.id.widget_meta_3, R.id.widget_meta_4, R.id.widget_meta_5, R.id.widget_meta_6, R.id.widget_meta_7, R.id.widget_meta_8, R.id.widget_meta_9, R.id.widget_meta_10, R.id.widget_meta_11, R.id.widget_meta_12, R.id.widget_meta_13, R.id.widget_meta_14, R.id.widget_meta_15, R.id.widget_meta_16};
    private static final int DARK = Color.rgb(32, 37, 34);
    private static final int MUTED = Color.rgb(132, 139, 134);
    private static final int GREEN = Color.rgb(51, 132, 107);

    private static final class WidgetEntry {
        final TodoItem item;
        final int stepIndex;
        final String title;
        WidgetEntry(TodoItem item, int stepIndex, String title) {
            this.item = item;
            this.stepIndex = stepIndex;
            this.title = title;
        }
        boolean homeworkStep() { return stepIndex >= 0; }
    }

    public static void refresh(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, TodoWidgetProvider.class));
        if (ids.length > 0) new TodoWidgetProvider().onUpdate(context, manager, ids);
    }

    @Override public void onReceive(Context context, Intent intent) {
        if (ACTION_COMPLETE_TASK.equals(intent.getAction())) {
            String id = intent.getStringExtra(EXTRA_TASK_ID);
            if (id != null && !id.isEmpty()) TodoStore.toggle(context, id);
            return;
        }
        if (ACTION_COMPLETE_HOMEWORK_STEP.equals(intent.getAction())) {
            String id = intent.getStringExtra(EXTRA_TASK_ID);
            int index = intent.getIntExtra(EXTRA_STEP_INDEX, -1);
            if (id != null && !id.isEmpty() && index >= 0) TodoStore.toggleHomeworkStep(context, id, index);
            return;
        }
        super.onReceive(context, intent);
    }

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        List<WidgetEntry> entries = buildEntries(TodoStore.all(context));
        String child = TodoStore.child(context);
        for (int id : ids) {
            int rowLimit = visibleRows(manager.getAppWidgetOptions(id));
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.todo_widget);
            views.setTextViewText(R.id.widget_title, child.isEmpty() ? "\u73ed\u7ea7\u6d88\u606f\u5f85\u529e" : child + "\u73ed\u7ea7\u6d88\u606f\u5f85\u529e");
            // Keep the widget private and uncluttered: show only the configured child title and pending items.
            views.setViewVisibility(R.id.widget_subtitle, android.view.View.GONE);
            views.setViewVisibility(R.id.widget_empty, entries.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);

            for (int i = 0; i < MAX_ROWS; i++) {
                int rowId = ROW_IDS[i];
                if (i >= rowLimit || i >= entries.size()) {
                    views.setViewVisibility(rowId, android.view.View.GONE);
                    continue;
                }
                WidgetEntry entry = entries.get(i);
                TodoItem item = entry.item;
                views.setViewVisibility(rowId, android.view.View.VISIBLE);
                views.setTextViewText(TITLE_IDS[i], entry.title);
                views.setInt(TITLE_IDS[i], "setTextColor", DARK);
                views.setTextViewText(MARK_IDS[i], "\u25cb");
                views.setInt(MARK_IDS[i], "setTextColor", GREEN);
                views.setContentDescription(MARK_IDS[i], "\u70b9\u6309\u6807\u8bb0\u5b8c\u6210");
                String meta = deadlineLabel(item);
                views.setTextViewText(META_IDS[i], meta);
                views.setViewVisibility(META_IDS[i], meta.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
                boolean overdue = item.dueAt > 0 && item.dueAt < System.currentTimeMillis();
                views.setInt(META_IDS[i], "setTextColor", overdue ? 0xFFB64037 : MUTED);

                Intent action = new Intent(context, TodoWidgetProvider.class)
                        .putExtra(EXTRA_TASK_ID, item.id);
                if (entry.homeworkStep()) {
                    action.setAction(ACTION_COMPLETE_HOMEWORK_STEP)
                            .putExtra(EXTRA_STEP_INDEX, entry.stepIndex)
                            .setData(android.net.Uri.parse("banwu://widget/homework/" + item.id + "/" + entry.stepIndex));
                } else {
                    action.setAction(ACTION_COMPLETE_TASK)
                            .setData(android.net.Uri.parse("banwu://widget/task/" + item.id));
                }
                PendingIntent pending = PendingIntent.getBroadcast(context, action.getData().toString().hashCode(), action,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                views.setOnClickPendingIntent(rowId, pending);
            }

            Intent launch = new Intent(context, MainActivity.class);
            PendingIntent openApp = PendingIntent.getActivity(context, id, launch,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_root, openApp);
            manager.updateAppWidget(id, views);
        }
    }

    private static List<WidgetEntry> buildEntries(List<TodoItem> items) {
        ArrayList<TodoItem> sorted = new ArrayList<>(items);
        sorted.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));
        ArrayList<WidgetEntry> entries = new ArrayList<>();
        for (TodoItem item : sorted) {
            if (item.homeworkPack) {
                if (item.done || item.homeworkSteps == null) continue;
                for (int i = 0; i < item.homeworkSteps.length(); i++) {
                    org.json.JSONObject step = item.homeworkSteps.optJSONObject(i);
                    if (step == null) continue;
                    String text = step.optString("text", "").trim();
                    if (!text.isEmpty() && !step.optBoolean("done", false)) entries.add(new WidgetEntry(item, i, "作业｜" + text));
                }
                continue;
            }
            if (item.done) continue;
            String category = TodoItem.normalizeCategory(item.category);
            String label;
            if (TodoItem.CATEGORY_NOTICE.equals(category)) label = "通知";
            else if (TodoItem.CATEGORY_CHECKIN.equals(category)) label = "打卡";
            else label = "任务";
            entries.add(new WidgetEntry(item, -1, label + "｜" + item.title));
        }
        return entries;
    }

    @Override public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int appWidgetId, android.os.Bundle newOptions) {
        onUpdate(context, manager, new int[]{appWidgetId});
    }

    private static int visibleRows(android.os.Bundle options) {
        int height = options == null ? 234 : options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 234);
        return Math.max(1, Math.min(MAX_ROWS, (height - 72) / 48));
    }

    private static String deadlineLabel(TodoItem item) {
        if (item.dueAt <= 0) {
            String due = item.due == null ? "" : item.due.trim();
            if (due.isEmpty() || "null".equalsIgnoreCase(due) || "未注明时间".equals(due) || "未设置截止时间".equals(due)) return "";
            return "截止 " + due;
        }
        boolean overdue = item.dueAt < System.currentTimeMillis();
        String prefix = overdue ? "\u5df2\u8d85\u65f6 " : "\u622a\u6b62 ";
        return prefix + dueWithWeekday(item.dueAt);
    }

    private static String dueWithWeekday(long time) {
        String day = new SimpleDateFormat("M\u6708d\u65e5", Locale.CHINA).format(new Date(time));
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.setTimeInMillis(time);
        String[] weekdays = {"\u661f\u671f\u65e5", "\u661f\u671f\u4e00", "\u661f\u671f\u4e8c", "\u661f\u671f\u4e09", "\u661f\u671f\u56db", "\u661f\u671f\u4e94", "\u661f\u671f\u516d"};
        String clock = new SimpleDateFormat("HH:mm", Locale.CHINA).format(new Date(time));
        return day + " \u00b7 " + weekdays[calendar.get(java.util.Calendar.DAY_OF_WEEK) - 1] + " " + clock;
    }
}
