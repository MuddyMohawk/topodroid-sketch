/* @file SpecialPointSizing.java
 *
 * @brief Shared footprint defaults for compact annotated special points
 */
package com.topodroid.TDX;

import com.topodroid.types.PointScale;

final class SpecialPointSizing
{
  static final float COMPACT_FRAME_SCALE = SketchPointScale.legacyScaleValue( PointScale.SCALE_S );

  private SpecialPointSizing() { }
}
