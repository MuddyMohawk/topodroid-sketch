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
  private static final float LINE_Y = 32.5f;
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
    int zoom1Runs = renderRepeatedPatternAndCountRuns( 1.0f );
    int zoom4Runs = renderRepeatedPatternAndCountRuns( 4.0f );

    assertEquals( "Repeated line stamp count changed across zoom", zoom1Runs, zoom4Runs, 1 );
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
  public void sectionFacingDirectionFollowsVisibleTick()
  {
    int lineType = BrushManager.getLineIndexByThName( SymbolLibrary.SECTION );
    assertTrue( "Section line symbol is missing", lineType >= 0 );

    DrawingLinePath line = new DrawingLinePath( lineType, 0 );
    line.addStartPoint( 0.0f, 0.0f );
    line.addPoint( 100.0f, 0.0f );
    line.computeUnitNormal();

    assertEquals( "A left-to-right section tick should point upward",
                  -1.0f, line.sectionDirectionY(), 0.001f );
    assertEquals( "A left-to-right section should face the upward tick",
                  0.0f, DrawingWindow.sectionAzimuthFromTick( line ), 0.001f );

    line.setReversed( true );
    assertEquals( "Reversing a section line should reverse its visible tick",
                  1.0f, line.sectionDirectionY(), 0.001f );
    assertEquals( "Reversing a section line should reverse the xsection azimuth",
                  180.0f, DrawingWindow.sectionAzimuthFromTick( line ), 0.001f );
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

  @Test
  public void terminalArrow_drawsOnceAtEndingTangentAndReversesEndpoint()
  {
    LineSymbolEffect effect = terminalArrowEffect( null );
    Path line = new Path();
    line.moveTo( 20.0f, 60.0f );
    line.lineTo( 180.0f, 60.0f );

    Bitmap forward = Bitmap.createBitmap( 220, 120, Bitmap.Config.ARGB_8888 );
    forward.eraseColor( Color.BLACK );
    assertTrue( "Terminal arrow effect did not draw",
                effect.draw( new Canvas( forward ), line, whitePaint(), false ) );
    assertTrue( "Forward arrowhead must terminate at the final point",
                hasForegroundNear( forward, 179, 60, 1 ) );
    assertTrue( "Forward arrowhead wing is missing",
                hasForegroundNear( forward, 172, 56, 1 ) );
    assertTrue( "Terminal arrowhead must not repeat through the middle",
                ! hasForegroundNear( forward, 100, 56, 1 ) );
    assertTrue( "Terminal arrowhead must not appear at the starting point",
                ! hasForegroundNear( forward, 28, 56, 1 ) );

    Bitmap reversed = Bitmap.createBitmap( 220, 120, Bitmap.Config.ARGB_8888 );
    reversed.eraseColor( Color.BLACK );
    assertTrue( "Reversed terminal arrow effect did not draw",
                effect.draw( new Canvas( reversed ), line, whitePaint(), true ) );
    assertTrue( "Reversing must move and turn the arrowhead to the starting point",
                hasForegroundNear( reversed, 28, 56, 1 ) );
    assertTrue( "Reversed terminal arrowhead must leave the original end",
                ! hasForegroundNear( reversed, 172, 56, 1 ) );

    forward.recycle();
    reversed.recycle();
  }

  @Test
  public void terminalArrow_dashedCarrierRetainsRhythmAndScalesWithWeight()
  {
    LineSymbolEffect effect = terminalArrowEffect( new float[] { 6.0f, 4.0f } );
    Path line = new Path();
    line.moveTo( 20.0f, 60.0f );
    line.lineTo( 180.0f, 60.0f );

    Bitmap standard = Bitmap.createBitmap( 220, 120, Bitmap.Config.ARGB_8888 );
    standard.eraseColor( Color.BLACK );
    assertTrue( effect.draw( new Canvas( standard ), line, whitePaint(), false ) );
    assertTrue( "Dashed arrow carrier must retain a dash-on interval",
                hasForegroundNear( standard, 23, 60, 0 ) );
    assertEquals( "Dashed arrow carrier must retain a dash-off interval",
                  Color.BLACK, standard.getPixel( 28, 60 ) );
    assertTrue( "Dashed carrier must still receive one terminal arrowhead",
                hasForegroundNear( standard, 172, 56, 1 ) );

    Paint thickPaint = whitePaint();
    thickPaint.setStrokeWidth( 2.0f );
    Bitmap thick = Bitmap.createBitmap( 240, 140, Bitmap.Config.ARGB_8888 );
    thick.eraseColor( Color.BLACK );
    assertTrue( effect.draw( new Canvas( thick ), line, thickPaint, false ) );
    assertTrue( "Arrowhead length and width must scale with line weight",
                hasForegroundNear( thick, 164, 52, 1 ) );

    standard.recycle();
    thick.recycle();
  }

  @Test
  public void terminalArrow_insetStopsCarrierBeforeTheTip()
  {
    LineSymbolEffect effect = terminalArrowEffect( null );
    assertEquals( "Terminal carrier join must use the arrow notch", 6.0f, effect.terminalInset(), 0.001f );

    Path line = new Path();
    line.moveTo( 20.0f, 80.0f );
    line.lineTo( 180.0f, 80.0f );
    Paint paint = whitePaint();
    paint.setStrokeWidth( 8.0f );
    Bitmap bitmap = Bitmap.createBitmap( 220, 160, Bitmap.Config.ARGB_8888 );
    bitmap.eraseColor( Color.BLACK );
    assertTrue( effect.draw( new Canvas( bitmap ), line, paint, false ) );

    assertEquals( "A full-width carrier cap must not protrude around the arrow tip",
                  Color.BLACK, bitmap.getPixel( 179, 83 ) );
    assertTrue( "The filled arrow tip itself must remain visible",
                hasForegroundNear( bitmap, 179, 80, 1 ) );
    bitmap.recycle();
  }

  @Test
  public void gapStamp_drawsFourDotsInsideEverySolidCarrierBreak()
  {
    Path dots = new Path();
    dots.addCircle( 1.5f, 0.0f, 0.5f, Path.Direction.CCW );
    dots.addCircle( 4.5f, 0.0f, 0.5f, Path.Direction.CCW );
    dots.addCircle( 7.5f, 0.0f, 0.5f, Path.Direction.CCW );
    dots.addCircle( 10.5f, 0.0f, 0.5f, Path.Direction.CCW );
    Path arrow = terminalArrowPath();
    ArrayList< LineSymbolEffect.Carrier > carriers = new ArrayList<>();
    carriers.add( new LineSymbolEffect.Carrier( -0.5f, 0.5f ) );
    LineSymbolEffect effect = new LineSymbolEffect( new Path(), new Path(), 0.0f,
                                                    new float[] { 18.0f, 12.0f } );
    effect.setSketchEffect( new Path(), new Path(), dots, dots, arrow, arrow,
                            carriers, false, true, 6.0f );

    Path line = new Path();
    line.moveTo( 20.0f, 60.0f );
    line.lineTo( 180.0f, 60.0f );
    Bitmap bitmap = Bitmap.createBitmap( 220, 120, Bitmap.Config.ARGB_8888 );
    bitmap.eraseColor( Color.BLACK );
    assertTrue( effect.draw( new Canvas( bitmap ), line, whitePaint(), false ) );

    assertEquals( "Every 12-unit carrier break must contain exactly four solid dots",
                  4, countForegroundRuns( bitmap, 60, 38, 50 ) );
    assertEquals( "The four-dot interruption must repeat with the declared cycle",
                  4, countForegroundRuns( bitmap, 60, 68, 80 ) );
    assertTrue( "The intermittent dotted line must retain its terminal arrow",
                hasForegroundNear( bitmap, 172, 56, 1 ) );
    bitmap.recycle();
  }

  @Test
  public void slopeEffects_keepUniformBaseAndApplySymmetricCosinePeak()
  {
    Bitmap uniform = renderSlopeEffect( false, 1.0f, false, 168.0f );
    Bitmap fan = renderSlopeEffect( true, 3.0f, false, 168.0f );
    int baseline = 140;

    int uniformEnd = foregroundExtent( uniform, 22, baseline, true );
    int uniformMiddle = foregroundExtent( uniform, 102, baseline, true );
    assertTrue( "Uniform Slope must render its base hachure", uniformEnd >= 5 );
    assertEquals( "Uniform Slope hachures must keep a constant length", uniformEnd, uniformMiddle, 1 );

    int fanStart = foregroundExtent( fan, 22, baseline, true );
    int fanMiddle = foregroundExtent( fan, 102, baseline, true );
    int fanEnd = foregroundExtent( fan, 186, baseline, true );
    assertEquals( "Slope fan ends must remain symmetric", fanStart, fanEnd, 1 );
    assertTrue( "Slope fan midpoint must be approximately three times the base: "
        + fanStart + " -> " + fanMiddle,
        fanMiddle >= fanStart * 2.5f && fanMiddle <= fanStart * 3.5f );

    uniform.recycle();
    fan.recycle();
  }

  @Test
  public void slopeFan_supportsOneAndTenTimesAndReversal()
  {
    Bitmap one = renderSlopeEffect( true, 1.0f, false, 168.0f );
    Bitmap ten = renderSlopeEffect( true, 10.0f, false, 168.0f );
    Bitmap reversed = renderSlopeEffect( true, 3.0f, true, 168.0f );
    int baseline = 140;

    int oneMiddle = foregroundExtent( one, 102, baseline, true );
    int tenMiddle = foregroundExtent( ten, 102, baseline, true );
    assertTrue( "A 10x peak must strongly exceed a 1x peak: " + oneMiddle + " -> " + tenMiddle,
                tenMiddle >= oneMiddle * 8.5f );
    assertEquals( "Forward fan should not draw on the reverse side", 0,
                  foregroundExtent( one, 102, baseline, false ) );
    assertTrue( "Reversing Slope fan must mirror its hachures below the path",
                foregroundExtent( reversed, 102, baseline, false ) >= 14 );
    assertEquals( "Reversed fan should not remain above the path", 0,
                  foregroundExtent( reversed, 102, baseline, true ) );

    one.recycle();
    ten.recycle();
    reversed.recycle();
  }

  @Test
  public void slopeFan_shortLine_stampsOnceAtPeak()
  {
    Bitmap shortFan = renderSlopeEffect( true, 3.0f, false, 2.0f );
    int extent = foregroundExtent( shortFan, 21, 140, true );
    assertTrue( "A sub-repeat Slope fan line must retain one midpoint hachure", extent >= 14 );
    shortFan.recycle();
  }

  @Test
  public void slopeFanEffect_expandsLineCullingForLargePeaks()
  {
    int lineType = BrushManager.getLineIndexByThName( SymbolLibrary.SLOPE_FAN );
    assertTrue( "Slope fan line symbol is missing", lineType >= 0 );
    DrawingLinePath line = new DrawingLinePath( lineType, 0 );
    line.addStartPoint( 20.0f, 80.0f );
    line.addPoint( 188.0f, 80.0f );
    line.computeUnitNormal();
    line.setSlopeFanPeak( 10.0f );

    Paint paint = BrushManager.getLinePaint( lineType, false );
    LineSymbolEffect effect = BrushManager.getLineEffect( lineType );
    float radius = effect.samplePatternRadius( paint.getStrokeWidth(), false, 10.0f );
    RectF hachureOnlyBox = new RectF( 0.0f, 80.0f - radius, 220.0f, 80.0f - radius + 2.0f );

    Bitmap bitmap = Bitmap.createBitmap( 220, 120, Bitmap.Config.ARGB_8888 );
    bitmap.eraseColor( Color.BLACK );
    line.draw( new Canvas( bitmap ), new Matrix(), 1.0f, hachureOnlyBox );
    assertTrue( "Effect-aware culling must draw when only a long hachure intersects the viewport",
                countForegroundPixels( bitmap ) > 0 );
    bitmap.recycle();
  }

  private int renderRepeatedPatternAndCountRuns( float zoom )
  {
    Path stamp = new Path();
    stamp.moveTo( 0.0f, -4.0f );
    stamp.lineTo( 0.0f, 4.0f );
    LineSymbolEffect effect = new LineSymbolEffect( stamp, stamp, 10.0f, null );
    effect.setSketchEffect( stamp, stamp, new ArrayList< LineSymbolEffect.Carrier >() );

    Path line = new Path();
    line.moveTo( 0.0f, LINE_Y );
    line.lineTo( LINE_LENGTH, LINE_Y );

    Matrix matrix = new Matrix();
    matrix.setScale( zoom, zoom );
    matrix.postTranslate( ORIGIN_X, ORIGIN_Y );

    Bitmap bitmap = Bitmap.createBitmap( WIDTH, HEIGHT, Bitmap.Config.ARGB_8888 );
    bitmap.eraseColor( Color.BLACK );
    Canvas canvas = new Canvas( bitmap );
    int save = canvas.save();
    canvas.concat( matrix );
    effect.draw( canvas, line, whitePaint(), false, 1.0f / zoom );
    canvas.restoreToCount( save );

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

  private int countForegroundRuns( Bitmap bitmap, int y, int xmin, int xmax )
  {
    int runs = 0;
    boolean inRun = false;
    for ( int x = Math.max( 0, xmin ); x < Math.min( bitmap.getWidth(), xmax ); ++x ) {
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

  private static LineSymbolEffect terminalArrowEffect( float[] dash )
  {
    Path arrow = terminalArrowPath();
    ArrayList< LineSymbolEffect.Carrier > carriers = new ArrayList<>();
    carriers.add( new LineSymbolEffect.Carrier( -0.5f, 0.5f ) );
    LineSymbolEffect effect = new LineSymbolEffect( new Path(), new Path(), 0.0f, dash );
    effect.setSketchEffect( arrow, arrow, carriers, false, true, 6.0f );
    return effect;
  }

  private static Path terminalArrowPath()
  {
    Path arrow = new Path();
    arrow.moveTo( -8.0f, -4.0f );
    arrow.lineTo( 0.0f, 0.0f );
    arrow.lineTo( -8.0f, 4.0f );
    arrow.lineTo( -6.0f, 0.0f );
    arrow.close();
    return arrow;
  }

  private Bitmap renderSlopeEffect( boolean envelope, float peak, boolean reversed, float length )
  {
    Path fallback = rectanglePath( 1.6f, 0.0f, 2.6f, -5.1f, false );
    Path fallbackRev = rectanglePath( 1.6f, 0.0f, 2.6f, -5.1f, true );
    LineSymbolEffect effect = new LineSymbolEffect( fallback, fallbackRev, 4.2f, null );
    effect.setSketchEffect( fallback, fallbackRev, new ArrayList< LineSymbolEffect.Carrier >() );
    if ( envelope ) effect.setCosineEnvelope( 3.0f, 1.0f, 10.0f );

    Path line = new Path();
    line.moveTo( 20.0f, 140.0f );
    line.lineTo( 20.0f + length, 140.0f );
    Bitmap bitmap = Bitmap.createBitmap( 220, 220, Bitmap.Config.ARGB_8888 );
    bitmap.eraseColor( Color.BLACK );
    assertTrue( "Slope effect did not draw", effect.draw( new Canvas( bitmap ), line, whitePaint(), reversed, 1.0f, peak ) );
    return bitmap;
  }

  private int foregroundExtent( Bitmap bitmap, int x, int baseline, boolean above )
  {
    int extent = 0;
    int y0 = above ? 0 : baseline + 1;
    int y1 = above ? baseline - 1 : bitmap.getHeight() - 1;
    for ( int y = y0; y <= y1; ++y ) {
      boolean foreground = false;
      for ( int xx = Math.max( 0, x - 1 ); xx <= Math.min( bitmap.getWidth() - 1, x + 1 ); ++xx ) {
        if ( bitmap.getPixel( xx, y ) != Color.BLACK ) {
          foreground = true;
          break;
        }
      }
      if ( foreground ) extent = Math.max( extent, Math.abs( baseline - y ) );
    }
    return extent;
  }

  private int countForegroundPixels( Bitmap bitmap )
  {
    int count = 0;
    for ( int y = 0; y < bitmap.getHeight(); ++y ) {
      for ( int x = 0; x < bitmap.getWidth(); ++x ) {
        if ( bitmap.getPixel( x, y ) != Color.BLACK ) ++count;
      }
    }
    return count;
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
