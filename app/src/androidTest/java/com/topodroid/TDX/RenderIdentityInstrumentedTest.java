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
import java.util.ArrayList;
import java.util.List;

/** Renders the imported Demo fixture off-screen under a fixed set of view
 *  transforms and display variants, and reports a SHA-256 pixel hash per
 *  variant (plus a PNG of each frame in the case artifacts).
 *
 *  This test never fails on hash content: it PRODUCES hashes. The workflow is
 *  to run it on an unmodified tree, keep tmp-test-artifacts as the baseline,
 *  then re-run after each renderer change and byte-compare render_hashes.txt.
 *  Identical hashes prove the change is pixel-identical, which the golden
 *  screenshot tests (5% tolerance) cannot.
 */
@RunWith( AndroidJUnit4.class )
@LargeTest
public class RenderIdentityInstrumentedTest
{
  private static final String FIXTURE_ASSET = "fixtures/Demo.zip";
  private static final String FIXTURE_FILE  = "Demo.zip";
  private static final String SURVEY_NAME   = "Demo";
  private static final String PLAN_PLOT_LABEL = "Demo";

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
  public void renderDemoFixture_reportsPixelHashes() throws Exception
  {
    mSupport.prepareForCase( VisualTestSupport.allSurveyNames( SURVEY_NAME ) );
    mSupport.launchMainWindow();

    File importZip = mSupport.copyAssetToDownloads( FIXTURE_ASSET, FIXTURE_FILE );
    mSupport.openMainImportDialogFromToolbar();
    mSupport.tapViewByDevice( R.id.button_ok );
    mSupport.pickDocumentByFileName( importZip.getName() );
    mSupport.waitForSurveyOnMainList( SURVEY_NAME );

    mSupport.openSurveyFromMainList( SURVEY_NAME );
    mSupport.openExistingPlanPlot( PLAN_PLOT_LABEL );
    mSupport.enterDrawMode();
    mSupport.setCanonicalToolbarState();

    assertTrue( "Imported fixture did not load any sketch line paths",
      mSupport.countSketchLinePaths() > 0 );

    // fixed, run-to-run stable view tuples derived from the plot's own restore state
    float[] view = mSupport.currentPlotViewForRenderHash();
    float ox = view[0], oy = view[1], zm = view[2];
    float[][] transforms = {
      { ox, oy, zm },                       // as-opened view
      { ox - 120f, oy - 80f, zm },          // panned
      { ox, oy, zm * 2.0f },                // zoomed in
    };

    List< String > report = new ArrayList<>();
    captureVariant( report, "normal",    transforms, false, false, true,  DrawingSplayPath.SPLAY_MODE_LINE );
    captureVariant( report, "gridoff",   transforms, false, false, false, DrawingSplayPath.SPLAY_MODE_LINE );
    captureVariant( report, "editmode",  transforms, false, true,  true,  DrawingSplayPath.SPLAY_MODE_LINE );
    captureVariant( report, "splaydots", transforms, false, false, true,  DrawingSplayPath.SPLAY_MODE_POINT );
    captureVariant( report, "landscape", transforms, true,  false, true,  DrawingSplayPath.SPLAY_MODE_LINE );

    // add an area (exercises the saveLayer / area-paint path), then re-capture
    float sceneCx = ( VisualTestSupport.RENDER_HASH_WIDTH  * 0.5f ) / zm - ox;
    float sceneCy = ( VisualTestSupport.RENDER_HASH_HEIGHT * 0.5f ) / zm - oy;
    mSupport.addTestAreaForRenderHash( sceneCx, sceneCy, 60f / zm );
    captureVariant( report, "witharea",     transforms, false, false, true, DrawingSplayPath.SPLAY_MODE_LINE );
    captureVariant( report, "witharea_edit", transforms, false, true, true, DrawingSplayPath.SPLAY_MODE_LINE );

    mSupport.writeRenderHashReport( report );
    for ( String line : report ) mSupport.reportStep( "RENDER_HASH " + line );
  }

  private void captureVariant( List< String > report, String variant, float[][] transforms,
                               boolean landscape, boolean displayPoints, boolean gridVisible, int splayMode )
    throws Exception
  {
    for ( int k = 0; k < transforms.length; ++ k ) {
      String name = variant + "_t" + k;
      String hash = mSupport.captureRenderHash( name,
        transforms[k][0], transforms[k][1], transforms[k][2],
        landscape, displayPoints, gridVisible, splayMode );
      report.add( name + " " + hash );
    }
  }
}
