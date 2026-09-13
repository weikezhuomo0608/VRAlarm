package dev.hazel.livealarm;

import android.util.Base64;
import org.json.*;
import java.io.*;
import java.net.*;

/**
 * One-shot vision OCR over the user's own AI account (DeepSeek-compatible chat endpoint).
 * The schedule picture leaves the device only when the user taps AI recognition in the
 * schedule editor; nothing else about the watch is ever sent.
 */
public final class AiOcr {
    private AiOcr() {}

    /** Built per request so relative dates ("今天", "明天") can be resolved against today. */
    static String prompt(){
        java.time.LocalDate today=java.time.LocalDate.now();
        String[] names={"星期一","星期二","星期三","星期四","星期五","星期六","星期日"};
        String weekday=names[today.getDayOfWeek().getValue()-1];
        return "这是一张B站主播的直播周表或开播预告图片。请提取其中每一条直播安排，每行一条，严格按以下管道分隔格式输出，不要输出任何解释或多余符号：星期|开始|结束|日期|备注。"
            +"星期用数字0到6表示周一到周日，按图片内容判断，判断不了就留空；"
            +"开始和结束用24小时制 HH:MM，结束时间没有就留空，「晚上8点」这类写法换算成 20:00；"
            +"日期列只在该行写了具体日期（如 2月15日、2/15、15号）或相对日期（今天、明天、后天）时填写，统一写成 月/日（例如 2/15，跨年写成 2027/2/15），相对日期按今天是 "
            +today.getYear()+"年"+today.getMonthValue()+"月"+today.getDayOfMonth()+"日 "+weekday+" 换算，没有日期就留空；"
            +"备注列写直播内容、联动对象等，没有就留空；"
            +"「休息」「停播」这类没有具体时间的行不要输出。"
            +"示例：1|20:00|22:30|2/17|联动回。";
    }

    /** image must be JPEG or PNG bytes; returns the model's plain-text schedule lines.
     *  Reasoning models sometimes spend every token thinking and return an empty answer;
     *  when that happens one permissive retry (more tokens, "guess anyway" instruction)
     *  goes out, because a rough result beats no result. */
    public static String readSchedule(byte[] image,String mime,String key,String model)throws IOException{
        if(key==null||key.trim().isEmpty())throw new IOException("请先在设置里填写 AI 接口密钥");
        if(image==null||image.length==0)throw new IOException("没有可识别的图片");
        String answer=chat(image,mime,key,model,4000,"");
        if(answer.isEmpty())answer=chat(image,mime,key,model,8000,
            "注意：即使不确定，也要按上面的格式给出最佳猜测的输出行；星期无法确定就留空，但不要因此整个留空回答。");
        if(answer.isEmpty())throw new IOException("AI 没有返回内容，请稍后再试或手动录入");
        return answer;
    }
    private static String chat(byte[] image,String mime,String key,String model,int maxTokens,String extra)throws IOException{
        try{
            JSONObject message=new JSONObject();
            Prefs.put(message,"role","user");
            JSONArray content=new JSONArray();
            JSONObject text=new JSONObject();Prefs.put(text,"type","text");Prefs.put(text,"text",prompt()+extra);
            content.put(text);
            JSONObject img=new JSONObject();Prefs.put(img,"type","image_url");
            JSONObject url=new JSONObject();
            Prefs.put(url,"url","data:"+(mime==null||mime.isEmpty()?"image/jpeg":mime)+";base64,"+Base64.encodeToString(image,Base64.NO_WRAP));
            Prefs.put(img,"image_url",url);
            content.put(img);
            Prefs.put(message,"content",content);
            JSONArray messages=new JSONArray();messages.put(message);
            JSONObject body=new JSONObject();
            Prefs.put(body,"model",model==null||model.trim().isEmpty()?"deepseek-flash":model.trim());
            Prefs.put(body,"max_tokens",maxTokens);
            Prefs.put(body,"messages",messages);

            HttpURLConnection c=(HttpURLConnection)new URL("https://api.deepseek.com/chat/completions").openConnection();
            c.setConnectTimeout(15000);c.setReadTimeout(120000);
            c.setRequestMethod("POST");c.setDoOutput(true);
            c.setRequestProperty("Content-Type","application/json");
            c.setRequestProperty("Authorization","Bearer "+key.trim());
            try(OutputStream out=c.getOutputStream()){out.write(body.toString().getBytes("UTF-8"));}
            int status=c.getResponseCode();
            String resp=readAll(status<400?c.getInputStream():c.getErrorStream());
            if(status!=200){
                String hint=resp;
                try{JSONObject err=new JSONObject(resp).optJSONObject("error");if(err!=null)hint=err.optString("message",resp);}catch(Exception ignored){}
                if(hint.length()>300)hint=hint.substring(0,300);
                throw new IOException("AI 接口返回 HTTP "+status+"："+hint);
            }
            JSONObject root=new JSONObject(resp);
            JSONArray choices=root.optJSONArray("choices");
            JSONObject msg=choices==null||choices.length()==0?null:choices.optJSONObject(0);
            msg=msg==null?null:msg.optJSONObject("message");
            String answer=msg==null?"":msg.optString("content","");
            return answer.replace("```","").trim();
        }catch(org.json.JSONException e){throw new IOException("AI 响应无法解析，请稍后再试");}
    }
    private static String readAll(InputStream in)throws IOException{
        if(in==null)return "";
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;
        while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>1048576)throw new IOException("AI 响应过大");}
        return out.toString("UTF-8");
    }
}
