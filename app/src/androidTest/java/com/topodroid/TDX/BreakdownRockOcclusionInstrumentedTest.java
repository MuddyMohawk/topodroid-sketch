package com.topodroid.TDX;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.RegionIterator;
import android.graphics.pdf.PdfDocument;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.topodroid.types.PointScale;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Locale;

@RunWith( AndroidJUnit4.class )
@LargeTest
public class BreakdownRockOcclusionInstrumentedTest
{
  private static final int PERF_SAMPLES = 20;

  private Context mContext;
  private Context mPreviousContext;
  private int mBoulder;

  @Before public void setUp()
  {
    mPreviousContext = TDInstance.context;
    mContext = InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
    TDInstance.setContext( mContext );
    TopoDroidApp.installSymbols( true );
    BrushManager.reloadPointLibrary( mContext, mContext.getResources() );
    mBoulder = BrushManager.getPointIndexByThName( "boulder" );
    assertTrue( "Missing boulder point", mBoulder >= 0 );
  }

  @After public void tearDown()
  {
    TDInstance.context = mPreviousContext;
  }

  @Test public void occlusionCodec_rejectsMalformedFutureAndMismatchedGroups()
  {
    assertNull( SketchOcclusionCodec.fromOptions( "-tdx-occlude v=2,g=breakdown" ) );
    assertNull( SketchOcclusionCodec.fromOptions( "-tdx-occlude v=1,g=" ) );
    assertNull( SketchOcclusionCodec.fromOptions( "-tdx-occlude v=1,g=bad/group" ) );
    assertNull( SketchOcclusionCodec.fromOptions( "-tdx-occlude v=1,v=1,g=breakdown" ) );
    assertEquals( "breakdown", SketchOcclusionCodec.fromOptions(
        "-tdx-occlude v=1,g=old -foo bar -tdx-occlude v=1,g=breakdown" ) );

    String mismatched = "-tdx-occlude v=1,g=other -foo bar";
    DrawingPointPath point = new DrawingPointPath( mBoulder, 0.0f, 0.0f, PointScale.SCALE_M,
                                                   null, mismatched, 0 );
    assertFalse( point.hasSketchOcclusion() );
    assertEquals( mismatched, point.getOptions() );
  }

  @Test public void silhouetteParser_skipsDegenerateMovesAndRejectsOpenOutlines()
  {
    Path closed = SymbolPoint.makeOuterSilhouette(
        "moveTo 0 0 moveTo 1 1 lineTo 5 1 lineTo 5 5 lineTo 1 5 lineTo 1 1 moveTo 2 2 lineTo 3 2", 1.0f );
    assertNotNull( closed );
    RectF bounds = new RectF();
    closed.computeBounds( bounds, true );
    assertEquals( new RectF( 1.0f, 1.0f, 5.0f, 5.0f ), bounds );
    assertNull( SymbolPoint.makeOuterSilhouette( "moveTo 0 0 lineTo 5 0 lineTo 5 5", 1.0f ) );
    assertNull( SymbolPoint.makeOuterSilhouette( "moveTo 0 0", 1.0f ) );
  }

  @Test public void allBreakdownMasters_haveAffineOcclusionAndBodySilhouettes()
  {
    String[] names = { "boulder", "angular-block", "bedding-slab" };
    for ( String name : names ) {
      int type = BrushManager.getPointIndexByThName( name );
      assertTrue( "Missing breakdown master " + name, type >= 0 );
      assertTrue( name + " is not affine-capable", BrushManager.isPointAffine( type ) );
      assertEquals( name + " has the wrong occlusion group", "breakdown",
                    BrushManager.pointDefaultOccludeGroup( type ) );
      assertNotNull( name + " has no structural detail path", BrushManager.getPointOrigDetailPath( type ) );
      assertEquals( name + " shading is not thin enough", 0.25f,
                    BrushManager.getPointDetailStrokeScale( type ), 0.0001f );
      assertEquals( name + " structural ink does not match same-weight lines", 1.0f,
                    BrushManager.getPointSketchStrokeScale( type ), 0.0001f );

      Path silhouette = BrushManager.getPointOrigOcclusionSilhouette( type );
      assertNotNull( name + " has no derived silhouette", silhouette );
      float fill_ratio = silhouetteFillRatio( silhouette, 32.0f );
      assertTrue( name + " silhouette looks like an outlined ribbon instead of a filled rock body: " + fill_ratio,
                  fill_ratio > 0.45f );

      DrawingPointPath placed = new DrawingPointPath( type, 20.0f, 20.0f, PointScale.SCALE_M, 0 );
      assertTrue( name + " placement did not receive its affine transform", placed.hasSketchAffineTransform() );
      assertTrue( name + " placement did not receive its occlusion membership", placed.hasSketchOcclusion() );
    }
  }

