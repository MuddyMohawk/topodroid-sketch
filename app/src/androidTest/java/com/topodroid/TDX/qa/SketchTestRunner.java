package com.topodroid.TDX.qa;

import android.app.Activity;
import android.os.Bundle;
import androidx.test.runner.AndroidJUnitRunner;
import dalvik.system.DexFile;
import java.lang.reflect.Method;
import java.util.*;
import org.junit.Test;

/** Inventory comes from the installed test APK, never a source-file selector list. */
public final class SketchTestRunner extends AndroidJUnitRunner {
  private Bundle arguments;
  public static final String PHYSICAL_CLASS = "com.topodroid.TDX.PhysicalCompatInstrumentedTest";

  public static boolean permitsTarget(boolean emulator, String selector, boolean physical) {
    return emulator || (physical && (PHYSICAL_CLASS.equals(selector) ||
      (PHYSICAL_CLASS+"#sketchVanillaZipRoundTrip_onPhysicalTablet").equals(selector)));
  }

  @Override public void onCreate(Bundle args) {
    arguments = new Bundle(args);
    if("record".equals(args.getString("visual_baseline_mode")) && !"true".equals(args.getString("qa_authoring"))) {
      Bundle result=new Bundle();result.putString("shortMsg","Baseline recording requires the explicit refresh authoring path");
      finish(Activity.RESULT_CANCELED,result);return;
    }
    // Guard before AndroidJUnitRunner can execute class initializers, rules or @Before.
    boolean emulator = "1".equals(property("ro.kernel.qemu"));
    if (!permitsTarget(emulator, args.getString("class", ""),
        "true".equals(args.getString("qa_physical")))) {
      Bundle result = new Bundle();
      result.putString("shortMsg", "BLOCKED: ordinary tests may run only on an emulator; physical checks require the exact allowlisted class");
      finish(Activity.RESULT_CANCELED, result);
      return;
    }
    super.onCreate(args);
  }

  private String property(String key) {
    try {
      return (String)Class.forName("android.os.SystemProperties")
          .getMethod("get", String.class).invoke(null, key);
    } catch (Exception ex) { throw new IllegalStateException("Cannot establish device safety", ex); }
  }

  @Override public void onStart() {
    if (!"true".equals(arguments.getString("qa_discover"))) { super.onStart(); return; }
    Bundle result = new Bundle();
    int count = 0;
    DexFile dex = null;
    try {
      dex = new DexFile(getContext().getPackageCodePath());
      List<String> names = Collections.list(dex.entries());
      Collections.sort(names);
      for (String name : names) {
        if (!name.startsWith("com.topodroid.")) continue;
        Class<?> cls = Class.forName(name, false, getContext().getClassLoader());
        if(!java.lang.reflect.Modifier.isPublic(cls.getModifiers()) || java.lang.reflect.Modifier.isAbstract(cls.getModifiers()) || cls.isAnonymousClass() || cls.isLocalClass())continue;
        for (Method method : cls.getMethods()) {
          if (method.getAnnotation(Test.class) == null) continue;
          org.junit.runner.RunWith runWith=cls.getAnnotation(org.junit.runner.RunWith.class);
          if(runWith==null || runWith.value()!=com.topodroid.TDX.QaJUnit4.class)throw new IllegalStateException("Test must use the shared timed AndroidJUnit4 runner: "+name);
          boolean fixture=false;
          for(java.lang.reflect.Field field:cls.getFields())if(field.getAnnotation(org.junit.Rule.class)!=null && field.getType()==com.topodroid.TDX.QaFixtureRule.class)fixture=true;
          if(!fixture)throw new IllegalStateException("Test missing shared safety fixture: " + name);
          TestTier tier = method.getAnnotation(TestTier.class);
          if (tier == null) tier = cls.getAnnotation(TestTier.class);
          if (tier == null) throw new IllegalStateException("Unclassified test: " + name + "#" + method.getName());
          com.topodroid.qa.TestFeatures features = method.getAnnotation(com.topodroid.qa.TestFeatures.class);
          if (features == null) features = cls.getAnnotation(com.topodroid.qa.TestFeatures.class);
          if (features == null || features.value().length == 0)
            throw new IllegalStateException("Test missing feature classification: " + name + "#" + method.getName());
          Set<String> featureNames = new TreeSet<>();
          for (com.topodroid.qa.TestFeatures.Feature feature : features.value())
            if (!featureNames.add(feature.name().toLowerCase(Locale.ROOT)))
              throw new IllegalStateException("Duplicate feature: " + name + "#" + method.getName());
          Bundle item = new Bundle();
          item.putString("stream", "QA_TEST|" + name + "#" + method.getName() + "|" + tier.value() + "|" + tier.baseline() + "|" + String.join(",", featureNames) + "\n");
          sendStatus(0, item);
          count++;
        }
      }
      if (count == 0) throw new IllegalStateException("APK discovery found zero tests");
      result.putString("stream", "QA_DISCOVERED|" + count + "\n");
      finish(Activity.RESULT_OK, result);
    } catch (Throwable ex) {
      result.putString("shortMsg", ex.toString());
      finish(Activity.RESULT_CANCELED, result);
    } finally {
      if (dex != null) try { dex.close(); } catch (java.io.IOException ignored) { }
    }
  }
}
