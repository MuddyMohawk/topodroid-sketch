package com.topodroid.TDX;

import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;

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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Measures full-frame render cost of the imported Demo fixture by timing
 *  repeated off-screen executeAll renders (via DrawingSurface.drawCanvas) in
 *  normal mode and in edit mode (selection dots).
 *
 *  Reports mean/p50/p95 per variant through the instrumentation stream and
 *  render_perf.txt in the case artifacts. Run on the same device before and
 *  after a renderer change for A/B numbers (emulator or physical tablet).
 */
@RunWith( AndroidJUnit4.class )
@LargeTest
public class RenderPerfInstrumentedTest
{
  private static final String FIXTURE_ASSET = "fixtures/Demo.zip";
  private static final String FIXTURE_FILE  = "Demo.zip";
  private static final String SURVEY_NAME   = "Demo";
  private static final String PLAN_PLOT_LABEL = "Demo";
  private static final int WARMUP = 10;
  private static final int FRAMES = 100;

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
  public void renderDemoFixture_reportsFrameTimes() throws Exception
  {
    mSupport.prepareForCase( VisualTestSupport.allSurveyNames( SURVEY_NAME ) );
    mSupport.launchMainWindowOnAnyDevice(); // perf timings are device-agnostic (off-screen fixed-size canvas)

    File importZip = mSupport.copyAssetToDownloads( FIXTURE_ASSET, FIXTURE_FILE );
    mSupport.openMainImportDialogFromToolbar();
    mSupport.tapViewByDevice( R.id.button_ok );
    mSupport.pickDocumentByFileName( importZip.getName() );
    mSupport.waitForSurveyOnMainList( SURVEY_NAME );

    mSupport.openSurveyFromMainList( SURVEY_NAME );
    mSupport.openExistingPlanPlot( PLAN_PLOT_LABEL );
    mSupport.enterDrawMode();
    mSupport.setCanonicalToolbarState();

    float[] view = mSupport.currentPlotViewForRenderHash();

    List< String > report = new ArrayList<>();
    report.add( timeVariant( "normal",   view, false ) );
    report.add( timeVariant( "editmode", view, true ) );
    mSupport.writeRenderPerfReport( report );
    for ( String line : report ) mSupport.reportStep( "RENDER_PERF " + line );
  }

  private String timeVariant( String variant, float[] view, boolean displayPoints ) throws Exception
  {
    long[] ns = mSupport.timeOffscreenRenders( variant, view[0], view[1], view[2], displayPoints, WARMUP, FRAMES );
    long[] sorted = ns.clone();
    Arrays.sort( sorted );
    long sum = 0;
    for ( long v : ns ) sum += v;
    return String.format( Locale.US, "%s frames %d mean %.2f p50 %.2f p95 %.2f max %.2f ms",
      variant, ns.length,
      sum / ( ns.length * 1e6 ),
      sorted[ ns.length / 2 ] / 1e6,
      sorted[ (int)( ns.length * 0.95 ) ] / 1e6,
      sorted[ ns.length - 1 ] / 1e6 );
  }
}
