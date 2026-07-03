/* @file SketchBrushStyle.java
 *
 * @author MuddyMohawk
 * @date jun 2026
 *
 * @brief TopoDroid Sketch brush-style metadata
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

import com.topodroid.prefs.TDSetting;

public class SketchBrushStyle
{
  public static final float DEFAULT_WEIGHT_THIN     = TDSetting.DEFAULT_SKETCH_STYLE_WEIGHT_THIN;
  public static final float DEFAULT_WEIGHT_STANDARD = TDSetting.DEFAULT_SKETCH_STYLE_WEIGHT_STANDARD;
  public static final float DEFAULT_WEIGHT_THICK    = TDSetting.DEFAULT_SKETCH_STYLE_WEIGHT_THICK;
  public static final float DEFAULT_POINT_SCALE     = 1.0f;
  public static final float DEFAULT_OPACITY         = 1.0f;

  private final boolean mHasWeight;
  private final float   mWeight;
  private final boolean mHasPointScale;
  private final float   mPointScale;
  private final boolean mHasOpacity;
  private final float   mOpacity;
  private final boolean mHasColor;
  private final int     mColor;

  private SketchBrushStyle( boolean has_weight, float weight,
                            boolean has_point_scale, float point_scale,
                            boolean has_opacity, float opacity,
                            boolean has_color, int color )
  {
    mHasWeight = has_weight;
    mWeight = weight;
    mHasPointScale = has_point_scale;
    mPointScale = point_scale;
    mHasOpacity = has_opacity;
    mOpacity = opacity;
    mHasColor = has_color;
    mColor = color & 0x00ffffff;
  }

  public static SketchBrushStyle of( float weight, float point_scale, float opacity )
  {
    return new SketchBrushStyle( true, normalizePositive( weight, DEFAULT_WEIGHT_STANDARD ),
                                 true, normalizePositive( point_scale, DEFAULT_POINT_SCALE ),
                                 true, normalizeOpacity( opacity ),
                                 false, 0 );
  }

  public static SketchBrushStyle of( float weight, float point_scale, float opacity, int color )
  {
    return new SketchBrushStyle( true, normalizePositive( weight, DEFAULT_WEIGHT_STANDARD ),
                                 true, normalizePositive( point_scale, DEFAULT_POINT_SCALE ),
                                 true, normalizeOpacity( opacity ),
                                 true, color );
  }

  public static SketchBrushStyle fromFields( boolean has_weight, float weight,
                                             boolean has_point_scale, float point_scale,
                                             boolean has_opacity, float opacity,
                                             boolean has_color, int color )
  {
    if ( ! has_weight && ! has_point_scale && ! has_opacity && ! has_color ) return null;
    return new SketchBrushStyle( has_weight, normalizePositive( weight, DEFAULT_WEIGHT_STANDARD ),
                                 has_point_scale, normalizePositive( point_scale, DEFAULT_POINT_SCALE ),
                                 has_opacity, normalizeOpacity( opacity ),
                                 has_color, color );
  }

  public boolean hasWeight() { return mHasWeight; }
  public boolean hasPointScale() { return mHasPointScale; }
  public boolean hasOpacity() { return mHasOpacity; }
  public boolean hasColor() { return mHasColor; }

  public float weightOr( float fallback ) { return mHasWeight ? mWeight : fallback; }
  public float pointScaleOr( float fallback ) { return mHasPointScale ? mPointScale : fallback; }
  public float opacityOr( float fallback ) { return mHasOpacity ? mOpacity : fallback; }
  public int colorOr( int fallback ) { return mHasColor ? mColor : fallback; }

  SketchBrushStyle withPointScale( float point_scale )
  {
    return new SketchBrushStyle( mHasWeight, mWeight,
                                 true, normalizePositive( point_scale, DEFAULT_POINT_SCALE ),
                                 mHasOpacity, mOpacity,
                                 mHasColor, mColor );
  }

  static SketchBrushStyle pointScaleOnly( float point_scale )
  {
    return new SketchBrushStyle( false, DEFAULT_WEIGHT_STANDARD,
                                 true, normalizePositive( point_scale, DEFAULT_POINT_SCALE ),
                                 false, DEFAULT_OPACITY,
                                 false, 0 );
  }

  public boolean isEmpty()
  {
    return ! mHasWeight && ! mHasPointScale && ! mHasOpacity && ! mHasColor;
  }

  private static float normalizePositive( float value, float fallback )
  {
    return ( value > 0.0f && ! Float.isNaN( value ) && ! Float.isInfinite( value ) ) ? value : fallback;
  }

  private static float normalizeOpacity( float value )
  {
    if ( Float.isNaN( value ) || Float.isInfinite( value ) ) return DEFAULT_OPACITY;
    if ( value < 0.0f ) return 0.0f;
    if ( value > 1.0f ) return 1.0f;
    return value;
  }
}
