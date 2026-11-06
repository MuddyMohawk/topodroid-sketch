package com.topodroid.TDX;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import org.json.JSONObject;

/** Test-only, durable ownership record; the host can recover after this process is killed. */
final class QaPhysicalCwdGuard {
  private static final String KEY = "DISTOX_CWD";
  private static File marker(Context context) { return new File(context.getFilesDir(), "qa-physical-cwd.json"); }
  private static JSONObject read(File file) throws Exception {
    return new JSONObject(new String(new AtomicFile(file).readFully(), java.nio.charset.StandardCharsets.UTF_8));
  }
  private static boolean originalMatches(JSONObject record, SharedPreferences prefs) throws Exception {
    JSONObject original=record.getJSONObject("original");
    return original.getBoolean("present")==prefs.contains(KEY) &&
      (!prefs.contains(KEY) || original.getString("value").equals(prefs.getString(KEY,null)));
  }
  static void begin(Context context, SharedPreferences prefs, String qaCwd) throws Exception {
    File file=marker(context);
    JSONObject record;
    if(file.exists()) {
      record=read(file);
      if(record.getInt("version")!=1 || !context.getPackageName().equals(record.getString("package")) ||
          !record.isNull("qaCwd") || !originalMatches(record,prefs))
        throw new IllegalStateException("Pending physical working-directory recovery; refusing fixture setup");
    } else {
      if(prefs.getString(KEY,"").startsWith("TopoDroid Sketch QA/"))
        throw new IllegalStateException("QA directory active without its original recovery record; refusing to guess");
      JSONObject original=new JSONObject().put("present",prefs.contains(KEY))
        .put("value",prefs.contains(KEY)?prefs.getString(KEY,null):JSONObject.NULL);
      record=new JSONObject().put("version",1).put("package",context.getPackageName())
        .put("original",original).put("pending",true);
    }
    record.put("qaCwd",qaCwd);
    AtomicFile atomic=new AtomicFile(file);
    FileOutputStream stream=atomic.startWrite();
    try {
      stream.write(record.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
      stream.getFD().sync();atomic.finishWrite(stream);
    } catch(Exception error) {atomic.failWrite(stream);throw error;}
  }
  static void finish(Context context, SharedPreferences prefs) throws Exception {
    File file=marker(context);
    if(file.exists() && originalMatches(read(file),prefs))new AtomicFile(file).delete();
  }
}
