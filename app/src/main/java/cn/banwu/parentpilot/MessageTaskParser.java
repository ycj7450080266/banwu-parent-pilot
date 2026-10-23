package cn.banwu.parentpilot;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Local, rule-based classifier for the visible text in a WeChat notification. */
public final class MessageTaskParser {
    public static final class Candidate {
        public final String title, due, category;
        Candidate(String title, String due, String category) {
            this.title = title; this.due = due; this.category = category;
        }
    }

    private static final String DEFAULT_SCOPE = "家长,家长们,亲爱的家长,亲爱的家长们,各位家长,各位家长们,全体家长,请家长,@所有人,@全体成员,@全体家长,@全体同学,全体同学,同学们,各位同学,全班同学,全班,班级统一,孩子们,小朋友们";
    private static final String DEFAULT_TASK = "作业,练习,试卷,卷子,考试,测验,听写,默写,背诵,朗读,阅读,抄写,订正,预习,复习,生字,课文,绘画,画画,手工,答题,做题,提交,上交,上传,补交,补做,填写,填报,签字,签署,签名,回执,回复,确认,反馈,报数,接龙,打卡,缴费,报名,申请书,请假条,带回,带去,带好,带上,带来,准备好,穿校服,领取,归还,收集,统计,检查,请假";
    private static final String[] OPTIONAL = {"自愿", "有意向", "感兴趣", "有兴趣", "不参加可忽略", "不参加请忽略", "不参加也没关系", "可选报名"};
    private static final String[] RECURRING_CHECKIN = {"每日打卡", "每天打卡", "每周打卡", "连续打卡", "坚持打卡", "每日签到", "每天签到", "每周签到", "连续签到"};
    private static final String[] CHATTER = {"收到", "好的", "好嘞", "谢谢", "辛苦了", "哈哈", "😂", "👍", "收到，谢谢", "好的谢谢", "没事", "不好意思", "太棒了"};
    private static final String[] GENERIC_ACTION = {"请", "提醒", "需要", "记得", "完成"};
    private static final String[] ACTION_CUES = {"交回", "带回", "带去", "带好", "带上", "带来", "放在家里", "督促孩子", "接送", "穿校服", "准备好", "提交", "上交", "缴费", "报名", "接龙", "填报", "填写", "签字", "签署", "归还", "领取", "收集", "补交", "补做", "写完", "抄写", "订正", "朗读", "背诵", "默写", "听写", "练习", "交作业", "做作业", "做题", "打卡", "签到", "请假"};
    private static final String[] NOTICE_CUES = {"温馨提示", "温馨提醒", "家长会", "时间调整", "接送", "放学", "开学", "停课", "调课", "天气", "安全", "活动安排", "课程安排", "放假", "返校", "请阅读", "请查收", "通知书", "地点", "安排", "会议", "公告", "重要提醒", "校服"};
    private static final String[] FORMAL_NOTICE_CUES = {"通知如下", "请知悉", "特此通知", "学校通知", "转发通知", "以下通知"};

    private MessageTaskParser() {}

    public static Candidate parse(String raw, String childName) {
        return parse(raw, childName, DEFAULT_SCOPE, DEFAULT_TASK);
    }

    public static Candidate parse(String raw, String childName, String scopeText, String taskText) {
        if (raw == null) return null;
        String text = raw.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
        if (text.length() < 3 || text.length() > 1200) return null;
        for (String chatter : CHATTER) if (text.equals(chatter)) return null;

        String[] scopeWords = splitKeywords(scopeText);
        String[] taskWords = splitKeywords(taskText);
        boolean childNamed = childName != null && !childName.trim().isEmpty() && text.contains(childName.trim());
        boolean addressedAll = text.contains("@所有人") || text.contains("@全体成员") || containsAny(text, scopeWords);
        // A notification explicitly aimed at another family should not leak into this child's list.
        if (isExplicitlyDirectedToOtherFamily(text, childName, scopeText)) return null;

        String[] concreteWords = java.util.Arrays.stream(taskWords)
                .filter(word -> !java.util.Arrays.asList(GENERIC_ACTION).contains(word))
                .toArray(String[]::new);
        boolean action = containsAny(text, concreteWords) || containsAny(text, ACTION_CUES);
        boolean notice = containsAny(text, NOTICE_CUES) || isFormalNotice(text)
                || (text.contains("通知") && addressedAll);
        if (!action && !notice) return null;
        if (!action && containsAny(text, CHATTER) && !containsAny(text, NOTICE_CUES)) return null;
        String category;
        if (action && containsAny(text, RECURRING_CHECKIN)) category = TodoItem.CATEGORY_CHECKIN;
        else if (action) category = TodoItem.CATEGORY_TASK;
        else if (notice) category = TodoItem.CATEGORY_NOTICE;
        else return null;

        String title;
        if (TodoItem.CATEGORY_NOTICE.equals(category)) {
            title = text;
        } else {
            title = firstActionClause(text, taskWords);
            title = title.replaceAll("^[@#\\s，。:：]+", "").trim();
            String matchedPrefix = "";
            for (String scopeWord : scopeWords) if (scopeWord.length() > matchedPrefix.length() && title.startsWith(scopeWord)) matchedPrefix = scopeWord;
            if (!matchedPrefix.isEmpty()) title = title.substring(matchedPrefix.length()).replaceFirst("^[\\s，,：:。]+", "").trim();
            if (title.isEmpty()) title = text;
        }
        if (title.length() > 88) title = title.substring(0, 88) + "…";
        return new Candidate(title, findDue(text), category);
    }

