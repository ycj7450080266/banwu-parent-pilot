package cn.banwu.parentpilot;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.content.res.ColorStateList;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Date;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    public static final String ACTION_REFRESH = "cn.banwu.parentpilot.REFRESH";
    private static final int BLUE = Color.rgb(50, 96, 230);
    private static final int INK = Color.rgb(26, 28, 34);
    private static final int MUTED = Color.rgb(112, 116, 126);
    private static final int BG = Color.rgb(246, 247, 250);
    private LinearLayout root, content, bottomActions;
    private TextView syncStatus;
    private RadarView radarView;
    private EditText settingsChildField, settingsGroupField, settingsScopeKeywordsField, settingsTaskKeywordsField, manualTitleField;
    private TextView manualAssignedValue, manualDueValue, manualAssignerValue;
    private boolean settingsPage, manualPage;
    private String activeCategory = "";
    private boolean showCompletedCategoryItems;
    private boolean showCompletedFeed;
    private long manualAssignedAt, manualDueAt;
    private String manualAssigner = "";
    private boolean receiverRegistered;
    private final BroadcastReceiver refreshReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!settingsPage && !manualPage) { showCompletedFeed = false; renderHome(); }
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        buildRoot();
        renderHome();
    }

    @Override protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(ACTION_REFRESH);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(refreshReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(refreshReceiver, filter);
        receiverRegistered = true;
    }

    @Override protected void onStop() {
        if (receiverRegistered) { unregisterReceiver(refreshReceiver); receiverRegistered = false; }
        if (radarView != null) radarView.setRunning(false);
        super.onStop();
    }

    @Override protected void onResume() {
        super.onResume();
        if (root != null) { if (settingsPage) renderSettings(); else if (manualPage) renderManualForm(); else renderHome(); }
    }

    private void buildRoot() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG);
        setContentView(root);
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); top.setPadding(dp(22), dp(43), dp(18), dp(10));
        TextView title = text("班务清单", 26, INK, true);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout syncBadge = new LinearLayout(this); syncBadge.setGravity(Gravity.CENTER_VERTICAL);
        radarView = new RadarView(this); syncBadge.addView(radarView, new LinearLayout.LayoutParams(dp(24), dp(24)));
        syncStatus = text("同步未开启", 11, MUTED, true); syncStatus.setGravity(Gravity.CENTER_VERTICAL);
        syncStatus.setSingleLine(true); syncStatus.setMaxLines(1);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-2, -2); statusParams.setMargins(dp(4), 0, 0, 0);
        syncBadge.addView(syncStatus, statusParams);
        top.addView(syncBadge);
        root.addView(top);
        ScrollView scroller = new ScrollView(this); scroller.setFillViewport(true);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(18), dp(8), dp(18), dp(30));
        scroller.addView(content);
        root.addView(scroller, new LinearLayout.LayoutParams(-1, 0, 1));
        bottomActions = new LinearLayout(this); bottomActions.setOrientation(LinearLayout.HORIZONTAL);
        bottomActions.setGravity(Gravity.CENTER); bottomActions.setPadding(dp(24), dp(3), dp(24), dp(12));
        root.addView(bottomActions, new LinearLayout.LayoutParams(-1, -2));
    }

    private void renderHome() {
        if (content == null) return;
        settingsPage = false; manualPage = false;
        activeCategory = "";
        content.removeAllViews();
        List<TodoItem> items = TodoStore.all(this);

        updateSyncStatus();
        renderMessageTimeline(items);

        TextView note = text("识别只在本机进行；群免打扰、通知预览关闭或系统未显示通知时，可能收不到消息。", 11, MUTED, false);
        note.setPadding(dp(4), dp(16), dp(4), dp(6)); content.addView(note);
        configureHomeActions();
    }

    private void updateSyncStatus() {
        if (syncStatus == null || radarView == null) return;
        boolean enabled = WeChatNotificationListener.isEnabled(this);
        boolean paused = TodoStore.paused(this);
        String label = !enabled ? "同步未开启" : paused ? "整理已暂停" : "群消息同步已开启";
        syncStatus.setText(label);
        syncStatus.setTextColor(enabled && !paused ? Color.rgb(43, 126, 94) : MUTED);
        radarView.setRunning(enabled && !paused);
    }

    private void renderMessageTimeline(List<TodoItem> items) {
        int totalPending = 0, totalCompleted = 0;
        for (TodoItem item : items) if (item.done) totalCompleted++; else totalPending++;
        LinearLayout tabs = new LinearLayout(this); tabs.setOrientation(LinearLayout.HORIZONTAL); tabs.setPadding(dp(4), dp(4), dp(4), dp(4));
        tabs.setBackground(shape(Color.rgb(235, 237, 242), dp(14), Color.rgb(235, 237, 242)));
        TextView pendingTab = tab("待完成  " + totalPending, !showCompletedFeed);
        TextView completedTab = tab("已完成  " + totalCompleted, showCompletedFeed);
        tabs.addView(pendingTab, new LinearLayout.LayoutParams(0, dp(44), 1));
        tabs.addView(completedTab, new LinearLayout.LayoutParams(0, dp(44), 1));
        pendingTab.setOnClickListener(v -> { showCompletedFeed = false; renderHome(); });
        completedTab.setOnClickListener(v -> { showCompletedFeed = true; renderHome(); });
        content.addView(tabs, margin(0, dp(2), 0, dp(6)));

        if (items.isEmpty()) {
            emptySection(showCompletedFeed ? "还没有已完成的群消息。" : "还没有待完成的群消息。开启通知访问后，新消息会按日期显示在这里。");
            return;
        }
        ArrayList<TodoItem> sorted = new ArrayList<>(items);
        sorted.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));
        LinkedHashMap<String, ArrayList<TodoItem>> byDay = new LinkedHashMap<>();
        for (TodoItem item : sorted) {
            if (item.done != showCompletedFeed) continue;
            String key = new SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(new Date(item.createdAt));
            byDay.computeIfAbsent(key, ignored -> new ArrayList<>()).add(item);
        }
        if (byDay.isEmpty()) {
            emptySection(showCompletedFeed ? "还没有已完成的群消息。" : "没有待完成事项，太好了。");
            return;
        }
        for (ArrayList<TodoItem> dayItems : byDay.values()) {
            int pending = 0, completed = 0;
            for (TodoItem item : dayItems) if (item.done) completed++; else pending++;
            LinearLayout heading = new LinearLayout(this); heading.setGravity(Gravity.CENTER_VERTICAL);
            TextView dayTitle = text("群消息  " + formatDay(dayItems.get(0).createdAt), 15, INK, true);
            heading.addView(dayTitle, new LinearLayout.LayoutParams(0, -2, 1));
            TextView counts = text(pending + " 待完成  ·  " + completed + " 已完成", 12, BLUE, true);
            counts.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL); heading.addView(counts);
            heading.setPadding(dp(3), dp(13), dp(3), dp(8)); content.addView(heading);
            for (TodoItem item : dayItems) addTimelineItemCard(item);
        }
    }

    private void addTimelineItemCard(TodoItem item) {
        LinearLayout itemCard = card(); itemCard.setOrientation(LinearLayout.HORIZONTAL); itemCard.setGravity(Gravity.CENTER_VERTICAL);
        if (item.done) itemCard.setBackground(shape(Color.rgb(241, 243, 241), dp(17), Color.rgb(232, 235, 232)));

        TextView badge = text(categoryLabel(item.category), 11, categoryColor(item.category), true);
        badge.setGravity(Gravity.CENTER); badge.setPadding(dp(7), dp(6), dp(7), dp(6));
        badge.setBackground(shape(categoryTint(item.category), dp(10), categoryTint(item.category)));
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(-2, -2);
        badgeParams.setMargins(0, 0, dp(9), 0); itemCard.addView(badge, badgeParams);

        LinearLayout copy = new LinearLayout(this); copy.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(item.title, 14, item.done ? MUTED : INK, true);
        title.setMaxLines(3); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if (item.done) title.setPaintFlags(title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        copy.addView(title);
        TextView sender = text((item.sender.isEmpty() ? "微信群消息" : item.sender) + "  ·  " + formatDate(item.createdAt), 11, MUTED, false);
        copy.addView(sender, margin(0, dp(4), 0, 0));
        String extra = "截止：" + (item.due.isEmpty() ? "未注明" : item.due);
        if (item.done) extra += "  ·  完成：" + (item.completedAt > 0 ? formatDate(item.completedAt) : "时间未记录");
        TextView date = text(extra, 11, MUTED, false); copy.addView(date, margin(0, dp(2), 0, 0));
        itemCard.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));

        TextView check = text(item.done ? "✓" : "", 18, item.done ? Color.WHITE : BLUE, true);
        check.setGravity(Gravity.CENTER); check.setContentDescription(item.done ? "取消勾选" : "标记完成");
        check.setBackground(shape(item.done ? Color.rgb(51, 132, 107) : Color.WHITE, dp(999), item.done ? Color.rgb(51, 132, 107) : Color.rgb(204, 209, 219)));
        LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        checkParams.setMargins(dp(9), 0, 0, 0); itemCard.addView(check, checkParams);
        check.setOnClickListener(v -> {
            TodoStore.toggle(this, item.id);
            showCompletedFeed = false;
            renderHome();
            android.widget.Toast.makeText(this, item.done ? "已取消勾选" : "已标记完成", android.widget.Toast.LENGTH_SHORT).show();
        });
        itemCard.setOnClickListener(v -> showDetail(item));
        content.addView(itemCard, margin(0, 0, 0, dp(8)));
    }

    private int categoryColor(String category) {
        if (TodoItem.CATEGORY_REPLY.equals(category)) return Color.rgb(105, 76, 179);
        if (TodoItem.CATEGORY_NOTICE.equals(category)) return Color.rgb(40, 116, 99);
        if (TodoItem.CATEGORY_OPTIONAL.equals(category)) return Color.rgb(166, 103, 25);
        return BLUE;
    }

    private int categoryTint(String category) {
        if (TodoItem.CATEGORY_REPLY.equals(category)) return Color.rgb(239, 234, 250);
        if (TodoItem.CATEGORY_NOTICE.equals(category)) return Color.rgb(230, 243, 238);
        if (TodoItem.CATEGORY_OPTIONAL.equals(category)) return Color.rgb(250, 241, 225);
        return Color.rgb(232, 238, 255);
    }

    private void addCategoryCard(List<TodoItem> items, String category, String label, String helper, int accent) {
        int pending = 0, completed = 0;
        TodoItem latest = null, latestPending = null;
        for (TodoItem item : items) if (TodoItem.normalizeCategory(item.category).equals(category)) {
            if (latest == null || item.createdAt > latest.createdAt) latest = item;
            if (item.done) completed++; else { pending++; if (latestPending == null || item.createdAt > latestPending.createdAt) latestPending = item; }
        }
        TodoItem summaryItem = latestPending != null ? latestPending : latest;
        LinearLayout card = card(); card.setOrientation(LinearLayout.VERTICAL);
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(label, 16, INK, true); top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView count = text(pending + " 待完成  ·  " + completed + " 已完成   ›", 12, accent, true); count.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        top.addView(count); card.addView(top);
        String summary = summaryItem == null ? helper : summaryItem.title;
        TextView excerpt = text(summary, 13, summaryItem == null ? MUTED : INK, false);
        excerpt.setMaxLines(2); excerpt.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(excerpt, margin(0, dp(7), 0, 0));
        if (summaryItem != null) {
            String meta = (summaryItem.sender.isEmpty() ? "微信群消息" : summaryItem.sender) + "  ·  " + formatDay(summaryItem.createdAt);
            TextView source = text(meta, 11, MUTED, false); card.addView(source, margin(0, dp(3), 0, 0));
        }
        card.setClickable(true); card.setFocusable(true); card.setOnClickListener(v -> openCategory(category));
        content.addView(card, margin(0, 0, 0, dp(9)));
    }

    private void openCategory(String category) {
        activeCategory = category;
        showCompletedCategoryItems = false;
        renderCategoryDetail();
    }

    private String categoryLabel(String category) {
        if (TodoItem.CATEGORY_REPLY.equals(category)) return "回复";
        if (TodoItem.CATEGORY_NOTICE.equals(category)) return "通知";
        if (TodoItem.CATEGORY_OPTIONAL.equals(category)) return "自愿";
        return "待办";
    }

    private void renderCategoryDetail() {
        if (content == null || activeCategory.isEmpty()) return;
        settingsPage = false; manualPage = false;
        content.removeAllViews();
        List<TodoItem> all = TodoStore.all(this);
        ArrayList<TodoItem> inCategory = new ArrayList<>();
        int pending = 0, completed = 0;
        for (TodoItem item : all) if (TodoItem.normalizeCategory(item.category).equals(activeCategory)) {
            inCategory.add(item); if (item.done) completed++; else pending++;
        }
        LinearLayout heading = new LinearLayout(this); heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹  返回分类", 14, BLUE, true); back.setPadding(dp(2), dp(7), dp(12), dp(7));
        back.setOnClickListener(v -> renderHome()); heading.addView(back);
        TextView title = text(categoryLabel(activeCategory), 20, INK, true); title.setPadding(dp(5), 0, 0, 0); heading.addView(title);
        content.addView(heading, margin(0, 0, 0, dp(8)));

        LinearLayout tabs = new LinearLayout(this); tabs.setOrientation(LinearLayout.HORIZONTAL); tabs.setPadding(dp(4), dp(4), dp(4), dp(4));
        tabs.setBackground(shape(Color.rgb(235, 237, 242), dp(14), Color.rgb(235, 237, 242)));
        TextView pendingTab = tab("待完成  " + pending, !showCompletedCategoryItems);
        TextView completedTab = tab("已完成  " + completed, showCompletedCategoryItems);
        tabs.addView(pendingTab, new LinearLayout.LayoutParams(0, dp(44), 1));
        tabs.addView(completedTab, new LinearLayout.LayoutParams(0, dp(44), 1));
        pendingTab.setOnClickListener(v -> { showCompletedCategoryItems = false; renderCategoryDetail(); });
        completedTab.setOnClickListener(v -> { showCompletedCategoryItems = true; renderCategoryDetail(); });
        content.addView(tabs, margin(0, 0, 0, dp(10)));

        ArrayList<TodoItem> shown = new ArrayList<>();
        for (TodoItem item : inCategory) if (item.done == showCompletedCategoryItems) shown.add(item);
        shown.sort((a, b) -> Long.compare(categoryTime(b), categoryTime(a)));
        if (shown.isEmpty()) {
            String empty = showCompletedCategoryItems ? "这里还没有已完成内容" : "这里还没有待处理内容";
            emptySection(empty);
        } else {
            LinkedHashMap<String, ArrayList<TodoItem>> groups = new LinkedHashMap<>();
            for (TodoItem item : shown) {
                String key = new SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(new Date(categoryTime(item)));
                groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(item);
            }
            for (Map.Entry<String, ArrayList<TodoItem>> entry : groups.entrySet()) {
                section(formatDay(categoryTime(entry.getValue().get(0))));
                for (TodoItem item : entry.getValue()) addCategoryItemCard(item);
            }
        }
        configureHomeActions();
    }

    private TextView tab(String label, boolean selected) {
        TextView view = text(label, 13, selected ? BLUE : MUTED, selected);
        view.setGravity(Gravity.CENTER); view.setBackground(shape(selected ? Color.WHITE : Color.TRANSPARENT, dp(11), selected ? Color.WHITE : Color.TRANSPARENT));
        view.setClickable(true); view.setFocusable(true); return view;
    }

    private long categoryTime(TodoItem item) { return item.done && item.completedAt > 0 ? item.completedAt : item.createdAt; }

    private void addCategoryItemCard(TodoItem item) {
        LinearLayout itemCard = card(); itemCard.setOrientation(LinearLayout.HORIZONTAL); itemCard.setGravity(Gravity.CENTER_VERTICAL);
        if (item.done) itemCard.setBackground(shape(Color.rgb(241, 243, 241), dp(17), Color.rgb(232, 235, 232)));
        LinearLayout copy = new LinearLayout(this); copy.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(item.title, 15, item.done ? MUTED : INK, true); title.setMaxLines(4); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if (item.done) title.setPaintFlags(title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        copy.addView(title);
        copy.addView(text("布置人：" + (item.sender.isEmpty() ? "未填写" : item.sender), 12, MUTED, false), margin(0, dp(4), 0, 0));
        String dates = "布置日期：" + formatDate(item.createdAt) + "\n完成日期：" + (item.due.isEmpty() ? "未注明时间" : item.due)
                + (item.done ? "\n实际完成：" + (item.completedAt > 0 ? formatDate(item.completedAt) : "历史未记录") : "");
        copy.addView(text(dates, 12, MUTED, false), margin(0, dp(6), 0, 0));
        itemCard.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        TextView check = text(item.done ? "✓" : "", 18, item.done ? Color.WHITE : BLUE, true);
        check.setGravity(Gravity.CENTER); check.setContentDescription(item.done ? "取消勾选并移回待完成" : "标记完成");
        check.setBackground(shape(item.done ? Color.rgb(51, 132, 107) : Color.WHITE, dp(999), item.done ? Color.rgb(51, 132, 107) : Color.rgb(204, 209, 219)));
        LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(dp(48), dp(48)); checkParams.setMargins(dp(12), 0, 0, 0); itemCard.addView(check, checkParams);
        check.setOnClickListener(v -> {
            TodoStore.toggle(this, item.id);
            android.widget.Toast.makeText(this, item.done ? "已移回待完成" : "已移入已完成", android.widget.Toast.LENGTH_SHORT).show();
            renderCategoryDetail();
        });
        itemCard.setOnClickListener(v -> showDetail(item));
        content.addView(itemCard, margin(0, 0, 0, dp(9)));
    }

    private void renderGroupedItems(List<TodoItem> items) {
        ArrayList<TodoItem> sorted = new ArrayList<>(items);
        sorted.sort((a, b) -> {
            int byDate = Long.compare(dayBucket(groupTime(b)), dayBucket(groupTime(a)));
            if (byDate != 0) return byDate;
            if (a.done != b.done) return a.done ? 1 : -1;
            return Long.compare(groupTime(b), groupTime(a));
        });
        LinkedHashMap<String, ArrayList<TodoItem>> groups = new LinkedHashMap<>();
        for (TodoItem item : sorted) {
            String key = new SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(new Date(groupTime(item)));
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(item);
        }
        for (Map.Entry<String, ArrayList<TodoItem>> entry : groups.entrySet()) {
            ArrayList<TodoItem> dayItems = entry.getValue();
            int pending = 0, done = 0;
            for (TodoItem item : dayItems) if (item.done) done++; else pending++;
            long date = groupTime(dayItems.get(0));
            String heading = formatDay(date) + "  ·  " + (pending > 0 ? "待完成 " + pending : "")
                    + (pending > 0 && done > 0 ? "  /  " : "") + (done > 0 ? "已完成 " + done : "");
            section(heading);
            for (TodoItem item : dayItems) addItemCard(item);
        }
    }

    private long groupTime(TodoItem item) {
        return item.done && item.completedAt > 0 ? item.completedAt : item.createdAt;
    }

    private String formatDay(long time) {
        return new SimpleDateFormat("M月d日", Locale.CHINA).format(new Date(time));
    }

    private long dayBucket(long time) {
        return Long.parseLong(new SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(new Date(time)));
    }

    private void addItemCard(TodoItem item) {
        LinearLayout card = card(); card.setOrientation(LinearLayout.HORIZONTAL); card.setGravity(Gravity.CENTER_VERTICAL);
        if (item.done) card.setBackground(shape(Color.rgb(241, 243, 241), dp(17), Color.rgb(232, 235, 232)));
        LinearLayout copy = new LinearLayout(this); copy.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(item.title, 15, item.done ? MUTED : INK, true);
        if (item.done) title.setPaintFlags(title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        TextView meta = text("布置人：" + (item.sender.isEmpty() ? "未填写" : item.sender), 12, MUTED, false);
        TextView dates = text("布置日期：" + formatDate(item.createdAt) + "\n完成日期：" + (item.due.isEmpty() ? "未填写" : item.due)
                + (item.done ? "\n实际完成：" + (item.completedAt > 0 ? formatDate(item.completedAt) : "历史未记录") : ""), 12, MUTED, false);
        copy.addView(title); copy.addView(meta, margin(0, dp(4), 0, 0));
        copy.addView(dates, margin(0, dp(6), 0, 0));
        card.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        TextView check = text(item.done ? "✓" : "", 18, item.done ? Color.WHITE : BLUE, true);
        check.setGravity(Gravity.CENTER); check.setContentDescription(item.done ? "取消勾选" : "标记完成");
        check.setBackground(shape(item.done ? Color.rgb(31, 139, 93) : Color.WHITE, dp(999), item.done ? Color.rgb(31, 139, 93) : Color.rgb(204, 209, 219)));
        LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(dp(38), dp(38));
        checkParams.setMargins(dp(12), 0, 0, 0); card.addView(check, checkParams);
        check.setOnClickListener(v -> {
            TodoStore.toggle(this, item.id);
            renderHome();
            android.widget.Toast.makeText(this, item.done ? "已取消勾选" : "已记入完成历史", android.widget.Toast.LENGTH_SHORT).show();
        });
        card.setOnClickListener(v -> showDetail(item));
        content.addView(card, margin(0, 0, 0, dp(9)));
    }

    private void emptySection(String message) {
        TextView empty = text(message, 13, MUTED, false);
        empty.setPadding(dp(12), dp(12), dp(12), dp(16));
        content.addView(empty);
    }

    private String formatDate(long time) {
        if (time <= 0) return "时间未记录";
        return new SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(new Date(time));
    }

    private void renderSettings() {
        settingsPage = true; manualPage = false; content.removeAllViews();
        updateSyncStatus();
        section("同步设置");
        String child = TodoStore.child(this), group = TodoStore.group(this);
        settingsChildField = input("孩子姓名（用于识别点名事项）", child);
        settingsGroupField = input("微信群名称（按通知标题匹配）", group);
        content.addView(cardField("孩子姓名", settingsChildField));
        content.addView(cardField("班级微信群", settingsGroupField), margin(0, dp(10), 0, dp(14)));

        boolean enabled = WeChatNotificationListener.isEnabled(this);
        Button permission = button(enabled ? "通知访问已开启 · 管理权限" : "前往系统开启通知访问", enabled ? Color.rgb(229, 244, 236) : BLUE, enabled ? Color.rgb(28, 114, 72) : Color.WHITE);
        permission.setOnClickListener(v -> enableNotificationAccess());
        content.addView(permission, margin(0, 0, 0, dp(8)));
        TextView privacy = text("请在系统权限页选择“班务清单试用版”。应用只筛选微信中标题含已填写群名的可见通知；待办和必要的原通知摘录仅保存在本机，不上传群聊。你可以随时撤销通知访问或手动移除待办。", 13, MUTED, false);
        privacy.setPadding(dp(3), dp(5), dp(3), dp(16)); content.addView(privacy);

        Button pause = button(TodoStore.paused(this) ? "恢复整理" : "暂停整理", Color.WHITE, INK);
        pause.setOnClickListener(v -> {
            boolean paused = !TodoStore.paused(this);
            TodoStore.setPaused(this, paused);
            pause.setText(paused ? "恢复整理" : "暂停整理");
            updateSyncStatus();
        }); content.addView(pause, margin(0, 0, 0, dp(9)));

        section("最近通知诊断");
        TextView diagnosticStatus = text(TodoStore.lastDiagnostic(this), 13, INK, false);
        diagnosticStatus.setPadding(dp(12), dp(12), dp(12), dp(12));
        diagnosticStatus.setBackground(shape(Color.WHITE, dp(13), Color.rgb(229, 231, 236)));
        diagnosticStatus.setClickable(true);
        diagnosticStatus.setFocusable(true);
        diagnosticStatus.setOnClickListener(v -> showDiagnosticHistory());
        content.addView(diagnosticStatus, margin(0, 0, 0, dp(6)));
        TextView diagnosticHint = text("点诊断卡片查看全部处理结果并复制；仅保存步骤和关键词命中，不保存群消息正文。", 11, MUTED, false);
        content.addView(diagnosticHint, margin(dp(3), 0, dp(3), dp(5)));
        Button refreshDiagnostic = button("刷新最近通知状态", Color.WHITE, BLUE);
        refreshDiagnostic.setOnClickListener(v -> diagnosticStatus.setText(TodoStore.lastDiagnostic(this)));
        content.addView(refreshDiagnostic, margin(0, 0, 0, dp(8)));

        section("识别关键词");
        TextView keywordHelp = text("范围词用于识别面向家长或全班的消息；任务词用于判断消息是否像待办。也可命中孩子姓名。用逗号或换行分隔。词越宽泛，误识别可能越多。", 12, MUTED, false);
        keywordHelp.setPadding(dp(3), 0, dp(3), dp(9)); content.addView(keywordHelp);
        settingsScopeKeywordsField = input("例如：各位家长、家长们、@所有人", TodoStore.scopeKeywords(this));
        settingsScopeKeywordsField.setSingleLine(false); settingsScopeKeywordsField.setMinLines(3); settingsScopeKeywordsField.setMaxLines(6);
        settingsScopeKeywordsField.setGravity(Gravity.TOP | Gravity.START);
        settingsScopeKeywordsField.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        content.addView(cardField("面向范围关键词", settingsScopeKeywordsField), margin(0, 0, 0, dp(9)));

        settingsTaskKeywordsField = input("例如：作业、试卷、接龙、签字", TodoStore.taskKeywords(this));
        settingsTaskKeywordsField.setSingleLine(false); settingsTaskKeywordsField.setMinLines(4); settingsTaskKeywordsField.setMaxLines(8);
        settingsTaskKeywordsField.setGravity(Gravity.TOP | Gravity.START);
        settingsTaskKeywordsField.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        content.addView(cardField("任务内容关键词", settingsTaskKeywordsField), margin(0, 0, 0, dp(9)));
        Button resetKeywords = button("恢复默认词库", Color.WHITE, BLUE);
        resetKeywords.setOnClickListener(v -> {
            settingsScopeKeywordsField.setText(TodoStore.defaultScopeKeywords());
            settingsTaskKeywordsField.setText(TodoStore.defaultTaskKeywords());
        });
        content.addView(resetKeywords, margin(0, 0, 0, dp(10)));

        TextView pilot = text("家长端本地清单\n待办与完成历史会保留在本机，直到手动移除。当前版本不会在家长之间转发任务。", 12, MUTED, false);
        content.addView(pilot);
        configureSingleAction(android.R.drawable.ic_menu_save, "保存并返回", this::saveSettingsAndReturn);
    }

    private void showDiagnosticHistory() {
        String history = TodoStore.diagnosticHistory(this);
        TextView log = text(history, 12, INK, false);
        log.setTextIsSelectable(true);
        log.setPadding(dp(12), dp(8), dp(12), dp(8));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.addView(log, new ScrollView.LayoutParams(-1, -2));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("通知处理记录")
                .setView(scroll)
                .setPositiveButton("复制全部", null)
                .setNegativeButton("关闭", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("通知处理诊断", TodoStore.diagnosticHistory(this)));
                android.widget.Toast.makeText(this, "完整诊断记录已复制", android.widget.Toast.LENGTH_SHORT).show();
            }
        }));
        dialog.show();
    }

    private void saveSettingsAndReturn() {
        if (settingsChildField == null || settingsGroupField == null || settingsScopeKeywordsField == null || settingsTaskKeywordsField == null) return;
        TodoStore.saveSetup(this, settingsChildField.getText().toString(), settingsGroupField.getText().toString());
        TodoStore.saveKeywordSettings(this, settingsScopeKeywordsField.getText().toString(), settingsTaskKeywordsField.getText().toString());
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(settingsGroupField.getWindowToken(), 0);
        settingsChildField = null; settingsGroupField = null; settingsScopeKeywordsField = null; settingsTaskKeywordsField = null;
        renderHome();
    }

    private void renderManualForm() {
        settingsPage = false; manualPage = true; content.removeAllViews();
        updateSyncStatus();
        if (manualAssignedAt == 0) manualAssignedAt = System.currentTimeMillis();
        section("手动补充待办");

        manualTitleField = input("填写任务内容", "");
        manualTitleField.setSingleLine(false); manualTitleField.setMinLines(2); manualTitleField.setMaxLines(4);
        manualTitleField.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        content.addView(formRow("待办任务：", manualTitleField), margin(0, 0, 0, dp(12)));

        manualAssignedValue = formValue(formatDate(manualAssignedAt));
        manualAssignedValue.setOnClickListener(v -> showDateTimePicker(true));
        content.addView(formRow("布置日期：", manualAssignedValue), margin(0, 0, 0, dp(12)));

        manualDueValue = formValue(manualDueAt > 0 ? formatDate(manualDueAt) : "点击选择日期和时间");
        manualDueValue.setOnClickListener(v -> showDateTimePicker(false));
        content.addView(formRow("完成日期：", manualDueValue), margin(0, 0, 0, dp(12)));

        manualAssignerValue = formValue(manualAssigner.isEmpty() ? "选择布置人" : manualAssigner);
        manualAssignerValue.setOnClickListener(v -> showAssignerPicker());
        content.addView(formRow("布置人：", manualAssignerValue), margin(0, 0, 0, dp(14)));

        TextView helper = text("勾选任务时会自动记录实际完成时间；再次点勾可取消勾选。", 12, MUTED, false);
        content.addView(helper);
        configureManualActions();
    }

    private View formRow(String label, View value) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        TextView fixed = text(label, 14, INK, true);
        row.addView(fixed, new LinearLayout.LayoutParams(dp(88), -2));
        row.addView(value, new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }

    private TextView formValue(String value) {
        TextView field = text(value, 15, value.equals("选择布置人") || value.equals("点击选择日期和时间") ? MUTED : INK, false);
        field.setPadding(dp(13), dp(13), dp(13), dp(13));
        field.setBackground(shape(Color.WHITE, dp(13), Color.rgb(229, 231, 236)));
        field.setMinHeight(dp(48));
        return field;
    }

    private void showDateTimePicker(boolean assignmentDate) {
        Calendar current = Calendar.getInstance();
        long initial = assignmentDate ? manualAssignedAt : (manualDueAt > 0 ? manualDueAt : System.currentTimeMillis());
        current.setTimeInMillis(initial);
        int nowYear = Calendar.getInstance().get(Calendar.YEAR);
        NumberPicker year = wheel(nowYear - 5, nowYear + 10, current.get(Calendar.YEAR), null);
        NumberPicker month = wheel(1, 12, current.get(Calendar.MONTH) + 1, twoDigits(1, 12));
        NumberPicker day = wheel(1, current.getActualMaximum(Calendar.DAY_OF_MONTH), current.get(Calendar.DAY_OF_MONTH), null);
        NumberPicker hour = wheel(0, 23, current.get(Calendar.HOUR_OF_DAY), twoDigits(0, 23));
        NumberPicker minute = wheel(0, 59, current.get(Calendar.MINUTE), twoDigits(0, 59));
        year.setOnValueChangedListener((picker, oldValue, newValue) -> updateDayMaximum(day, newValue, month.getValue()));
        month.setOnValueChangedListener((picker, oldValue, newValue) -> updateDayMaximum(day, year.getValue(), newValue));

        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(12), dp(4), dp(12), dp(4));
        box.addView(text("日期（上下滑动选择）", 12, MUTED, false));
        LinearLayout dateRow = new LinearLayout(this); dateRow.setOrientation(LinearLayout.HORIZONTAL);
        addWheel(dateRow, year, "年"); addWheel(dateRow, month, "月"); addWheel(dateRow, day, "日");
        box.addView(dateRow);
        box.addView(text("时间", 12, MUTED, false));
        LinearLayout timeRow = new LinearLayout(this); timeRow.setOrientation(LinearLayout.HORIZONTAL);
        addWheel(timeRow, hour, "时"); addWheel(timeRow, minute, "分");
        box.addView(timeRow);

        new AlertDialog.Builder(this).setTitle(assignmentDate ? "选择布置日期" : "选择完成日期")
                .setView(box).setNegativeButton("取消", null).setPositiveButton("确定", (dialog, which) -> {
                    Calendar selected = Calendar.getInstance();
                    selected.set(Calendar.YEAR, year.getValue());
                    selected.set(Calendar.MONTH, month.getValue() - 1);
                    selected.set(Calendar.DAY_OF_MONTH, Math.min(day.getValue(), daysInMonth(year.getValue(), month.getValue())));
                    selected.set(Calendar.HOUR_OF_DAY, hour.getValue()); selected.set(Calendar.MINUTE, minute.getValue());
                    selected.set(Calendar.SECOND, 0); selected.set(Calendar.MILLISECOND, 0);
                    if (assignmentDate) { manualAssignedAt = selected.getTimeInMillis(); manualAssignedValue.setText(formatDate(manualAssignedAt)); }
                    else { manualDueAt = selected.getTimeInMillis(); manualDueValue.setText(formatDate(manualDueAt)); manualDueValue.setTextColor(INK); }
                }).show();
    }

    private NumberPicker wheel(int minimum, int maximum, int value, String[] labels) {
        NumberPicker picker = new NumberPicker(this); picker.setMinValue(minimum); picker.setMaxValue(maximum); picker.setValue(value);
        picker.setWrapSelectorWheel(true); picker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        if (labels != null) picker.setDisplayedValues(labels);
        return picker;
    }

    private String[] twoDigits(int first, int last) {
        String[] values = new String[last - first + 1];
        for (int i = first; i <= last; i++) values[i - first] = String.format(Locale.CHINA, "%02d", i);
        return values;
    }

    private void addWheel(LinearLayout row, NumberPicker picker, String unit) {
        LinearLayout column = new LinearLayout(this); column.setOrientation(LinearLayout.VERTICAL); column.setGravity(Gravity.CENTER);
        TextView label = text(unit, 11, MUTED, false); label.setGravity(Gravity.CENTER);
        column.addView(label); column.addView(picker, new LinearLayout.LayoutParams(-1, dp(104)));
        row.addView(column, new LinearLayout.LayoutParams(0, -2, 1));
    }

    private void updateDayMaximum(NumberPicker day, int year, int month) {
        int max = daysInMonth(year, month);
        int value = day.getValue(); day.setMaxValue(max);
        if (value > max) day.setValue(max);
    }

    private int daysInMonth(int year, int month) {
        Calendar c = Calendar.getInstance(); c.set(Calendar.DAY_OF_MONTH, 1); c.set(Calendar.YEAR, year); c.set(Calendar.MONTH, month - 1);
        return c.getActualMaximum(Calendar.DAY_OF_MONTH);
    }

    private void showAssignerPicker() {
        String[] roles = {"班主任", "语文老师", "数学老师", "英语老师", "科学老师", "体育老师", "其他/自定义"};
        new AlertDialog.Builder(this).setTitle("选择布置人类别").setItems(roles, (dialog, which) -> showAssignerName(roles[which].startsWith("其他") ? "" : roles[which])).show();
    }

    private void showAssignerName(String role) {
        EditText name = input("例如：语文老师 王老师", role.isEmpty() ? "" : role + " ");
        name.setSelection(name.getText().length());
        new AlertDialog.Builder(this).setTitle("填写布置人").setView(name).setNegativeButton("取消", null)
                .setPositiveButton("确定", (dialog, which) -> {
                    manualAssigner = name.getText().toString().trim();
                    manualAssignerValue.setText(manualAssigner.isEmpty() ? "选择布置人" : manualAssigner);
                    manualAssignerValue.setTextColor(manualAssigner.isEmpty() ? MUTED : INK);
                }).show();
    }

    private void saveManualTask() {
        String title = manualTitleField == null ? "" : manualTitleField.getText().toString().trim();
        if (title.isEmpty()) { android.widget.Toast.makeText(this, "请填写待办任务", android.widget.Toast.LENGTH_SHORT).show(); return; }
        if (manualDueAt <= 0) { android.widget.Toast.makeText(this, "请选择完成日期", android.widget.Toast.LENGTH_SHORT).show(); return; }
        if (manualAssigner.isEmpty()) { android.widget.Toast.makeText(this, "请选择并填写布置人", android.widget.Toast.LENGTH_SHORT).show(); return; }
        boolean added = TodoStore.addAt(this, title, formatDate(manualDueAt), manualAssigner, "", manualAssignedAt);
        if (!added) android.widget.Toast.makeText(this, "这项待办似乎已在清单中", android.widget.Toast.LENGTH_SHORT).show();
        else android.widget.Toast.makeText(this, "待办已添加", android.widget.Toast.LENGTH_SHORT).show();
        manualTitleField = null; manualDueAt = 0; manualAssigner = ""; manualAssignedAt = 0;
        renderHome();
    }

    private void configureHomeActions() {
        bottomActions.removeAllViews(); bottomActions.setVisibility(View.VISIBLE);
        LinearLayout.LayoutParams settings = new LinearLayout.LayoutParams(0, dp(54), 1); settings.setMargins(0, 0, dp(16), 0);
        LinearLayout.LayoutParams manual = new LinearLayout.LayoutParams(0, dp(54), 1);
        bottomActions.addView(actionTile(android.R.drawable.ic_menu_manage, "同步设置", this::renderSettings), settings);
        bottomActions.addView(actionTile(android.R.drawable.ic_input_add, "手动补充", () -> {
            manualAssignedAt = System.currentTimeMillis(); manualDueAt = 0; manualAssigner = ""; renderManualForm();
        }), manual);
    }

    private void configureCategoryActions() { configureHomeActions(); }

    private void configureSingleAction(int icon, String label, Runnable action) {
        bottomActions.removeAllViews(); bottomActions.setVisibility(View.VISIBLE);
        bottomActions.addView(actionTile(icon, label, action), new LinearLayout.LayoutParams(-1, dp(64)));
    }

    private void configureManualActions() {
        bottomActions.removeAllViews(); bottomActions.setVisibility(View.VISIBLE);
        bottomActions.addView(actionTile(android.R.drawable.ic_menu_save, "保存待办", this::saveManualTask), new LinearLayout.LayoutParams(0, dp(64), 1));
        bottomActions.addView(actionTile(android.R.drawable.ic_menu_close_clear_cancel, "取消", this::renderHome), new LinearLayout.LayoutParams(0, dp(64), 1));
    }

    private View actionTile(int iconResource, String label, Runnable action) {
        LinearLayout tile = new LinearLayout(this); tile.setOrientation(LinearLayout.VERTICAL); tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(8), dp(5), dp(8), dp(4)); tile.setBackground(shape(Color.WHITE, dp(15), Color.rgb(233, 236, 240)));
        ImageView icon = new ImageView(this); icon.setImageResource(iconResource); icon.setColorFilter(BLUE);
        icon.setContentDescription(label);
        tile.addView(icon, new LinearLayout.LayoutParams(dp(29), dp(29)));
        TextView caption = text(label, 11, INK, false); caption.setGravity(Gravity.CENTER);
        tile.addView(caption, margin(0, dp(1), 0, 0));
        tile.setClickable(true); tile.setOnClickListener(v -> action.run());
        return tile;
    }

    private void enableNotificationAccess() {
        new AlertDialog.Builder(this)
                .setTitle("开启消息整理")
                .setMessage("允许后，应用会筛选微信中匹配所选群名的可见通知，在本机整理成待办。安卓还会单独询问是否允许本机提醒；即使拒绝提醒，清单仍可查看。群聊内容不会上传。")
                .setNegativeButton("暂不", null)
                .setNeutralButton("只开启读取", (d, w) -> openListenerSettings())
                .setPositiveButton("继续", (d, w) -> {
                    if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 71);
                    } else openListenerSettings();
                }).show();
    }

    private void openListenerSettings() { startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }

    @Override public void onBackPressed() {
        if (settingsPage || manualPage || !activeCategory.isEmpty()) renderHome();
        else super.onBackPressed();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 71) openListenerSettings();
    }

    private void showDetail(TodoItem item) {
        String info = "布置日期：" + formatDate(item.createdAt) + "\n完成日期：" + (item.due.isEmpty() ? "未填写" : item.due)
                + "\n布置人：" + item.sender + "\n状态：" + (item.done ? "已完成" : "待完成")
                + (item.done ? "\n实际完成：" + (item.completedAt > 0 ? formatDate(item.completedAt) : "历史未记录") : "")
                + "\n\n原通知摘录（仅保存在本机）：\n" + (item.source.isEmpty() ? "家长手动补充" : item.source);
        AlertDialog.Builder dialog = new AlertDialog.Builder(this).setTitle(item.title).setMessage(info)
                .setNeutralButton("移除此项", (d, w) -> new AlertDialog.Builder(this).setMessage("从本机清单和完成历史中移除此项？").setNegativeButton("取消", null).setPositiveButton("移除", (x, y) -> { TodoStore.remove(this, item.id); renderHome(); }).show())
                .setNegativeButton("关闭", null);
        dialog.show();
    }

    private void section(String name) {
        TextView t = text(name, 14, MUTED, true); t.setPadding(dp(4), dp(12), 0, dp(8)); content.addView(t);
    }

    private EditText input(String hint, String value) {
        EditText e = new EditText(this); e.setSingleLine(true); e.setTextSize(15); e.setTextColor(INK); e.setHintTextColor(MUTED); e.setHint(hint); e.setText(value); e.setPadding(dp(13), dp(9), dp(13), dp(9));
        e.setBackground(shape(Color.WHITE, dp(13), Color.rgb(229, 231, 236))); return e;
    }

    private View cardField(String label, EditText input) {
        LinearLayout c = card(); c.setOrientation(LinearLayout.VERTICAL); c.addView(text(label, 12, MUTED, false)); c.addView(input, margin(0, dp(7), 0, 0)); return c;
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(15), dp(14), dp(15), dp(14)); l.setBackground(shape(Color.WHITE, dp(17), Color.rgb(237, 238, 242))); return l;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(sp); t.setTextColor(color); if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); t.setGravity(Gravity.CENTER_VERTICAL); return t;
    }

    private Button button(String label, int background, int foreground) {
        Button b = new Button(this); b.setText(label); b.setTextSize(13); b.setAllCaps(false); b.setTextColor(foreground); b.setBackgroundTintList(ColorStateList.valueOf(background)); return b;
    }

    private GradientDrawable shape(int color, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius); d.setStroke(dp(1), stroke); return d;
    }

    private LinearLayout.LayoutParams margin(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(left, top, right, bottom); return p;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private final class RadarView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path sweep = new Path();
        private final RectF bounds = new RectF();
        private ValueAnimator animator;
        private float angle;
        private boolean running;

        RadarView(Context context) { super(context); setContentDescription("群消息同步状态"); }

        void setRunning(boolean active) {
            running = active;
            if (active) startAnimation();
            else {
                if (animator != null) animator.cancel();
                animator = null; angle = 0f; invalidate();
            }
        }

        private void startAnimation() {
            if (animator != null || !isAttachedToWindow()) return;
            animator = ValueAnimator.ofFloat(0f, 360f);
            animator.setDuration(1900L); animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(value -> { angle = (float) value.getAnimatedValue(); invalidate(); });
            animator.start();
        }

        @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); if (running) startAnimation(); }
        @Override protected void onDetachedFromWindow() {
            if (animator != null) animator.cancel();
            animator = null; super.onDetachedFromWindow();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f, cy = getHeight() / 2f, radius = Math.min(cx, cy) - dp(2);
            int green = running ? Color.rgb(43, 126, 94) : Color.rgb(157, 163, 173);
            paint.setColor(green); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1));
            canvas.drawCircle(cx, cy, radius, paint); canvas.drawCircle(cx, cy, radius * .62f, paint); canvas.drawCircle(cx, cy, radius * .27f, paint);
            if (running) {
                canvas.save(); canvas.rotate(angle, cx, cy);
                bounds.set(cx - radius, cy - radius, cx + radius, cy + radius);
                sweep.reset(); sweep.moveTo(cx, cy); sweep.lineTo(cx + radius, cy); sweep.arcTo(bounds, 0f, 48f); sweep.close();
                paint.setStyle(Paint.Style.FILL); paint.setColor(Color.argb(45, 43, 126, 94)); canvas.drawPath(sweep, paint);
                paint.setStyle(Paint.Style.STROKE); paint.setColor(green); paint.setStrokeWidth(dp(2));
                canvas.drawLine(cx, cy, cx + radius, cy, paint); canvas.restore();
            }
            paint.setStyle(Paint.Style.FILL); paint.setColor(green); canvas.drawCircle(cx, cy, dp(2), paint);
        }
    }
}
