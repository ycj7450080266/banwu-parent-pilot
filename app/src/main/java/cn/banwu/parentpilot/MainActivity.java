package cn.banwu.parentpilot;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ActivityNotFoundException;
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
import android.os.Handler;
import android.os.Looper;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.content.res.ColorStateList;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.TextView;
import android.text.TextWatcher;
import android.text.Editable;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Date;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

public class MainActivity extends Activity {
    public static final String ACTION_REFRESH = "cn.banwu.parentpilot.REFRESH";
    private static final int BLUE = Color.rgb(50, 96, 230);
    private static final int INK = Color.rgb(26, 28, 34);
    private static final int MUTED = Color.rgb(112, 116, 126);
    private static final int BG = Color.rgb(246, 247, 250);
    private LinearLayout root, content, bottomActions, entryBar;
    private TextView syncStatus;
    private TextView settingsSaveStatus;
    private Button settingsPermissionButton;
    private RadarView radarView;
    private EditText settingsChildField, settingsGroupField, settingsScopeKeywordsField, settingsTaskKeywordsField, manualTitleField;
    private EditText settingsApiUrlField, settingsAiModelField, settingsApiKeyField;
    private CheckBox settingsAiEnabledField, settingsAiConsentField;
    private Button settingsTestApiButton;
    private TextView manualAssignedValue, manualDueValue, manualAssignerValue;
    private TextView manualTypeValue;
    private String manualType = "task";
    private boolean settingsPage, manualPage;
    private String activeCategory = "";
    private boolean showCompletedCategoryItems;
    private boolean showCompletedFeed;
    private long manualAssignedAt, manualDueAt;
    private String manualAssigner = "";
    private boolean receiverRegistered;
    private boolean clearApiKey;
    private final Handler settingsSaveHandler = new Handler(Looper.getMainLooper());
    private final Runnable settingsAutosave = this::persistSettings;
    private final Handler listenerRecoveryHandler = new Handler(Looper.getMainLooper());
    private final Runnable listenerRecoveryCheck = new Runnable() {
        @Override public void run() {
            if (isFinishing() || isDestroyed()) return;
            WeChatNotificationListener.ensureBound(MainActivity.this);
            updateSyncStatus();
            listenerRecoveryHandler.postDelayed(this, 5000L);
        }
    };
    private static final int REQUEST_CAMERA_TEXT = 231, REQUEST_VOICE_TEXT = 232;
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
        listenerRecoveryHandler.removeCallbacks(listenerRecoveryCheck);
        if (receiverRegistered) { unregisterReceiver(refreshReceiver); receiverRegistered = false; }
        if (radarView != null) radarView.setRunning(false);
        super.onStop();
    }

    @Override protected void onPause() {
        if (settingsPage) {
            settingsSaveHandler.removeCallbacks(settingsAutosave);
            persistSettings();
        }
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        WeChatNotificationListener.ensureBound(this);
        listenerRecoveryHandler.removeCallbacks(listenerRecoveryCheck);
        listenerRecoveryHandler.postDelayed(listenerRecoveryCheck, 5000L);
        if (root != null) {
            if (settingsPage) updateSyncStatus();
            else if (manualPage) renderManualForm();
            else renderHome();
        }
    }

    private void buildRoot() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG);
        setContentView(root);
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); top.setPadding(dp(22), dp(43), dp(18), dp(10));
        TextView title = text("班级消息待办", 26, INK, true);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout syncBadge = new LinearLayout(this); syncBadge.setGravity(Gravity.CENTER_VERTICAL);
        radarView = new RadarView(this); syncBadge.addView(radarView, new LinearLayout.LayoutParams(dp(24), dp(24)));
        syncStatus = text("同步未开启", 11, MUTED, true); syncStatus.setGravity(Gravity.CENTER_VERTICAL);
        syncStatus.setSingleLine(true); syncStatus.setMaxLines(1);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-2, -2); statusParams.setMargins(dp(4), 0, 0, 0);
        syncBadge.addView(syncStatus, statusParams);
        top.addView(syncBadge);
        root.addView(top);
        entryBar = new LinearLayout(this);
        entryBar.setOrientation(LinearLayout.HORIZONTAL);
        entryBar.setPadding(dp(18), dp(2), dp(18), dp(4));
        FrameLayout body = new FrameLayout(this);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        ScrollView scroller = new ScrollView(this); scroller.setFillViewport(true);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(18), dp(8), dp(18), dp(112));
        scroller.addView(content);
        body.addView(scroller, new FrameLayout.LayoutParams(-1, -1));
        bottomActions = new LinearLayout(this); bottomActions.setOrientation(LinearLayout.HORIZONTAL);
        bottomActions.setGravity(Gravity.CENTER); bottomActions.setPadding(dp(16), dp(8), dp(16), dp(16));
        body.addView(bottomActions, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        body.addView(entryBar, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
    }

    private void renderHome() {
        if (content == null) return;
        settingsPage = false; manualPage = false;
        activeCategory = "";
        renderMainEntryBar();
        content.removeAllViews();
        content.setPadding(dp(18), dp(8), dp(18), dp(84));
        List<TodoItem> items = TodoStore.all(this);

        updateSyncStatus();
        renderMessageTimeline(items);

        String recognitionNote=TodoStore.aiEnabled(this)&&TodoStore.aiConsent(this)
                ? "已选择模型分析时，选中群通知正文及该群未完成事项摘要脱敏后发送到你配置的服务；照片 OCR 在本机完成。通知预览关闭或系统未显示通知时，可能收不到消息。"
                : "本机按规则整理通知；照片 OCR 在本机完成。群免打扰、通知预览关闭或系统未显示通知时，可能收不到消息。";
        TextView note = text(recognitionNote, 11, MUTED, false);
        note.setPadding(dp(4), dp(16), dp(4), dp(6)); content.addView(note);
        configureHomeActions();
    }

    private void updateSyncStatus() {
        if (syncStatus == null || radarView == null) return;
        boolean enabled = WeChatNotificationListener.isEnabled(this);
        boolean connected = WeChatNotificationListener.isRuntimeConnected();
        boolean paused = TodoStore.paused(this);
        String label = !enabled ? "\u901a\u77e5\u8bbf\u95ee\u672a\u5f00\u542f" : paused ? "\u6574\u7406\u5df2\u6682\u505c" : connected ? "\u7fa4\u6d88\u606f\u540c\u6b65\u5df2\u5f00\u542f" : "\u901a\u77e5\u76d1\u542c\u8fde\u63a5\u4e2d";
        syncStatus.setText(label);
        syncStatus.setTextColor(enabled && !paused && connected ? Color.rgb(43, 126, 94) : MUTED);
        radarView.setRunning(enabled && !paused && connected);
        if (settingsPermissionButton != null) {
            settingsPermissionButton.setText(enabled ? "通知访问已开启 · 管理权限" : "前往系统开启通知访问");
            settingsPermissionButton.setBackgroundTintList(ColorStateList.valueOf(enabled ? Color.rgb(229, 244, 236) : BLUE));
            settingsPermissionButton.setTextColor(enabled ? Color.rgb(28, 114, 72) : Color.WHITE);
        }
    }

    private void renderMainEntryBar() {
        if (entryBar == null) return;
        entryBar.removeAllViews();
        int pending = 0, completed = 0;
        for (TodoItem item : TodoStore.all(this)) if (item.done) completed++; else pending++;
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(dp(4), dp(4), dp(4), dp(4));
        tabs.setBackground(shape(Color.rgb(235, 237, 242), dp(14), Color.rgb(235, 237, 242)));
        boolean feedSelected = !settingsPage && !manualPage && activeCategory.isEmpty();
        tabs.addView(homeEntry(R.drawable.ic_nav_pending, "\u5f85\u5b8c\u6210", pending,
                feedSelected && !showCompletedFeed, () -> { showCompletedFeed = false; renderHome(); }),
                new LinearLayout.LayoutParams(0, dp(60), 1));
        tabs.addView(homeEntry(R.drawable.ic_nav_completed, "\u5df2\u5b8c\u6210", completed,
                feedSelected && showCompletedFeed, () -> { showCompletedFeed = true; renderHome(); }),
                new LinearLayout.LayoutParams(0, dp(60), 1));
        tabs.addView(homeEntry(R.drawable.ic_nav_add, "\u624b\u52a8\u8865\u5145", -1, manualPage,
                this::openManualSupplement), new LinearLayout.LayoutParams(0, dp(60), 1));
        tabs.addView(homeEntry(R.drawable.ic_nav_settings, "\u8bbe\u7f6e", -1, settingsPage,
                this::renderSettings), new LinearLayout.LayoutParams(0, dp(60), 1));
        entryBar.addView(tabs, new LinearLayout.LayoutParams(-1, -2));
    }
    private void renderMessageTimeline(List<TodoItem> items) {
        ArrayList<TodoItem> shown = new ArrayList<>();
        for (TodoItem item : items) if (item.done == showCompletedFeed) shown.add(item);
        if (shown.isEmpty()) {
            emptySection(showCompletedFeed ? "还没有已完成的群消息。" : "还没有待完成的群消息。开启通知访问后，新消息会按日期显示在这里。");
            return;
        }

        shown.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));
        LinkedHashMap<String, ArrayList<TodoItem>> homeworkByDay = new LinkedHashMap<>();
        ArrayList<TodoItem> homeworkItems = new ArrayList<>();
        for (TodoItem item : shown) if (item.homeworkPack) homeworkItems.add(item);
        for (TodoItem item : homeworkItems) {
            String key = new SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(new Date(item.createdAt));
            homeworkByDay.computeIfAbsent(key, ignored -> new ArrayList<>()).add(item);
        }
        shown.removeAll(homeworkItems);
        for (ArrayList<TodoItem> dayItems : homeworkByDay.values()) {
            int pendingSteps = 0, completedSteps = 0;
            for (TodoItem item : dayItems) if (item.homeworkSteps != null) {
                for (int i = 0; i < item.homeworkSteps.length(); i++) {
                    org.json.JSONObject step = item.homeworkSteps.optJSONObject(i);
                    if (step == null) continue;
                    if (step.optBoolean("done", false)) completedSteps++; else pendingSteps++;
                }
            }
            LinearLayout heading = new LinearLayout(this); heading.setGravity(Gravity.CENTER_VERTICAL);
            TextView dayTitle = text("\u5b66\u4e60\u4f5c\u4e1a  " + formatDay(dayItems.get(0).createdAt), 15, INK, true);
            heading.addView(dayTitle, new LinearLayout.LayoutParams(0, -2, 1));
            TextView counts = text(pendingSteps + " \u5f85\u5b8c\u6210  \u00b7  " + completedSteps + " \u5df2\u5b8c\u6210", 12, BLUE, true);
            counts.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL); heading.addView(counts);
            heading.setPadding(dp(3), dp(13), dp(3), dp(8)); content.addView(heading);
            addHomeworkDateCard(dayItems);
        }

        LinkedHashMap<String, ArrayList<TodoItem>> byDay = new LinkedHashMap<>();
        for (TodoItem item : shown) {
            String key = new SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(new Date(item.createdAt));
            byDay.computeIfAbsent(key, ignored -> new ArrayList<>()).add(item);
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
        boolean overdue = !item.done && item.dueAt > 0 && item.dueAt < System.currentTimeMillis();
        String extra = "截止：" + formatDueWithWeekday(item);
        if (overdue) extra += "  ·  已超时";
        else if (item.overdueDeferred && !item.done) extra += "  ·  已手动延时";
        if (item.done) extra += "  ·  完成：" + (item.completedAt > 0 ? formatDate(item.completedAt) : "时间未记录");
        TextView date = text(extra, 11, overdue ? Color.rgb(188, 64, 55) : MUTED, overdue); copy.addView(date, margin(0, dp(2), 0, 0));
        itemCard.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));

        TextView check = text(item.done ? "✓" : "", 18, item.done ? Color.WHITE : BLUE, true);
        check.setGravity(Gravity.CENTER); check.setContentDescription(item.done ? "取消勾选" : "标记完成");
        check.setBackground(shape(item.done ? Color.rgb(51, 132, 107) : Color.WHITE, dp(999), item.done ? Color.rgb(51, 132, 107) : Color.rgb(204, 209, 219)));
        LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(dp(32), dp(32));
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

    private static final class HomeworkRow {
        final TodoItem item;
        final int stepIndex;
        final org.json.JSONObject step;
        HomeworkRow(TodoItem item, int stepIndex, org.json.JSONObject step) {
            this.item = item; this.stepIndex = stepIndex; this.step = step;
        }
    }

    private void addHomeworkDateCard(List<TodoItem> items) {
        LinearLayout table = card(); table.setOrientation(LinearLayout.VERTICAL);
        ArrayList<HomeworkRow> rows = new ArrayList<>();
        for (TodoItem item : items) {
            if (item.homeworkSteps == null) continue;
            for (int i = 0; i < item.homeworkSteps.length(); i++) {
                org.json.JSONObject step = item.homeworkSteps.optJSONObject(i);
                if (step != null) rows.add(new HomeworkRow(item, i, step));
            }
        }
        rows.sort((a, b) -> {
            int subjectOrder = Integer.compare(
                    homeworkSubjectOrder(a.step.optString("text", "")),
                    homeworkSubjectOrder(b.step.optString("text", "")));
            if (subjectOrder != 0) return subjectOrder;
            int dateOrder = Long.compare(b.item.createdAt, a.item.createdAt);
            return dateOrder != 0 ? dateOrder : Integer.compare(a.stepIndex, b.stepIndex);
        });
        for (int pos = 0; pos < rows.size(); pos++) {
            HomeworkRow homeworkRow = rows.get(pos);
            boolean checked = homeworkRow.step.optBoolean("done", false);
            LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(62)); row.setPadding(dp(10), dp(5), dp(4), dp(5));
            TextView taskText = text(homeworkRow.step.optString("text", ""), 14,
                    checked ? MUTED : INK, false);
            taskText.setPadding(0, dp(7), dp(8), dp(7));
            if (checked) taskText.setPaintFlags(taskText.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            row.addView(taskText, new LinearLayout.LayoutParams(0, -2, 1));
            TextView checkbox = text(checked ? "\u2713" : "", 15, checked ? Color.WHITE : BLUE, true);
            checkbox.setGravity(Gravity.CENTER);
            checkbox.setContentDescription((checked ? "\u53d6\u6d88\u5b8c\u6210\uff1a" : "\u6807\u8bb0\u5b8c\u6210\uff1a")
                    + homeworkRow.step.optString("text", ""));
            checkbox.setBackground(shape(checked ? Color.rgb(51, 132, 107) : Color.WHITE, dp(999),
                    checked ? Color.rgb(51, 132, 107) : Color.rgb(204, 209, 219)));
            row.addView(checkbox, new LinearLayout.LayoutParams(dp(34), dp(34)));
            checkbox.setOnClickListener(v -> {
                TodoStore.toggleHomeworkStep(this, homeworkRow.item.id, homeworkRow.stepIndex);
                renderHome();
            });
            row.setOnClickListener(v -> showDetail(homeworkRow.item));
            table.addView(row);
            if (pos < rows.size() - 1) {
                View divider = new View(this); divider.setBackgroundColor(Color.rgb(235, 237, 241));
                table.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
            }
        }
        content.addView(table, margin(0, 0, 0, dp(9)));
    }

    private int categoryColor(String category) {
        if (TodoItem.CATEGORY_CHECKIN.equals(category)) return Color.rgb(105, 76, 179);
        if (TodoItem.CATEGORY_NOTICE.equals(category)) return Color.rgb(40, 116, 99);
        return BLUE;
    }

    private int categoryTint(String category) {
        if (TodoItem.CATEGORY_CHECKIN.equals(category)) return Color.rgb(239, 234, 250);
        if (TodoItem.CATEGORY_NOTICE.equals(category)) return Color.rgb(230, 243, 238);
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
        if (TodoItem.CATEGORY_CHECKIN.equals(category)) return "打卡";
        if (TodoItem.CATEGORY_NOTICE.equals(category)) return "通知";
        return "任务";
    }

    private void renderCategoryDetail() {
        if (content == null || activeCategory.isEmpty()) return;
        settingsPage = false; manualPage = false;
        renderMainEntryBar();
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

    private View homeEntry(int icon, String label, int count, boolean selected, Runnable action) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(dp(2), dp(4), dp(2), dp(4));
        cell.setBackground(shape(selected ? Color.WHITE : Color.TRANSPARENT, dp(11), selected ? Color.WHITE : Color.TRANSPARENT));
        ImageView image = new ImageView(this);
        image.setImageResource(icon);
        image.setImageTintList(ColorStateList.valueOf(selected ? BLUE : MUTED));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(20), dp(20));
        cell.addView(image, iconParams);
        String labelText = count >= 0 ? label + " " + count : label;
        TextView caption = text(labelText, 11, selected ? BLUE : MUTED, selected);
        caption.setGravity(Gravity.CENTER);
        caption.setSingleLine(true);
        caption.setIncludeFontPadding(false);
        LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(-1, -2);
        captionParams.topMargin = dp(3);
        cell.addView(caption, captionParams);
        cell.setContentDescription(labelText);
        cell.setSelected(selected);
        cell.setClickable(true);
        cell.setFocusable(true);
        cell.setOnClickListener(v -> action.run());
        return cell;
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
        boolean overdue = !item.done && item.dueAt > 0 && item.dueAt < System.currentTimeMillis();
        String dates = "布置日期：" + formatDate(item.createdAt) + "\n完成日期：" + (item.due.isEmpty() ? "未注明时间" : item.due)
                + (overdue ? "  ·  超时" : item.overdueDeferred && !item.done ? "  ·  已手动延时" : "")
                + (item.done ? "\n实际完成：" + (item.completedAt > 0 ? formatDate(item.completedAt) : "历史未记录") : "");
        copy.addView(text(dates, 12, overdue ? Color.rgb(188, 64, 55) : MUTED, overdue), margin(0, dp(6), 0, 0));
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
        LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(dp(29), dp(29));
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

    private int homeworkSubjectOrder(String text) {
        String value = text == null ? "" : text.trim();
        int separator = value.indexOf('\uFF5C');
        if (separator < 0) separator = value.indexOf('|');
        String subject = (separator >= 0 ? value.substring(0, separator) : value).trim();
        if (subject.contains("\u8bed\u6587")) return 0;
        if (subject.contains("\u6570\u5b66")) return 1;
        if (subject.contains("\u82f1\u8bed")) return 2;
        if (subject.contains("\u4f53\u80b2") || subject.contains("\u8fd0\u52a8")) return 3;
        return 4;
    }

    private String formatDueWithWeekday(TodoItem item) {
        if (item == null) return "\u672a\u6ce8\u660e\u65f6\u95f4";
        long at = item.dueAt;
        if (at <= 0 && item.due != null && !item.due.trim().isEmpty() && !"null".equalsIgnoreCase(item.due.trim())) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,2})\u6708(\\d{1,2})\u65e5(?:\\s*(\\d{1,2}):(\\d{2}))?").matcher(item.due);
            if (m.find()) {
                Calendar c = Calendar.getInstance();
                c.set(Calendar.MONTH, Integer.parseInt(m.group(1))-1);
                c.set(Calendar.DAY_OF_MONTH, Integer.parseInt(m.group(2)));
                c.set(Calendar.HOUR_OF_DAY, m.group(3)==null?21:Integer.parseInt(m.group(3)));
                c.set(Calendar.MINUTE, m.group(4)==null?0:Integer.parseInt(m.group(4)));
                c.set(Calendar.SECOND,0); c.set(Calendar.MILLISECOND,0);
                at=c.getTimeInMillis();
            }
        }
        if (at > 0) {
            String day = new SimpleDateFormat("M\u6708d\u65e5 HH:mm", Locale.CHINA).format(new Date(at));
            Calendar weekCalendar = Calendar.getInstance(); weekCalendar.setTimeInMillis(at);
            String[] weekdays = {"\u661f\u671f\u65e5", "\u661f\u671f\u4e00", "\u661f\u671f\u4e8c", "\u661f\u671f\u4e09", "\u661f\u671f\u56db", "\u661f\u671f\u4e94", "\u661f\u671f\u516d"};
            String weekday = weekdays[weekCalendar.get(Calendar.DAY_OF_WEEK) - 1];
            return day + " \u00b7 " + weekday;
        }
        if (item.due == null || item.due.trim().isEmpty() || "null".equalsIgnoreCase(item.due.trim())) return "\u672a\u6ce8\u660e\u65f6\u95f4";
        return item.due;
    }

    private String formatDate(long time) {
        if (time <= 0) return "时间未记录";
        return new SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(new Date(time));
    }

    private void renderSettings() {
        settingsPage = true; manualPage = false; renderMainEntryBar(); content.removeAllViews();
        settingsSaveHandler.removeCallbacks(settingsAutosave);
        settingsPermissionButton = null;
        settingsSaveStatus = null;
        content.setPadding(dp(18), dp(8), dp(18), dp(84));
        bottomActions.setVisibility(View.GONE);
        updateSyncStatus();
        section("\u8bbe\u7f6e");
        settingsSaveStatus = text("自动保存已开启 · 已保存", 12, MUTED, false);
        settingsSaveStatus.setPadding(dp(4), 0, dp(4), dp(8));
        content.addView(settingsSaveStatus);
        String child = TodoStore.child(this), group = TodoStore.group(this);
        settingsChildField = input("孩子姓名（用于识别点名事项）", child);
        settingsGroupField = input("每行填写一个群聊名称，可添加多个", group.replace("、", "\n"));
        settingsGroupField.setSingleLine(false); settingsGroupField.setMinLines(2); settingsGroupField.setMaxLines(5);
        settingsGroupField.setGravity(Gravity.TOP | Gravity.START);
        settingsGroupField.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        content.addView(cardField("孩子姓名", settingsChildField));
        content.addView(cardField("群聊（每行一个）", settingsGroupField), margin(0, dp(10), 0, dp(5)));
        TextView groupsHelp = text("可填写多个微信班级群名称，每行一个；通知标题匹配任一群名时才会整理。", 14, MUTED, false);
        groupsHelp.setPadding(dp(4), 0, dp(4), dp(10)); content.addView(groupsHelp);

        boolean enabled = WeChatNotificationListener.isEnabled(this);
        settingsPermissionButton = button(enabled ? "通知访问已开启 · 管理权限" : "前往系统开启通知访问", enabled ? Color.rgb(229, 244, 236) : BLUE, enabled ? Color.rgb(28, 114, 72) : Color.WHITE);
        settingsPermissionButton.setOnClickListener(v -> enableNotificationAccess());
        content.addView(settingsPermissionButton, margin(0, 0, 0, dp(8)));
        TextView privacy = text("请在系统权限页选择“班级消息待办”。应用只读取微信显示出来的通知。不开启模型时按本机规则整理；开启并同意云端分析后，将选中群通知正文及该群未完成事项摘要（孩子姓名先替换为“孩子”）发送到你配置的模型服务。API Key 仅加密保存在本机。照片 OCR 在本机完成，不上传图片；你可以随时关闭模型分析或通知访问。", 14, MUTED, false);
        privacy.setPadding(dp(3), dp(5), dp(3), dp(16)); content.addView(privacy);

        Button pause = button(TodoStore.paused(this) ? "恢复整理" : "暂停整理", Color.WHITE, INK);
        pause.setOnClickListener(v -> {
            boolean paused = !TodoStore.paused(this);
            TodoStore.setPaused(this, paused);
            pause.setText(paused ? "恢复整理" : "暂停整理");
            updateSyncStatus();
        }); content.addView(pause, margin(0, 0, 0, dp(9)));

        section("可选：模型语义识别 API");
        TextView aiHelp = text("不填 API 时继续使用当前本地关键词规则。填写并启用后，模型会结合该群未完成事项摘要判断消息含义，区分新建、更新或忽略，合并同一接龙、报名事件。模型接口须兼容 OpenAI Chat Completions 且使用 HTTPS；调用失败会回退本地规则。", 14, MUTED, false);
        aiHelp.setPadding(dp(3), 0, dp(3), dp(8)); content.addView(aiHelp);
        settingsApiUrlField = input("例如：https://api.example.com/v1", TodoStore.aiUrl(this));
        settingsAiModelField = input("模型名称，例如 gpt-4o-mini", TodoStore.aiModel(this));
        settingsApiKeyField = input("留空则保留已保存的 Key", "");
        settingsApiKeyField.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        content.addView(cardField("API Base URL", settingsApiUrlField));
        content.addView(cardField("模型名称", settingsAiModelField), margin(0, dp(8), 0, 0));
        content.addView(cardField("API Key（加密保存在本机）", settingsApiKeyField), margin(0, dp(8), 0, dp(5)));
        Button clearKey = button("清除本机保存的 API Key", Color.WHITE, BLUE);
        clearKey.setOnClickListener(v -> { clearApiKey = true; settingsApiKeyField.setText(""); settingsApiKeyField.setHint("已请求清除，自动保存中"); scheduleSettingsAutosave(); });
        content.addView(clearKey, margin(0, 0, 0, dp(5)));
        settingsAiEnabledField = new CheckBox(this); settingsAiEnabledField.setText("启用模型识别（未配置时仍使用本地规则）");
        settingsAiEnabledField.setTextSize(14); settingsAiEnabledField.setTextColor(INK); settingsAiEnabledField.setChecked(TodoStore.aiEnabled(this));
        content.addView(settingsAiEnabledField);
        settingsAiConsentField = new CheckBox(this); settingsAiConsentField.setText("我同意将选中群通知正文和该群未完成事项摘要脱敏后发送到上方模型 API 分析");
        settingsAiConsentField.setTextSize(14); settingsAiConsentField.setTextColor(INK); settingsAiConsentField.setChecked(TodoStore.aiConsent(this));
        content.addView(settingsAiConsentField);
        settingsTestApiButton = button("测试 API 连接", Color.WHITE, BLUE);
        settingsTestApiButton.setOnClickListener(v -> testAiApi());
        content.addView(settingsTestApiButton, margin(0, dp(3), 0, dp(12)));
        clearApiKey = false;

        section("最近通知诊断");
        TextView diagnosticStatus = text(TodoStore.lastDiagnostic(this), 14, INK, false);
        diagnosticStatus.setPadding(dp(12), dp(12), dp(12), dp(12));
        diagnosticStatus.setBackground(shape(Color.WHITE, dp(13), Color.rgb(229, 231, 236)));
        diagnosticStatus.setClickable(true);
        diagnosticStatus.setFocusable(true);
        diagnosticStatus.setOnClickListener(v -> showDiagnosticHistory());
        content.addView(diagnosticStatus, margin(0, 0, 0, dp(6)));
        TextView diagnosticHint = text("点诊断卡片查看并复制处理记录；只记录步骤、筛选依据、API 成功或失败及回退状态，不记录通知正文或 API Key。", 14, MUTED, false);
        content.addView(diagnosticHint, margin(dp(3), 0, dp(3), dp(5)));
        Button refreshDiagnostic = button("刷新最近通知状态", Color.WHITE, BLUE);
        refreshDiagnostic.setOnClickListener(v -> diagnosticStatus.setText(TodoStore.lastDiagnostic(this)));
        content.addView(refreshDiagnostic, margin(0, 0, 0, dp(8)));

        section("识别关键词");
        TextView keywordHelp = text("范围词用于识别面向家长或全班的消息；任务词用于判断消息是否像待办。也可命中孩子姓名。用逗号或换行分隔。词越宽泛，误识别可能越多。", 14, MUTED, false);
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

        section("功能与隐私");
        String versionName;
        try { versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (PackageManager.NameNotFoundException ignored) { versionName = "?"; }
        TextView about = text("功能：读取微信群通知，按群合并整理为通知、任务、作业和打卡；学习作业可拆成多项逐项勾选。清单和完成状态保存在本机。"
                + "\n\nAPI：可不填。留空时用本地规则；启用模型并明确同意后，才会将选中群通知正文与相关待办摘要发送到你配置的 HTTPS API；发送前会脱敏孩子姓名，API Key 加密保存在本机。"
                + "\n\n隐私：照片 OCR 在本机完成，不上传图片；诊断记录不保存通知正文和 API Key。可随时关闭模型识别或撤销通知访问。", 14, MUTED, false);
        about.setPadding(dp(12), dp(12), dp(12), dp(12));
        about.setBackground(shape(Color.WHITE, dp(13), Color.rgb(229, 231, 236)));
        content.addView(about, margin(0, 0, 0, dp(8)));
        section("版本信息");
        TextView version = text("当前版本  " + versionName, 14, MUTED, false);
        version.setPadding(dp(12), dp(12), dp(12), dp(12));
        version.setBackground(shape(Color.WHITE, dp(13), Color.rgb(229, 231, 236)));
        content.addView(version, margin(0, 0, 0, dp(8)));

        settingsChildField.addTextChangedListener(settingsWatcher());
        settingsGroupField.addTextChangedListener(settingsWatcher());
        settingsScopeKeywordsField.addTextChangedListener(settingsWatcher());
        settingsTaskKeywordsField.addTextChangedListener(settingsWatcher());
        settingsApiUrlField.addTextChangedListener(settingsWatcher());
        settingsAiModelField.addTextChangedListener(settingsWatcher());
        settingsApiKeyField.addTextChangedListener(settingsWatcher());
        settingsAiEnabledField.setOnCheckedChangeListener((button, checked) -> scheduleSettingsAutosave());
        settingsAiConsentField.setOnCheckedChangeListener((button, checked) -> scheduleSettingsAutosave());
        clearApiKey = false;
        configureSettingsActions();
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

    private TextWatcher settingsWatcher() {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { scheduleSettingsAutosave(); }
            @Override public void afterTextChanged(Editable s) { }
        };
    }

    private void scheduleSettingsAutosave() {
        if (!settingsPage || settingsSaveStatus == null) return;
        settingsSaveStatus.setText("有更改，正在自动保存…");
        settingsSaveStatus.setTextColor(MUTED);
        settingsSaveStatus.setOnClickListener(null);
        settingsSaveHandler.removeCallbacks(settingsAutosave);
        settingsSaveHandler.postDelayed(settingsAutosave, 650L);
    }

    private void persistSettings() {
        if (!settingsPage || settingsChildField == null || settingsGroupField == null
                || settingsScopeKeywordsField == null || settingsTaskKeywordsField == null
                || settingsApiUrlField == null || settingsAiModelField == null || settingsApiKeyField == null
                || settingsAiEnabledField == null || settingsAiConsentField == null) return;
        settingsSaveHandler.removeCallbacks(settingsAutosave);
        try {
            TodoStore.saveSetup(this, settingsChildField.getText().toString(), settingsGroupField.getText().toString());
            TodoStore.saveKeywordSettings(this, settingsScopeKeywordsField.getText().toString(), settingsTaskKeywordsField.getText().toString());
            TodoStore.saveAiSettings(this, settingsApiUrlField.getText().toString(), settingsAiModelField.getText().toString(),
                    settingsApiKeyField.getText().toString(), settingsAiEnabledField.isChecked(), settingsAiConsentField.isChecked(), clearApiKey);
            clearApiKey = false;
            if (settingsSaveStatus != null) {
                settingsSaveStatus.setText("已自动保存");
                settingsSaveStatus.setTextColor(Color.rgb(43, 126, 94));
                settingsSaveStatus.setOnClickListener(null);
            }
        } catch (RuntimeException error) {
            if (settingsSaveStatus != null) {
                settingsSaveStatus.setText("保存失败，点按重试");
                settingsSaveStatus.setTextColor(Color.rgb(188, 64, 55));
                settingsSaveStatus.setOnClickListener(v -> persistSettings());
            }
        }
    }

    private void testAiApi() {
        persistSettings();
        String url=settingsApiUrlField.getText().toString().trim(), model=settingsAiModelField.getText().toString().trim();
        String key=settingsApiKeyField.getText().toString().trim(); if(key.isEmpty())key=TodoStore.aiKey(this);
        settingsTestApiButton.setText("正在测试…"); settingsTestApiButton.setEnabled(false);
        final String testKey=key;
        new Thread(() -> {
            String result;
            try { result=AiTaskAnalyzer.test(this,url,model,testKey); }
            catch(Exception e){ result="连接失败："+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()); }
            final String message=result;
            runOnUiThread(() -> { if(settingsTestApiButton!=null){settingsTestApiButton.setText("测试 API 连接");settingsTestApiButton.setEnabled(true);} new AlertDialog.Builder(this).setTitle("模型 API 测试").setMessage(message).setPositiveButton("知道了",null).show(); });
        }).start();
    }

    private void renderManualForm() {
        settingsPage = false; manualPage = true; renderMainEntryBar(); content.removeAllViews();
        content.setPadding(dp(18), dp(8), dp(18), dp(172));
        updateSyncStatus();
        if (manualAssignedAt == 0) manualAssignedAt = System.currentTimeMillis();
        section("手动补充待办");

        manualTitleField = input("填写任务内容", "");
        manualTitleField.setSingleLine(false); manualTitleField.setMinLines(2); manualTitleField.setMaxLines(4);
        manualTitleField.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        content.addView(formRow("待办任务：", manualTitleField), margin(0, 0, 0, dp(12)));
        manualTypeValue = formValue(manualTypeLabel());
        manualTypeValue.setOnClickListener(v -> showManualTypePicker());
        content.addView(formRow("\u4e8b\u9879\u7c7b\u578b\uff1a", manualTypeValue), margin(0, 0, 0, dp(12)));

        LinearLayout capture = new LinearLayout(this); capture.setOrientation(LinearLayout.HORIZONTAL);
        Button photo = button("拍照识别文字", Color.WHITE, BLUE); photo.setOnClickListener(v -> capturePhoto());
        Button voice = button("语音输入", Color.WHITE, BLUE); voice.setOnClickListener(v -> captureVoice());
        capture.addView(photo, new LinearLayout.LayoutParams(0, dp(48), 1));
        capture.addView(voice, new LinearLayout.LayoutParams(0, dp(48), 1));
        content.addView(capture, margin(0, dp(-4), 0, dp(12)));

        manualAssignedValue = formValue(formatDate(manualAssignedAt));
        manualAssignedValue.setOnClickListener(v -> showDateTimePicker(true));
        content.addView(formRow("布置日期：", manualAssignedValue), margin(0, 0, 0, dp(12)));

        manualDueValue = formValue(manualDueAt > 0 ? formatDate(manualDueAt) : "作业自动计算；点此手动选择");
        manualDueValue.setOnClickListener(v -> showDateTimePicker(false));
        content.addView(formRow("完成日期：", manualDueValue), margin(0, 0, 0, dp(12)));

        if (manualAssigner.isEmpty()) manualAssigner = "爸爸";
        manualAssignerValue = formValue(manualAssigner);
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
        TextView field = text(value, 15, value.equals("选择布置人") || value.equals("点击选择日期和时间") || value.startsWith("作业自动计算") ? MUTED : INK, false);
        field.setPadding(dp(13), dp(13), dp(13), dp(13));
        field.setBackground(shape(Color.WHITE, dp(13), Color.rgb(229, 231, 236)));
        field.setMinHeight(dp(48));
        return field;
    }

    private String manualTypeLabel() {
        if ("notice".equals(manualType)) return "\u901a\u77e5";
        if ("homework".equals(manualType)) return "\u4f5c\u4e1a";
        if ("checkin".equals(manualType)) return "\u6253\u5361";
        return "\u4efb\u52a1";
    }

    private void showManualTypePicker() {
        String[] labels = {"\u901a\u77e5", "\u4efb\u52a1", "\u4f5c\u4e1a", "\u6253\u5361"};
        String[] values = {"notice", "task", "homework", "checkin"};
        int selected = "notice".equals(manualType) ? 0 : "homework".equals(manualType) ? 2 : "checkin".equals(manualType) ? 3 : 1;
        new AlertDialog.Builder(this).setTitle("\u9009\u62e9\u4e8b\u9879\u7c7b\u578b")
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    manualType = values[which];
                    manualTypeValue.setText(labels[which]);
                    if (manualDueAt <= 0) {
                        manualDueValue.setText("homework".equals(manualType)
                                ? "\u4f5c\u4e1a\u622a\u6b62\u65f6\u95f4\u81ea\u52a8\u8ba1\u7b97\uff0c\u70b9\u51fb\u53ef\u4fee\u6539"
                                : "\u70b9\u51fb\u9009\u62e9\u5b8c\u6210\u65f6\u95f4");
                        manualDueValue.setTextColor(MUTED);
                    }
                    dialog.dismiss();
                }).setNegativeButton("\u53d6\u6d88", null).show();
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
        if (title.isEmpty()) { android.widget.Toast.makeText(this, "\u8bf7\u586b\u5199\u5185\u5bb9", android.widget.Toast.LENGTH_SHORT).show(); return; }
        if (manualAssigner.isEmpty()) manualAssigner = "\u7238\u7238";
        boolean homework = "homework".equals(manualType);
        boolean notice = "notice".equals(manualType);
        boolean checkin = "checkin".equals(manualType);
        if (manualDueAt <= 0 && !notice) {
            DeadlineRules.Result automatic = DeadlineRules.resolve(title, "", manualAssignedAt > 0 ? manualAssignedAt : System.currentTimeMillis(), homework);
            if (automatic.at > 0) manualDueAt = automatic.at;
            else { android.widget.Toast.makeText(this, "\u8bf7\u4e3a\u8fd9\u9879\u4efb\u52a1\u9009\u62e9\u5b8c\u6210\u65f6\u95f4", android.widget.Toast.LENGTH_SHORT).show(); return; }
        }
        String due = manualDueAt > 0 ? formatDate(manualDueAt) : "\u672a\u6ce8\u660e\u65f6\u95f4";
        org.json.JSONArray checklist = new org.json.JSONArray();
        if (homework) checklist.put(title);
        String category = notice ? TodoItem.CATEGORY_NOTICE : checkin ? TodoItem.CATEGORY_CHECKIN : TodoItem.CATEGORY_TASK;
        int result = TodoStore.addStructured(this, title, due, manualDueAt, manualAssigner, "", category, "",
                "", "", title, homework, checklist);
        if (result == 0) android.widget.Toast.makeText(this, "\u8fd9\u9879\u5185\u5bb9\u4f3c\u4e4e\u5df2\u5728\u6e05\u5355\u4e2d", android.widget.Toast.LENGTH_SHORT).show();
        else android.widget.Toast.makeText(this, "\u5df2\u6dfb\u52a0", android.widget.Toast.LENGTH_SHORT).show();
        manualTitleField = null; manualTypeValue = null; manualType = "task";
        manualDueAt = 0; manualAssigner = ""; manualAssignedAt = 0;
        renderHome();
    }

    private void configureHomeActions() {
        bottomActions.removeAllViews();
        bottomActions.setVisibility(View.GONE);
    }

    private void configureCategoryActions() { configureHomeActions(); }

    private void configureSettingsActions() { configureHomeActions(); }

    private void openManualSupplement() {
        manualAssignedAt = System.currentTimeMillis();
        manualDueAt = 0;
        manualAssigner = "";
        manualType = "task";
        renderManualForm();
    }

    private void configureSingleAction(int icon, String label, Runnable action) {
        bottomActions.removeAllViews(); bottomActions.setVisibility(View.VISIBLE);
        bottomActions.setOrientation(LinearLayout.HORIZONTAL); bottomActions.setPadding(dp(16), dp(8), dp(16), dp(12));
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) bottomActions.getLayoutParams();
        params.width = -1; params.height = -2; params.gravity = Gravity.BOTTOM; params.setMargins(0, 0, 0, 0); bottomActions.setLayoutParams(params);
        bottomActions.addView(actionTile(icon, label, action), new LinearLayout.LayoutParams(-1, dp(64)));
    }

    private void configureManualActions() {
        bottomActions.removeAllViews(); bottomActions.setVisibility(View.VISIBLE);
        bottomActions.setOrientation(LinearLayout.HORIZONTAL); bottomActions.setPadding(dp(16), dp(8), dp(16), dp(12));
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) bottomActions.getLayoutParams();
        params.width = -1; params.height = -2; params.gravity = Gravity.BOTTOM; params.setMargins(0, 0, 0, dp(60)); bottomActions.setLayoutParams(params);
        bottomActions.addView(actionTile(android.R.drawable.ic_menu_save, "保存待办", this::saveManualTask), new LinearLayout.LayoutParams(0, dp(64), 1));
        bottomActions.addView(actionTile(android.R.drawable.ic_menu_close_clear_cancel, "取消", this::renderHome), new LinearLayout.LayoutParams(0, dp(64), 1));
    }

    private void capturePhoto() {
        Intent intent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        try { startActivityForResult(intent, REQUEST_CAMERA_TEXT); }
        catch (ActivityNotFoundException e) { android.widget.Toast.makeText(this, "没有可用的相机应用", android.widget.Toast.LENGTH_SHORT).show(); }
    }

    private void captureVoice() {
        Intent intent = new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
        intent.putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "说出要补充的待办");
        try { startActivityForResult(intent, REQUEST_VOICE_TEXT); }
        catch (ActivityNotFoundException e) { android.widget.Toast.makeText(this, "此手机没有可用的语音识别服务", android.widget.Toast.LENGTH_SHORT).show(); }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || manualTitleField == null) return;
        if (requestCode == REQUEST_VOICE_TEXT) {
            ArrayList<String> results=data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);
            if(results!=null&&!results.isEmpty()) appendCapturedText(results.get(0), "语音转写结果");
        } else if(requestCode == REQUEST_CAMERA_TEXT) {
            android.os.Bundle extras=data.getExtras();
            android.graphics.Bitmap bitmap=extras==null?null:(android.graphics.Bitmap)extras.get("data");
            if(bitmap==null){android.widget.Toast.makeText(this,"相机没有返回照片，请重试",android.widget.Toast.LENGTH_SHORT).show();return;}
            TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build())
                    .process(InputImage.fromBitmap(bitmap,0))
                    .addOnSuccessListener(result->{String recognized=result.getText(); if(recognized==null||recognized.trim().isEmpty())android.widget.Toast.makeText(this,"照片中没有识别到文字",android.widget.Toast.LENGTH_SHORT).show();else appendCapturedText(recognized,"照片文字识别");})
                    .addOnFailureListener(error->android.widget.Toast.makeText(this,"照片识别失败，请手动输入",android.widget.Toast.LENGTH_SHORT).show());
        }
    }

    private void appendCapturedText(String captured, String title) {
        EditText review=input("确认并可编辑识别出的文字",captured);
        review.setSingleLine(false);review.setMinLines(3);review.setGravity(Gravity.TOP|Gravity.START);
        new AlertDialog.Builder(this).setTitle(title).setView(review).setNegativeButton("取消",null)
                .setPositiveButton("填入任务",(d,w)->{String text=review.getText().toString().trim();if(!text.isEmpty()){String old=manualTitleField.getText().toString().trim();manualTitleField.setText(old.isEmpty()?text:old+"\n"+text);}}).show();
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

    private View actionFab(int iconResource, String label, Runnable action) {
        FrameLayout button = new FrameLayout(this);
        GradientDrawable surface = new GradientDrawable(); surface.setShape(GradientDrawable.OVAL); surface.setColor(0xDFFFFFFF);
        GradientDrawable mask = new GradientDrawable(); mask.setShape(GradientDrawable.OVAL); mask.setColor(Color.WHITE);
        button.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x337096A1), surface, mask));
        button.setElevation(dp(5)); button.setClickable(true); button.setFocusable(true);
        button.setContentDescription(label); button.setTooltipText(label);
        ImageView icon = new ImageView(this); icon.setImageResource(iconResource); icon.setColorFilter(BLUE); icon.setContentDescription(null);
        button.addView(icon, new FrameLayout.LayoutParams(dp(27), dp(27), Gravity.CENTER));
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private void enableNotificationAccess() {
        new AlertDialog.Builder(this)
                .setTitle("开启消息整理")
                .setMessage("允许后，应用会筛选微信中匹配已设置群聊之一的可见通知。不开启模型时本机按关键词整理；开启并同意模型 API 后，匹配群聊通知正文及该群未完成事项摘要会先将孩子姓名替换为“孩子”，再发送到你配置的模型服务分析。")
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
        if (settingsPage) { persistSettings(); renderHome(); }
        else if (manualPage || !activeCategory.isEmpty()) renderHome();
        else super.onBackPressed();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 71) openListenerSettings();
    }

    private void showDetail(TodoItem item) {
        boolean overdue = !item.done && item.dueAt > 0 && item.dueAt < System.currentTimeMillis();
        String info = "布置日期：" + formatDate(item.createdAt) + "\n完成日期：" + (item.due.isEmpty() ? "未填写" : item.due)
                + "\n布置人：" + item.sender + "\n状态：" + (item.done ? "已完成" : "待完成")
                + (overdue ? "（超时）" : item.overdueDeferred && !item.done ? "（已手动延时）" : "")
                + (item.done ? "\n实际完成：" + (item.completedAt > 0 ? formatDate(item.completedAt) : "历史未记录") : "")
                + (item.summary == null || item.summary.isEmpty() ? "" : "\n\n整理说明（AI归纳）：\n" + item.summary)
                + "\n\n原通知摘录（仅保存在本机）：\n" + (item.source.isEmpty() ? "家长手动补充" : item.source);
        AlertDialog.Builder dialog = new AlertDialog.Builder(this).setTitle(item.title).setMessage(info)
                .setNeutralButton("移除此项", (d, w) -> new AlertDialog.Builder(this).setMessage("从本机清单和完成历史中移除此项？").setNegativeButton("取消", null).setPositiveButton("移除", (x, y) -> { TodoStore.remove(this, item.id); renderHome(); }).show())
                .setNegativeButton("关闭", null);
        if (!item.done && item.dueAt > 0 && item.dueAt < System.currentTimeMillis()) {
            dialog.setPositiveButton("手动延时", (d, w) -> showDeferChoices(item));
        }
        dialog.show();
    }

    private void showDeferChoices(TodoItem item) {
        String[] choices={"延后 1 天", "延后 3 天", "延后 7 天"};
        new AlertDialog.Builder(this).setTitle("选择新的完成日期").setItems(choices,(d,which)->{
            Calendar due=Calendar.getInstance();due.setTimeInMillis(Math.max(item.dueAt,System.currentTimeMillis()));
            due.add(Calendar.DAY_OF_YEAR,which==0?1:(which==1?3:7));
            TodoStore.defer(this,item.id,due.getTimeInMillis());
            renderHome();
            android.widget.Toast.makeText(this,"已按你的选择延时；不会自动顺延",android.widget.Toast.LENGTH_SHORT).show();
        }).show();
    }

    private void section(String name) {
        TextView t = text(name, 16, INK, true); t.setPadding(dp(4), dp(14), 0, dp(8)); content.addView(t);
    }

    private EditText input(String hint, String value) {
        EditText e = new EditText(this); e.setSingleLine(true); e.setTextSize(16); e.setTextColor(INK); e.setHintTextColor(MUTED); e.setHint(hint); e.setText(value); e.setPadding(dp(13), dp(9), dp(13), dp(9));
        e.setBackground(shape(Color.WHITE, dp(13), Color.rgb(229, 231, 236))); return e;
    }

    private View cardField(String label, EditText input) {
        LinearLayout c = card(); c.setOrientation(LinearLayout.VERTICAL); c.addView(text(label, 14, INK, true)); c.addView(input, margin(0, dp(7), 0, 0)); return c;
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