    /** Filters direct mentions to another family before local or AI analysis. */
    public static boolean isExplicitlyDirectedToOtherFamily(String raw, String childName, String scopeText) {
        if (raw == null) return false;
        String text = raw.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
        if (!text.contains("@")) return false;
        boolean childNamed = childName != null && !childName.trim().isEmpty() && text.contains(childName.trim());
        boolean broadcast = text.contains("@所有人") || text.contains("@全体成员")
                || containsAny(text, splitKeywords(scopeText));
        return !childNamed && !broadcast;
    }

    /** Returns keyword-match evidence without including the notification body itself. */
    public static String diagnosticSummary(String raw, String childName, String scopeText, String taskText) {
        String text = raw == null ? "" : raw.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
        String[] scopeWords = splitKeywords(scopeText);
        String[] taskWords = splitKeywords(taskText);
        boolean childNamed = childName != null && !childName.trim().isEmpty() && text.contains(childName.trim());
        boolean addressedAll = text.contains("@所有人") || text.contains("@全体成员") || containsAny(text, scopeWords);
        boolean specificMention = text.contains("@") && !childNamed && !addressedAll;
        return "筛选依据：任务词命中=" + matchedWords(text, taskWords)
                + "；孩子姓名命中=" + (childNamed ? "是" : "否")
                + "；范围词命中=" + matchedWords(text, scopeWords)
                + "；动作词命中=" + matchedWords(text, ACTION_CUES)
                + "；通知词命中=" + matchedWords(text, NOTICE_CUES)
                + "；正式通知格式=" + (isFormalNotice(text) ? "是" : "否")
                + "；是否因@其他人排除=" + (specificMention ? "是" : "否");
    }

    private static String firstActionClause(String text, String[] taskWords) {
        String[] clauses = text.split("[。！？!?；;]");
        for (String clause : clauses) if (containsAny(clause, taskWords)) return clause.trim();
        return text;
    }

    private static String findDue(String text) {
        Matcher monthDay = Pattern.compile("(\\d{1,2}月\\d{1,2}日(?:前|前提交|截止)?)").matcher(text);
        if (monthDay.find()) return monthDay.group(1);
        Matcher weekday = Pattern.compile("(本周|这周)?(周[一二三四五六日天])(?:前|晚上|早上|下午)?").matcher(text);
        if (weekday.find()) return weekday.group().trim();
        if (text.contains("明天")) return "明天";
        if (text.contains("今晚")) return "今晚";
        if (text.contains("今天")) return "今天";
        if (text.contains("本周")) return "本周内";
        return "未注明时间";
    }

    private static boolean containsAny(String text, String[] words) {
        for (String word : words) if (text.contains(word)) return true;
        return false;
    }

    private static String matchedWords(String text, String[] words) {
        StringBuilder matches = new StringBuilder("[");
        for (String word : words) if (!word.isEmpty() && text.contains(word)) {
            if (matches.length() > 1) matches.append("、");
            matches.append(word);
        }
        return matches.append("]").toString();
    }

    private static boolean isFormalNotice(String text) {
        if (containsAny(text, FORMAL_NOTICE_CUES)) return true;
        String normalized = text.replaceFirst("^[\\p{Punct}\\s\\u3000]*", "");
        return normalized.matches("^通知[：:、，,\\s].*");
    }

    private static String[] splitKeywords(String raw) {
        if (raw == null || raw.trim().isEmpty()) return new String[0];
        return java.util.Arrays.stream(raw.split("[,，;；\\n\\r]+"))
                .map(String::trim).filter(word -> !word.isEmpty()).toArray(String[]::new);
    }
}
