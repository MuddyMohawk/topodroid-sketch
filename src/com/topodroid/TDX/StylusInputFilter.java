package com.topodroid.TDX;

import android.view.MotionEvent;

/** Projects a mixed touch stream onto one real pen, before any view sees it. */
final class StylusInputFilter
{
  interface Dispatch { boolean dispatch( MotionEvent event ); }

  private int penId = -1;
  private long penDownTime;
  private MotionEvent lastDelivered;
  private boolean awaitingDown;

  static boolean isPen( MotionEvent event, int index )
  {
    int tool = event.getToolType( index );
    return tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER;
  }

  boolean touch( MotionEvent event, boolean enabled, Dispatch target )
  {
    int action = event.getActionMasked();
    if ( awaitingDown ) {
      if ( action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_POINTER_DOWN ) return true;
      // A pointer-down can start a pen while rejected fingers are still down.
      if ( ! enabled && action != MotionEvent.ACTION_DOWN ) return true;
      awaitingDown = false;
    }
    if ( ! enabled ) return deliver( event, target );

    if ( action == MotionEvent.ACTION_CANCEL ) {
      cancel( target );
      return true;
    }
    int changed = event.getActionIndex();
    if ( action == MotionEvent.ACTION_DOWN ) {
      cancel( target );
      awaitingDown = false;
    }
    if ( penId < 0 ) {
      if ( ( action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_POINTER_DOWN )
          || ! isPen( event, changed ) ) return true;
      penId = event.getPointerId( changed );
      penDownTime = event.getEventTime();
      return projectAndDeliver( event, changed, MotionEvent.ACTION_DOWN, target );
    }
    int index = event.findPointerIndex( penId );
    if ( index < 0 || ! isPen( event, index ) ) {
      cancel( target );
      return true;
    }
    if ( action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP ) {
      if ( event.getPointerId( changed ) == penId ) {
        // FLAG_CANCELED is inlined and safe to inspect on pre-33 devices too.
        int projectedAction = ( event.getFlags() & MotionEvent.FLAG_CANCELED ) != 0
            ? MotionEvent.ACTION_CANCEL : MotionEvent.ACTION_UP;
        boolean handled = projectAndDeliver( event, index, projectedAction, target );
        penId = -1;
        return handled;
      }
    }
    return projectAndDeliver( event, index, MotionEvent.ACTION_MOVE, target );
  }

  boolean generic( MotionEvent event, boolean enabled, Dispatch target )
  {
    if ( ! enabled ) return target.dispatch( event );
    for ( int i = 0; i < event.getPointerCount(); ++i ) {
      if ( isPen( event, i ) ) {
        MotionEvent projected = project( event, i, event.getActionMasked(), event.getDownTime() );
        try { return target.dispatch( projected ); }
        finally { projected.recycle(); }
      }
    }
    return true;
  }

  void cancel( Dispatch target )
  {
    penId = -1;
    awaitingDown = true;
    if ( lastDelivered != null ) {
      MotionEvent canceled = lastDelivered;
      lastDelivered = null;
      canceled.setAction( MotionEvent.ACTION_CANCEL );
      try { target.dispatch( canceled ); }
      finally { canceled.recycle(); }
    }
  }

  private boolean deliver( MotionEvent event, Dispatch target )
  {
    if ( lastDelivered != null ) { lastDelivered.recycle(); lastDelivered = null; }
    int action = event.getActionMasked();
    if ( action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL ) {
      lastDelivered = MotionEvent.obtain( event );
    }
    return target.dispatch( event );
  }

  private boolean projectAndDeliver( MotionEvent event, int index, int action, Dispatch target )
  {
    MotionEvent projected = project( event, index, action, penDownTime );
    try { return deliver( projected, target ); }
    finally { projected.recycle(); }
  }

  private static MotionEvent project( MotionEvent event, int index, int action, long downTime )
  {
    MotionEvent.PointerProperties properties = new MotionEvent.PointerProperties();
    event.getPointerProperties( index, properties );
    MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
    int history = ( action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_HOVER_MOVE )
        ? event.getHistorySize() : 0;
    if ( history > 0 ) event.getHistoricalPointerCoords( index, 0, coords );
    else event.getPointerCoords( index, coords );
    MotionEvent projected = MotionEvent.obtain( downTime,
        history > 0 ? event.getHistoricalEventTime( 0 ) : event.getEventTime(), action, 1,
        new MotionEvent.PointerProperties[] { properties }, new MotionEvent.PointerCoords[] { coords },
        event.getMetaState(), event.getButtonState(), event.getXPrecision(), event.getYPrecision(),
        event.getDeviceId(), event.getEdgeFlags(), event.getSource(), event.getFlags() );
    for ( int h = 1; h < history; ++h ) {
      event.getHistoricalPointerCoords( index, h, coords );
      projected.addBatch( event.getHistoricalEventTime( h ), new MotionEvent.PointerCoords[] { coords }, event.getMetaState() );
    }
    if ( history > 0 ) {
      event.getPointerCoords( index, coords );
      projected.addBatch( event.getEventTime(), new MotionEvent.PointerCoords[] { coords }, event.getMetaState() );
    }
    return projected;
  }
}
