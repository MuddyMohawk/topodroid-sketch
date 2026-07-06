package com.topodroid.TDX;

import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import java.io.File;

@RunWith( AndroidJUnit4.class )
@LargeTest
public class ImportedSketchVisualInstrumentedTest
{
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

  @Test
  public void importDemoSketch_liveScreenAndPngExport_matchGoldens() throws Exception
  {
    mSupport.prepareForCase( VisualTestSupport.allSurveyNames( SURVEY_NAME ) );
    mSupport.launchMainWindow();

    File importZip = mSupport.copyAssetToDownloads( FIXTURE_ASSET, FIXTURE_FILE );
    mSupport.assertZipContainsSurveyCore( importZip );
    mSupport.reportStep( "demo fixture copied" );

    mSupport.openMainImportDialogFromToolbar();
    mSupport.tapViewByDevice( R.id.button_ok );
    mSupport.pickDocumentByFileName( importZip.getName() );
    mSupport.waitForSurveyOnMainList( SURVEY_NAME );
    mSupport.reportStep( "demo fixture imported" );

    mSupport.openSurveyFromMainList( SURVEY_NAME );
    mSupport.openExistingPlanPlot( PLAN_PLOT_LABEL );
    mSupport.enterDrawMode();
    mSupport.setCanonicalToolbarState();

    assertTrue( "Imported fixture did not load any sketch line paths",
      mSupport.countSketchLinePaths() > 0 );
    assertTrue( "Imported fixture did not load its reference image path",
      mSupport.countReferencePoints() > 0 );

    mSupport.reportStep( "demo fixture plot opened" );
    mSupport.captureAndAssertScreen( "demo_fixture_screen.png" );

    mSupport.openCurrentMenuAndClickText( mSupport.string( R.string.menu_export ) );
    mSupport.chooseSpinnerValue( R.id.spin, "PNG" );
    mSupport.replaceTextInField( R.id.png_filename, PNG_EXPORT_FILENAME );
    mSupport.tapViewByDevice( R.id.button_ok );
    mSupport.reportStep( "demo fixture png export submitted" );

    File pngFile = mSupport.waitForFile(
      mSupport.getPngExportFile( SURVEY_NAME, PNG_EXPORT_FILENAME ),
      VisualTestSupport.FILE_TIMEOUT_MS
    );
    assertTrue( "PNG export was not created", pngFile.exists() );
    mSupport.assertPngFileMatchesGolden(
      pngFile,
      "demo_fixture_export_png.png",
      DEMO_PNG_MAX_DIFF_RATIO,
      DEMO_PNG_SAMPLE_SIZE
    );
  }
}
