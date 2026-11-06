package com.topodroid.TDX;

import static org.junit.Assert.assertTrue;

import androidx.test.filters.LargeTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

@RunWith( com.topodroid.TDX.QaJUnit4.class )
@LargeTest
@com.topodroid.TDX.qa.TestTier(com.topodroid.TDX.qa.TestTier.Tier.UI)
@com.topodroid.TDX.QaFixtureRule.Database
@com.topodroid.qa.TestFeatures({com.topodroid.qa.TestFeatures.Feature.SYMBOLS, com.topodroid.qa.TestFeatures.Feature.EXCHANGE})
public class SketchBrushRoundTripInstrumentedTest
{
  @org.junit.Rule public final com.topodroid.TDX.QaFixtureRule qaFixture = new com.topodroid.TDX.QaFixtureRule();
  private static final String SURVEY = "brush_roundtrip_phase7_case";
  private static final String PLOT_NAME = "1";

  private VisualTestSupport mSupport;

  @Before
  public void setUp()
  {
    mSupport = new VisualTestSupport( "brush_roundtrip_phase7" );
  }

  @After
  public void tearDown()
  {
    if ( mSupport != null ) mSupport.finish();
  }

  @com.topodroid.TDX.qa.TestTier(value=com.topodroid.TDX.qa.TestTier.Tier.UI)
  @Test
  public void sketchZipRoundTrip_preservesStyledLineAndPointOptions() throws Exception
  {
    mSupport.prepareForCase( VisualTestSupport.allSurveyNames( SURVEY ) );
    mSupport.launchMainWindow();
    createSurveyAndOpenSketch();

    mSupport.assertStyleBarVisible( "Thin", "Standard", "Thick" );
    mSupport.tapStyleButton( 3 );
    mSupport.clickRecentLineByThName( SymbolLibrary.USER );
    mSupport.drawCurveStrokeNormalized( 0.25, 0.40, 0.72, 0.44, 0.07, 12, 14 );
    mSupport.assertLatestLineBrushWeight( 5.0f );

    mSupport.addOrdinaryPointWithActiveStyle( 80.0f, 120.0f );
    mSupport.setLatestPointExactScaleForTest( 1.60f );
    mSupport.assertLatestPointBrushWeight( 5.0f );
    mSupport.assertLatestPointBrushScale( 1.60f, 0.0001f );

    SketchTextStyle labelStyle = SketchTextStyle.of(
      SketchFontRegistry.FONT_DEFAULT,
      SketchTextStyle.SizeMode.SCREEN,
      31.0f,
      5.0f,
      true,
      true,
      true,
      SketchTextStyle.Alignment.CENTER,
      0xff55ccff );
    mSupport.addStyledLabelForRoundTrip( "Italic\nround trip", 150.0f, 95.0f, labelStyle );
    mSupport.assertLatestLabelStyle( labelStyle );
    mSupport.reportStep( "styled sketch ready" );

    File importZip = exportCopyDeleteAndImportZip();

    assertTrue( "Imported ZIP missing: " + importZip.getAbsolutePath(), importZip.exists() );
    mSupport.openExistingPlanPlot( PLOT_NAME );
    mSupport.assertLatestLineBrushWeight( 5.0f );
    mSupport.assertLatestPointBrushWeight( 5.0f );
    mSupport.assertLatestPointBrushScale( 1.60f, 0.0001f );
    mSupport.assertLatestLabelStyle( labelStyle );
  }

  private void createSurveyAndOpenSketch()
  {
    mSupport.createSurveyAndOpenShots( SURVEY, "Brush Round Trip Team", "1", "brush style metadata round trip" );
    mSupport.addManualShot( "1", "2", "10.0", "90.0", "0.0", true );
    mSupport.createPlotThroughProductionApi( PLOT_NAME, "1" );
    mSupport.enterDrawMode();
  }

  private File exportCopyDeleteAndImportZip() throws Exception
  {
    QaFixtureRule.closeActivities();
    File zipFile = mSupport.exportCurrentArchiveThroughProductionApi();
    mSupport.assertZipContainsSurveyCore(zipFile);
    File importZip = mSupport.copyArchiveForProductionImport(zipFile);
    mSupport.deleteGeneratedSurveyAndArtifacts(SURVEY);
    mSupport.importArchiveThroughProductionApi(importZip,SURVEY);
    return importZip;
  }
}
