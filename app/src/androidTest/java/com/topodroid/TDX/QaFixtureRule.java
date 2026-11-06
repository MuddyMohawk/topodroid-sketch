package com.topodroid.TDX;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import com.topodroid.TDX.qa.SketchTestRunner;
import com.topodroid.TDX.qa.TestTier;
import java.io.File;
import java.io.IOException;
import java.util.*;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/** Owns one fresh working directory per method; failures retain their entire case. */
public final class QaFixtureRule implements TestRule {
  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
  @java.lang.annotation.Target(java.lang.annotation.ElementType.TYPE)
  public @interface Database {}
  private static File root;
  private static final Set<String> owned = new HashSet<>();
  private static final Map<String,Long> surveys = new HashMap<>();
  private static final List<Throwable> cleanupErrors = new ArrayList<>();
  private static final String run = Long.toString(System.currentTimeMillis(), 36);
  private static boolean symbolsReady;
  private static final java.util.concurrent.atomic.AtomicLong hookFixture=new java.util.concurrent.atomic.AtomicLong(),hookCleanup=new java.util.concurrent.atomic.AtomicLong();
  static void recordHook(boolean before,long milliseconds){(before?hookFixture:hookCleanup).addAndGet(milliseconds);}
  public static void ensureSymbols() {
    if (symbolsReady) return;
    Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    TopoDroidApp.installSymbols(true);
    BrushManager.reloadPointLibrary(context,context.getResources());
    BrushManager.reloadLineLibrary(context.getResources());
    BrushManager.reloadAreaLibrary(context.getResources());
    symbolsReady=true;
  }
  public static File root() {
    if (root == null) throw new IllegalStateException("No active QA fixture");
    return root;
  }
  public static boolean owns(File file) {
    try {
      String path = file.getCanonicalPath();
      return root != null && (path.equals(root.getCanonicalPath()) || path.startsWith(root.getCanonicalPath() + File.separator));
    } catch (Exception ex) { return false; }
  }
  public static void record(File file) {
    try {
      if(file.exists())throw new SecurityException("Cannot claim an existing file: " + file);
      File downloads=Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
      if(!owns(file) && !(file.getCanonicalFile().getParentFile().equals(downloads.getCanonicalFile()) && file.getName().startsWith("qa-")))
        throw new SecurityException("Cannot claim path outside fixture or unique QA download: " + file);
      owned.add(file.getCanonicalPath());
    }
    catch (Exception ex) { throw new IllegalStateException(ex); }
  }
  public static boolean recorded(File file) {
    try { return owned.contains(file.getCanonicalPath()); }
    catch (Exception ex) { return false; }
  }
  public static void cleanupError(Throwable error) { cleanupErrors.add(error); }
  static void recordSurvey(String name,long id) {surveys.put(name,id);}
  static void requireSurvey(String name,long id) {
    if(!Objects.equals(surveys.get(name),id))throw new SecurityException("Refusing unrecorded survey deletion: " + name + " id=" + id);
  }
  public static void closeActivities() {
    List<Activity> children=new ArrayList<>();
    InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
      TopoDroidApp app=(TopoDroidApp)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
      try {
        java.lang.reflect.Field field=TopoDroidApp.class.getDeclaredField("mCurrentDialog");field.setAccessible(true);
        android.app.Dialog dialog=(android.app.Dialog)field.get(app);if(dialog!=null && dialog.isShowing())dialog.dismiss();
      } catch(ReflectiveOperationException ex) {throw new IllegalStateException("Cannot close owning dialog",ex);}
      // Finish children first. Scenario.close() can otherwise deadlock on a paused owner.
      LinkedHashSet<Activity> activities = new LinkedHashSet<>();
      for (Stage stage : new Stage[]{Stage.RESUMED, Stage.PAUSED, Stage.STARTED, Stage.STOPPED, Stage.CREATED})
        activities.addAll(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage));
      for (Activity activity : activities) if (!(activity instanceof MainWindow)) {children.add(activity);activity.finish();}
    });
    long deadline=SystemClock.uptimeMillis()+10000;
    while(SystemClock.uptimeMillis()<deadline) {
      final boolean[] destroyed={true};
      InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {for(Activity child:children)if(!child.isDestroyed())destroyed[0]=false;});
      if(destroyed[0]) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
          ((TopoDroidApp)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext()).clearSurveyReferences();
          TopoDroidApp.mDrawingWindow=null;
        });
        return;
      }
      SystemClock.sleep(50);
    }
    throw new AssertionError("Child activities did not close within 10 seconds: "+children);
  }
  private static void event(String type, long started) {
    emit(type,SystemClock.elapsedRealtime()-started);
  }
  private static void emit(String type,long milliseconds) {
    Bundle result = new Bundle();
    result.putString("stream", "QA_TIMING|" + type + "|" + milliseconds + "\n");
    InstrumentationRegistry.getInstrumentation().sendStatus(0, result);
  }
  @Override public Statement apply(Statement body, Description description) {
    return new Statement() { @Override public void evaluate() throws Throwable {
      // The runner provides the first guard. This also protects IDE/default-runner execution.
      boolean emulator = false;
      try { emulator = "1".equals(Class.forName("android.os.SystemProperties").getMethod("get", String.class).invoke(null,"ro.kernel.qemu")); }
      catch (Exception ex) { throw new IllegalStateException("Cannot establish fixture target safety", ex); }
      boolean safePhysical = SketchTestRunner.PHYSICAL_CLASS.equals(description.getClassName()) &&
          "true".equals(InstrumentationRegistry.getArguments().getString("qa_physical"));
      if (!emulator && !safePhysical) throw new AssertionError("Unsafe physical target refused before fixture setup");
      Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
      // UiAutomator otherwise inserts its own ten-second idle wait before every query.
      // Conditions below have explicit deadlines; animated drawing surfaces need not go idle.
      androidx.test.uiautomator.Configurator.getInstance().setWaitForIdleTimeout(0).setWaitForSelectorTimeout(0);
      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
      Map<String, ?> saved = new HashMap<>(prefs.getAll());
      Context previousContext=TDInstance.context;
      float previousLengthUnit=com.topodroid.prefs.TDSetting.mUnitLength,previousAngleUnit=com.topodroid.prefs.TDSetting.mUnitAngle;
      String previousLengthLabel=com.topodroid.prefs.TDSetting.mUnitLengthStr,previousAngleLabel=com.topodroid.prefs.TDSetting.mUnitAngleStr;
      float previousManualLength=ManualCalibration.mLength,previousManualAzimuth=ManualCalibration.mAzimuth,previousManualClino=ManualCalibration.mClino;
      boolean previousManualLrud=ManualCalibration.mLRUD;
      boolean previousStylus=StylusOnlyInputController.isEnabled(),previousPinch=StylusOnlyInputController.isPinchAllowed();
      if(TopoDroidApp.mDData==null)TopoDroidApp.mDData=new DeviceHelper(context);
      String previousExit=TopoDroidApp.mDData.getValue("say_dialogR");
      long started = SystemClock.elapsedRealtime();
      Throwable failure = null;
      DataHelper initialData=null;
      boolean bodyStarted=false;
      hookFixture.set(0);hookCleanup.set(0);
      cleanupErrors.clear(); owned.clear(); surveys.clear();
      String cwd = "TopoDroid Sketch QA/" + run + "/" + description.getClassName().substring(description.getClassName().lastIndexOf('.')+1) + "-" + description.getMethodName() + "-" + System.nanoTime();
      root = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), cwd);
      try {
        if(safePhysical)QaPhysicalCwdGuard.begin(context,prefs,cwd);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
          ((TopoDroidApp)context.getApplicationContext()).clearSurveyReferences();
          TopoDroidApp.mDrawingWindow=null;TopoDroidApp.mMainActivity=null;
        });
        if (TopoDroidApp.mData != null) { TopoDroidApp.mData.closeDatabase(); TopoDroidApp.mData = null; }
        prefs.edit().putString("DISTOX_CWD", cwd).commit();
        TDInstance.cwd = cwd;
        TDPath.setTdPaths(cwd);
        TDInstance.setContext(context.getApplicationContext());
        com.topodroid.prefs.TDSetting.loadSecondaryPreferences(new com.topodroid.prefs.TDPrefHelper(context));
        DrawingSurface.clearManagersCache();
        ManualCalibration.reset();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
          ActionKeyBindingManager.cancelVolumeDownGesture();
          StylusOnlyInputController.setEnabled(false);
          StylusOnlyInputController.setAllowPinch(false);
        });
        if (description.getTestClass().isAnnotationPresent(Database.class)) {initialData=new DataHelper(context);TopoDroidApp.mData=initialData;}
        TDInstance.sid = -1; TDInstance.survey = null;
        if (TopoDroidApp.mDData == null) TopoDroidApp.mDData = new DeviceHelper(context);
        TopoDroidApp.mDData.setValue("say_dialogR", "NO");
        if (!symbolsReady) {
          // Never replace a tablet's edited symbols. Emulator cases explicitly install the package.
          if (emulator) TopoDroidApp.installSymbols(true);
          BrushManager.reloadPointLibrary(context, context.getResources());
          BrushManager.reloadLineLibrary(context.getResources());
          BrushManager.reloadAreaLibrary(context.getResources());
          symbolsReady = true;
        }
        event("fixture", started);
        started = SystemClock.elapsedRealtime();
        bodyStarted=true;
        body.evaluate();
      } catch (Throwable ex) { failure = ex; }
      finally {
        long bodyTime=SystemClock.elapsedRealtime()-started;
        if(bodyStarted) {
          emit("fixture",hookFixture.get());emit("cleanup",hookCleanup.get());
          emit("test",Math.max(0,bodyTime-hookFixture.get()-hookCleanup.get()));
        } else {emit("fixture",bodyTime);emit("test",0);}
        started = SystemClock.elapsedRealtime();
        try {
          closeActivities();
          InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (Activity activity : new ArrayList<>(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))) activity.finish();
          });
          InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {});
          InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            ((TopoDroidApp)context.getApplicationContext()).clearSurveyReferences();
            TopoDroidApp.mDrawingWindow=null;TopoDroidApp.mMainActivity=null;
          });
          DataHelper currentData=TopoDroidApp.mData;
          if (currentData != null) {currentData.closeDatabase();TopoDroidApp.mData=null;}
          if(initialData!=null && initialData!=currentData)initialData.closeDatabase();
          TDInstance.sid = -1; TDInstance.survey = null;
          SharedPreferences.Editor edit = prefs.edit().clear();
          for (Map.Entry<String, ?> entry : saved.entrySet()) {
            Object v = entry.getValue(); String k = entry.getKey();
            if (v instanceof String) edit.putString(k,(String)v);
            else if (v instanceof Boolean) edit.putBoolean(k,(Boolean)v);
            else if (v instanceof Integer) edit.putInt(k,(Integer)v);
            else if (v instanceof Long) edit.putLong(k,(Long)v);
            else if (v instanceof Float) edit.putFloat(k,(Float)v);
            else if (v instanceof Set) edit.putStringSet(k,new HashSet<>((Set<String>)v));
          }
          if(!edit.commit())throw new IOException("Fixture preference restoration did not commit");
          TDInstance.cwd = prefs.getString("DISTOX_CWD", "TopoDroid Sketch");
          TDPath.setTdPaths(TDInstance.cwd);
          com.topodroid.prefs.TDSetting.loadSecondaryPreferences(new com.topodroid.prefs.TDPrefHelper(context));
          InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            ActionKeyBindingManager.cancelVolumeDownGesture();
            StylusOnlyInputController.setEnabled(previousStylus);
            StylusOnlyInputController.setAllowPinch(previousPinch);
          });
          // Controller setters may write preferences; restore the exact saved values again.
          for(String key:new String[]{StylusOnlyInputController.PREFERENCE,StylusOnlyInputController.PINCH_PREFERENCE}) {
            if(saved.containsKey(key))prefs.edit().putBoolean(key,(Boolean)saved.get(key)).commit();
            else prefs.edit().remove(key).commit();
          }
          TDInstance.context=previousContext;
          com.topodroid.prefs.TDSetting.mUnitLength=previousLengthUnit;com.topodroid.prefs.TDSetting.mUnitAngle=previousAngleUnit;
          com.topodroid.prefs.TDSetting.mUnitLengthStr=previousLengthLabel;com.topodroid.prefs.TDSetting.mUnitAngleStr=previousAngleLabel;
          ManualCalibration.mLength=previousManualLength;ManualCalibration.mAzimuth=previousManualAzimuth;ManualCalibration.mClino=previousManualClino;ManualCalibration.mLRUD=previousManualLrud;
          if(previousExit!=null)TopoDroidApp.mDData.setValue("say_dialogR",previousExit);
          else TopoDroidApp.mDData.getDb().delete("configs","key=?",new String[]{"say_dialogR"});
          if(safePhysical)QaPhysicalCwdGuard.finish(context,prefs);
        } catch (Throwable ex) { cleanupErrors.add(ex); }
        if (failure == null && cleanupErrors.isEmpty()) {
          try {
            for(String path:owned)if(!owns(new File(path))) {
              File file=new File(path);if(file.exists() && !file.delete())throw new IOException("Cannot remove recorded download " + file);
            }
            deleteOwned(root);
          } catch (Throwable ex) { cleanupErrors.add(ex); }
        }
        for (Throwable ex : cleanupErrors) {
          if (failure == null) failure = ex; else failure.addSuppressed(ex);
        }
        event("cleanup", started);
        root = null;
      }
      if (failure != null) throw failure;
    }};
  }
  static void deleteOwned(File file) throws Exception {
    if (!owns(file)) throw new SecurityException("Refusing unowned path " + file);
    if (!file.exists()) return;
    if (file.isDirectory()) {
      File[] children = file.listFiles();
      if (children == null) throw new java.io.IOException("Cannot enumerate " + file);
      for (File child : children) deleteOwned(child);
    }
    if (!file.delete()) throw new java.io.IOException("Cannot remove owned fixture " + file);
  }
}