  @Test public void newerRockHidesOlderRockButDoesNotHideOrdinaryContent()
  {
    DrawingPointPath oldRock = rock( 100.0f, 100.0f, 0xffff2020 );
    DrawingPointPath newRock = rock( 118.0f, 100.0f, 0xff20ff20 );
    Path newMask = newRock.copyOcclusionSilhouette();
    assertNotNull( newMask );

    Bitmap control = renderDirect( oldRock, newRock );
    int controlRed = countDominantInMask( control, newMask, Color.RED );
    assertTrue( "Control fixture does not contain a meaningful overlap: " + controlRed, controlRed > 15 );

    Scrap scrap = new Scrap( 0, "breakdown-stack" );
    scrap.addCommand( new ProbeLine( 45.0f, 100.0f, 175.0f, 100.0f, 0xff2080ff ) );
    scrap.addCommand( oldRock );
    scrap.addCommand( newRock );
    assertTrue( scrap.hasOccludingPoints() );

    Bitmap stacked = renderScrap( scrap );
    int stackedRed = countDominantInMask( stacked, newMask, Color.RED );
    int stackedBlue = countDominantInMask( stacked, newMask, Color.BLUE );
    assertEquals( "Older rock ink remains beneath the newer silhouette", 0, stackedRed );
    assertTrue( "Ordinary line was incorrectly occluded by the rock mask: " + stackedBlue, stackedBlue > 20 );

    scrap.undo();
    Bitmap afterUndo = renderScrap( scrap );
    assertTrue( "Undo did not restore the whole older rock", countDominantInMask( afterUndo, newMask, Color.RED ) > 15 );
  }

  @Test public void movingTopRock_invalidatesCachedVisibleInk()
  {
    DrawingPointPath oldRock = rock( 100.0f, 100.0f, 0xffff2020 );
    DrawingPointPath newRock = rock( 118.0f, 100.0f, 0xff20ff20 );
    Path oldMask = newRock.copyOcclusionSilhouette();
    Scrap scrap = new Scrap( 0, "breakdown-cache" );
    scrap.addCommand( oldRock );
    scrap.addCommand( newRock );
    Bitmap before = renderScrap( scrap );
    assertEquals( 0, countDominantInMask( before, oldMask, Color.RED ) );

    newRock.shiftBy( 90.0f, 0.0f );
    Bitmap after = renderScrap( scrap );
    assertTrue( "Moving the top rock reused stale clipped ink",
                countDominantInMask( after, oldMask, Color.RED ) > 15 );
  }

  @Test public void transparentBitmapAndPdfCanvas_renderStackWithoutRasterLayer() throws Exception
  {
    Scrap scrap = new Scrap( 0, "breakdown-export" );
    scrap.addCommand( rock( 100.0f, 100.0f, 0xffff2020 ) );
    scrap.addCommand( rock( 118.0f, 100.0f, 0xff20ff20 ) );

    Bitmap transparent = Bitmap.createBitmap( 520, 400, Bitmap.Config.ARGB_8888 );
    transparent.eraseColor( Color.TRANSPARENT );
    Matrix export_matrix = new Matrix();
    export_matrix.setScale( 2.0f, 2.0f );
    scrap.drawAll( new Canvas( transparent ), export_matrix, 2.0f, bitmapBounds(), false );
    assertTrue( "Transparent 2x render did not draw the rock stack", countNonTransparent( transparent ) > 250 );
    assertEquals( "Transparent background was unexpectedly filled", 0, Color.alpha( transparent.getPixel( 4, 4 ) ) );
    transparent.recycle();

    PdfDocument pdf = new PdfDocument();
    try {
      PdfDocument.Page page = pdf.startPage(
          new PdfDocument.PageInfo.Builder( 520, 400, 1 ).create() );
      scrap.drawAll( page.getCanvas(), export_matrix, 2.0f, bitmapBounds(), false );
      pdf.finishPage( page );
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      pdf.writeTo( bytes );
      assertTrue( "PDF canvas produced an unexpectedly empty document", bytes.size() > 500 );
    } finally {
      pdf.close();
    }
  }

