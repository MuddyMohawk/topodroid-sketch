/* @file SpecialPointRenderer.java
 *
 * @brief Dynamic renderer contract for survey-aware point symbols
 */
package com.topodroid.TDX;

import android.graphics.Canvas;
import android.graphics.RectF;

interface SpecialPointRenderer
{
  void draw( DrawingSemanticPointPath point, Canvas canvas, int xor_color );

  RectF sceneBounds( DrawingSemanticPointPath point );
}
