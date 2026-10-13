package cn.banwu.parentpilot;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Local homework deadline defaults. Unknown future holidays are intentionally not guessed. */
final class DeadlineRules {
    static final class Result { final long at; final String label; Result(long at, String label) { this.at = at; this.label = label; } }
    private static final long DAY = 24L * 60 * 60 * 1000;
    private DeadlineRules() {}

    static boolean looksLikeHomework(String text) {
        if (text == null) return false;
        String s = text.replace(" ", "");
        return contains(s, "作业", "数学", "语文", "英语", "听写", "默写", "背诵", "预习", "复习", "练习", "《练》", "《口》", "卷子", "试卷", "生字", "课文")
                || Pattern.compile("(?:练|口)\\s*\\d").matcher(s).find();
    }

    static Result resolve(String text, String explicitDue, long receivedAt, boolean homework) {
        Calendar due = Calendar.getInstance(); due.setTimeInMillis(receivedAt);
        String body = text == null ? "" : text;
        boolean explicit = false;
        Matcher md = Pattern.compile("(?:(\\d{4})年)?(\\d{1,2})月(\\d{1,2})日").matcher(body);
        Matcher slash = Pattern.compile("(?:(\\d{4})[-/年])?(\\d{1,2})[-/月](\\d{1,2})(?:日)?").matcher(body);
        if (md.find()) { setDate(due, md.group(1), md.group(2), md.group(3)); explicit = true; }
        else if (slash.find()) { setDate(due, slash.group(1), slash.group(2), slash.group(3)); explicit = true; }
        else if (body.contains("后天")) { due.add(Calendar.DAY_OF_YEAR, 2); explicit = true; }
        else if (body.contains("明天")) { due.add(Calendar.DAY_OF_YEAR, 1); explicit = true; }
        else if (body.contains("今天") || body.contains("今晚")) explicit = true;
        else {
            Matcher weekday = Pattern.compile("(?:本周|这周)?周([一二三四五六日天])").matcher(body);
            if (weekday.find()) {
                int target = weekday(weekday.group(1));
                int current = due.get(Calendar.DAY_OF_WEEK);
                int delta = (target - current + 7) % 7;
                due.add(Calendar.DAY_OF_YEAR, delta);
                explicit = true;
            }
        }
        if (!explicit && explicitDue != null && !explicitDue.isEmpty() && !"未注明时间".equals(explicitDue)) {
            Matcher d = Pattern.compile("(\\d{1,2})月(\\d{1,2})日").matcher(explicitDue);
            if (d.find()) { setDate(due, null, d.group(1), d.group(2)); explicit = true; }
        }
        if (!explicit && !homework) return new Result(0L, "未注明时间");

        int hour = 21, minute = 0;
        Matcher clock = Pattern.compile("(?:晚上|今晚|下午)?\\s*(\\d{1,2})(?::(\\d{2})|点(?:半|(\\d{1,2})分)?)").matcher(body);
        if (clock.find()) {
            int h = Integer.parseInt(clock.group(1));
            if (body.contains("下午") || body.contains("晚上") || body.contains("今晚")) { if (h < 12) h += 12; }
            hour = h; minute = clock.group(2) != null ? Integer.parseInt(clock.group(2)) : (clock.group(3) != null ? Integer.parseInt(clock.group(3)) : (clock.group().contains("半") ? 30 : 0));
        } else if (homework) {
            int dow = due.get(Calendar.DAY_OF_WEEK);
            if (inHoliday(due)) {
                moveToHolidayEnd(due);
                hour = 19;
            } else if (dow == Calendar.SATURDAY) {
                due.add(Calendar.DAY_OF_YEAR, 1);
                hour = 19;
            } else if (dow == Calendar.SUNDAY) {
                hour = 19;
            } else {
                // A task received on a school day is due by 21:00 that evening.
                hour = 21;
            }
        }
        due.set(Calendar.HOUR_OF_DAY, hour); due.set(Calendar.MINUTE, minute); due.set(Calendar.SECOND, 0); due.set(Calendar.MILLISECOND, 0);
        String label = new SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(new Date(due.getTimeInMillis()));
        return new Result(due.getTimeInMillis(), label);
    }

    private static void setDate(Calendar c, String year, String month, String day) {
        int y = year == null || year.isEmpty() ? c.get(Calendar.YEAR) : Integer.parseInt(year);
        c.set(Calendar.YEAR, y); c.set(Calendar.MONTH, Integer.parseInt(month) - 1); c.set(Calendar.DAY_OF_MONTH, Integer.parseInt(day));
    }
    private static int weekday(String ch) {
        switch (ch) { case "一": return Calendar.MONDAY; case "二": return Calendar.TUESDAY; case "三": return Calendar.WEDNESDAY; case "四": return Calendar.THURSDAY; case "五": return Calendar.FRIDAY; case "六": return Calendar.SATURDAY; default: return Calendar.SUNDAY; }
    }
    private static boolean contains(String s, String... words) { for (String w : words) if (s.contains(w)) return true; return false; }

    // 2026 national holiday periods; keep this table local so dates are never inferred from a cloud service.
    private static final String[][] HOLIDAYS_2026 = {{"2026-01-01","2026-01-03"},{"2026-02-15","2026-02-23"},{"2026-04-04","2026-04-06"},{"2026-05-01","2026-05-05"},{"2026-06-19","2026-06-21"},{"2026-09-25","2026-09-27"},{"2026-10-01","2026-10-07"}};
    private static long dateValue(Calendar c) { Calendar x = (Calendar)c.clone(); x.set(Calendar.HOUR_OF_DAY,0);x.set(Calendar.MINUTE,0);x.set(Calendar.SECOND,0);x.set(Calendar.MILLISECOND,0);return x.getTimeInMillis(); }
    private static Calendar parseDate(String s) { try { Calendar c=Calendar.getInstance();c.setTime(new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).parse(s));return c; } catch(Exception e){return Calendar.getInstance();} }
    private static boolean inHoliday(Calendar d) { long value=dateValue(d); for(String[] range:HOLIDAYS_2026) if(value>=dateValue(parseDate(range[0]))&&value<=dateValue(parseDate(range[1])))return true;return false; }
    private static void moveToHolidayEnd(Calendar d) { long value=dateValue(d); for(String[] range:HOLIDAYS_2026) if(value>=dateValue(parseDate(range[0]))&&value<=dateValue(parseDate(range[1]))){d.setTimeInMillis(parseDate(range[1]).getTimeInMillis());break;} }
}
