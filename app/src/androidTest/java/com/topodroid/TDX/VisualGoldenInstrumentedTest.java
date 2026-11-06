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
@com.topodroid.qa.TestFeatures({com.topodroid.qa.TestFeatures.Feature.DATA, com.topodroid.qa.TestFeatures.Feature.EXCHANGE})
public class VisualGoldenInstrumentedTest
{
  @org.junit.Rule public final com.topodroid.TDX.QaFixtureRule qaFixture = new com.topodroid.TDX.QaFixtureRule();
  private static final String SURVEY_SKETCH  = "visual_sketch_case";
  private static final String SURVEY_ZIP     = "visual_zip_case";
  private static final String SURVEY_PNG     = "visual_png_case";
  private static final String SURVEY_COMPASS = "visual_compass_case";
  private static final String SURVEY_CSV     = "visual_csv_case";
  private static final String SURVEY_COMPASS_BACK = "visual_compass_back_case";
  private static final String PLOT_NAME      = "1";
  private static final String PNG_EXPORT_FILENAME = "visual_png_export.png";
  private static final String PNG_HALF_TRANSPARENT = "visual_png_half_transparent.png";
  private static final String PNG_FULL_OPAQUE = "visual_png_full_opaque.png";

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
  @com.topodroid.qa.TestFeatures({com.topodroid.qa.TestFeatures.Feature.SYMBOLS, com.topodroid.qa.TestFeatures.Feature.DRAWING})
  @Test
  public void renderingFixture_fixedCurvesAndLineWeights_matchesGolden() throws Exception
  {
    mSupport.prepareForCase(
      VisualTestSupport.allSurveyNames( SURVEY_SKETCH, SURVEY_ZIP, SURVEY_PNG, SURVEY_COMPASS, SURVEY_CSV )
    );
    mSupport.launchMainWindow();
    createCanonicalSurveyAndOpenSketch( SURVEY_SKETCH );
    mSupport.assertDefaultSketchToolbarVisible();
    drawCanonicalSketch();
    mSupport.setCanonicalToolbarState();
    mSupport.reportStep( "sketch drawn" );
    mSupport.captureAndAssertScreen( "sketch_screen.png" );
  }



  @com.topodroid.TDX.qa.TestTier(value=com.topodroid.TDX.qa.TestTier.Tier.UI, baseline=true)
  @com.topodroid.qa.TestFeatures({com.topodroid.qa.TestFeatures.Feature.SYMBOLS, com.topodroid.qa.TestFeatures.Feature.EXCHANGE})
  @Test
  public void exportPng_matchesGolden() throws Exception
  {
    mSupport.prepareForCase(
      VisualTestSupport.allSurveyNames( SURVEY_SKETCH, SURVEY_ZIP, SURVEY_PNG, SURVEY_COMPASS, SURVEY_CSV )
    );
    mSupport.configureCanonicalExportInk();
    mSupport.launchMainWindow();
    createCanonicalSurveyAndOpenSketch( SURVEY_PNG );
    drawCanonicalSketch();
    mSupport.setCanonicalToolbarState();
    mSupport.reportStep( "png sketch drawn" );

    int captures=mSupport.isBaselineAuthoring()?3:1;
    for(int capture=1;capture<=captures;capture++) {
      String filename=captures==1?PNG_EXPORT_FILENAME:"canonical_authoring_"+capture+".png";
      File pngFile=mSupport.exportPngThroughProductionApi(new SketchPngExportOptions(
        true,true,true,true,true,true,false,true,1.0f,filename));
      mSupport.assertCanonicalPngGolden(pngFile,"export_png.png",capture);
    }
  }

  @com.topodroid.TDX.qa.TestTier(value=com.topodroid.TDX.qa.TestTier.Tier.UI)
  @Test
  public void exportPng_scaleAndTransparencyOptionsAffectEncodedImage() throws Exception
  {
    mSupport.prepareForCase( VisualTestSupport.allSurveyNames( SURVEY_PNG ) );
    mSupport.launchMainWindow();
    createCanonicalSurveyAndOpenSketch( SURVEY_PNG );
    drawCanonicalSketch();

    mSupport.reportStep( "PNG options: half-scale transparent export starting" );
    exportPngWithContractOptions( PNG_HALF_TRANSPARENT, "0.5", true );
    File halfTransparent = mSupport.waitForFile(
      mSupport.getPngExportFile( SURVEY_PNG, PNG_HALF_TRANSPARENT ),
      VisualTestSupport.FILE_TIMEOUT_MS
    );

    mSupport.reportStep( "PNG options: half-scale transparent file ready; full-scale opaque export starting" );
    exportPngWithContractOptions( PNG_FULL_OPAQUE, "1.0", false );
    File fullOpaque = mSupport.waitForFile(
      mSupport.getPngExportFile( SURVEY_PNG, PNG_FULL_OPAQUE ),
      VisualTestSupport.FILE_TIMEOUT_MS
    );

    mSupport.reportStep( "PNG options: both files ready; checking encoded images" );
    mSupport.assertPngScaleAndTransparencyContract( halfTransparent, fullOpaque );
  }

