package com.topodroid.TDX;

import com.topodroid.TDX.qa.TestTier;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.File;

@RunWith(QaJUnit4.class)
@TestTier(TestTier.Tier.SMOKE)
@QaFixtureRule.Database
@com.topodroid.qa.TestFeatures({com.topodroid.qa.TestFeatures.Feature.DRAWING, com.topodroid.qa.TestFeatures.Feature.DATA, com.topodroid.qa.TestFeatures.Feature.EXCHANGE})
public class SmokeWorkflowInstrumentedTest {
  @Rule public final QaFixtureRule fixture = new QaFixtureRule();
  private VisualTestSupport support;
  @After public void finish() { if (support != null) support.finish(); }

  @Test public void fieldSession_preservesEnteredDataAndDrawingAcrossColdReloadAndPng() throws Throwable {
    java.util.List<Throwable> failures = new java.util.ArrayList<>();
    try {
      support = new VisualTestSupport("field-session-integrity");
      support.prepareForCase(VisualTestSupport.allSurveyNames("qa_field_session"));
      support.configureCanonicalExportInk();
      support.launchMainWindow();
      support.createSurveyThroughUiAndOpenShots("qa_field_session","QA","1","real field-session persistence");
      QaFieldSession session = new QaFieldSession(support);
      session.enterShotsThroughUi();
      checkStoredShots(session,failures);
      support.createPlotThroughProductionApi("1","1");
      session.drawObjectsThroughInput();
      QaFieldSession.Snapshot before = session.snapshot();
      session.coldReload("qa_field_session","1");
      checkStoredShots(session,failures);
      session.assertSameSnapshot(before,session.snapshot());
      File png = support.exportPngThroughProductionApi(new SketchPngExportOptions(false,false,false,false,false,false,true,true,1f,"field-session.png"));
      session.assertPngObjects(before,png);
    } catch(Throwable failure) {
      if(support!=null)try {support.captureFailureEvidence("field-session-failure");}catch(Throwable diagnostic){failure.addSuppressed(diagnostic);}
      failures.add(failure);
    }
    org.junit.runners.model.MultipleFailureException.assertEmpty(failures);
  }
  private static void checkStoredShots(QaFieldSession session,java.util.List<Throwable> failures) {
    // A measurement mismatch keeps the test red while independent drawing/export
    // assertions still run. Never change the expected values to match corruption.
    try {session.assertShots();} catch(AssertionError failure) {failures.add(failure);}
  }
}
