/* @file AreaPatternRenderer.java
 *
 * @author MuddyMohawk
 * @date jul 2026
 *
 * @brief TopoDroid Sketch patterned area renderer
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

import com.topodroid.util.TDLog;

import java.util.HashMap;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

class AreaPatternRenderer
{
  private static final float EPS = 1.0e-4f;

  private final HashMap< String, Path > mUnionCoverage = new HashMap<>();

  boolean draw( Canvas canvas, Matrix matrix, RectF bbox, DrawingAreaPath area )
  {
    return draw( canvas, matrix, bbox, area, 0, false );
  }

  boolean draw( Canvas canvas, Matrix matrix, RectF bbox, DrawingAreaPath area, int xor_color )
  {
    return draw( canvas, matrix, bbox, area, xor_color, true );
  }

  private boolean draw( Canvas canvas, Matrix matrix, RectF bbox, DrawingAreaPath area,
                        int xor_color, boolean with_xor )
  {
    if ( canvas == null || matrix == null || area == null || ! area.intersects( bbox ) ) return false;

    AreaLinePattern pattern = area.getAreaLinePattern();
    if ( pattern == null || ! pattern.isParallelWorldUnion() ) return false;

    Path area_path = new Path( area.mPath );
    area_path.close();
    Path visible_path = area_path;
    String key = area.getThName();
    Path covered = mUnionCoverage.get( key );

    if ( covered != null ) {
      visible_path = new Path();
      try {
        if ( ! visible_path.op( area_path, covered, Path.Op.DIFFERENCE ) ) {
          visible_path = area_path;
        }
      } catch ( RuntimeException e ) {
        TDLog.e( "Area pattern difference failed: " + e.getMessage() );
        visible_path = area_path;
      }
    }

    int save = canvas.save();
    try {
      canvas.concat( matrix );
      drawScenePattern( canvas, visible_path, pattern, area.getSketchBrushStyle(), with_xor, xor_color );
      if ( area.isVisible() ) canvas.drawPath( area_path, BrushManager.borderPaint );
    } finally {
      canvas.restoreToCount( save );
    }

    if ( covered == null ) {
      mUnionCoverage.put( key, new Path( area_path ) );
    } else {
      try {
        covered.op( area_path, Path.Op.UNION );
      } catch ( RuntimeException e ) {
        TDLog.e( "Area pattern union failed: " + e.getMessage() );
      }
    }
    return true;
  }

  private void drawScenePattern( Canvas canvas, Path clip_path, AreaLinePattern pattern,
                                 SketchBrushStyle style, boolean with_xor, int xor_color )
  {
    RectF bounds = new RectF();
    clip_path.computeBounds( bounds, true );
    if ( bounds.width() <= EPS || bounds.height() <= EPS ) return;

    float scene_unit = SketchBrushRenderer.sceneUnit( style );
    float stroke_width = normalizePositive( pattern.mWidthScale * scene_unit, scene_unit );
    float spacing = normalizePositive( pattern.mSpacingScale * scene_unit, 6.0f * scene_unit );

    Paint paint = new Paint( Paint.ANTI_ALIAS_FLAG );
    paint.setStyle( Paint.Style.STROKE );
    paint.setStrokeJoin( Paint.Join.ROUND );
    paint.setStrokeCap( Paint.Cap.ROUND );
    paint.setStrokeWidth( stroke_width );
    paint.setColor( with_xor ? BrushManager.xorColor( pattern.mColor ) : pattern.mColor );

    float radians = (float)Math.toRadians( pattern.mAngle );
    float dx = (float)Math.cos( radians );
    float dy = (float)Math.sin( radians );
    float nx = -dy;
    float ny = dx;

    float margin = Math.max( stroke_width, spacing ) * 2.0f;
    float[] xs = { bounds.left - margin, bounds.right + margin };
    float[] ys = { bounds.top - margin, bounds.bottom + margin };
    float min = Float.MAX_VALUE;
    float max = -Float.MAX_VALUE;
    for ( int ix = 0; ix < 2; ++ix ) {
      for ( int iy = 0; iy < 2; ++iy ) {
        float projection = xs[ix] * nx + ys[iy] * ny;
        if ( projection < min ) min = projection;
        if ( projection > max ) max = projection;
      }
    }

    float diagonal = (float)Math.sqrt( bounds.width() * bounds.width() + bounds.height() * bounds.height() ) + 2.0f * margin;
    int first = (int)Math.floor( min / spacing ) - 1;
    int last = (int)Math.ceil( max / spacing ) + 1;

    int save = canvas.save();
    try {
      canvas.clipPath( clip_path );
      for ( int k = first; k <= last; ++k ) {
        float projection = k * spacing;
        float cx = nx * projection;
        float cy = ny * projection;
        canvas.drawLine( cx - dx * diagonal, cy - dy * diagonal,
                         cx + dx * diagonal, cy + dy * diagonal, paint );
      }
    } finally {
      canvas.restoreToCount( save );
    }
  }

  private static float normalizePositive( float value, float fallback )
  {
    return ( value > EPS && ! Float.isNaN( value ) && ! Float.isInfinite( value ) ) ? value : fallback;
  }
}
