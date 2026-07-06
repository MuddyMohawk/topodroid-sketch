/* @file TDGreenDot.java
 *
 * @author marco corvi
 * @date feb 2020
 *
 * @grief graphical utilities
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.ui;

import com.topodroid.TDX.SelectionPoint;
import com.topodroid.TDX.BrushManager;
// import com.topodroid.TDX.DrawingSplayPath;

import android.graphics.Path;
import android.graphics.Paint;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;

public class TDGreenDot
{
  /** draw a selection point, as a green dot
   * @param canvas     canvas
   * @param matrix     transform matrix
   * @param pt         selection point
   * @param dot_radius circle radius
   *
   * @note this function could be a forward to
   *       draw( canvas, matrix, pt.mPoint.x, pt.mPoint.y, dot_radius, BrushManager.highlightPaint2 );
   *       or pt.mItem.x etc.
   */
  public static void draw( Canvas canvas, Matrix matrix, SelectionPoint pt, float dot_radius )
  {
    Path path = new Path();
    if ( pt.mPoint != null ) { // line-point
      path.addCircle( pt.mPoint.x, pt.mPoint.y, dot_radius, Path.Direction.CCW );
    } else {  
      path.addCircle( pt.mItem.cx, pt.mItem.cy, dot_radius, Path.Direction.CCW );
    }
    path.transform( matrix );
    canvas.drawPath( path, BrushManager.highlightPaint2 );
  }

  /** draw a point, as a dot with the given paint
   * @param canvas     canvas
   * @param matrix     transform matrix
   * @param x          X coordinate
   * @param y          Y coordinate
   * @param dot_radius circle radius
   * @param paint      dot paint
   */
  public static void draw( Canvas canvas, Matrix matrix, float x, float y, float dot_radius, Paint paint )
  {
    Path path = new Path();
    path.addCircle( x, y, dot_radius, Path.Direction.CCW );
    path.transform( matrix );
    canvas.drawPath( path, paint );
  }

  // Allocation-free dot drawing for the per-frame hot paths (edit-mode
  // selection dots, splay endpoint dots): the circle is rebuilt into a
  // caller-owned scratch Path (rewind + addCircle + transform) instead of
  // allocating a new Path per dot per frame. The path route (rather than
  // canvas.drawCircle at the mapped center) is deliberate: Skia's oval fast
  // path rasterizes slightly differently, and these dots must stay
  // pixel-identical to the historical rendering.

  /** draw a dot with the given paint, allocation-free
   * @param canvas     canvas
   * @param matrix     scene-to-screen transform
   * @param x          X scene coord
   * @param y          Y scene coord
   * @param dot_radius circle radius [scene units]
   * @param paint      dot paint
   * @param scratch    caller-owned scratch path - must not be shared between threads
   */
  public static void drawMapped( Canvas canvas, Matrix matrix, float x, float y, float dot_radius, Paint paint, Path scratch )
  {
    scratch.rewind();
    scratch.addCircle( x, y, dot_radius, Path.Direction.CCW );
    scratch.transform( matrix );
    canvas.drawPath( scratch, paint );
  }

  /** draw a selection point as a green dot, allocation-free, with per-point bbox culling
   * @param canvas     canvas
   * @param matrix     scene-to-screen transform
   * @param pt         selection point
   * @param bbox       scene clipping rectangle (null = no culling)
   * @param dot_radius circle radius [scene units]
   * @param scratch    caller-owned scratch path - must not be shared between threads
   * @note the cull margin is twice the dot radius so anti-aliasing bleed can
   *       never make a skipped dot differ from the clipped rendering
   */
  public static void drawMapped( Canvas canvas, Matrix matrix, SelectionPoint pt,
                                 RectF bbox, float dot_radius, Path scratch )
  {
    float x, y;
    if ( pt.mPoint != null ) { // line-point
      x = pt.mPoint.x;
      y = pt.mPoint.y;
    } else {
      x = pt.mItem.cx;
      y = pt.mItem.cy;
    }
    float margin = 2 * dot_radius;
    if ( bbox != null
      && ( x < bbox.left - margin || x > bbox.right  + margin
        || y < bbox.top  - margin || y > bbox.bottom + margin ) ) return; // off-screen: clipped today, skipped now
    drawMapped( canvas, matrix, x, y, dot_radius, BrushManager.highlightPaint2, scratch );
  }

  /** draw a selection point as a green dot on a canvas already carrying the
   *  scene-to-screen transform (canvas.concat), allocation-free, culled
   * @param canvas     canvas with the scene transform concatenated
   * @param pt         selection point
   * @param bbox       scene clipping rectangle (null = no culling)
   * @param dot_radius circle radius [scene units]
   * @param scratch    caller-owned scratch path - must not be shared between threads
   * @note skips the per-dot path.transform: Skia maps the same conic control
   *       points through the same matrix at scan conversion, producing the
   *       same device-space outline (verified byte-identical by the
   *       render-hash gate)
   */
  public static void drawScene( Canvas canvas, SelectionPoint pt, RectF bbox, float dot_radius, Path scratch )
  {
    float x, y;
    if ( pt.mPoint != null ) { // line-point
      x = pt.mPoint.x;
      y = pt.mPoint.y;
    } else {
      x = pt.mItem.cx;
      y = pt.mItem.cy;
    }
    float margin = 2 * dot_radius;
    if ( bbox != null
      && ( x < bbox.left - margin || x > bbox.right  + margin
        || y < bbox.top  - margin || y > bbox.bottom + margin ) ) return;
    scratch.rewind();
    scratch.addCircle( x, y, dot_radius, Path.Direction.CCW );
    canvas.drawPath( scratch, BrushManager.highlightPaint2 );
  }

  // /** draw a point, as a dot with the given paint
  //  * @param canvas     canvas
  //  * @param x          X coordinate
  //  * @param y          Y coordinate
  //  * @param dot_radius circle radius
  //  * @param paint      dot paint
  //  */
  // public static void draw( Canvas canvas, float x, float y, float dot_radius, Paint paint )
  // {
  //   Path path = new Path();
  //   path.addCircle( x, y, dot_radius, Path.Direction.CCW );
  //   canvas.drawPath( path, paint );
  // }

}