  @Test public void productionScrapRenderer_meetsPlannedFieldThresholds()
  {
    PerformanceMetrics distributed = measureProductionPair( false );
    PerformanceMetrics dense = measureProductionPair( true );
    String metrics = String.format( Locale.US,
        "BREAKDOWN_PRODUCTION_METRICS distributed_control_ms=%.3f distributed_stack_ms=%.3f distributed_ratio=%.3f distributed_build_ms=%.3f "
      + "dense_control_ms=%.3f dense_stack_ms=%.3f dense_ratio=%.3f dense_build_ms=%.3f\n",
        distributed.controlMs(), distributed.stackMs(), distributed.ratio(), distributed.buildMs(),
        dense.controlMs(), dense.stackMs(), dense.ratio(), dense.buildMs() );
    Bundle status = new Bundle();
    status.putString( "stream", metrics );
    InstrumentationRegistry.getInstrumentation().sendStatus( 0, status );
    System.out.print( metrics );

    assertTrue( "Distributed production render exceeds 500 ms: " + distributed.stackMs(), distributed.stackMs() < 500.0 );
    assertTrue( "Dense production render exceeds 500 ms: " + dense.stackMs(), dense.stackMs() < 500.0 );
    assertTrue( "Distributed production render exceeds 2x control: " + distributed.ratio(), distributed.ratio() <= 2.0 );
    assertTrue( "Dense production render exceeds 2x control: " + dense.ratio(), dense.ratio() <= 2.0 );
    assertTrue( "Distributed production cache build exceeds 500 ms: " + distributed.buildMs(), distributed.buildMs() < 500.0 );
    assertTrue( "Dense production cache build exceeds 500 ms: " + dense.buildMs(), dense.buildMs() < 500.0 );
  }

  private DrawingPointPath rock( float x, float y, int color )
  {
    DrawingPointPath point = new DrawingPointPath( mBoulder, x, y, PointScale.SCALE_M, 0 );
    point.setSketchBrushStyle( SketchBrushStyle.of( 2.0f, 1.0f, 1.0f, color & 0x00ffffff ) );
    assertTrue( point.setSketchAffineTransform( SketchAffineTransform.create( 12.0f, 0.0f, 0.0f, 12.0f ) ) );
    assertTrue( point.hasSketchOcclusion() );
    return point;
  }

  private DrawingPointPath performanceRock( float x, float y, float scale, boolean occluding )
  {
    DrawingPointPath point;
    if ( occluding ) {
      point = new DrawingPointPath( mBoulder, x, y, PointScale.SCALE_M, 0 );
    } else {
      String options = SketchAffineTransformCodec.storeInOptions( null, SketchAffineTransform.identity() );
      point = new DrawingPointPath( mBoulder, x, y, PointScale.SCALE_M, null, options, 0 );
    }
    point.setSketchBrushStyle( SketchBrushStyle.of( 2.0f, 1.0f, 1.0f ) );
    assertTrue( point.setSketchAffineTransform( SketchAffineTransform.create( scale, 0.0f, 0.0f, scale ) ) );
    assertEquals( occluding, point.hasSketchOcclusion() );
    return point;
  }

