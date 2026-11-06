package com.topodroid.TDX;

import com.topodroid.TDX.qa.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import static org.junit.Assert.*;

@RunWith(com.topodroid.TDX.QaJUnit4.class)
@TestTier(TestTier.Tier.FAST)
@QaFixtureRule.Database
@com.topodroid.qa.TestFeatures({com.topodroid.qa.TestFeatures.Feature.RUNNER})
public class QaSafetyInstrumentedTest {
  @Rule public final QaFixtureRule fixture = new QaFixtureRule();
  @Test public void canonicalOwnership_rejectsTraversalAndPreservesUnrelatedFiles() throws Exception {
    File root=QaFixtureRule.root();
    File child=new File(root,"owned/sub/file");assertTrue(child.getParentFile().mkdirs());
    try(FileOutputStream out=new FileOutputStream(child)){out.write(42);}
    File sentinel=new File(root.getParentFile(),"sentinel-"+System.nanoTime());
    try(FileOutputStream out=new FileOutputStream(sentinel)){out.write(7);}
    try {
      assertTrue(QaFixtureRule.owns(child));
      assertFalse(QaFixtureRule.owns(new File(root,"../"+sentinel.getName())));
      boolean refused=false;
      try{QaFixtureRule.deleteOwned(sentinel);}catch(SecurityException expected){refused=true;}
      assertTrue("Unowned deletion must be refused",refused);
      QaFixtureRule.deleteOwned(new File(root,"owned"));
      assertFalse(child.exists());assertTrue(sentinel.isFile());
      try(FileInputStream in=new FileInputStream(sentinel)){assertEquals(7,in.read());}
    }finally{assertTrue(sentinel.delete());}
  }
  @Test public void exactSurveyDeletion_preservesUnrelatedSentinelSurvey() throws Exception {
    DataHelper data=TopoDroidApp.mData;
    long sentinel=data.setSurvey("unrelated_sentinel",0);
    long owned=data.setSurvey("owned_fixture",0);
    assertTrue(sentinel>=0 && owned>=0 && sentinel!=owned);
    QaFixtureRule.recordSurvey("owned_fixture",owned);
    File sentinelFile=new File(QaFixtureRule.root(),"unrelated_sentinel/out/sentinel.txt");
    assertTrue(sentinelFile.getParentFile().mkdirs());
    try(FileOutputStream output=new FileOutputStream(sentinelFile)){output.write(23);}
    VisualTestSupport helper=new VisualTestSupport("ownership-contract");
    helper.deleteGeneratedSurveyAndArtifacts("owned_fixture");
    assertEquals(sentinel,data.getSurveyId("unrelated_sentinel"));
    assertFalse(data.hasSurveyName("owned_fixture"));
    boolean refused=false;
    try{helper.deleteGeneratedSurveyAndArtifacts("unrelated_sentinel");}catch(SecurityException expected){refused=true;}
    assertTrue("Shared helper must refuse an unrecorded survey",refused);
    assertEquals(sentinel,data.getSurveyId("unrelated_sentinel"));
    assertTrue("Unrelated survey files must survive",sentinelFile.isFile());
  }
  @Test public void physicalGuard_acceptsOnlyExplicitAllowlistedCompatibilityCheck() {
    String safe=SketchTestRunner.PHYSICAL_CLASS;
    assertFalse(SketchTestRunner.permitsTarget(false,"",false));
    assertFalse(SketchTestRunner.permitsTarget(false,safe,false));
    assertFalse(SketchTestRunner.permitsTarget(false,safe+",com.topodroid.TDX.SpecialPointInstrumentedTest",true));
    assertFalse(SketchTestRunner.permitsTarget(false,safe+"#notAllowlisted",true));
    assertTrue(SketchTestRunner.permitsTarget(false,safe,true));
    assertTrue(SketchTestRunner.permitsTarget(true,"",false));
  }
  @Test public void physicalCwdRecord_survivesUntilExactOriginalPreferenceIsRestored() throws Exception {
    android.content.Context context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext();
    String name="qa-cwd-contract-"+System.nanoTime();
    android.content.SharedPreferences prefs=context.getSharedPreferences(name,0);
    File marker=new File(context.getFilesDir(),"qa-physical-cwd.json");
    assertFalse("Never replace an existing recovery record",marker.exists());
    String owned="TopoDroid Sketch QA/contract/owned-case";
    try {
      assertTrue(prefs.edit().putInt("unrelated-sentinel",17).commit());
      QaPhysicalCwdGuard.begin(context,prefs,owned);
      org.json.JSONObject durable=new org.json.JSONObject(new String(new android.util.AtomicFile(marker).readFully(),java.nio.charset.StandardCharsets.UTF_8));
      assertEquals(owned,durable.getString("qaCwd"));
      assertFalse("Absent original preference must stay distinguishable",durable.getJSONObject("original").getBoolean("present"));
      assertTrue(prefs.edit().putString("DISTOX_CWD",owned).commit());
      QaPhysicalCwdGuard.finish(context,prefs);
      assertTrue("An unrestored directory must retain its recovery record",marker.isFile());
      boolean refused=false;
      try{QaPhysicalCwdGuard.begin(context,prefs,owned);}catch(IllegalStateException expected){refused=true;}
      assertTrue("Pending recovery must block another fixture",refused);
      assertTrue(prefs.edit().remove("DISTOX_CWD").commit());
      QaPhysicalCwdGuard.finish(context,prefs);
      assertFalse("Verified original preference clears the record",marker.exists());
      assertEquals(17,prefs.getInt("unrelated-sentinel",-1));
      assertTrue(prefs.edit().putString("DISTOX_CWD",owned).commit());
      refused=false;
      try{QaPhysicalCwdGuard.begin(context,prefs,owned);}catch(IllegalStateException expected){refused=true;}
      assertTrue("Unknown QA directory must not become the recorded field directory",refused);
      assertFalse(marker.exists());
    } finally {
      new android.util.AtomicFile(marker).delete(); // This test proved the marker absent before creating it.
      context.deleteSharedPreferences(name);
    }
  }
}
