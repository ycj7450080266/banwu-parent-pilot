package cn.banwu.parentpilot;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.widget.RemoteViews;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StrikethroughSpan;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TodoWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_COMPLETE_TASK = "cn.banwu.parentpilot.widget.COMPLETE_TASK";
    private static final String EXTRA_TASK_ID = "task_id";
    private static final int MAX_ROWS = 3;
    private static final int[] ROW_IDS = {R.id.widget_row_1, R.id.widget_row_2, R.id.widget_row_3};
    private static final int[] MARK_IDS = {R.id.widget_mark_1, R.id.widget_mark_2, R.id.widget_mark_3};
    private static final int[] TITLE_IDS = {R.id.widget_task_1, R.id.widget_task_2, R.id.widget_task_3};
    private static final int[] META_IDS = {R.id.widget_meta_1, R.id.widget_meta_2, R.id.widget_meta_3};
    private static final int DARK = Color.rgb(32, 37, 34);
    private static final int MUTED = Color.rgb(132, 139, 134);
    private static final int GREEN = Color.rgb(51, 132, 107);

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
        super.onReceive(context, intent);
    }

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        List<TodoItem> all = TodoStore.all(context);
        ArrayList<TodoItem> shownItems = new ArrayList<>();
        for (TodoItem item : all) {
            if (!TodoItem.CATEGORY_TODO.equals(TodoItem.normalizeCategory(item.category))
                    && !TodoItem.CATEGORY_REPLY.equals(TodoItem.normalizeCategory(item.category))) continue;
            if (shownItems.size() == MAX_ROWS) break;
            shownItems.add(item);
        }
        shownItems.sort((a, b) -> {
            if (a.done != b.done) return a.done ? 1 : -1;
            return Long.compare(b.createdAt, a.createdAt);
        });
        String child = TodoStore.child(context);
        String group = TodoStore.group(context);
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.todo_widget);
            views.setTextViewText(R.id.widget_title, child.isEmpty() ? "班务清单 · 待办" : child + " · 待办清单");
            views.setTextViewText(R.id.widget_subtitle, group + " · 点按勾选或取消");
            views.setViewVisibility(R.id.widget_empty, shownItems.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);

            for (int i = 0; i < MAX_ROWS; i++) {
                int rowId = ROW_IDS[i];
                if (i >= shownItems.size()) {
                    views.setViewVisibility(rowId, android.view.View.GONE);
                    continue;
                }

                TodoItem item = shownItems.get(i);
                views.setViewVisibility(rowId, android.view.View.VISIBLE);
                CharSequence title = item.title;
                if (item.done && !item.title.isEmpty()) {
                    SpannableString struck = new SpannableString(item.title);
                    struck.setSpan(new StrikethroughSpan(), 0, struck.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    title = struck;
                }
                views.setTextViewText(TITLE_IDS[i], title);
                views.setInt(TITLE_IDS[i], "setTextColor", item.done ? MUTED : DARK);
                views.setTextViewText(MARK_IDS[i], item.done ? "✓" : "○");
                views.setInt(MARK_IDS[i], "setTextColor", GREEN);
                String meta = "布置 " + shortDate(item.createdAt);
                if (item.done) meta += "  ·  完成 " + (item.completedAt > 0 ? shortDate(item.completedAt) : "时间未记录");
                else if (!item.due.isEmpty() && !"未注明时间".equals(item.due)) meta += "  ·  截止 " + item.due;
                views.setTextViewText(META_IDS[i], meta);
                views.setInt(META_IDS[i], "setTextColor", MUTED);

                Intent complete = new Intent(context, TodoWidgetProvider.class)
                            .setAction(ACTION_COMPLETE_TASK)
                            .putExtra(EXTRA_TASK_ID, item.id)
                            .setData(android.net.Uri.parse("banwu://widget/task/" + item.id));
                    PendingIntent pending = PendingIntent.getBroadcast(context, item.id.hashCode(), complete,
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

    private static String shortDate(long time) {
        if (time <= 0) return "时间未记录";
        return new SimpleDateFormat("M月d日", Locale.CHINA).format(new Date(time));
    }
}