  private Scrap performanceScene( boolean dense, boolean occluding )
  {
    Scrap scrap = new Scrap( 0, dense ? "breakdown-perf-dense" : "breakdown-perf-distributed" );
    int count = dense ? 50 : 500;
    for ( int i = 0; i < count; ++i ) {
      float x;
      float y;
      float scale;
      if ( dense ) {
        x = 320.0f + ( i % 5 ) * 1.5f;
        y = 260.0f + ( i / 5 ) * 1.2f;
        scale = 10.0f;
      } else {
        x = 24.0f + ( i % 25 ) * 26.0f;
        y = 24.0f + ( i / 25 ) * 25.0f;
        scale = 2.0f;
      }
      scrap.addCommand( performanceRock( x, y, scale, occluding ) );
    }
    return scrap;
  }

  private PerformanceMetrics measureProductionPair( boolean dense )
  {
    Scrap control = performanceScene( dense, false );
    Scrap stack = performanceScene( dense, true );
    Bitmap control_bitmap = Bitmap.createBitmap( 700, 550, Bitmap.Config.ARGB_8888 );
    Bitmap stack_bitmap = Bitmap.createBitmap( 700, 550, Bitmap.Config.ARGB_8888 );
    Canvas control_canvas = new Canvas( control_bitmap );
    Canvas stack_canvas = new Canvas( stack_bitmap );
    RectF bounds = new RectF( 0.0f, 0.0f, 700.0f, 550.0f );
    Matrix identity = new Matrix();

    long build_start = System.nanoTime();
    stack.drawAll( stack_canvas, identity, 1.0f, bounds, false );
    long build_ns = System.nanoTime() - build_start;
    for ( int i = 0; i < 4; ++i ) {
      control.drawAll( control_canvas, identity, 1.0f, bounds, false );
      stack.drawAll( stack_canvas, identity, 1.0f, bounds, false );
    }

    long[] control_samples = new long[PERF_SAMPLES];
    long[] stack_samples = new long[PERF_SAMPLES];
    for ( int i = 0; i < PERF_SAMPLES; ++i ) {
      if ( ( i & 1 ) == 0 ) {
        control_samples[i] = timedDraw( control, control_canvas, identity, bounds );
        stack_samples[i] = timedDraw( stack, stack_canvas, identity, bounds );
      } else {
        stack_samples[i] = timedDraw( stack, stack_canvas, identity, bounds );
        control_samples[i] = timedDraw( control, control_canvas, identity, bounds );
      }
    }
    Arrays.sort( control_samples );
    Arrays.sort( stack_samples );
    PerformanceMetrics result = new PerformanceMetrics(
        median( control_samples ), median( stack_samples ), build_ns );
    control_bitmap.recycle();
    stack_bitmap.recycle();
    return result;
  }

  private static long timedDraw( Scrap scrap, Canvas canvas, Matrix matrix, RectF bounds )
  {
    long start = System.nanoTime();
    scrap.drawAll( canvas, matrix, 1.0f, bounds, false );
    return System.nanoTime() - start;
  }

  private static double median( long[] sorted )
  {
    int middle = sorted.length / 2;
    return ( sorted.length % 2 == 0 )
        ? 0.5 * ( sorted[middle - 1] + sorted[middle] )
        : sorted[middle];
  }

  private static Bitmap renderDirect( DrawingPointPath oldRock, DrawingPointPath newRock )
  {
    Bitmap bitmap = blankBitmap();
    Canvas canvas = new Canvas( bitmap );
    RectF bbox = bitmapBounds();
    oldRock.draw( canvas, new Matrix(), 1.0f, bbox );
    newRock.draw( canvas, new Matrix(), 1.0f, bbox );
    return bitmap;
  }

  private static Bitmap renderScrap( Scrap scrap )
  {
    Bitmap bitmap = blankBitmap();
    scrap.drawAll( new Canvas( bitmap ), new Matrix(), 1.0f, bitmapBounds(), false );
    return bitmap;
  }

  private static Bitmap blankBitmap()
  {
    Bitmap bitmap = Bitmap.createBitmap( 260, 200, Bitmap.Config.ARGB_8888 );
    bitmap.eraseColor( Color.BLACK );
    return bitmap;
  }

  private static RectF bitmapBounds() { return new RectF( 0.0f, 0.0f, 260.0f, 200.0f ); }

