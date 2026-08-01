/* @file SymbolPreviewRenderer.java
 *
 * @brief Canvas-accurate symbol previews rendered through production drawing paths
 * --------------------------------------------------------
 * Copyright This software is distributed under GPL-3.0 or later
 * See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

import com.topodroid.prefs.TDSetting;
import com.topodroid.types.PointScale;
import com.topodroid.types.SymbolType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.WeakHashMap;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RectF;

/** Builds representative production drawing paths and fits their actual ink to UI boxes. */
class SymbolPreviewRenderer
{
  private static final int POINT_PROBE = 256;
  private static final int WIDE_PROBE_W = 512;
  private static final int WIDE_PROBE_H = 256;
  private static final int PROBE_PAD = 12;
  private static final float LIST_PADDING_DP = 4.0f;
  private static final float MAX_PADDING_FRACTION = 0.10f;
  private static final RectF DRAW_BBOX = new RectF( -100000.0f, -100000.0f, 100000.0f, 100000.0f );
  private static final SketchBrushStyle STANDARD_STYLE = SketchBrushStyle.of(
      SketchBrushStyle.DEFAULT_WEIGHT_STANDARD,
      SketchBrushStyle.DEFAULT_POINT_SCALE,
      SketchBrushStyle.DEFAULT_OPACITY );

  private static final Object CACHE_LOCK = new Object();
  private static final WeakHashMap< SymbolInterface, HashMap< BoundsKey, RectF > > BOUNDS_CACHE = new WeakHashMap<>();
  private static Bitmap sPointProbe;
  private static Bitmap sWideProbe;
  private static int[] sPointPixels;
  private static int[] sWidePixels;

  private final Scene mScene;
  private final RectF mInkBounds;
  private final float mDensity;

  private SymbolPreviewRenderer( Scene scene, RectF ink_bounds, float density )
  {
    mScene = scene;
    mInkBounds = ink_bounds;
    mDensity = density;
  }

  static SymbolPreviewRenderer create( int symbol_type, int library_index, SymbolInterface symbol, float density )
  {
    if ( symbol == null || library_index < 0 ) return null;
    Scene scene = makeScene( symbol_type, library_index, symbol );
    if ( scene == null ) return null;
    BoundsKey key = new BoundsKey( symbol_type, library_index, symbol.getAngle(),
                                   Float.floatToIntBits( TDSetting.inkUnit() ),
                                   Float.floatToIntBits( TDSetting.pointInkUnit() ) );
    RectF bounds = cachedBounds( symbol, key );
    if ( bounds == null ) {
      bounds = measureInkBounds( scene, symbol_type == SymbolType.POINT );
      if ( bounds == null || bounds.isEmpty() ) bounds = new RectF( scene.mProbeFrame );
      cacheBounds( symbol, key, bounds );
    }
    return new SymbolPreviewRenderer( scene, new RectF( bounds ), density );
  }

  static void invalidateCache()
  {
    synchronized ( CACHE_LOCK ) { BOUNDS_CACHE.clear(); }
  }

  void draw( Canvas canvas, RectF content )
  {
    if ( canvas == null || content == null || content.width() <= 0.0f || content.height() <= 0.0f || mInkBounds.isEmpty() ) return;
    float short_side = Math.min( content.width(), content.height() );
    float padding = Math.min( LIST_PADDING_DP * mDensity, short_side * MAX_PADDING_FRACTION );
    RectF target = new RectF( content );
    target.inset( padding, padding );
    if ( target.width() <= 0.0f || target.height() <= 0.0f ) return;

    Matrix matrix = new Matrix();
    matrix.setRectToRect( mInkBounds, target, Matrix.ScaleToFit.CENTER );
    int save = canvas.save();
    try {
      canvas.clipRect( content );
      mScene.draw( canvas, matrix );
    } finally {
      canvas.restoreToCount( save );
    }
  }

  RectF getInkBoundsForTest() { return new RectF( mInkBounds ); }

