package com.topodroid.TDX;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.Collections;

/** A canvas-only two-finger scale gesture. No finger events reach drawing or UI handlers. */
final class StylusPinchZoom
{
  interface Zoom { void zoom( float factor, float x, float y ); }
  private final View canvas;
  private final Zoom zoom;
  private final float slop;
  private int firstId = -1;
  private int secondId = -1;
  private float initialSpan;
  private float previousSpan;
  private boolean scaling;

  StylusPinchZoom( View canvas, Zoom zoom )
  {
    this.canvas = canvas;
    this.zoom = zoom;
    slop = ViewConfiguration.get( canvas.getContext() ).getScaledTouchSlop();
  }

  boolean owns( View view ) { return canvas == view; }

  void cancel()
  {
    firstId = secondId = -1;
    scaling = false;
  }

  void touch( MotionEvent event, boolean penActive )
  {
    int action = event.getActionMasked();
    if ( penActive || action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP
        || action == MotionEvent.ACTION_POINTER_UP || ( event.getFlags() & MotionEvent.FLAG_CANCELED ) != 0 ) {
      cancel();
      return;
    }
    if ( ! canvas.isShown() || ! canvas.hasWindowFocus() || event.getPointerCount() > 2 ) {
      cancel();
      return;
    }
    for ( int i = 0; i < event.getPointerCount(); ++i ) {
      if ( event.getToolType( i ) != MotionEvent.TOOL_TYPE_FINGER ) { cancel(); return; }
    }
    if ( action == MotionEvent.ACTION_DOWN ) {
      cancel();
      if ( onCanvas( event.getX(), event.getY() ) ) firstId = event.getPointerId( 0 );
      return;
    }
    if ( firstId < 0 ) return; // Require a fresh finger press after cancellation or pen input.
    int first = event.findPointerIndex( firstId );
    if ( first < 0 || ! onCanvas( event.getX( first ), event.getY( first ) ) ) { cancel(); return; }
    if ( action == MotionEvent.ACTION_POINTER_DOWN && event.getPointerCount() == 2 ) {
      int second = event.getActionIndex();
      if ( secondId >= 0 || event.getPointerId( second ) == firstId
          || ! onCanvas( event.getX( second ), event.getY( second ) ) ) { cancel(); return; }
      secondId = event.getPointerId( second );
      initialSpan = previousSpan = span( event.getX( first ), event.getY( first ), event.getX( second ), event.getY( second ) );
      return;
    }
    if ( action != MotionEvent.ACTION_MOVE || secondId < 0 ) return;
    int second = event.findPointerIndex( secondId );
    if ( second < 0 || event.getPointerCount() != 2
        || ! onCanvas( event.getX( second ), event.getY( second ) ) ) { cancel(); return; }
    for ( int h = 0; h < event.getHistorySize(); ++h ) {
      scale( event.getHistoricalX( first, h ), event.getHistoricalY( first, h ),
          event.getHistoricalX( second, h ), event.getHistoricalY( second, h ) );
    }
    scale( event.getX( first ), event.getY( first ), event.getX( second ), event.getY( second ) );
  }

  private static float span( float x1, float y1, float x2, float y2 )
  {
    return (float)Math.hypot( x2 - x1, y2 - y1 );
  }

  private void scale( float x1, float y1, float x2, float y2 )
  {
    if ( secondId < 0 ) return;
    float currentSpan = span( x1, y1, x2, y2 );
    if ( currentSpan < slop * 2 ) return;
    if ( initialSpan < slop * 2 ) { initialSpan = previousSpan = currentSpan; return; }
    if ( ! scaling && Math.abs( currentSpan - initialSpan ) <= slop ) return;
    float factor = currentSpan / previousSpan;
    if ( Float.isNaN( factor ) || Float.isInfinite( factor ) || factor <= 0 ) { cancel(); return; }
    int[] where = new int[2]; canvas.getLocationInWindow( where );
    zoom.zoom( factor, ( x1 + x2 ) / 2 - where[0], ( y1 + y2 ) / 2 - where[1] );
    previousSpan = currentSpan;
    scaling = true;
  }

  private boolean onCanvas( float x, float y )
  {
    return hit( canvas.getRootView(), x, y ) == canvas;
  }

  /** Respect overlapping toolbars and menus, including their elevation. */
  private static View hit( View view, float x, float y )
  {
    if ( view.getVisibility() != View.VISIBLE ) return null;
    int[] where = new int[2]; view.getLocationInWindow( where );
    if ( x < where[0] || y < where[1] || x >= where[0] + view.getWidth() || y >= where[1] + view.getHeight() ) return null;
    if ( view instanceof ViewGroup ) {
      ViewGroup group = (ViewGroup)view;
      ArrayList<View> children = new ArrayList<>();
      for ( int i = group.getChildCount() - 1; i >= 0; --i ) children.add( group.getChildAt( i ) );
      Collections.sort( children, ( a, b ) -> Float.compare( b.getZ(), a.getZ() ) );
      for ( View child : children ) {
        View target = hit( child, x, y );
        if ( target != null ) return target;
      }
      return view.isClickable() || view.getBackground() != null ? view : null;
    }
    return view;
  }
}
