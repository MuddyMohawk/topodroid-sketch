/* @file SketchBrushRenderer.java
 *
 * @author MuddyMohawk
 * @date jun 2026
 *
 * @brief TopoDroid Sketch brush-style rendering helpers (world-space ink model)
 *
 * Ink stroke widths are SCENE units: weight * TDSetting.mLineThickness.
 * The canvas scene->screen transform magnifies them with zoom / export scale,
 * so relative thicknesses are invariant.
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

import com.topodroid.prefs.TDSetting;

import android.graphics.Paint;

class SketchBrushRenderer
{
  private static final float EPS = 1.0e-4f;

  private SketchBrushRenderer() { }

  static Paint linePaint( Paint source, SketchBrushStyle style )
  {
    return paint( source, style );
  }

  static Paint pointPaint( Paint source, SketchBrushStyle style )
  {
    return paint( source, style );
  }

  /** @return the ink thickness [scene units] for a brush style (fallback: standard weight) */
  static float sceneUnit( SketchBrushStyle style )
  {
    float weight = ( style != null && style.hasWeight() )
                 ? style.weightOr( SketchBrushStyle.DEFAULT_WEIGHT_STANDARD )
                 : SketchBrushStyle.DEFAULT_WEIGHT_STANDARD;
    return normalizedPositive( weight * TDSetting.mLineThickness );
  }

  /** @return weight ratio relative to the standard weight (for point-footprint expansion) */
  static float effectScale( SketchBrushStyle style )
  {
    if ( style == null || ! style.hasWeight() ) return 1.0f;
    return normalizedScale( style.weightOr( SketchBrushStyle.DEFAULT_WEIGHT_STANDARD ),
                            SketchBrushStyle.DEFAULT_WEIGHT_STANDARD );
  }

  private static Paint paint( Paint source, SketchBrushStyle style )
  {
    if ( source == null || style == null || style.isEmpty() ) return source;

    Paint paint = new Paint( source );
    int alpha = source.getAlpha();
    int rgb = style.colorOr( source.getColor() ) & 0x00ffffff;
    if ( style.hasOpacity() ) {
      alpha = Math.max( 0, Math.min( 255, Math.round( alpha * style.opacityOr( SketchBrushStyle.DEFAULT_OPACITY ) ) ) );
    }
    paint.setColor( ( alpha << 24 ) | rgb );
    if ( style.hasWeight() ) {
      paint.setStrokeWidth( style.weightOr( SketchBrushStyle.DEFAULT_WEIGHT_STANDARD ) * TDSetting.mLineThickness );
    }
    return paint;
  }

  static float normalizedScale( float value, float fallback )
  {
    float scale = value / fallback;
    return normalizedPositive( scale );
  }

  private static float normalizedPositive( float value )
  {
    return normalizedPositive( value, 1.0f );
  }

  private static float normalizedPositive( float value, float fallback )
  {
    return ( value > EPS && ! Float.isNaN( value ) && ! Float.isInfinite( value ) ) ? value : fallback;
  }
}
