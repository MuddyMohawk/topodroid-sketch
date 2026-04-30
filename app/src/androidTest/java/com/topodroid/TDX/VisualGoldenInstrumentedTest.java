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
  public void createSurvey_addShots_createSketch_drawPresetsAndSketchLines_matchesGolden() throws Exception
  {
    mSupport.prepareForCase(
      VisualTestSupport.allSurveyNames( SURVEY_SKETCH, SURVEY_ZIP, SURVEY_PNG, SURVEY_COMPASS )
    );
    mSupport.launchMainWindow();
    createCanonicalSurveyAndOpenSketch( SURVEY_SKETCH );
    drawCanonicalSketch();
    mSupport.setCanonicalToolbarState();
    mSupport.captureAndAssertScreen( "sketch_screen.png" );
  }

  @Test
  public void exportZip_includesSketchLineSymbols_and_importRoundTripsThroughPicker() throws Exception
  {
    mSupport.prepareForCase(
      VisualTestSupport.allSurveyNames( SURVEY_SKETCH, SURVEY_ZIP, SURVEY_PNG, SURVEY_COMPASS )
    );
    mSupport.launchMainWindow();
    createCanonicalSurveyAndOpenSketch( SURVEY_ZIP );
    drawCanonicalSketch();
    mSupport.setCanonicalToolbarState();

    mSupport.pressBackToShotWindow();
    mSupport.pressBackToMainWindow();
    mSupport.openSurveyWindowFromMainListLongPress( SURVEY_ZIP );
    mSupport.openCurrentMenuAndClickText( mSupport.string( R.string.menu_export ) );
    mSupport.chooseSpinnerValue( R.id.spin, "ZIP" );
    mSupport.setCheckboxChecked( R.id.zip_symbols, true );
    mSupport.setZipSymbolsExportEnabled( true );
    mSupport.tapView( R.id.button_ok );

    File zipFile = mSupport.waitForFile( mSupport.getZipFile( SURVEY_ZIP ), VisualTestSupport.FILE_TIMEOUT_MS );
    mSupport.assertZipContainsSketchLineSymbols( zipFile );
    File importZip = mSupport.copyFileToDownloads( zipFile );

    mSupport.openCurrentMenuAndClickText( mSupport.string( R.string.menu_delete ) );
    mSupport.confirmAlertOk();
    mSupport.relaunchMainWindow();

    // The in-app delete flow normally clears the DB row synchronously, but if
    // anything races (dialog dismiss, activity teardown, etc.) the survey row
    // can still be present when the import runs, and the ZIP importer then
    // bails with "Failed: duplicate survey name". Nuke it defensively here
    // before invoking the picker so the import always sees a clean slate.
    mSupport.forceDeleteSurveyByName( SURVEY_ZIP );

    mSupport.openMainImportDialogFromToolbar();
    mSupport.tapView( R.id.button_ok );
    mSupport.pickDocumentByFileName( importZip.getName() );
    mSupport.waitForSurveyOnMainList( SURVEY_ZIP );

    mSupport.openSurveyFromMainList( SURVEY_ZIP );
    mSupport.openExistingPlanPlot( PLOT_NAME );
    mSupport.setCanonicalToolbarState();
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

    mSupport.openCurrentMenuAndClickText( mSupport.string( R.string.menu_export ) );
    mSupport.chooseSpinnerValue( R.id.spin, "PNG" );
    mSupport.replaceTextInField( R.id.png_filename, PNG_EXPORT_FILENAME );
    mSupport.tapView( R.id.button_ok );

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
    mSupport.tapView( R.id.button_ok );

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

    // Exercise every combination of {preset 1, preset 2} x
    // {user-fine, user-standard, user-thick} so the golden screenshot locks in
    // all six line appearances. Strokes:
    //   - live in the left half of the canvas (x in [0.08, 0.48]) to stay
    //     clear of the station markers for shots 1->2, 2->3, 2->4 (which
    //     cluster on the right);
    //   - are stacked in a compact band (y in [0.13, 0.73]) so nothing falls
    //     off the bottom of the drawing surface on the emulator;
    //   - are drawn as quadratic-bezier CURVES with alternating arc direction
    //     rather than straight swipes. Preset 1 (segment=1) preserves the
    //     curve shape sample-by-sample; preset 2 (segment=10) smooths it
    //     heavily. Straight swipes render identically under both presets,
    //     which defeats the whole point of testing both.
    //
    // Previous iterations of this routine tapped recent-line indices 0, 1, 2
    // directly, which on a default TopoDroid palette lands on walls, section,
    // and only then user-fine. That made two of the strokes draw section
    // lines (triggering the cross-section dialog) and never exercised
    // user-standard or user-thick at all.
    drawUserLineCurve( R.id.button_preset_1, SketchLineSymbolManager.LEGACY_TH_NAME_FINE,
      0.08, 0.14, 0.48, 0.14,  0.04 );
    drawUserLineCurve( R.id.button_preset_2, SketchLineSymbolManager.LEGACY_TH_NAME_FINE,
      0.08, 0.26, 0.48, 0.26, -0.04 );

    drawUserLineCurve( R.id.button_preset_1, SketchLineSymbolManager.LEGACY_TH_NAME_STANDARD,
      0.08, 0.38, 0.48, 0.38,  0.05 );
    drawUserLineCurve( R.id.button_preset_2, SketchLineSymbolManager.LEGACY_TH_NAME_STANDARD,
      0.08, 0.50, 0.48, 0.50, -0.05 );

    drawUserLineCurve( R.id.button_preset_1, SketchLineSymbolManager.LEGACY_TH_NAME_THICK,
      0.08, 0.62, 0.48, 0.62,  0.06 );
    drawUserLineCurve( R.id.button_preset_2, SketchLineSymbolManager.LEGACY_TH_NAME_THICK,
      0.08, 0.74, 0.48, 0.74, -0.06 );
  }

  private void drawUserLineCurve( int presetButtonId, String lineThName,
    double startX, double startY, double endX, double endY, double curveOffset )
  {
    mSupport.tapPresetButton( presetButtonId );
    mSupport.clickRecentLineByThName( lineThName );
    // 30 samples along the path, 6 interpolation steps between each pair, for
    // a reasonably smooth but not-too-slow gesture.
    mSupport.drawCurveStrokeNormalized( startX, startY, endX, endY, curveOffset, 30, 6 );
  }
}
