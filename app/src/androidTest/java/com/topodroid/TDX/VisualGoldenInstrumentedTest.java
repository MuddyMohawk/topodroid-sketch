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
public class VisualGoldenInstrumentedTest
{
  private static final String SURVEY_SKETCH  = "visual_sketch_case";
  private static final String SURVEY_ZIP     = "visual_zip_case";
  private static final String SURVEY_PNG     = "visual_png_case";
  private static final String SURVEY_COMPASS = "visual_compass_case";
  private static final String PLOT_NAME      = "1";
  private static final String PNG_EXPORT_FILENAME = "visual_png_export.png";

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
  public void createSurvey_addShots_createSketch_drawPresetsAndLineWeights_matchesGolden() throws Exception
  {
    mSupport.prepareForCase(
      VisualTestSupport.allSurveyNames( SURVEY_SKETCH, SURVEY_ZIP, SURVEY_PNG, SURVEY_COMPASS )
    );
    mSupport.launchMainWindow();
    createCanonicalSurveyAndOpenSketch( SURVEY_SKETCH );
    mSupport.assertDefaultSketchToolbarVisible();
    drawCanonicalSketch();
    mSupport.setCanonicalToolbarState();
    mSupport.reportStep( "sketch drawn" );
    mSupport.captureAndAssertScreen( "sketch_screen.png" );
  }

  @Test
  public void exportZip_includesSurveyCore_and_importRoundTripsThroughPicker() throws Exception
  {
    mSupport.prepareForCase(
      VisualTestSupport.allSurveyNames( SURVEY_SKETCH, SURVEY_ZIP, SURVEY_PNG, SURVEY_COMPASS )
    );
    mSupport.launchMainWindow();
    createCanonicalSurveyAndOpenSketch( SURVEY_ZIP );
    drawCanonicalSketch();
    mSupport.setCanonicalToolbarState();
    mSupport.reportStep( "zip sketch drawn" );

    mSupport.pressBackToShotWindow();
    mSupport.pressBackToMainWindow();
    mSupport.openSurveyWindowFromMainListLongPress( SURVEY_ZIP );
    mSupport.openCurrentMenuAndClickText( mSupport.string( R.string.menu_export ) );
    mSupport.chooseSpinnerValue( R.id.spin, "ZIP" );
    mSupport.setZipSymbolsExportEnabled( true );
    mSupport.tapViewByDevice( R.id.button_ok );

    File zipFile = mSupport.waitForFile( mSupport.getZipFile( SURVEY_ZIP ), VisualTestSupport.FILE_TIMEOUT_MS );
    mSupport.assertZipContainsSurveyCore( zipFile );
    File importZip = mSupport.copyFileToDownloads( zipFile );
    mSupport.reportStep( "zip exported" );

    mSupport.openCurrentMenuAndClickText( mSupport.string( R.string.menu_delete ) );
    mSupport.confirmAlertOk();
    mSupport.relaunchMainWindow();

    mSupport.waitForSurveyAbsentInDatabase( SURVEY_ZIP );
    mSupport.reportStep( "zip source deleted" );

    mSupport.openMainImportDialogFromToolbar();
    mSupport.tapViewByDevice( R.id.button_ok );
    mSupport.pickDocumentByFileName( importZip.getName() );
    mSupport.waitForSurveyOnMainList( SURVEY_ZIP );
    mSupport.reportStep( "zip imported" );

    mSupport.openSurveyFromMainList( SURVEY_ZIP );
    mSupport.openExistingPlanPlot( PLOT_NAME );
    mSupport.setCanonicalToolbarState();
    mSupport.reportStep( "zip plot reopened" );
    mSupport.captureAndAssertScreen( "zip_roundtrip_screen.png" );
  }

