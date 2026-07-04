package com.topodroid.TDX;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

/** World-space ink model invariants:
 *  - dash/pattern repeat count along a line is independent of zoom (patterns are scene geometry)
 *  - ink thickness on screen scales linearly with zoom (pure magnification)
 */
@RunWith( AndroidJUnit4.class )
@LargeTest
public class LinePatternInstrumentedTest
{
  private static final int WIDTH = 1600;
  private static final int HEIGHT = 260;
  private static final float LINE_LENGTH = 180.0f;
  private static final float LINE_Y = 32.0f;
  private static final float ORIGIN_X = 40.0f;
  private static final float ORIGIN_Y = 60.0f;

  @Before
  public void setUp()
  {
    BrushManager.reloadLineLibrary( InstrumentationRegistry.getInstrumentation().getTargetContext().getResources() );
  }

  @Test
  public void dashRepeatCountIsStableAcrossZoom() throws Exception
  {
    // At 1x the built-in section dash is sub-pixel and antialiasing collapses
    // the scanline into a mostly continuous stroke. Use drawable zoom levels.
    int zoom3Runs = renderSectionLineAndCountRuns( 3.0f );
    int zoom4Runs = renderSectionLineAndCountRuns( 4.0f );

    assertEquals( "Dashed line repeat count changed across zoom", zoom3Runs, zoom4Runs, 1 );
  }

  @Test
  public void inkThicknessScalesLinearlyWithZoom() throws Exception
  {
    int lineType = BrushManager.getLineIndexByThName( SymbolLibrary.WALL );
    assertTrue( "Wall line symbol is missing", lineType >= 0 );

    int zoom1Thickness = renderPlainLineAndMeasureThickness( lineType, 1.0f );
    int zoom4Thickness = renderPlainLineAndMeasureThickness( lineType, 4.0f );

    assertTrue( "Ink thickness should magnify with zoom: " + zoom1Thickness + " -> " + zoom4Thickness,
                zoom4Thickness >= 3 * zoom1Thickness && zoom4Thickness <= 5 * zoom1Thickness + 2 );
  }

  @Test
  public void sketchCarrierEffect_drawsContinuousCurvedCarrier()
  {
    Path fallback = rectanglePath( 0.0f, 0.0f, 20.0f, 1.0f, false );
    Path fallbackRev = rectanglePath( 0.0f, 0.0f, 20.0f, 1.0f, true );
    LineSymbolEffect effect = new LineSymbolEffect( fallback, fallbackRev, 20.0f, null );
    ArrayList< LineSymbolEffect.Carrier > carriers = new ArrayList<>();
    carriers.add( new LineSymbolEffect.Carrier( 0.0f, 4.0f ) );
    effect.setSketchEffect( new Path(), new Path(), carriers );

    Path line = new Path();
    line.moveTo( 40.0f, 130.0f );
    line.cubicTo( 95.0f, 40.0f, 175.0f, 220.0f, 240.0f, 130.0f );

    Bitmap bitmap = Bitmap.createBitmap( 300, 260, Bitmap.Config.ARGB_8888 );
    bitmap.eraseColor( Color.BLACK );
    Canvas canvas = new Canvas( bitmap );
    assertTrue( "Sketch carrier effect did not draw", effect.draw( canvas, line, whitePaint(), false ) );

    PathMeasure measure = new PathMeasure( line, false );
    float[] pos = new float[2];
    float[] tan = new float[2];
    float length = measure.getLength();
    for ( float distance = 6.0f; distance < length - 6.0f; distance += 8.0f ) {
      assertTrue( measure.getPosTan( distance, pos, tan ) );
      float mag = (float)Math.sqrt( tan[0] * tan[0] + tan[1] * tan[1] );
      float nx = -tan[1] / mag;
      float ny =  tan[0] / mag;
      int x = Math.round( pos[0] + nx * 2.0f );
      int y = Math.round( pos[1] + ny * 2.0f );
      assertTrue( "Carrier gap near distance " + distance, hasForegroundNear( bitmap, x, y, 3 ) );
    }
  }