  RectF getSceneFrameForTest() { return new RectF( mScene.mProbeFrame ); }

  private static Scene makeScene( int symbol_type, int index, SymbolInterface symbol )
  {
    switch ( symbol_type ) {
      case SymbolType.POINT: return makePointScene( index, symbol );
      case SymbolType.LINE:  return makeLineScene( index );
      case SymbolType.AREA:  return makeAreaScene( index );
      default: return null;
    }
  }

  private static Scene makePointScene( int index, SymbolInterface symbol )
  {
    DrawingPointPath point = new DrawingPointPath( index, 0.0f, 0.0f, PointScale.SCALE_M, 0 );
    point.setSketchBrushStyle( STANDARD_STYLE );
    if ( BrushManager.isPointOrientable( index ) ) point.setOrientation( symbol.getAngle() );

    Paint paint = SketchBrushRenderer.pointPaint( BrushManager.getPointPaint( index ), STANDARD_STYLE );
    Path path = point.mPath;
    boolean direct = path == null || path.isEmpty();
    if ( direct ) path = symbol.getPath();
    if ( path == null || path.isEmpty() ) return null;
    RectF frame = new RectF();
    path.computeBounds( frame, true );
    float stroke = ( paint == null ) ? TDSetting.inkUnit() : Math.max( TDSetting.inkUnit(), paint.getStrokeWidth() );
    frame.inset( -2.0f * stroke, -2.0f * stroke );
    return new PointScene( point, direct ? new Path( path ) : null, paint, frame );
  }

  private static Scene makeLineScene( int index )
  {
    Paint paint = SketchBrushRenderer.linePaint( BrushManager.getLinePaint( index, false ), STANDARD_STYLE );
    if ( paint == null ) return null;
    float stroke = Math.max( 0.001f, paint.getStrokeWidth() );
    LineSymbolEffect effect = BrushManager.getLineEffect( index );
    float repeat = 0.0f;
    RectF pattern_bounds = null;
    if ( effect != null ) {
      repeat = effect.sampleRepeatLength( stroke, false );
      pattern_bounds = effect.samplePatternBounds( stroke, false );
    } else {
      float[] dash = BrushManager.getLineDashBase( index );
      if ( dash != null ) for ( float value : dash ) repeat += Math.max( 0.0f, value * stroke );
    }
    float length = Math.max( 24.0f * stroke, 3.0f * repeat );
    DrawingLinePath line = new DrawingLinePath( index, 0 );
    line.setSketchBrushStyle( STANDARD_STYLE );
    line.addStartPoint( -0.5f * length, 0.0f );
    line.addPoint( 0.5f * length, 0.0f );
    line.computeUnitNormal();

    float left = -0.5f * length - 2.0f * stroke;
    float right = 0.5f * length + 2.0f * stroke;
    float top = -4.0f * stroke;
    float bottom = 4.0f * stroke;
    if ( pattern_bounds != null && ! pattern_bounds.isEmpty() ) {
      left -= Math.max( 0.0f, -pattern_bounds.left );
      right += Math.max( 0.0f, pattern_bounds.right );
      top = Math.min( top, pattern_bounds.top - stroke );
      bottom = Math.max( bottom, pattern_bounds.bottom + stroke );
    }
    return new LineScene( line, new RectF( left, top, right, bottom ) );
  }