  private static float silhouetteFillRatio( Path source, float scale )
  {
    Path path = new Path( source );
    Matrix matrix = new Matrix();
    matrix.setScale( scale, scale );
    path.transform( matrix );
    RectF bounds = new RectF();
    path.computeBounds( bounds, true );
    int left = (int)Math.floor( bounds.left );
    int top = (int)Math.floor( bounds.top );
    int right = (int)Math.ceil( bounds.right );
    int bottom = (int)Math.ceil( bounds.bottom );
    Region region = new Region();
    region.setPath( path, new Region( left, top, right, bottom ) );
    RegionIterator iterator = new RegionIterator( region );
    android.graphics.Rect rectangle = new android.graphics.Rect();
    long area = 0;
    while ( iterator.next( rectangle ) ) area += (long)rectangle.width() * rectangle.height();
    long box_area = (long)( right - left ) * ( bottom - top );
    return ( box_area <= 0 ) ? 0.0f : area / (float)box_area;
  }

  private static int countNonTransparent( Bitmap bitmap )
  {
    int count = 0;
    for ( int y = 0; y < bitmap.getHeight(); ++y ) {
      for ( int x = 0; x < bitmap.getWidth(); ++x ) {
        if ( Color.alpha( bitmap.getPixel( x, y ) ) > 0 ) ++count;
      }
    }
    return count;
  }

  private static int countDominantInMask( Bitmap bitmap, Path maskPath, int channel )
  {
    RectF bounds = new RectF();
    maskPath.computeBounds( bounds, true );
    Region clip = new Region( 0, 0, bitmap.getWidth(), bitmap.getHeight() );
    Region mask = new Region();
    mask.setPath( maskPath, clip );
    int count = 0;
    for ( int y = Math.max( 0, (int)Math.floor( bounds.top ) ); y < Math.min( bitmap.getHeight(), (int)Math.ceil( bounds.bottom ) ); ++y ) {
      for ( int x = Math.max( 0, (int)Math.floor( bounds.left ) ); x < Math.min( bitmap.getWidth(), (int)Math.ceil( bounds.right ) ); ++x ) {
        if ( ! mask.contains( x, y ) ) continue;
        int color = bitmap.getPixel( x, y );
        int red = Color.red( color );
        int green = Color.green( color );
        int blue = Color.blue( color );
        if ( channel == Color.RED && red > green * 2 && red > blue * 2 && red > 64 ) ++count;
        if ( channel == Color.BLUE && blue > red * 2 && blue > green && blue > 64 ) ++count;
      }
    }
    return count;
  }

  private static class ProbeLine extends DrawingPath
  {
    ProbeLine( float x0, float y0, float x1, float y1, int color )
    {
      super( DrawingPath.DRAWING_PATH_POINT, null, 0 );
      mPath = new Path();
      mPath.moveTo( x0, y0 );
      mPath.lineTo( x1, y1 );
      mPaint = new Paint( Paint.ANTI_ALIAS_FLAG );
      mPaint.setColor( color );
      mPaint.setStyle( Paint.Style.STROKE );
      mPaint.setStrokeWidth( 2.0f );
      left = Math.min( x0, x1 ) - 1.0f;
      right = Math.max( x0, x1 ) + 1.0f;
      top = Math.min( y0, y1 ) - 1.0f;
      bottom = Math.max( y0, y1 ) + 1.0f;
      cx = 0.5f * ( x0 + x1 );
      cy = 0.5f * ( y0 + y1 );
    }
  }

  private static final class PerformanceMetrics
  {
    private final double mControlNs;
    private final double mStackNs;
    private final long mBuildNs;

    PerformanceMetrics( double control_ns, double stack_ns, long build_ns )
    {
      mControlNs = control_ns;
      mStackNs = stack_ns;
      mBuildNs = build_ns;
    }

    double controlMs() { return mControlNs / 1.0e6; }
    double stackMs() { return mStackNs / 1.0e6; }
    double buildMs() { return mBuildNs / 1.0e6; }
    double ratio() { return mStackNs / Math.max( 1.0, mControlNs ); }
  }
}
