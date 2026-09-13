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

    static final String PROMPT="这是一张B站主播的直播周表图片。请提取其中所有直播安排，每行一条，严格按格式输出：星期|开始|结束|备注。"
        +"星期用数字0到6表示周一到周日；结束时间没有就留空；没有具体时间的行不要输出；不要输出任何解释。";

    /** image must be JPEG or PNG bytes; returns the model's plain-text schedule lines. */
    public static String readSchedule(byte[] image,String mime,String key,String model)throws IOException{
        if(key==null||key.trim().isEmpty())throw new IOException("请先在设置里填写 AI 接口密钥");
        if(image==null||image.length==0)throw new IOException("没有可识别的图片");
        try{
            JSONObject message=new JSONObject();
            Prefs.put(message,"role","user");
            JSONArray content=new JSONArray();
            JSONObject text=new JSONObject();Prefs.put(text,"type","text");Prefs.put(text,"text",PROMPT);
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
            Prefs.put(body,"max_tokens",4000);
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
            if(answer.trim().isEmpty())throw new IOException("AI 没有返回内容，请稍后再试或手动录入");
            return answer;
        }catch(org.json.JSONException e){throw new IOException("AI 响应无法解析，请稍后再试");}
    }
    private static String readAll(InputStream in)throws IOException{
        if(in==null)return "";
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;
        while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>1048576)throw new IOException("AI 响应过大");}
        return out.toString("UTF-8");
    }
}
