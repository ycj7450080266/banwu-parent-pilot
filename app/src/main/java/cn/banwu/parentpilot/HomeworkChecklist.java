package cn.banwu.parentpilot;

import org.json.JSONArray;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lightweight fallback step extraction for homework when no model API is configured. */
final class HomeworkChecklist {
    private static final Pattern NUMBERED = Pattern.compile("(?:^|\\s)(?:\\d+\\s*[.、．)]|[①②③④⑤⑥⑦⑧⑨⑩]|[1-9]\\uFE0F?\\u20E3)\\s*");
    private HomeworkChecklist() { }

    static JSONArray extract(String text) {
        JSONArray steps = new JSONArray();
        if (text == null || text.trim().isEmpty()) return steps;
        Matcher matcher = NUMBERED.matcher(text);
        int start = -1;
        while (matcher.find()) {
            if (start >= 0) add(steps, text.substring(start, matcher.start()));
            start = matcher.end();
        }
        if (start >= 0) add(steps, text.substring(start));
        if (steps.length() == 0) add(steps, text);
        return steps;
    }

    private static void add(JSONArray steps, String value) {
        String clean = value.replaceAll("[\\r\\n]+", " ").replaceAll("\\s+", " ")
                .replaceAll("^[\\s，,。；;：:]+|[\\s，,。；;：:]+$", "").trim();
        if (clean.length() > 180) clean = clean.substring(0, 177) + "…";
        if (clean.length() >= 3) steps.put(clean);
    }

    static String title(String text) {
        String value = text == null ? "" : text;
        if (value.contains("国庆")) return "国庆假期学习任务";
        if (value.contains("假期") || value.contains("放假")) return "假期学习任务";
        int subjects = (value.contains("语文") ? 1 : 0) + (value.contains("数学") ? 1 : 0) + (value.contains("英语") ? 1 : 0);
        if (subjects > 1) return "多学科学习任务";
        if (value.contains("语文")) return "语文学习任务";
        if (value.contains("数学")) return "数学学习任务";
        if (value.contains("英语")) return "英语学习任务";
        return "今日学习任务";
    }
}