  @Test
  public void sketchDashedEffect_stampsOncePerDashOnSegment()
  {
    Path fallback = rectanglePath( 0.0f, 0.0f, 13.0f, 1.0f, false );
    Path fallbackRev = rectanglePath( 0.0f, 0.0f, 13.0f, 1.0f, true );
    LineSymbolEffect effect = new LineSymbolEffect( fallback, fallbackRev, 13.0f, new float[] { 15.0f, 5.0f } );
    ArrayList< LineSymbolEffect.Carrier > carriers = new ArrayList<>();
    carriers.add( new LineSymbolEffect.Carrier( 0.0f, 1.0f ) );
    effect.setSketchEffect( rectanglePath( 6.0f, 8.0f, 7.0f, 12.0f, false ),
                            rectanglePath( 6.0f, 8.0f, 7.0f, 12.0f, true ),
                            carriers );

    Path line = new Path();
    line.moveTo( 40.0f, 70.0f );
    line.lineTo( 140.0f, 70.0f );

    Bitmap bitmap = Bitmap.createBitmap( 200, 120, Bitmap.Config.ARGB_8888 );
    bitmap.eraseColor( Color.BLACK );
    Canvas canvas = new Canvas( bitmap );
    assertTrue( "Sketch dashed effect did not draw", effect.draw( canvas, line, whitePaint(), false ) );

    assertEquals( "Dashed sketch stamps should reset once per dash-on segment",
                  5, countForegroundRuns( bitmap, 80 ) );
  }

  @Test
  public void sketchDashedEffect_anchorsStampToCurvedLine()
  {
    Path fallback = rectanglePath( 0.0f, 0.0f, 15.0f, 1.0f, false );
    Path fallbackRev = rectanglePath( 0.0f, 0.0f, 15.0f, 1.0f, true );
    LineSymbolEffect effect = new LineSymbolEffect( fallback, fallbackRev, 15.0f, new float[] { 15.0f, 5.0f } );
    ArrayList< LineSymbolEffect.Carrier > carriers = new ArrayList<>();
    carriers.add( new LineSymbolEffect.Carrier( 0.0f, 1.0f ) );
    effect.setSketchEffect( rectanglePath( 7.0f, 1.0f, 8.0f, 18.0f, false ),
                            rectanglePath( 7.0f, 1.0f, 8.0f, 18.0f, true ),
                            carriers );

    Path line = new Path();
    line.arcTo( new RectF( 60.0f, 60.0f, 100.0f, 100.0f ), 180.0f, 90.0f );

    Bitmap bitmap = Bitmap.createBitmap( 140, 140, Bitmap.Config.ARGB_8888 );
    bitmap.eraseColor( Color.BLACK );
    Canvas canvas = new Canvas( bitmap );
    assertTrue( "Sketch curved dashed effect did not draw", effect.draw( canvas, line, whitePaint(), false ) );

    PathMeasure measure = new PathMeasure( line, false );
    float[] pos = new float[2];
    float[] tan = new float[2];
    assertTrue( measure.getPosTan( 7.5f, pos, tan ) );
    float mag = (float)Math.sqrt( tan[0] * tan[0] + tan[1] * tan[1] );
    float nx = -tan[1] / mag;
    float ny =  tan[0] / mag;
    int x = Math.round( pos[0] + nx * 14.0f );
    int y = Math.round( pos[1] + ny * 14.0f );
    assertTrue( "Curved stamp should follow the tangent at its local anchor",
                hasForegroundNear( bitmap, x, y, 2 ) );
  }

