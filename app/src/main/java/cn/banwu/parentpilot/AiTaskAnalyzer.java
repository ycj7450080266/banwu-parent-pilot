package cn.banwu.parentpilot;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** OpenAI-compatible chat-completions adapter. It sends only the selected notification text. */
final class AiTaskAnalyzer {
    static final class Task {
        final String title, summary, category, due, eventKey, action, targetId;
        final boolean homework;
        final JSONArray checklist;
        Task(String title, String category, String due, String eventKey, boolean homework) {
            this(title, title, category, due, eventKey, homework, "new", "");
        }
        Task(String title, String summary, String category, String due, String eventKey, boolean homework, String action, String targetId) {
            this(title, summary, category, due, eventKey, homework, action, targetId, new JSONArray());
        }
        Task(String title, String summary, String category, String due, String eventKey, boolean homework, String action, String targetId, JSONArray checklist) {
            this.title=title; this.summary=summary; this.category=TodoItem.normalizeCategory(category); this.due=due; this.eventKey=eventKey;
            this.homework=homework; this.action=action; this.targetId=targetId; this.checklist=checklist == null ? new JSONArray() : checklist;
        }
    }    static final class Result { final List<Task> tasks; final String raw; Result(List<Task> tasks, String raw){this.tasks=tasks;this.raw=raw;} }
    private AiTaskAnalyzer() {}

