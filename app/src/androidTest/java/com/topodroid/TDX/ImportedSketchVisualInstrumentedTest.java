package com.topodroid.TDX;

import static org.junit.Assert.assertTrue;

import androidx.test.filters.LargeTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import java.io.File;

@RunWith( com.topodroid.TDX.QaJUnit4.class )
@LargeTest
@com.topodroid.TDX.qa.TestTier(com.topodroid.TDX.qa.TestTier.Tier.UI)
@com.topodroid.TDX.QaFixtureRule.Database
@com.topodroid.qa.TestFeatures({com.topodroid.qa.TestFeatures.Feature.SYMBOLS, com.topodroid.qa.TestFeatures.Feature.EXCHANGE})
public class ImportedSketchVisualInstrumentedTest
{
  @org.junit.Rule public final com.topodroid.TDX.QaFixtureRule qaFixture = new com.topodroid.TDX.QaFixtureRule();
  private static final String FIXTURE_ASSET = "fixtures/Demo.zip";
  private static final String FIXTURE_FILE  = "Demo.zip";
  private static final String SURVEY_NAME   = "Demo";
  private static final String PLAN_PLOT_LABEL = "Demo";
  private static final String PNG_EXPORT_FILENAME = "demo_fixture_export.png";
  private static final double DEMO_PNG_MAX_DIFF_RATIO = 0.01;
  private static final int DEMO_PNG_SAMPLE_SIZE = 8;

  private VisualTestSupport mSupport;

  @Rule
  public final TestName mTestName = new TestName();

  @Before
  public void setUp()
  {
    mSupport = new VisualTestSupport( mTestName.getMethodName() );
  }

  @After
  public void tearDown()
  {
    if ( mSupport != null ) mSupport.finish();
  }

  @com.topodroid.TDX.qa.TestTier(value=com.topodroid.TDX.qa.TestTier.Tier.UI, baseline=true)
  @Test
  public void importDemoSketch_liveScreenAndPngExport_matchGoldens() throws Exception
  {
    mSupport.prepareForCase( VisualTestSupport.allSurveyNames( SURVEY_NAME ) );
    mSupport.launchMainWindow();

    File importZip = mSupport.copyAssetForProductionImport( FIXTURE_ASSET, FIXTURE_FILE );
    mSupport.assertZipContainsSurveyCore( importZip );
    mSupport.reportStep( "demo fixture copied" );

    mSupport.importArchiveThroughProductionApi(importZip,SURVEY_NAME);

    mSupport.openExistingPlanPlot( PLAN_PLOT_LABEL );
    mSupport.enterDrawMode();
    mSupport.setCanonicalToolbarState();

    assertTrue( "Imported fixture did not load any sketch line paths",
      mSupport.countSketchLinePaths() > 0 );
    assertTrue( "Imported fixture did not load its reference image path",
      mSupport.countReferencePoints() > 0 );

    mSupport.reportStep( "demo fixture plot opened" );
    mSupport.captureAndAssertScreen( "demo_fixture_screen.png" );

    int captures=mSupport.isBaselineAuthoring()?3:1;
    for(int capture=1;capture<=captures;capture++) {
      String filename=captures==1?PNG_EXPORT_FILENAME:"demo_authoring_"+capture+".png";
      File pngFile=mSupport.exportPngThroughProductionApi(filename);
      mSupport.assertCompleteSampledPngGolden(pngFile,"demo_fixture_export_png.png",DEMO_PNG_MAX_DIFF_RATIO,DEMO_PNG_SAMPLE_SIZE,capture);
    }
  }
}
