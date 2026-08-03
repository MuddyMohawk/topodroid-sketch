/* @file SketchAffineGizmo.java
 *
 * @author MuddyMohawk
 * @date aug 2026
 *
 * @brief Screen-space edit handles for committed Sketch affine points
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;

final class SketchAffineGizmo
{
  static final int NONE = 0;
  static final int CORNER_NW = 1;
  static final int CORNER_NE = 2;
  static final int CORNER_SE = 3;
  static final int CORNER_SW = 4;
  static final int EDGE_N = 5;
  static final int EDGE_E = 6;
  static final int EDGE_S = 7;
  static final int EDGE_W = 8;
  static final int SHEAR_X = 9;
  static final int SHEAR_Y = 10;
  static final int ROTATE = 11;

  private static final float HANDLE_RADIUS_PX = 6.0f;
  private static final float HANDLE_HIT_RADIUS_PX = 13.0f;
  private static final float MIN_FRAME_PX = 72.0f;

  private SketchAffineGizmo() { }

  static void draw( Canvas canvas, Matrix view, float zoom, DrawingPointPath point )
  {
    if ( canvas == null || view == null || point == null || ! point.hasSketchAffineTransform() ) return;
    RectF local = displayBounds( point, zoom );
    if ( local.isEmpty() ) return;
    PointF[] corners = {
      mapped( point, local.left, local.top ), mapped( point, local.right, local.top ),
      mapped( point, local.right, local.bottom ), mapped( point, local.left, local.bottom )
    };
    Path frame = new Path();
    frame.moveTo( corners[0].x, corners[0].y );
    frame.lineTo( corners[1].x, corners[1].y );
    frame.lineTo( corners[2].x, corners[2].y );
    frame.lineTo( corners[3].x, corners[3].y );
    frame.close();
    PointF rotate = handlePoint( point, local, ROTATE );
    PointF top = handlePoint( point, local, EDGE_N );
    frame.moveTo( top.x, top.y );
    frame.lineTo( rotate.x, rotate.y );
    frame.transform( view );
    Paint frame_paint = paint( 0xff00d8ff, Paint.Style.STROKE, 2.0f );
    canvas.drawPath( frame, frame_paint );

    float radius = HANDLE_RADIUS_PX / Math.max( zoom, 0.0001f );
    for ( int role = CORNER_NW; role <= ROTATE; ++role ) {
      PointF handle = handlePoint( point, local, role );
      Path marker = new Path();
      marker.addCircle( handle.x, handle.y, radius, Path.Direction.CCW );
      marker.transform( view );
      int color = ( role == ROTATE ) ? 0xffff4fd8 : ( role == SHEAR_X || role == SHEAR_Y ) ? 0xffffa22b : 0xff00d8ff;
      canvas.drawPath( marker, paint( color, Paint.Style.FILL, 1.0f ) );
      canvas.drawPath( marker, paint( 0xff102028, Paint.Style.STROKE, 1.5f ) );
    }
  }

  static int hitHandle( DrawingPointPath point, float scene_x, float scene_y, float zoom )
  {
    if ( point == null || ! point.hasSketchAffineTransform() ) return NONE;
    RectF local = displayBounds( point, zoom );
    float radius = HANDLE_HIT_RADIUS_PX / Math.max( zoom, 0.0001f );
    float radius2 = radius * radius;
    int best_role = NONE;
    float best_distance = radius2;
    for ( int role = CORNER_NW; role <= ROTATE; ++role ) {
      PointF handle = handlePoint( point, local, role );
      float dx = scene_x - handle.x;
      float dy = scene_y - handle.y;
      float distance = dx * dx + dy * dy;
      if ( distance <= best_distance ) {
        best_distance = distance;
        best_role = role;
      }
    }
    return best_role;
  }

  static Drag beginDrag( DrawingPointPath point, int role, float scene_x, float scene_y, float zoom )
  {
    if ( point == null || role == NONE || ! point.hasSketchAffineTransform() ) return null;
    return new Drag( point, role, scene_x, scene_y, displayBounds( point, zoom ) );
  }

  static PointF handlePoint( DrawingPointPath point, int role, float zoom )
  {
    return handlePoint( point, displayBounds( point, zoom ), role );
  }

  private static RectF displayBounds( DrawingPointPath point, float zoom )
  {
    RectF bounds = point.getSketchAffineLocalBounds();
    SketchAffineTransform affine = point.getSketchAffineTransform();
    if ( bounds.isEmpty() || affine == null ) return bounds;
    float footprint = point.getSketchAffineFootprintScale();
    float safe_zoom = Math.max( zoom, 0.0001f );
    float x_scale = footprint * (float)Math.hypot( affine.m00, affine.m10 ) * safe_zoom;
    float y_scale = footprint * (float)Math.hypot( affine.m01, affine.m11 ) * safe_zoom;
    float width = Math.max( bounds.width(), MIN_FRAME_PX / Math.max( x_scale, 0.0001f ) );
    float height = Math.max( bounds.height(), MIN_FRAME_PX / Math.max( y_scale, 0.0001f ) );
    return new RectF( bounds.centerX() - 0.5f * width, bounds.centerY() - 0.5f * height,
                      bounds.centerX() + 0.5f * width, bounds.centerY() + 0.5f * height );
  }

  private static PointF handlePoint( DrawingPointPath point, RectF bounds, int role )
  {
    float center_x = 0.5f * ( bounds.left + bounds.right );
    float center_y = 0.5f * ( bounds.top + bounds.bottom );
    float width = bounds.width();
    float height = bounds.height();
    float x = center_x;
    float y = center_y;
    switch ( role ) {
      case CORNER_NW: x = bounds.left; y = bounds.top; break;
      case CORNER_NE: x = bounds.right; y = bounds.top; break;
      case CORNER_SE: x = bounds.right; y = bounds.bottom; break;
      case CORNER_SW: x = bounds.left; y = bounds.bottom; break;
      case EDGE_N: y = bounds.top; break;
      case EDGE_E: x = bounds.right; break;
      case EDGE_S: y = bounds.bottom; break;
      case EDGE_W: x = bounds.left; break;
      case SHEAR_X: y = bounds.bottom + 0.38f * height; break;
      case SHEAR_Y: x = bounds.right + 0.38f * width; break;
      case ROTATE: y = bounds.top - 0.48f * height; break;
      default: break;
    }
    return mapped( point, x, y );
  }

  private static PointF mapped( DrawingPointPath point, float x, float y )
  {
    PointF result = new PointF();
    point.mapSketchAffineLocalPoint( x, y, result );
    return result;
  }

  private static Paint paint( int color, Paint.Style style, float width )
  {
    Paint paint = new Paint( Paint.ANTI_ALIAS_FLAG );
    paint.setColor( color );
    paint.setStyle( style );
    paint.setStrokeWidth( width );
    return paint;
  }

  static final class Drag
  {
    private final DrawingPointPath mPoint;
    private final int mRole;
    private final SketchAffineTransform mStart;
    private final RectF mBounds;
    private final float mFootprint;
    private final float mStartAngle;

    Drag( DrawingPointPath point, int role, float scene_x, float scene_y, RectF bounds )
    {
      mPoint = point;
      mRole = role;
      mStart = point.getSketchAffineTransform();
      mBounds = bounds;
      mFootprint = point.getSketchAffineFootprintScale();
      mStartAngle = (float)Math.atan2( scene_y - point.cy, scene_x - point.cx );
    }

    boolean update( float scene_x, float scene_y )
    {
      if ( mStart == null ) return false;
      SketchAffineTransform next;
      if ( mRole == ROTATE ) {
        float angle = (float)Math.atan2( scene_y - mPoint.cy, scene_x - mPoint.cx );
        next = mStart.rotateBy( (float)Math.toDegrees( angle - mStartAngle ) );
      } else {
        float[] local = inverseStart( scene_x, scene_y );
        if ( local == null ) return false;
        float center_x = 0.5f * ( mBounds.left + mBounds.right );
        float center_y = 0.5f * ( mBounds.top + mBounds.bottom );
        float sx = 1.0f;
        float sy = 1.0f;
        if ( mRole == CORNER_NW || mRole == CORNER_SW || mRole == EDGE_W ) sx = safeScale( local[0] - center_x, mBounds.left - center_x, column0() );
        if ( mRole == CORNER_NE || mRole == CORNER_SE || mRole == EDGE_E ) sx = safeScale( local[0] - center_x, mBounds.right - center_x, column0() );
        if ( mRole == CORNER_NW || mRole == CORNER_NE || mRole == EDGE_N ) sy = safeScale( local[1] - center_y, mBounds.top - center_y, column1() );
        if ( mRole == CORNER_SW || mRole == CORNER_SE || mRole == EDGE_S ) sy = safeScale( local[1] - center_y, mBounds.bottom - center_y, column1() );
        if ( mRole == SHEAR_X ) {
          float handle_y = mBounds.bottom + 0.38f * mBounds.height();
          float shear = ( local[0] - center_x ) / nonzero( handle_y - center_y );
          next = mStart.rightMultiply( 1.0f, shear, 0.0f, 1.0f );
        } else if ( mRole == SHEAR_Y ) {
          float handle_x = mBounds.right + 0.38f * mBounds.width();
          float shear = ( local[1] - center_y ) / nonzero( handle_x - center_x );
          next = mStart.rightMultiply( 1.0f, 0.0f, shear, 1.0f );
        } else {
          next = mStart.rightMultiply( sx, 0.0f, 0.0f, sy );
        }
      }
      return next != null && mPoint.setSketchAffineTransform( next );
    }

    private float[] inverseStart( float scene_x, float scene_y )
    {
      float det = mStart.determinant() * mFootprint * mFootprint;
      if ( det <= 0.0f ) return null;
      float dx = scene_x - mPoint.cx;
      float dy = scene_y - mPoint.cy;
      return new float[] {
        mFootprint * ( mStart.m11 * dx - mStart.m01 * dy ) / det,
        mFootprint * ( -mStart.m10 * dx + mStart.m00 * dy ) / det
      };
    }

    private float safeScale( float value, float reference, float column )
    {
      float scale = value / nonzero( reference );
      float minimum = SketchAffineTransform.MIN_AXIS_SCALE / Math.max( column, SketchAffineTransform.MIN_AXIS_SCALE );
      return Math.max( minimum, Math.min( 50.0f, scale ) );
    }

    private float column0() { return (float)Math.hypot( mStart.m00, mStart.m10 ); }
    private float column1() { return (float)Math.hypot( mStart.m01, mStart.m11 ); }
    private static float nonzero( float value ) { return ( Math.abs( value ) < 0.0001f ) ? ( value < 0.0f ? -0.0001f : 0.0001f ) : value; }
  }
}