  private static Scene makeAreaScene( int index )
  {
    AreaLinePattern pattern = BrushManager.getAreaLinePattern( index );
    float ink = Math.max( 0.001f, TDSetting.inkUnit() );
    float width = 32.0f * ink;
    float height = 24.0f * ink;
    if ( pattern != null ) {
      float row = Math.max( ink, pattern.mSpacingScale * ink );
      float fade = Math.max( 0.0f, pattern.mFadeScale * ink );

      // Four readable rows communicate the fill without shrinking fine strokes into
      // sub-pixel noise. A fading pattern also needs a real opaque core: otherwise a
      // water swatch whose fade is deeper than half its height is translucent everywhere.
      height = Math.max( height, 4.0f * row );
      if ( fade > 0.0f ) height = Math.max( height, 2.0f * fade + 3.0f * row );
      width = height * 4.0f / 3.0f;

      // Dashes and bedrock need enough horizontal context to show their stagger, but
      // four full periods made tall 4:3 swatches contain dozens of tiny rows. One and
      // a half periods preserves the production rhythm at a legible semantic zoom.
      if ( pattern.mType != AreaLinePattern.TYPE_PARALLEL ) {
        float period = Math.max( ink, pattern.mPeriodScale * ink );
        width = Math.max( width, 1.5f * period );
        height = width * 3.0f / 4.0f;
      }
    }
    DrawingAreaPath area = new DrawingAreaPath( index, 1, "preview", false, 0 );
    area.setSketchBrushStyle( STANDARD_STYLE );
    area.addStartPoint( -0.5f * width, -0.5f * height );
    area.addPoint( 0.5f * width, -0.5f * height );
    area.addPoint( 0.5f * width, 0.5f * height );
    area.addPoint( -0.5f * width, 0.5f * height );
    area.closePath();
    area.retracePath();
    RectF frame = new RectF( -0.5f * width - ink, -0.5f * height - ink,
                              0.5f * width + ink, 0.5f * height + ink );
    return new AreaScene( area, pattern, frame );
  }

  private static RectF measureInkBounds( Scene scene, boolean point_probe )
  {
    synchronized ( CACHE_LOCK ) {
      int width = point_probe ? POINT_PROBE : WIDE_PROBE_W;
      int height = point_probe ? POINT_PROBE : WIDE_PROBE_H;
      Bitmap bitmap;
      int[] pixels;
      if ( point_probe ) {
        if ( sPointProbe == null ) sPointProbe = Bitmap.createBitmap( width, height, Bitmap.Config.ARGB_8888 );
        if ( sPointPixels == null ) sPointPixels = new int[ width * height ];
        bitmap = sPointProbe;
        pixels = sPointPixels;
      } else {
        if ( sWideProbe == null ) sWideProbe = Bitmap.createBitmap( width, height, Bitmap.Config.ARGB_8888 );
        if ( sWidePixels == null ) sWidePixels = new int[ width * height ];
        bitmap = sWideProbe;
        pixels = sWidePixels;
      }
      Canvas canvas = new Canvas( bitmap );
      canvas.drawColor( Color.TRANSPARENT, PorterDuff.Mode.CLEAR );
      RectF target = new RectF( PROBE_PAD, PROBE_PAD, width - PROBE_PAD, height - PROBE_PAD );
      Matrix matrix = new Matrix();
      matrix.setRectToRect( scene.mProbeFrame, target, Matrix.ScaleToFit.CENTER );
      scene.draw( canvas, matrix );
      bitmap.getPixels( pixels, 0, width, 0, 0, width, height );

      int min_x = width;
      int min_y = height;
      int max_x = -1;
      int max_y = -1;
      for ( int y = 0; y < height; ++y ) {
        int row = y * width;
        for ( int x = 0; x < width; ++x ) {
          if ( ( pixels[row+x] >>> 24 ) != 0 ) {
            if ( x < min_x ) min_x = x;
            if ( x > max_x ) max_x = x;
            if ( y < min_y ) min_y = y;
            if ( y > max_y ) max_y = y;
          }
        }
      }
      if ( max_x < min_x || max_y < min_y ) return null;
      RectF pixel_bounds = new RectF( Math.max( 0, min_x - 1 ), Math.max( 0, min_y - 1 ),
                                     Math.min( width, max_x + 2 ), Math.min( height, max_y + 2 ) );
      Matrix inverse = new Matrix();
      if ( ! matrix.invert( inverse ) ) return null;
      inverse.mapRect( pixel_bounds );
      return pixel_bounds;
    }
  }