  private int renderSectionLineAndCountRuns( float zoom )
  {
    int lineType = BrushManager.getLineIndexByThName( SymbolLibrary.SECTION );
    assertTrue( "Section line symbol is missing", lineType >= 0 );
    assertTrue( "Section line should have a pattern effect", BrushManager.hasLinePathEffect( lineType ) );

    DrawingLinePath line = new DrawingLinePath( lineType, 0 );
    line.addStartPoint( 0.0f, LINE_Y );
    line.addPoint( LINE_LENGTH, LINE_Y );
    line.computeUnitNormal();

    Matrix matrix = new Matrix();
    matrix.setScale( zoom, zoom );
    matrix.postTranslate( ORIGIN_X, ORIGIN_Y );

    Bitmap bitmap = Bitmap.createBitmap( WIDTH, HEIGHT, Bitmap.Config.ARGB_8888 );
    bitmap.eraseColor( Color.BLACK );
    Canvas canvas = new Canvas( bitmap );
    line.draw( canvas, matrix, 1.0f / zoom, new RectF( -10.0f, -10.0f, LINE_LENGTH + 10.0f, LINE_Y + 10.0f ) );

    float[] points = new float[] { 0.0f, LINE_Y };
    matrix.mapPoints( points );
    int y = Math.max( 0, Math.min( HEIGHT - 1, Math.round( points[1] ) ) );
    return countForegroundRuns( bitmap, y );
  }

  private int renderPlainLineAndMeasureThickness( int lineType, float zoom )
  {
    DrawingLinePath line = new DrawingLinePath( lineType, 0 );
    line.addStartPoint( 0.0f, LINE_Y );
    line.addPoint( LINE_LENGTH, LINE_Y );
    line.computeUnitNormal();

    Matrix matrix = new Matrix();
    matrix.setScale( zoom, zoom );
    matrix.postTranslate( ORIGIN_X, ORIGIN_Y );

    Bitmap bitmap = Bitmap.createBitmap( WIDTH, HEIGHT, Bitmap.Config.ARGB_8888 );
    bitmap.eraseColor( Color.BLACK );
    Canvas canvas = new Canvas( bitmap );
    line.draw( canvas, matrix, 1.0f / zoom, new RectF( -10.0f, -10.0f, LINE_LENGTH + 10.0f, LINE_Y + 10.0f ) );

    float[] points = new float[] { LINE_LENGTH * 0.5f, LINE_Y };
    matrix.mapPoints( points );
    int x = Math.max( 0, Math.min( WIDTH - 1, Math.round( points[0] ) ) );
    return countVerticalThickness( bitmap, x );
  }

  private int countVerticalThickness( Bitmap bitmap, int x )
  {
    int best = 0;
    int run = 0;
    for ( int y = 0; y < bitmap.getHeight(); ++y ) {
      if ( bitmap.getPixel( x, y ) != Color.BLACK ) {
        ++run;
        if ( run > best ) best = run;
      } else {
        run = 0;
      }
    }
    return best;
  }

  private int countForegroundRuns( Bitmap bitmap, int y )
  {
    int runs = 0;
    boolean inRun = false;
    for ( int x = 0; x < bitmap.getWidth(); ++x ) {
      boolean foreground = bitmap.getPixel( x, y ) != Color.BLACK;
      if ( foreground && ! inRun ) ++runs;
      inRun = foreground;
    }
    return runs;
  }

  private static Paint whitePaint()
  {
    Paint paint = new Paint();
    paint.setColor( Color.WHITE );
    paint.setStyle( Paint.Style.STROKE );
    paint.setStrokeWidth( 1.0f ); // pattern unit: test geometry is authored 1:1
    return paint;
  }

  private static Path rectanglePath( float x0, float y0, float x1, float y1, boolean reversed )
  {
    float yy0 = reversed ? -y0 : y0;
    float yy1 = reversed ? -y1 : y1;
    Path path = new Path();
    path.moveTo( x0, yy0 );
    path.lineTo( x1, yy0 );
    path.lineTo( x1, yy1 );
    path.lineTo( x0, yy1 );
    path.lineTo( x0, yy0 );
    return path;
  }

  private static boolean hasForegroundNear( Bitmap bitmap, int cx, int cy, int radius )
  {
    int xmin = Math.max( 0, cx - radius );
    int xmax = Math.min( bitmap.getWidth() - 1, cx + radius );
    int ymin = Math.max( 0, cy - radius );
    int ymax = Math.min( bitmap.getHeight() - 1, cy + radius );
    for ( int y = ymin; y <= ymax; ++y ) {
      for ( int x = xmin; x <= xmax; ++x ) {
        if ( bitmap.getPixel( x, y ) != Color.BLACK ) return true;
      }
    }
    return false;
  }
}