  @com.topodroid.TDX.qa.TestTier(com.topodroid.TDX.qa.TestTier.Tier.FAST)
  @Test public void exportCompass_matchesFixture() throws Exception
  {
    mSupport.prepareForCase(VisualTestSupport.allSurveyNames(SURVEY_COMPASS));
    mSupport.seedSurveyData(SURVEY_COMPASS,"Visual Test Team","1","visual regression");
    addCanonicalShots();
    File datFile=mSupport.exportCurrentSurveyData(false);
    mSupport.assertTextFileMatchesGolden(datFile,"export_compass.dat",VisualTestSupport.COMPASS_NORMALIZER);
  }

  @com.topodroid.TDX.qa.TestTier(com.topodroid.TDX.qa.TestTier.Tier.FAST)
  @Test public void exportCsv_isStrictlyParseableAndEscapesSurveyMetadata() throws Exception
  {
    mSupport.prepareForCase(VisualTestSupport.allSurveyNames(SURVEY_CSV));
    mSupport.seedSurveyData(SURVEY_CSV,"Visual Test Team","1","visual regression");
    addCanonicalShots();mSupport.addCsvContractMetadata();
    mSupport.assertCsvExportContract(mSupport.exportCurrentSurveyData(true));
  }

  @com.topodroid.TDX.qa.TestTier(com.topodroid.TDX.qa.TestTier.Tier.FAST)
  @Test public void exportCompass_namedBacksightDoesNotDuplicateTraverseLength() throws Exception
  {
    mSupport.prepareForCase(VisualTestSupport.allSurveyNames(SURVEY_COMPASS_BACK));
    mSupport.seedSurveyData(SURVEY_COMPASS_BACK,"Compass Team","1","backsight contract");
    mSupport.addManualShot("1","2","10.0","90.0","0.0",true);
    mSupport.addNamedBacksightForExport("2","1",10,270,0);
    mSupport.assertCompassNamedBacksightContract(mSupport.exportCurrentSurveyData(false));
  }

  @com.topodroid.TDX.qa.TestTier(value=com.topodroid.TDX.qa.TestTier.Tier.UI,baseline=true)
  @com.topodroid.qa.TestFeatures({com.topodroid.qa.TestFeatures.Feature.SYMBOLS, com.topodroid.qa.TestFeatures.Feature.EXCHANGE})
  @Test public void productionArchiveRoundTrip_fixedCurves_matchApprovedScreen() throws Exception
  {
    mSupport.prepareForCase(VisualTestSupport.allSurveyNames(SURVEY_ZIP));
    mSupport.launchMainWindow();createCanonicalSurveyAndOpenSketch(SURVEY_ZIP);
    drawCanonicalSketch();mSupport.addArchiveContractMetadata();
    VisualTestSupport.SurveyArchiveSnapshot snapshot=mSupport.captureSurveyArchiveSnapshot();
    QaFixtureRule.closeActivities();
    File archive=mSupport.exportCurrentArchiveThroughProductionApi();
    mSupport.assertZipContainsSurveyCore(archive);
    File imported=mSupport.copyArchiveForProductionImport(archive);
    mSupport.deleteGeneratedSurveyAndArtifacts(SURVEY_ZIP);
    mSupport.importArchiveThroughProductionApi(imported,SURVEY_ZIP);
    mSupport.openExistingPlanPlot(PLOT_NAME);
    mSupport.assertSurveyArchiveSnapshotEquals(snapshot);
    mSupport.setCanonicalToolbarState();
    mSupport.captureAndAssertScreen("zip_roundtrip_screen.png");
  }

  private void createCanonicalSurveyWithShots( String surveyName )
  {
    mSupport.createSurveyAndOpenShots( surveyName, "Visual Test Team", "1", "visual regression" );
    addCanonicalShots();
  }

  private void exportPngWithContractOptions( String filename, String scale, boolean transparent ) throws Exception
  {
    mSupport.exportPngThroughProductionApi(new SketchPngExportOptions(
      false,false,false,false,false,false,transparent,true,Float.parseFloat(scale),filename));
  }

  private void createCanonicalSurveyAndOpenSketch( String surveyName )
  {
    createCanonicalSurveyWithShots( surveyName );
    mSupport.createPlotThroughProductionApi( PLOT_NAME, "1" );
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
    mSupport.assertLatestLineBrushWeight(style==1?1.0f:style==2?2.0f:5.0f);
  }
}