  @Test
  public void exportPng_matchesGolden() throws Exception
  {
    mSupport.prepareForCase(
      VisualTestSupport.allSurveyNames( SURVEY_SKETCH, SURVEY_ZIP, SURVEY_PNG, SURVEY_COMPASS )
    );
    mSupport.launchMainWindow();
    createCanonicalSurveyAndOpenSketch( SURVEY_PNG );
    drawCanonicalSketch();
    mSupport.setCanonicalToolbarState();
    mSupport.reportStep( "png sketch drawn" );

    mSupport.openCurrentMenuAndClickText( mSupport.string( R.string.menu_export ) );
    mSupport.chooseSpinnerValue( R.id.spin, "PNG" );
    mSupport.replaceTextInField( R.id.png_filename, PNG_EXPORT_FILENAME );
    mSupport.tapViewByDevice( R.id.button_ok );
    mSupport.reportStep( "png export submitted" );

    File pngFile = mSupport.waitForFile(
      mSupport.getPngExportFile( SURVEY_PNG, PNG_EXPORT_FILENAME ),
      VisualTestSupport.FILE_TIMEOUT_MS
    );
    assertTrue( "PNG export was not created", pngFile.exists() );
    mSupport.assertPngFileMatchesGolden( pngFile, "export_png.png" );
  }

  @Test
  public void exportCompass_matchesFixture() throws Exception
  {
    mSupport.prepareForCase(
      VisualTestSupport.allSurveyNames( SURVEY_SKETCH, SURVEY_ZIP, SURVEY_PNG, SURVEY_COMPASS )
    );
    mSupport.launchMainWindow();
    createCanonicalSurveyWithShots( SURVEY_COMPASS );
    mSupport.pressBackToMainWindow();

    mSupport.openSurveyWindowFromMainListLongPress( SURVEY_COMPASS );
    mSupport.openCurrentMenuAndClickText( mSupport.string( R.string.menu_export ) );
    mSupport.chooseSpinnerValue( R.id.spin, "Compass" );
    mSupport.tapViewByDevice( R.id.button_ok );

    File datFile = mSupport.waitForFile(
      mSupport.getCompassExportFile( SURVEY_COMPASS ),
      VisualTestSupport.FILE_TIMEOUT_MS
    );
    assertTrue( "Compass export was not created", datFile.exists() );
    mSupport.assertTextFileMatchesGolden( datFile, "export_compass.dat", VisualTestSupport.COMPASS_NORMALIZER );
  }

  private void createCanonicalSurveyWithShots( String surveyName )
  {
    mSupport.createSurveyAndOpenShots( surveyName, "Visual Test Team", "1", "visual regression" );
    addCanonicalShots();
  }

  private void createCanonicalSurveyAndOpenSketch( String surveyName )
  {
    createCanonicalSurveyWithShots( surveyName );
    mSupport.openNewPlotFromShotWindow( PLOT_NAME, "1" );
    mSupport.enterDrawMode();
  }

  private void addCanonicalShots()
  {
    mSupport.addManualShot( "1", "2", "10.0", "90.0", "0.0", false );
    mSupport.addManualShot( "2", "3", "6.0", "0.0", "0.0", false );
    mSupport.addManualShot( "2", "4", "5.0", "180.0", "0.0", true );
  }

  private void drawCanonicalSketch()
  {
    mSupport.enterDrawMode();

    // Exercise the current style bar on the normal user line so the golden
    // screenshot locks in the current symbol-plus-style drawing model. Strokes:
    //   - live in the left half of the canvas (x in [0.08, 0.48]) to stay
    //     clear of the station markers for shots 1->2, 2->3, 2->4 (which
    //     cluster on the right);
    //   - are stacked in a compact band (y in [0.18, 0.58]) so nothing falls
    //     off the bottom of the drawing surface on the emulator;
    //   - are drawn as quadratic-bezier curves inserted through the drawing
    //     model rather than as emulator swipes, which keeps this golden stable
    //     while the toolbar and style round-trip tests cover the UI behavior.
    //
    // Previous iterations selected weight-specific generated line symbols.
    // The current model stores line weight on placed objects through the
    // active brush style.
    drawUserLineCurve( 1, 1,
      0.08, 0.18, 0.48, 0.18,  0.04 );
    drawUserLineCurve( 2, 2,
      0.08, 0.38, 0.48, 0.38, -0.05 );
    drawUserLineCurve( 4, 3,
      0.08, 0.58, 0.48, 0.58,  0.06 );
  }

  private void drawUserLineCurve( int preset, int style,
    double startX, double startY, double endX, double endY, double curveOffset )
  {
    mSupport.tapPresetButton( preset );
    mSupport.tapStyleButton( style );
    mSupport.clickRecentLineByThName( SymbolLibrary.USER );
    mSupport.addUserLineCurveWithActiveStyle( startX, startY, endX, endY, curveOffset, 24 );
  }
}
