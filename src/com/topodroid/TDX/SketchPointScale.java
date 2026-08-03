/* @file SketchPointScale.java
 *
 * @author MuddyMohawk
 * @date jul 2026
 *
 * @brief TopoDroid Sketch smooth point scaling helpers
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

import com.topodroid.types.PointScale;

class SketchPointScale
{
  private static final float MIN_SCALE = 0.05f;
  private static final float DEFAULT_SCALE = 1.0f;
  private static final float DRAG_STEP = 75.0f;
  private static final float AFFINE_PLACEMENT_DRAG_SENSITIVITY = 3.0f;

  private static final float[] LEGACY_VALUES = { 0.50f, 0.72f, 1.0f, 1.41f, 2.0f };
  private static final int[] LEGACY_SCALES = {
    PointScale.SCALE_XS,
    PointScale.SCALE_S,
    PointScale.SCALE_M,
    PointScale.SCALE_L,
    PointScale.SCALE_XL
  };

  private SketchPointScale() { }

  static float normalize( float scale )
  {
    return normalize( scale, DEFAULT_SCALE );
  }

  static float normalize( float scale, float fallback )
  {
    if ( Float.isNaN( scale ) || Float.isInfinite( scale ) || scale <= 0.0f ) return normalizeFallback( fallback );
    return Math.max( MIN_SCALE, scale );
  }

  private static float normalizeFallback( float fallback )
  {
    return ( fallback > 0.0f && ! Float.isNaN( fallback ) && ! Float.isInfinite( fallback ) ) ? fallback : DEFAULT_SCALE;
  }

  static float legacyScaleValue( int scale )
  {
    int index = scale - PointScale.SCALE_XS;
    if ( index >= 0 && index < LEGACY_VALUES.length ) return LEGACY_VALUES[index];
    return DEFAULT_SCALE;
  }

  static int nearestLegacyScale( float scale )
  {
    float normalized = normalize( scale );
    int best = PointScale.SCALE_M;
    float best_distance = Float.MAX_VALUE;
    for ( int i = 0; i < LEGACY_VALUES.length; ++i ) {
      float distance = Math.abs( normalized - LEGACY_VALUES[i] );
      if ( distance < best_distance ) {
        best_distance = distance;
        best = LEGACY_SCALES[i];
      }
    }
    return best;
  }

  static float scaleFromDragDistance( float distance )
  {
    if ( Float.isNaN( distance ) || Float.isInfinite( distance ) || distance <= 0.0f ) return LEGACY_VALUES[0];
    return interpolateAnchors( distance / DRAG_STEP );
  }

  /** Affine symbols are visually shaped during placement, so their initial size
   *  tracks the stylus more closely than the legacy point-size gesture. */
  static float scaleFromAffinePlacementDragDistance( float distance )
  {
    return scaleFromDragDistance( distance * AFFINE_PLACEMENT_DRAG_SENSITIVITY );
  }

  static float scaleFromEditProgress( int progress )
  {
    return interpolateAnchors( ( progress - 20.0f ) / 35.0f );
  }

  static int editProgressFromScale( float scale )
  {
    float normalized = normalize( scale );
    float position = positionForScale( normalized );
    return Math.round( 20.0f + 35.0f * position );
  }

  private static float interpolateAnchors( float position )
  {
    if ( position <= 0.0f ) {
      float value = LEGACY_VALUES[0] + position * ( LEGACY_VALUES[1] - LEGACY_VALUES[0] );
      return normalize( value, LEGACY_VALUES[0] );
    }
    int low = (int)Math.floor( position );
    if ( low >= LEGACY_VALUES.length - 1 ) {
      float slope = LEGACY_VALUES[LEGACY_VALUES.length - 1] - LEGACY_VALUES[LEGACY_VALUES.length - 2];
      return LEGACY_VALUES[LEGACY_VALUES.length - 1] + ( position - ( LEGACY_VALUES.length - 1 ) ) * slope;
    }
    float fraction = position - low;
    return LEGACY_VALUES[low] + fraction * ( LEGACY_VALUES[low + 1] - LEGACY_VALUES[low] );
  }

  private static float positionForScale( float scale )
  {
    if ( scale <= LEGACY_VALUES[0] ) {
      float slope = LEGACY_VALUES[1] - LEGACY_VALUES[0];
      return ( scale - LEGACY_VALUES[0] ) / slope;
    }
    for ( int i = 0; i < LEGACY_VALUES.length - 1; ++i ) {
      if ( scale <= LEGACY_VALUES[i + 1] ) {
        return i + ( scale - LEGACY_VALUES[i] ) / ( LEGACY_VALUES[i + 1] - LEGACY_VALUES[i] );
      }
    }
    float slope = LEGACY_VALUES[LEGACY_VALUES.length - 1] - LEGACY_VALUES[LEGACY_VALUES.length - 2];
    return ( LEGACY_VALUES.length - 1 ) + ( scale - LEGACY_VALUES[LEGACY_VALUES.length - 1] ) / slope;
  }
}