    static Result analyze(Context context, String message, JSONArray activeEvents) throws Exception {
        String prompt = "你是家长的班级群消息整理助手。目标是让家长一眼看懂要做什么，不是复述聊天记录。输入含 new_messages（本次收到的一条或多条新通知）和 active_events（当前群已有未完成事项的摘要）。先把 new_messages 按不同主题拆成独立事件：一批通知里可能同时有少儿互助金统计、书写比赛文件等完全不同的事情；不能因为同群、同一发送人、时间接近就合并。每个主题只与 active_events 中主题明确相同的事项比较；内容不相干时必须 action=new，严禁指向其他事项。同一件事情即使换了说法、由不同老师补充、分几条通知发出，也必须合并为一个事项：按实际行动、对象、日期/时间段、所需提交物判断是否同一主题；同一个请假登记/请假条/照片提交不能拆成“任务”和“打卡”两项。只有确属新事项才 action=new；同一主题的补充、澄清、重复提醒才 action=update，并复制对应事项精确 target_id；普通成员的确认、提问、闲聊或无新增信息的接龙回应 action=ignore。一个事件在整批里只输出一次。title 必须改写成家长易懂的短行动名称，不带发送人、群名、消息条数和原句；summary 用1至3句把该事件所有相关通知合并成清晰说明，写明家长需要做什么及已明确的关键规则/截止时间，删除寒暄、重复内容和无关话题，不得复制整段聊天。示例：通知要求家长填写少儿互助金情况，今晚20点截止，老师又澄清已自行购买者选择“不购买”——整理为一个待办并把澄清写入 summary。若同批还含书写比赛文件，应另建独立 notice/todo，不得并入互助金事项。作业必须设置 homework=true，并输出 checklist 字符串数组；一条 checklist 对应一项可独立勾选的作业，内容简短明确，可用“语文｜……”标出学科。不要把一个老师的一整段作业拆成多个独立待办。若 active_events 中存在同群、同一个学习单位（同一上学日、同一周末（截止周日19:00）或同一完整假期（截止假期最后一天19:00））的 homework_pack，后续不同学科作业应 action=update 并追加到同一作业表；不要将通知或不同日期的作业混入。普通任务必须 homework=false 且 checklist=[]。category 只能为 notice、task、checkin：notice 是只需阅读的信息；task 是家长一次性需要完成的动作，包括作业、填写、报名、上传、必须回复及自愿参加事项；checkin 仅用于每天/每周等重复性签到或持续打卡活动；一次性接龙、一次性报数/填表/报名/上传照片都归入 task，不得标成 checkin。“收到”等确认不生成事项，老师说自己稍后处理的内容也忽略。action 只能为 new、update、ignore。due 只填写明确截止语义的期限，名单中的请假日期、比赛活动日期不算截止时间。返回 JSON 对象，顶层 items 为数组，元素字段为 action、target_id、title、summary、category、due、homework、checklist、event_key；ignore 项无需输出。没有家长需处理事项时 items 为空。";
        JSONObject input = new JSONObject();
        input.put("new_messages", message == null ? "" : message);
        input.put("active_events", activeEvents == null ? new JSONArray() : activeEvents);
        JSONObject response = post(context, input.toString(), prompt, true);
        JSONArray choices=response.optJSONArray("choices");
        if (choices==null || choices.length()==0) throw new IllegalStateException("模型响应没有 choices");
        String content=choices.optJSONObject(0).optJSONObject("message").optString("content", "").trim();
        if(content.startsWith("```")){content=content.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");}
        JSONObject parsed=new JSONObject(content);
        JSONArray arr=parsed.optJSONArray("items");
        ArrayList<Task> tasks=new ArrayList<>();
        Set<String> usedTargets=new HashSet<>();
        if(arr!=null) for(int i=0;i<arr.length() && i<12;i++){
            JSONObject item=arr.optJSONObject(i); if(item==null)continue;
            String action=item.optString("action", "new").trim().toLowerCase(java.util.Locale.ROOT);
            if("ignore".equals(action))continue;
            if(!"update".equals(action))action="new";
            String targetId="update".equals(action)?item.optString("target_id", "").trim():"";
            String title=cleanTitle(item.optString("title", ""), message);
            if(title.isEmpty() || ("update".equals(action) && targetId.isEmpty()))continue;
            if("update".equals(action) && !usedTargets.add(targetId)){action="new";targetId="";}
            String category=item.optString("category", "todo");
            String due=item.optString("due", "");
            String eventKey=item.optString("event_key", "").trim();
            if(eventKey.isEmpty() && !targetId.isEmpty() && activeEvents!=null){
                for(int j=0;j<activeEvents.length();j++){
                    JSONObject active=activeEvents.optJSONObject(j);
                    if(active!=null && targetId.equals(active.optString("id"))){eventKey=active.optString("event_key", "");break;}
                }
            }
            if(eventKey.isEmpty())eventKey=stableEventKey(title,category,due);
            String summary=item.optString("summary", "").trim();
            if(summary.isEmpty())summary=title;
            JSONArray checklist = new JSONArray();
            JSONArray returnedSteps = item.optJSONArray("checklist");
            if (returnedSteps != null) for (int k = 0; k < returnedSteps.length() && k < 40; k++) {
                String step = returnedSteps.optString(k, "").trim();
                if (!step.isEmpty()) checklist.put(step);
            }
            boolean homework = item.optBoolean("homework", false) || checklist.length() > 0;
            tasks.add(new Task(title,summary,category,due,eventKey,homework,action,targetId,checklist));
        }
        return new Result(tasks, content);
    }
    private static String cleanTitle(String title, String source) {
        if(title==null)return "";
        String clean=title.replaceAll("[\\r\\n]+"," ").trim();
        clean=clean.replaceFirst("^(?:\\[\\d+条\\]\\s*)+","");
        clean=clean.replaceFirst("^(?:事项|任务|待办)\\s*[：:]\\s*","");
        clean=clean.replaceAll("^[\"“”「」]+|[\"“”「」]+$","");
        clean=clean.trim();
        if(clean.length()>50)return "";
        String normalized=normalizeForCompare(clean);
        String original=normalizeForCompare(source);
        if(original.length()>=36 && normalized.equals(original))return "";
        return clean;
    }

    private static String normalizeForCompare(String value) {
        return value==null ? "" : value.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[\\s，。！？!?、；;：:（）()【】\\[\\]“”]+","");
    }

    private static String stableEventKey(String title, String category, String due) {
        String key=normalizeForCompare(title)+"|"+TodoItem.normalizeCategory(category)+"|"+normalizeForCompare(due);
        return "summary:"+java.util.UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }

    static String test(Context context, String url, String model, String key) throws Exception {
        JSONObject response=post(url,model,key,"", "只回答 OK", false);
        JSONArray choices=response.optJSONArray("choices");
        return choices!=null && choices.length()>0 ? "连接成功："+choices.optJSONObject(0).optJSONObject("message").optString("content", "已收到模型响应") : "连接成功，但响应格式不是 chat-completions";
    }

    private static JSONObject post(Context context, String message, String prompt, boolean redact) throws Exception {
        String child=TodoStore.child(context);
        String safe=message==null?"":message;
        if(redact && child!=null && !child.trim().isEmpty()) safe=safe.replace(child.trim(),"孩子");
        return post(TodoStore.aiUrl(context),TodoStore.aiModel(context),TodoStore.aiKey(context),safe,prompt,true);
    }

    private static JSONObject post(String base,String model,String key,String message,String prompt,boolean includeMessage) throws Exception {
        if(base==null || !base.trim().startsWith("https://"))throw new IllegalArgumentException("API 地址必须使用 HTTPS");
        if(model==null || model.trim().isEmpty())throw new IllegalArgumentException("请填写模型名称");
        if(key==null || key.trim().isEmpty())throw new IllegalArgumentException("请填写 API Key");
        String endpoint=base.trim().replaceAll("/+$", ""); if(!endpoint.endsWith("/chat/completions"))endpoint+="/chat/completions";
        JSONObject body=new JSONObject(); body.put("model",model.trim()); body.put("temperature",0.1);
        JSONArray messages=new JSONArray(); messages.put(new JSONObject().put("role","system").put("content",prompt));
        if(includeMessage)messages.put(new JSONObject().put("role","user").put("content",message));
        else messages.put(new JSONObject().put("role","user").put("content","请确认服务可以响应"));
        body.put("messages",messages);
        HttpURLConnection connection=(HttpURLConnection)new URL(endpoint).openConnection();
        connection.setRequestMethod("POST"); connection.setConnectTimeout(12000); connection.setReadTimeout(30000);
        connection.setDoOutput(true); connection.setRequestProperty("Content-Type","application/json; charset=utf-8");
        connection.setRequestProperty("Authorization","Bearer "+key.trim());
        try(OutputStream out=connection.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}
        int status=connection.getResponseCode(); InputStream stream=status>=200&&status<300?connection.getInputStream():connection.getErrorStream();
        String response=read(stream); connection.disconnect();
        if(status<200||status>=300)throw new IllegalStateException("API 返回 HTTP "+status);
        return new JSONObject(response);
    }
    private static String read(InputStream in)throws Exception { if(in==null)return "";try(InputStream source=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=source.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");} }
}