package com.topodroid.TDX;

import com.topodroid.prefs.TDSetting;
import android.view.MotionEvent;
import java.util.LinkedHashMap;

/** A navigation overlay: tool and symbol selection never change. */
final class HeldNavigationController
{
  interface Canvas {
    void pan( float dx, float dy );
    void zoom( float factor, float focusX, float focusY );
  }

  private final LinkedHashMap<Integer, Integer> mSources = new LinkedHashMap<>();
  private boolean mConsumeUntilUp;
  private int mPointerId = -1;
  private boolean mHoverAnchored;
  private float mLastX, mLastY, mFocusX, mFocusY;

  boolean active() { return ! mSources.isEmpty(); }
  boolean consumingContact() { return mConsumeUntilUp; }
  void reserveContact() { mConsumeUntilUp = true; }

  void begin( int source, int action, boolean touchActive )
  {
    mSources.remove( source );
    mSources.put( source, action );
    mPointerId = -1;
    mHoverAnchored = false;
    mConsumeUntilUp |= touchActive;
  }

  void end( int source )
  {
    mSources.remove( source );
    mPointerId = -1;
    mHoverAnchored = false;
  }

  void cancel()
  {
    mSources.clear();
    mPointerId = -1;
    mHoverAnchored = false;
    // Keep consuming a contact that began as navigation until it lifts.
  }

  private void anchor( MotionEvent event, int index )
  {
    mPointerId = event.getPointerId( index );
    mFocusX = mLastX = event.getX( index );
    mFocusY = mLastY = event.getY( index );
  }

  boolean onTouch( MotionEvent event, boolean awaitingPenHold, float density, Canvas canvas )
  {
    mHoverAnchored = false;
    int kind = event.getActionMasked();
    if ( kind == MotionEvent.ACTION_DOWN ) {
      mConsumeUntilUp = active() || awaitingPenHold;
      mPointerId = -1;
    }
    if ( ! active() && ! awaitingPenHold && ! mConsumeUntilUp ) return false;
    mConsumeUntilUp = true;
    if ( kind == MotionEvent.ACTION_UP || kind == MotionEvent.ACTION_CANCEL ) {
      mConsumeUntilUp = false;
      mPointerId = -1;
      return true;
    }
    if ( kind == MotionEvent.ACTION_POINTER_UP && event.getPointerId( event.getActionIndex() ) == mPointerId ) {
      mPointerId = -1;
      return true;
    }
    if ( ! active() ) return true;
    int index = event.findPointerIndex( mPointerId );
    if ( index < 0 ) { anchor( event, 0 ); return true; }
    if ( kind != MotionEvent.ACTION_MOVE ) return true;
    move( event.getX( index ), event.getY( index ), density, canvas );
    return true;
  }

  /** A held S Pen may report movement as hover even before it touches the canvas. */
  boolean onGenericMotion( MotionEvent event, boolean touchActive, float density, Canvas canvas )
  {
    int kind = event.getActionMasked();
    if ( kind == MotionEvent.ACTION_HOVER_EXIT ) { mHoverAnchored = false; return false; }
    if ( touchActive || ! active() ) return false;
    if ( kind != MotionEvent.ACTION_HOVER_ENTER && kind != MotionEvent.ACTION_HOVER_MOVE
        && kind != MotionEvent.ACTION_BUTTON_PRESS ) return false;
    int index = -1;
    for ( int i = 0; i < event.getPointerCount(); ++i ) {
      if ( StylusInputFilter.isPen( event, i ) ) { index = i; break; }
    }
    if ( index < 0 ) return false;
    if ( ! mHoverAnchored || kind != MotionEvent.ACTION_HOVER_MOVE ) {
      anchor( event, index );
      mHoverAnchored = true;
    } else move( event.getX( index ), event.getY( index ), density, canvas );
    return true;
  }

  private void move( float x, float y, float density, Canvas canvas )
  {
    int action = TDSetting.SPEN_ACTION_NONE;
    for ( int held : mSources.values() ) action = held;
    if ( action == TDSetting.SPEN_ACTION_HOLD_PAN ) canvas.pan( x - mLastX, y - mLastY );
    else if ( action == TDSetting.SPEN_ACTION_HOLD_ZOOM ) {
      // Up zooms in; 160 dp doubles the scale. Anchor the initial contact point.
      float exponent = Math.max( -2f, Math.min( 2f, (mLastY - y) / (160f * density) ) );
      canvas.zoom( (float)Math.pow( 2, exponent ), mFocusX, mFocusY );
    }
    mLastX = x; mLastY = y;
  }
}
