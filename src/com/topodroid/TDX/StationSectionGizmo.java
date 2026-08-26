/* @file StationSectionGizmo.java
 *
 * @brief Three-handle editor for at-station cross-section guides
 */
package com.topodroid.TDX;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;

final class StationSectionGizmo
{
  static final int NONE = 0;
  static final int FIRST = 1;
  static final int LAST = 2;
  static final int ROTATE = 3;

  private static final float HANDLE_RADIUS_PX = 7.0f;
  private static final float HIT_RADIUS_PX = 15.0f;
  private static final float ROTATE_OFFSET_PX = 34.0f;

  private StationSectionGizmo() { }

  static void draw( Canvas canvas, Matrix view, float zoom, DrawingLinePath line )
  {
    if ( canvas == null || view == null || ! StationSectionGuide.isGuide( line ) ) return;
    PointF rotate = rotateHandle( line, zoom );
    LinePoint center = StationSectionGuide.anchor( line );
    Path stem = new Path();
    stem.moveTo( center.x, center.y );
    stem.lineTo( rotate.x, rotate.y );
    stem.transform( view );
    canvas.drawPath( stem, paint( 0xffff4fd8, Paint.Style.STROKE, 2.0f ) );
    drawHandle( canvas, view, zoom, line.mFirst.x, line.mFirst.y, 0xff00d8ff );
    drawHandle( canvas, view, zoom, line.mLast.x, line.mLast.y, 0xff00d8ff );
    drawHandle( canvas, view, zoom, rotate.x, rotate.y, 0xffff4fd8 );
  }

  static int hitHandle( DrawingLinePath line, float x, float y, float zoom )
  {
    if ( ! StationSectionGuide.isGuide( line ) ) return NONE;
    float radius = HIT_RADIUS_PX / Math.max( zoom, 0.0001f );
    float radius2 = radius * radius;
    int best = NONE;
    float best_distance = radius2;
    float d = distance2( x, y, line.mFirst.x, line.mFirst.y );
    if ( d <= best_distance ) { best = FIRST; best_distance = d; }
    d = distance2( x, y, line.mLast.x, line.mLast.y );
    if ( d <= best_distance ) { best = LAST; best_distance = d; }
    PointF rotate = rotateHandle( line, zoom );
    d = distance2( x, y, rotate.x, rotate.y );
    if ( d <= best_distance ) best = ROTATE;
    return best;
  }

  static Drag beginDrag( DrawingLinePath line, int role, float x, float y, float zoom )
  {
    return ( role == NONE || ! StationSectionGuide.isGuide( line ) ) ? null : new Drag( line, role, x, y );
  }

  private static PointF rotateHandle( DrawingLinePath line, float zoom )
  {
    LinePoint center = StationSectionGuide.anchor( line );
    float distance = Math.max( com.topodroid.prefs.TDSetting.mArrowLength,
                               ROTATE_OFFSET_PX / Math.max( zoom, 0.0001f ) );
    return new PointF( center.x + line.sectionDirectionX() * distance,
                       center.y + line.sectionDirectionY() * distance );
  }

  private static void drawHandle( Canvas canvas, Matrix view, float zoom, float x, float y, int color )
  {
    Path marker = new Path();
    marker.addCircle( x, y, HANDLE_RADIUS_PX / Math.max( zoom, 0.0001f ), Path.Direction.CCW );
    marker.transform( view );
    canvas.drawPath( marker, paint( color, Paint.Style.FILL, 1.0f ) );
    canvas.drawPath( marker, paint( 0xff102028, Paint.Style.STROKE, 1.5f ) );
  }

  private static Paint paint( int color, Paint.Style style, float width )
  {
    Paint paint = new Paint( Paint.ANTI_ALIAS_FLAG );
    paint.setColor( color );
    paint.setStyle( style );
    paint.setStrokeWidth( width );
    return paint;
  }

  private static float distance2( float x1, float y1, float x2, float y2 )
  {
    float dx = x1 - x2;
    float dy = y1 - y2;
    return dx * dx + dy * dy;
  }

  static final class Drag
  {
    private final DrawingLinePath mLine;
    private final int mRole;
    private final float mFirstX, mFirstY, mCenterX, mCenterY, mLastX, mLastY;
    private final float mFirstLength, mLastLength;
    private final float mTangentX, mTangentY;
    private final float mStartAngle;

    Drag( DrawingLinePath line, int role, float x, float y )
    {
      mLine = line;
      mRole = role;
      LinePoint center = StationSectionGuide.anchor( line );
      mFirstX = line.mFirst.x;
      mFirstY = line.mFirst.y;
      mCenterX = center.x;
      mCenterY = center.y;
      mLastX = line.mLast.x;
      mLastY = line.mLast.y;
      mFirstLength = (float)Math.hypot( mFirstX - mCenterX, mFirstY - mCenterY );
      mLastLength = (float)Math.hypot( mLastX - mCenterX, mLastY - mCenterY );
      mTangentX = ( mLastX - mCenterX ) / Math.max( mLastLength, 0.0001f );
      mTangentY = ( mLastY - mCenterY ) / Math.max( mLastLength, 0.0001f );
      mStartAngle = (float)Math.atan2( y - mCenterY, x - mCenterX );
    }

    boolean isRotation() { return mRole == ROTATE; }

    boolean update( float x, float y, boolean profile, boolean allow_slant )
    {
      float tx = mTangentX;
      float ty = mTangentY;
      if ( mRole == ROTATE ) {
        float angle = (float)Math.atan2( y - mCenterY, x - mCenterX );
        float delta = angle - mStartAngle;
        float ca = (float)Math.cos( delta );
        float sa = (float)Math.sin( delta );
        tx = mTangentX * ca - mTangentY * sa;
        ty = mTangentX * sa + mTangentY * ca;
        if ( profile ) {
          float degrees = (float)Math.toDegrees( Math.atan2( ty, tx ) );
          float bin = allow_slant ? 10.0f : 90.0f;
          degrees = bin * Math.round( degrees / bin );
          tx = (float)Math.cos( degrees * Math.PI / 180.0 );
          ty = (float)Math.sin( degrees * Math.PI / 180.0 );
        }
        setFirst( mCenterX - mFirstLength * tx, mCenterY - mFirstLength * ty );
        setLast( mCenterX + mLastLength * tx, mCenterY + mLastLength * ty );
      } else {
        float projection = ( x - mCenterX ) * tx + ( y - mCenterY ) * ty;
        float minimum = StationSectionGuide.MIN_HALF_LENGTH_METRES * DrawingUtil.SCALE_FIX;
        if ( mRole == FIRST ) {
          float length = Math.max( minimum, -projection );
          setFirst( mCenterX - length * tx, mCenterY - length * ty );
        } else if ( mRole == LAST ) {
          float length = Math.max( minimum, projection );
          setLast( mCenterX + length * tx, mCenterY + length * ty );
        }
      }
      mLine.retracePath();
      mLine.computeUnitNormal();
      return true;
    }

    void cancel()
    {
      setFirst( mFirstX, mFirstY );
      LinePoint center = StationSectionGuide.anchor( mLine );
      center.x = mCenterX;
      center.y = mCenterY;
      setLast( mLastX, mLastY );
      mLine.retracePath();
      mLine.computeUnitNormal();
    }

    private void setFirst( float x, float y ) { mLine.mFirst.x = x; mLine.mFirst.y = y; }
    private void setLast( float x, float y ) { mLine.mLast.x = x; mLine.mLast.y = y; }
  }
}
