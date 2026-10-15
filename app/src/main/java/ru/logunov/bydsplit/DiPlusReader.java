package ru.logunov.bydsplit;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.json.JSONObject;
/** Di+ is the primary DiLink 2 reader, as in BYDMate-DiLink2-Seagull. */
final class DiPlusReader {
 private static final String TEMPLATE="SOC:{电量百分比}|Speed:{车速}|Mileage:{里程}|Power:{发动机功率}|ChargeGun:{充电枪插枪状态}|MinBatTemp:{最低电池温度}|MaxBatTemp:{最高电池温度}|Voltage12V:{蓄电池电压}|MinCellV:{最低电池电压}|MaxCellV:{最高电池电压}|ExtTemp:{车外温度}|InsideTemp:{车内温度}|ACStatus:{空调状态}|ACTemp:{主驾驶空调温度}|FanLevel:{风量档位}|ACCirc:{空调循环方式}|TirePressFL:{左前轮气压}|TirePressFR:{右前轮气压}|TirePressRL:{左后轮气压}|TirePressRR:{右后轮气压}|DriveMode:{整车运行模式}|Wind:{空调出风模式}|MotorRpm:{前电机转速}";
 static JSONObject read()throws Exception {
  HttpURLConnection c=(HttpURLConnection)new URL("http://127.0.0.1:8988/api/getDiPars?text="+URLEncoder.encode(TEMPLATE,"UTF-8")).openConnection();
  c.setConnectTimeout(1800);c.setReadTimeout(1800);c.setInstanceFollowRedirects(false);
  try {
   if(c.getResponseCode()!=200)throw new IOException("Di+ HTTP "+c.getResponseCode());
   String text;try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
    byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1){if(out.size()+n>65536)throw new IOException("Di+ response too large");out.write(buf,0,n);}text=new String(out.toByteArray(),StandardCharsets.UTF_8);
   }
   JSONObject body=new JSONObject(text);if(!body.optBoolean("success"))throw new IOException("Di+ did not return success");
   Map<String,Double> values=DiPlusValues.parse(body.optString("val",""));
   if(!values.containsKey("socPercent")&&!values.containsKey("mileage"))throw new IOException("Di+ snapshot unavailable");
   JSONObject result=new JSONObject();result.put("protocol",1);result.put("androidApi",28);result.put("source","Di+");result.put("ok",true);result.put("values",new JSONObject(values));result.put("errors",new JSONObject());return result;
  }finally{c.disconnect();}
 }
}
