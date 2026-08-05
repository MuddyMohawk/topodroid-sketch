/* @file SpecialPointSizing.java
 *
 * @brief Shared footprint defaults for compact annotated special points
 */
package com.topodroid.TDX;

import com.topodroid.types.PointScale;

final class SpecialPointSizing
{
  static final float COMPACT_FRAME_SCALE = SketchPointScale.legacyScaleValue( PointScale.SCALE_S );
  static final float BEDDING_ATTITUDE_SCALE = SketchPointScale.legacyScaleValue( PointScale.SCALE_S );
  static final float BEDDING_STRIKE_HALF_LENGTH = 9.0f;
  static final float BEDDING_DIP_TICK_LENGTH = 6.0f;
  static final float BEDDING_TEXT_OFFSET = 10.0f;

  private SpecialPointSizing() { }
}
