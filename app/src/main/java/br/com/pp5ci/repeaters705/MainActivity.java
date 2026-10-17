package br.com.pp5ci.repeaters705;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.location.*;
import android.security.keystore.*;
import android.util.*;
import android.webkit.*;
import android.view.*;
import org.json.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.net.*;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    WebView web; final ExecutorService worker=Executors.newSingleThreadExecutor();
    final Handler handler=new Handler(Looper.getMainLooper()); AtomicFile database;
    LocationManager locationManager; LocationListener locationListener; Runnable locationTimeout;
    static final int IMPORT=11,EXPORT=12,GPS=13;
    @Override public void onCreate(Bundle state){super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(11,16,26));getWindow().setNavigationBarColor(Color.rgb(11,16,26));
        // Keep controls inside system bars, including Android 15 edge-to-edge enforcement.
        android.widget.FrameLayout frame=new android.widget.FrameLayout(this);frame.setBackgroundColor(Color.rgb(11,16,26));
        web=new WebView(this);frame.addView(web);setContentView(frame);
        frame.setOnApplyWindowInsetsListener((v,insets)->{frame.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();});
        database=new AtomicFile(new File(getFilesDir(),"repeaters.json"));
        web.setBackgroundColor(Color.rgb(11,16,26));web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);
        web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.getSettings().setSupportMultipleWindows(false);web.getSettings().setDomStorageEnabled(false);
        web.addJavascriptInterface(new Bridge(),"Native");
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest r){return true;}
            @Override public WebResourceResponse shouldInterceptRequest(WebView w,WebResourceRequest r){
                Uri u=r.getUrl();if("https".equals(u.getScheme())&&"appassets.androidplatform.net".equals(u.getHost())&&u.getPath().startsWith("/assets/")){
                    try{String path=u.getPath().substring(8);if(path.contains(".."))throw new IOException();String type=path.endsWith(".js")?"application/javascript":path.endsWith(".css")?"text/css":"text/html";return new WebResourceResponse(type,"UTF-8",getAssets().open(path));}catch(Exception e){return new WebResourceResponse("text/plain","UTF-8",404,"Not found",null,new ByteArrayInputStream(new byte[0]));}
                }return new WebResourceResponse("text/plain","UTF-8",403,"Blocked",null,new ByteArrayInputStream(new byte[0]));
            }
        });
        web.setWebChromeClient(new WebChromeClient());
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html");
    }
    JSONObject error(Exception e){JSONObject j=new JSONObject();try{j.put("error",e.getMessage()==null?"Não foi possível concluir a operação.":e.getMessage());}catch(Exception ignored){}return j;}
    void event(String name,JSONObject payload){runOnUiThread(()->{if(web!=null)web.evaluateJavascript("window.nativeEvent("+JSONObject.quote(name)+","+payload+")",null);});}
    static String readUtf8(InputStream in)throws Exception {byte[] b=readBytes(in);try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b)).toString();}catch(CharacterCodingException e){return Charset.forName("windows-1252").decode(ByteBuffer.wrap(b)).toString();}}
    static byte[] readBytes(InputStream in)throws Exception {try(InputStream src=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=src.read(b))!=-1){if(out.size()+n>5_000_000)throw new IOException("Arquivo excede 5 MB.");out.write(b,0,n);}return out.toByteArray();}}
    synchronized JSONObject load()throws Exception {
        if(database.getBaseFile().exists())return new JSONObject(readUtf8(database.openRead()));
        List<String[]> seed=Csv.parse(readUtf8(getAssets().open("rpt_updated.csv")));JSONArray records=new JSONArray();
        for(String[] row:seed)records.put(record(row));JSONObject data=new JSONObject().put("version",1).put("records",records).put("tombstones",new JSONArray()).put("lastSync","");write(data);return data;
    }
    JSONObject record(String[] row)throws Exception{return new JSONObject().put("id",UUID.randomUUID().toString()).put("fields",new JSONArray(Arrays.asList(row))).put("sourceKey",Csv.key(row)).put("base",new JSONArray(Arrays.asList(row))).put("dirty",false).put("favorite",false);}
    synchronized void write(JSONObject data)throws Exception{FileOutputStream out=null;try{out=database.startWrite();out.write(data.toString().getBytes(StandardCharsets.UTF_8));database.finishWrite(out);}catch(Exception e){if(out!=null)database.failWrite(out);throw e;}}
    List<String[]> rows(JSONArray list,boolean records)throws Exception {List<String[]> result=new ArrayList<>();for(int i=0;i<list.length();i++){JSONArray a=records?list.getJSONObject(i).getJSONArray("fields"):list.getJSONArray(i);if(a.length()!=17)throw new IOException("Registro precisa de 17 campos.");String[] r=new String[17];for(int j=0;j<17;j++)r[j]=a.getString(j);result.add(r);}Csv.validateDatabase(result);return result;}
    public class Bridge {
        @JavascriptInterface public String loadData(){try{return load().toString();}catch(Exception e){return error(e).toString();}}
        @JavascriptInterface public String saveData(String json){try{JSONObject data=new JSONObject(json);rows(data.getJSONArray("records"),true);String old=load().toString();try(FileOutputStream out=new FileOutputStream(new File(getFilesDir(),"undo.json"))){out.write(old.getBytes(StandardCharsets.UTF_8));}write(data);return "{\"ok\":true}";}catch(Exception e){return error(e).toString();}}
        @JavascriptInterface public String undo(){try{File f=new File(getFilesDir(),"undo.json");if(!f.exists())throw new IOException("Não há alteração para desfazer.");JSONObject old=new JSONObject(readUtf8(new FileInputStream(f)));rows(old.getJSONArray("records"),true);JSONObject current=load();write(old);try(FileOutputStream out=new FileOutputStream(f)){out.write(current.toString().getBytes(StandardCharsets.UTF_8));}return old.toString();}catch(Exception e){return error(e).toString();}}
        @JavascriptInterface public String validateRow(String json){try{JSONArray a=new JSONArray(json);String[] r=new String[17];if(a.length()!=17)throw new IOException("São necessários 17 campos.");for(int j=0;j<17;j++)r[j]=a.getString(j);Csv.validate(r);return new JSONObject().put("ok",true).put("key",Csv.key(r)).toString();}catch(Exception e){return error(e).toString();}}
        @JavascriptInterface public void importCsv(){runOnUiThread(()->{try{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,IMPORT);}catch(Exception e){event("error",error(e));}});}
        @JavascriptInterface public void exportCsv(String json,String separator){worker.execute(()->{try{List<String[]> data=rows(new JSONArray(json),false);if(data.isEmpty())throw new IOException("Não há repetidoras selecionadas.");String csv=Csv.write(data,",".equals(separator)?',':';');File pending=new File(getCacheDir(),"export.csv");try(FileOutputStream out=new FileOutputStream(pending)){out.write(csv.getBytes(StandardCharsets.UTF_8));}runOnUiThread(()->{try{Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/csv").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"rpt_705_"+new java.text.SimpleDateFormat("yyyyMMdd",Locale.US).format(new Date())+".csv");startActivityForResult(i,EXPORT);}catch(Exception e){event("error",error(e));}});}catch(Exception e){event("error",error(e));}});}
        @JavascriptInterface public String getSettings(){try{android.content.SharedPreferences p=getSharedPreferences("settings",0);return new JSONObject().put("repo",p.getString("repo","cleziotc/rpt-705")).put("branch",p.getString("branch","main")).put("path",p.getString("path","data/rpt_updated.csv")).put("auto",p.getBoolean("auto",false)).put("tokenPresent",p.contains("token")).put("lastCheck",p.getLong("lastCheck",0)).toString();}catch(Exception e){return error(e).toString();}}
        @JavascriptInterface public String saveSettings(String json){try{JSONObject j=new JSONObject(json);String repo=j.getString("repo").trim(),branch=j.getString("branch").trim(),path=j.getString("path").trim();if(!repo.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")||!branch.matches("[A-Za-z0-9_./-]+")||!path.matches("[A-Za-z0-9_./-]+\\.csv")||path.contains(".."))throw new IOException("Repositório, branch ou caminho inválido.");android.content.SharedPreferences.Editor e=getSharedPreferences("settings",0).edit().putString("repo",repo).putString("branch",branch).putString("path",path).putBoolean("auto",j.getBoolean("auto"));if(j.optBoolean("removeToken"))e.remove("token");else if(!j.optString("token").trim().isEmpty())e.putString("token",crypt(j.getString("token").trim(),true));if(!e.commit())throw new IOException("Não foi possível salvar as configurações.");return "{\"ok\":true}";}catch(Exception e){return error(e).toString();}}
        @JavascriptInterface public void sync(){worker.execute(()->{HttpURLConnection conn=null;try{android.content.SharedPreferences p=getSharedPreferences("settings",0);String repo=p.getString("repo","cleziotc/rpt-705"),branch=p.getString("branch","main"),path=p.getString("path","data/rpt_updated.csv");p.edit().putLong("lastCheck",System.currentTimeMillis()).apply();URL url=new URL("https://api.github.com/repos/"+repo+"/contents/"+path+"?ref="+java.net.URLEncoder.encode(branch,"UTF-8"));conn=(HttpURLConnection)url.openConnection();conn.setConnectTimeout(15000);conn.setReadTimeout(20000);conn.setInstanceFollowRedirects(false);conn.setRequestProperty("Accept","application/vnd.github.raw+json");conn.setRequestProperty("X-GitHub-Api-Version","2022-11-28");conn.setRequestProperty("User-Agent","Repeaters-list-705/1.0.0");if(p.contains("token"))conn.setRequestProperty("Authorization","Bearer "+crypt(p.getString("token",""),false));int code=conn.getResponseCode();if(code!=200)throw new IOException(code==404?"Arquivo não encontrado ou repositório privado. Confira o acesso nas configurações.":code==401||code==403?"Acesso ao GitHub recusado. Confira o token e a permissão Contents: read (ou o limite de consultas).":"GitHub respondeu HTTP "+code+". Confira repositório, branch e caminho.");String text=readUtf8(conn.getInputStream());List<String[]> imported=Csv.parse(text);JSONArray array=new JSONArray();for(String[] r:imported)array.put(new JSONArray(Arrays.asList(r)));event("importReady",new JSONObject().put("rows",array).put("source","github").put("name",repo+" / "+path));}catch(Exception e){event("error",error(e));}finally{if(conn!=null)conn.disconnect();}});}
        @JavascriptInterface public void locate(){runOnUiThread(()->{if(checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},GPS);else startLocation();});}
        @JavascriptInterface public void openMap(String lat,String lon){runOnUiThread(()->{try{double a=Double.parseDouble(lat),b=Double.parseDouble(lon);if(!Double.isFinite(a)||!Double.isFinite(b)||Math.abs(a)>90||Math.abs(b)>180)throw new IOException("Coordenadas inválidas.");Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse("geo:"+a+","+b+"?q="+a+","+b));startActivity(i);}catch(Exception e){event("error",error(e));}});}
    }
    String crypt(String value,boolean encrypt)throws Exception{KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);String alias="rpt705.github";if(!ks.containsAlias(alias)){KeyGenerator k=KeyGenerator.getInstance("AES","AndroidKeyStore");k.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());k.generateKey();}javax.crypto.SecretKey key=(javax.crypto.SecretKey)ks.getKey(alias,null);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");if(encrypt){c.init(Cipher.ENCRYPT_MODE,key);return android.util.Base64.encodeToString(c.getIV(),android.util.Base64.NO_WRAP)+":"+android.util.Base64.encodeToString(c.doFinal(value.getBytes(StandardCharsets.UTF_8)),android.util.Base64.NO_WRAP);}String[] parts=value.split(":");c.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,android.util.Base64.decode(parts[0],android.util.Base64.NO_WRAP)));return new String(c.doFinal(android.util.Base64.decode(parts[1],android.util.Base64.NO_WRAP)),StandardCharsets.UTF_8);}
    @Override protected void onActivityResult(int request,int result,Intent intent){super.onActivityResult(request,result,intent);if(result!=RESULT_OK||intent==null||intent.getData()==null){if(request==IMPORT||request==EXPORT)event("cancelled",new JSONObject());return;}Uri uri=intent.getData();worker.execute(()->{try{if(request==IMPORT){List<String[]> imported=Csv.parse(readUtf8(getContentResolver().openInputStream(uri)));JSONArray arr=new JSONArray();for(String[] r:imported)arr.put(new JSONArray(Arrays.asList(r)));event("importReady",new JSONObject().put("rows",arr).put("source","file").put("name","CSV selecionado"));}else if(request==EXPORT){byte[] bytes=readBytes(new FileInputStream(new File(getCacheDir(),"export.csv")));try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("Destino indisponível.");out.write(bytes);}event("exported",new JSONObject().put("ok",true));}}catch(Exception e){event("error",error(e));}});}
    void startLocation(){try{stopLocation();locationManager=(LocationManager)getSystemService(LOCATION_SERVICE);locationListener=new LocationListener(){@Override public void onLocationChanged(Location l){stopLocation();try{event("location",new JSONObject().put("lat",l.getLatitude()).put("lon",l.getLongitude()).put("accuracy",l.getAccuracy()));}catch(Exception e){event("error",error(e));}}@Override public void onStatusChanged(String p,int s,Bundle b){}@Override public void onProviderEnabled(String p){}@Override public void onProviderDisabled(String p){}};boolean enabled=false;for(String provider:new String[]{LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER})if(locationManager.isProviderEnabled(provider)&&(!provider.equals(LocationManager.GPS_PROVIDER)||checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)){locationManager.requestLocationUpdates(provider,1000,0,locationListener);enabled=true;}if(!enabled)throw new IOException("Ative a localização do celular ou informe coordenadas manualmente.");locationTimeout=()->{stopLocation();event("error",error(new IOException("Tempo de localização esgotado. Tente ao ar livre ou informe coordenadas.")));};handler.postDelayed(locationTimeout,25000);}catch(Exception e){stopLocation();event("error",error(e));}}
    void stopLocation(){if(locationTimeout!=null)handler.removeCallbacks(locationTimeout);if(locationManager!=null&&locationListener!=null)locationManager.removeUpdates(locationListener);locationListener=null;}
    @Override public void onRequestPermissionsResult(int req,String[] p,int[] grants){super.onRequestPermissionsResult(req,p,grants);if(req==GPS){if(checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED)startLocation();else event("error",error(new IOException("Sem permissão de localização. Use coordenadas manuais no filtro.")));}}
    @Override public void onBackPressed(){web.evaluateJavascript("window.handleBack()",result->{if("false".equals(result))super.onBackPressed();});}
    @Override protected void onDestroy(){stopLocation();worker.shutdown();if(web!=null){web.removeJavascriptInterface("Native");web.destroy();web=null;}super.onDestroy();}
}