  private static RectF cachedBounds( SymbolInterface symbol, BoundsKey key )
  {
    synchronized ( CACHE_LOCK ) {
      HashMap< BoundsKey, RectF > values = BOUNDS_CACHE.get( symbol );
      RectF bounds = ( values == null ) ? null : values.get( key );
      return ( bounds == null ) ? null : new RectF( bounds );
    }
  }

  private static void cacheBounds( SymbolInterface symbol, BoundsKey key, RectF bounds )
  {
    synchronized ( CACHE_LOCK ) {
      HashMap< BoundsKey, RectF > values = BOUNDS_CACHE.get( symbol );
      if ( values == null ) {
        values = new HashMap<>();
        BOUNDS_CACHE.put( symbol, values );
      }
      values.put( key, new RectF( bounds ) );
    }
  }

  private abstract static class Scene
  {
    final RectF mProbeFrame;
    Scene( RectF frame ) { mProbeFrame = frame; }
    abstract void draw( Canvas canvas, Matrix matrix );
  }

  private static class PointScene extends Scene
  {
    final DrawingPointPath mPoint;
    final Path mDirectPath;
    final Paint mDirectPaint;
    PointScene( DrawingPointPath point, Path direct_path, Paint direct_paint, RectF frame )
    {
      super( frame );
      mPoint = point;
      mDirectPath = direct_path;
      mDirectPaint = direct_paint;
    }
    @Override void draw( Canvas canvas, Matrix matrix )
    {
      if ( mDirectPath == null ) {
        mPoint.draw( canvas, matrix, 1.0f, DRAW_BBOX );
      } else if ( mDirectPaint != null ) {
        int save = canvas.save();
        try {
          canvas.concat( matrix );
          canvas.drawPath( mDirectPath, mDirectPaint );
        } finally {
          canvas.restoreToCount( save );
        }
      }
    }
  }

  private static class LineScene extends Scene
  {
    final DrawingLinePath mLine;
    LineScene( DrawingLinePath line, RectF frame ) { super( frame ); mLine = line; }
    @Override void draw( Canvas canvas, Matrix matrix ) { mLine.draw( canvas, matrix, 1.0f, DRAW_BBOX ); }
  }

  private static class AreaScene extends Scene
  {
    final DrawingAreaPath mArea;
    final AreaLinePattern mPattern;
    final ArrayList< DrawingAreaPath > mMembers = new ArrayList<>();
    AreaScene( DrawingAreaPath area, AreaLinePattern pattern, RectF frame )
    {
      super( frame );
      mArea = area;
      mPattern = pattern;
      mMembers.add( area );
    }
    @Override void draw( Canvas canvas, Matrix matrix )
    {
      if ( mPattern == null ) {
        mArea.draw( canvas, matrix, DRAW_BBOX );
      } else {
        AreaPatternRenderer.drawGroup( canvas, matrix, DRAW_BBOX, mPattern, mMembers, false,
                                       mArea.getPatternWeightScale(), mArea.getPatternColor( mPattern ) );
      }
    }
  }

  private static class BoundsKey
  {
    final int mType;
    final int mIndex;
    final int mAngle;
    final int mInk;
    final int mPointInk;
    BoundsKey( int type, int index, int angle, int ink, int point_ink )
    {
      mType = type;
      mIndex = index;
      mAngle = angle;
      mInk = ink;
      mPointInk = point_ink;
    }
    @Override public int hashCode()
    {
      int value = mType;
      value = 31 * value + mIndex;
      value = 31 * value + mAngle;
      value = 31 * value + mInk;
      return 31 * value + mPointInk;
    }
    @Override public boolean equals( Object other )
    {
      if ( this == other ) return true;
      if ( ! ( other instanceof BoundsKey ) ) return false;
      BoundsKey key = (BoundsKey)other;
      return mType == key.mType && mIndex == key.mIndex && mAngle == key.mAngle
          && mInk == key.mInk && mPointInk == key.mPointInk;
    }
  }
}
