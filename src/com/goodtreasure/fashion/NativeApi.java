package com.goodtreasure.fashion;
import android.content.Context;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.security.SecureRandom;
import java.security.MessageDigest;
import android.util.Base64;
import java.util.UUID;

/** Only JSON/data requests. No website HTML, WebView or shopping browser. */
public final class NativeApi {
 public static final class Failure extends Exception {public final int status;public Failure(int status,String message){super(message);this.status=status;}}
 public final JSONObject config;
 private final Context context;private final Vault vault;private boolean nativeServerReady=false;
 public NativeApi(Context context,Vault vault) throws Exception{this.context=context;this.vault=vault;config=new JSONObject(asset("config.json"));}
 public String asset(String name) throws Exception{try(InputStream in=context.getAssets().open(name)){return read(in);}}
 private static String read(InputStream in) throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1){bytes.write(buffer,0,count);if(bytes.size()>2*1048576)throw new IOException("Response too large");}return bytes.toString("UTF-8");}
 public boolean signedIn(){return !vault.read("session").optString("refresh_token").isEmpty();}
 public String email(){JSONObject u=vault.read("session").optJSONObject("user");return u==null?"":u.optString("email");}
 public String userId(){JSONObject u=vault.read("session").optJSONObject("user");return u==null?"":u.optString("id");}
 private JSONObject http(String address,String method,JSONObject body,String token,boolean supabase) throws Exception{
  URL url=new URL(address);if(!url.getProtocol().equals("https"))throw new Failure(0,"Secure connection required.");HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setConnectTimeout(15000);c.setReadTimeout(25000);c.setInstanceFollowRedirects(false);c.setRequestMethod(method);c.setRequestProperty("Accept","application/json");c.setRequestProperty("Content-Type","application/json");
  if(token!=null&&!token.isEmpty())c.setRequestProperty("Authorization","Bearer "+token);
  if(supabase)c.setRequestProperty("apikey",config.getString("publishableKey"));
  try{if(body!=null){c.setDoOutput(true);try(OutputStream out=c.getOutputStream()){out.write(body.toString().getBytes("UTF-8"));}}
   int status=c.getResponseCode();String raw;try(InputStream in=status<400?c.getInputStream():c.getErrorStream()){raw=in==null?"{}":read(in);}JSONObject value;try{value=new JSONObject(raw.isEmpty()?"{}":raw);}catch(Exception bad){throw new Failure(status,"The store connection needs an update. Please try again later.");}
   if(status<200||status>=300)throw new Failure(status,value.optString("error",value.optString("msg",value.optString("message","Could not complete the request. Please try again."))));return value;
  }finally{c.disconnect();}
 }
 private synchronized void saveSession(JSONObject data) throws Exception{if(data.optString("access_token").isEmpty()||data.optString("refresh_token").isEmpty()||data.optJSONObject("user")==null)throw new Failure(401,"Sign-in did not return an account.");data.put("native_expires",System.currentTimeMillis()+data.optLong("expires_in",3600)*1000);vault.write("session",data);}
 public synchronized String access() throws Exception{
  JSONObject session=vault.read("session");if(session.optString("refresh_token").isEmpty())throw new Failure(401,"Sign in to keep your bag across devices.");
  if(session.optLong("native_expires")<System.currentTimeMillis()+60000)refresh();return vault.read("session").getString("access_token");
 }
 private synchronized void refresh() throws Exception{JSONObject session=vault.read("session");if(session.optString("refresh_token").isEmpty())throw new Failure(401,"Please sign in again.");JSONObject body=new JSONObject().put("refresh_token",session.getString("refresh_token"));try{saveSession(http(config.getString("supabase")+"/auth/v1/token?grant_type=refresh_token","POST",body,null,true));}catch(Failure failure){if(failure.status==400||failure.status==401){vault.remove("session");throw new Failure(401,"Please sign in again.");}throw failure;}}
 private synchronized void ensureNativeServer() throws Exception{if(nativeServerReady)return;try{JSONObject result=http(config.getString("api")+"mobile/bootstrap","GET",null,null,false);if(result.optInt("nativeVersion")<3)throw new Failure(503,"The store’s native app connection needs its server update.");nativeServerReady=true;}catch(Failure failure){throw new Failure(503,"The store’s native app connection needs its server update.");}}
 public JSONObject request(String path,String method,JSONObject body) throws Exception{ensureNativeServer();String token=access();try{return http(config.getString("api")+path,method,body,token,false);}catch(Failure failure){if(failure.status!=401)throw failure;refresh();return http(config.getString("api")+path,method,body,access(),false);}}
 public JSONObject prepareGoogle() throws Exception{
  ensureNativeServer();String client=config.optString("googleWebClientId");if(!client.matches("[A-Za-z0-9_-]+\\.apps\\.googleusercontent\\.com"))throw new Failure(503,"Google's web client ID has not been configured.");
  byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);String raw=Base64.encodeToString(bytes,Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);StringBuilder hashed=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(raw.getBytes("UTF-8")))hashed.append(String.format(java.util.Locale.US,"%02x",b&255));
  vault.write("google-native",new JSONObject().put("nonce",raw).put("started",System.currentTimeMillis()));return new JSONObject().put("client",client).put("nonce",hashed.toString());
 }
 public void finishGoogleToken(String token) throws Exception{
  JSONObject pending=vault.read("google-native");String nonce=pending.optString("nonce");vault.remove("google-native");
  if(nonce.isEmpty()||System.currentTimeMillis()-pending.optLong("started")>600000)throw new Failure(401,"Sign-in expired. Please try again.");
  saveSession(http(config.getString("supabase")+"/auth/v1/token?grant_type=id_token","POST",new JSONObject().put("provider","google").put("id_token",token).put("nonce",nonce),null,true));
 }
 public void cancelGoogle(){vault.remove("google-native");}
 public void clearSession(){vault.remove("session");vault.remove("google-native");}
 public void signOut(){try{http(config.optString("supabase")+"/auth/v1/logout?scope=local","POST",new JSONObject(),access(),true);}catch(Exception ignored){}clearSession();}
 public HttpURLConnection stream() throws Exception{ensureNativeServer();URL url=new URL(config.getString("api")+"cart/stream");HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setConnectTimeout(15000);c.setReadTimeout(55000);c.setInstanceFollowRedirects(false);c.setRequestProperty("Authorization","Bearer "+access());c.setRequestProperty("Accept","text/event-stream");if(c.getResponseCode()!=200){int code=c.getResponseCode();c.disconnect();throw new Failure(code,"Live bag connection is reconnecting.");}return c;}
 public void putFile(String destination,Uri uri,String type,long size) throws Exception{
  URL url=new URL(destination),trusted=new URL(config.getString("supabase"));if(!url.getProtocol().equals("https")||!url.getHost().equals(trusted.getHost())||url.getPort()!=trusted.getPort()||!url.getPath().startsWith("/storage/v1/object/upload/sign/"))throw new Failure(0,"Invalid upload destination.");
  String boundary="GT"+UUID.randomUUID().toString().replace("-","");byte[] prefix=("--"+boundary+"\r\nContent-Disposition: form-data; name=\"cacheControl\"\r\n\r\n3600\r\n--"+boundary+"\r\nContent-Disposition: form-data; name=\"\"; filename=\"design\"\r\nContent-Type: "+type+"\r\n\r\n").getBytes("UTF-8");byte[] suffix=("\r\n--"+boundary+"--\r\n").getBytes("UTF-8");
  HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setConnectTimeout(15000);c.setReadTimeout(45000);c.setInstanceFollowRedirects(false);c.setRequestMethod("PUT");c.setDoOutput(true);c.setRequestProperty("Content-Type","multipart/form-data; boundary="+boundary);c.setFixedLengthStreamingMode((long)prefix.length+size+suffix.length);
  try{try(InputStream in=context.getContentResolver().openInputStream(uri);OutputStream out=c.getOutputStream()){if(in==null)throw new IOException("File unavailable");out.write(prefix);byte[] bytes=new byte[8192];long count=0;int n;while((n=in.read(bytes))!=-1){count+=n;if(count>size)throw new IOException("The selected file changed. Select it again.");out.write(bytes,0,n);}if(count!=size)throw new IOException("The selected file changed.");out.write(suffix);}if(c.getResponseCode()<200||c.getResponseCode()>=300)throw new Failure(0,"Upload failed. Please try again.");}finally{c.disconnect();}
 }
}
