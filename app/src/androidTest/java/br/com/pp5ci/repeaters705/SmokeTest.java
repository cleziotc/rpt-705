package br.com.pp5ci.repeaters705;

import android.app.*;
import android.content.*;
import android.os.*;
import java.util.concurrent.*;
import org.json.*;

/** Exercises the actual Activity, asset origin, JS bridge, disk and Android Keystore. */
public class SmokeTest extends Instrumentation {
 MainActivity activity;
 @Override public void onCreate(Bundle args){super.onCreate(args);start();}
 String eval(String js)throws Exception{CountDownLatch latch=new CountDownLatch(1);String[] result={null};runOnMainSync(()->activity.web.evaluateJavascript(js,v->{result[0]=v;latch.countDown();}));if(!latch.await(10,TimeUnit.SECONDS))throw new AssertionError("JavaScript timeout");return result[0];}
 void check(boolean value,String label){if(!value)throw new AssertionError(label);}
 @Override public void onStart(){Bundle result=new Bundle();try{
  Intent i=new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);activity=(MainActivity)startActivitySync(i);waitForIdleSync();
  boolean loaded=false;for(int n=0;n<75;n++){if("589".equals(eval("typeof state!=='undefined'&&state?state.records.length:0"))){loaded=true;break;}Thread.sleep(200);}check(loaded,"Seed loads through secure WebView origin");
  check("41".equals(eval("C.search(state.records,{query:'',mode:'DV',sort:'name'}).length")),"DSTAR search");
  check("true".equals(eval("$('addButton').click();$('e2').value='Teste PP5CI';$('e4').value='PP5CI';$('e6').value='146.555000';$('e14').value='-26.3044';$('e15').value='-48.8487';$('editorForm').requestSubmit();state.records.length===590")),"Create and save through native validation");
  check("590".equals(eval("JSON.parse(Native.loadData()).records.length")),"Disk persistence");
  check("true".equals(eval("showEditor(state.records[state.records.length-1].id);$('e3').value='TEST';$('editorForm').requestSubmit();JSON.parse(Native.loadData()).records.at(-1).fields[3]==='TEST'")),"Edit persistence");
  check("true".equals(eval("confirmDelete(state.records[state.records.length-1].id);$('confirmDelete').click();state.records.length===589")),"Delete persistence");
  check("590".equals(eval("state=JSON.parse(Native.undo());state.records.length")),"Undo persisted deletion");
  check("589".equals(eval("let restored=JSON.parse(Native.loadData());restored.records=restored.records.filter(r=>r.fields[4]!=='PP5CI'||r.fields[6]!=='146.555000');Native.saveData(JSON.stringify(restored));state=restored;state.records.length")),"Clean up test data");
  check("true".equals(eval("!JSON.parse(Native.saveSettings(JSON.stringify({repo:'cleziotc/rpt-705',branch:'main',path:'data/rpt_updated.csv',token:'instrumentation-test-only',auto:false}))).error")),"Keystore encryption");
  android.content.SharedPreferences prefs=activity.getSharedPreferences("settings",0);String encrypted=prefs.getString("token","");check(!encrypted.contains("instrumentation-test-only")&&encrypted.contains(":"),"Token encrypted at rest");check("instrumentation-test-only".equals(activity.crypt(encrypted,false)),"Keystore decryption");
  eval("Native.saveSettings(JSON.stringify({repo:'cleziotc/rpt-705',branch:'main',path:'data/rpt_updated.csv',removeToken:true,auto:false}));$('modal').close();render();");
  result.putString("stream","PASS: Native WebView, seed, search, CRUD, disk persistence, undo, Keystore encryption and token removal.\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable e){result.putString("stream","FAIL: "+e+"\n");result.putString("shortMsg",e.toString());finish(Activity.RESULT_CANCELED,result);}}
}
