/* @file SPenGestureHelper.java
 * @author MuddyMohawk
 * Copyright This software is distributed under GPL-3.0 or later.
 * See the file COPYING.
 * Shared S Pen button recognizer. Ordinary shortcuts remain hover gestures;
 * held canvas actions follow the button across hover/contact transitions. */
package com.topodroid.TDX;

import com.topodroid.prefs.TDSetting;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;

public class SPenGestureHelper
{
  public static final int TRIGGER_SINGLE_CLICK = ActiveKeyGestureHelper.TRIGGER_SINGLE_PRESS;
  public static final int TRIGGER_LONG_CLICK = ActiveKeyGestureHelper.TRIGGER_LONG_PRESS;
  public static final int TRIGGER_DOUBLE_CLICK = ActiveKeyGestureHelper.TRIGGER_DOUBLE_PRESS;
  private static final int SPEN_BUTTON_MASK = MotionEvent.BUTTON_STYLUS_PRIMARY | MotionEvent.BUTTON_STYLUS_SECONDARY;

  public interface TriggerListener { void onSPenTrigger( int trigger ); }

  private final Handler mHandler = new Handler( Looper.getMainLooper() );
  private final ActiveKeyGestureHelper mGesture;
  private final Runnable mHoverExit = new Runnable() {
    @Override public void run() { mGesture.cancel(); }
  };

  public SPenGestureHelper( TriggerListener listener ) { this( listener, null ); }

  public SPenGestureHelper( TriggerListener listener, ActiveKeyGestureHelper.HeldActionListener heldListener )
  {
    mGesture = new ActiveKeyGestureHelper(
      trigger -> { if ( listener != null ) listener.onSPenTrigger( trigger ); },
      () -> TDSetting.mSPenDoubleClickAction != TDSetting.SPEN_ACTION_NONE,
      new ActiveKeyGestureHelper.HeldActionListener() {
        @Override public boolean onHeldActionStart( int trigger ) {
          if ( ! TDSetting.isHeldAction( getConfiguredAction( trigger ) ) ) return false;
          if ( heldListener != null ) heldListener.onHeldActionStart( trigger );
          return true;
        }
        @Override public void onHeldActionEnd() {
          if ( heldListener != null ) heldListener.onHeldActionEnd();
        }
      } );
  }

  public static int getConfiguredAction( int trigger )
  {
    switch ( trigger ) {
      case TRIGGER_SINGLE_CLICK: return TDSetting.mSPenSingleClickAction;
      case TRIGGER_LONG_CLICK: return TDSetting.mSPenLongClickAction;
      case TRIGGER_DOUBLE_CLICK: return TDSetting.mSPenDoubleClickAction;
      default: return TDSetting.SPEN_ACTION_NONE;
    }
  }

  private static boolean hasHeldBinding()
  {
    return TDSetting.isHeldAction( TDSetting.mSPenSingleClickAction )
        || TDSetting.isHeldAction( TDSetting.mSPenLongClickAction );
  }

  boolean awaitingHeldAction() { return hasHeldBinding() && mGesture.isButtonDown(); }

  private static boolean isStylusToolEvent( MotionEvent event )
  {
    for ( int i = 0; i < event.getPointerCount(); ++i ) {
      int tool = event.getToolType( i );
      if ( tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER ) return true;
    }
    return false;
  }

  /** Samsung uses separate contact actions while the pen button is held.
   * Convert before window dispatch/filtering so they reach the canvas as touch,
   * preserving genuine hover and leaving ordinary button shortcuts unchanged.
   */
  static MotionEvent normalizeHeldContact( MotionEvent event )
  {
    if ( ! hasHeldBinding() || ! isStylusToolEvent( event ) ) return null;
    int action;
    switch ( event.getActionMasked() ) {
      case 211: action = MotionEvent.ACTION_DOWN; break; // Samsung ACTION_PEN_DOWN
      case 212: action = MotionEvent.ACTION_UP; break; // Samsung ACTION_PEN_UP
      case 213: action = MotionEvent.ACTION_MOVE; break; // Samsung ACTION_PEN_MOVE
      case 214: action = MotionEvent.ACTION_CANCEL; break; // Samsung ACTION_PEN_CANCEL
      default: return null;
    }
    MotionEvent contact = MotionEvent.obtain( event );
    contact.setAction( action );
    return contact;
  }

  private void updateButtonState( MotionEvent event )
  {
    if ( (event.getButtonState() & SPEN_BUTTON_MASK) != 0 ) mGesture.onKeyDown( event.getEventTime() );
    else mGesture.onKeyUp( event.getEventTime() );
  }

  public void cancel()
  {
    mHandler.removeCallbacks( mHoverExit );
    mGesture.cancel();
  }

  /** Contact events are already filtered by the stylus-only input guard. */
  void onTouch( MotionEvent event )
  {
    if ( ! isStylusToolEvent( event ) || ! hasHeldBinding() ) return;
    mHandler.removeCallbacks( mHoverExit );
    if ( event.getActionMasked() == MotionEvent.ACTION_CANCEL ) cancel();
    else updateButtonState( event );
  }

  public boolean onGenericMotion( MotionEvent event, boolean touchActive )
  {
    if ( ! isStylusToolEvent( event ) || (touchActive && ! hasHeldBinding()) ) return false;
    switch ( event.getActionMasked() ) {
      case MotionEvent.ACTION_BUTTON_PRESS:
      case MotionEvent.ACTION_BUTTON_RELEASE:
      case MotionEvent.ACTION_HOVER_ENTER:
      case MotionEvent.ACTION_HOVER_MOVE:
        mHandler.removeCallbacks( mHoverExit );
        boolean wasDown = mGesture.isButtonDown();
        updateButtonState( event );
        return wasDown != mGesture.isButtonDown();
      case MotionEvent.ACTION_HOVER_EXIT:
        if ( ! mGesture.isButtonDown() ) return false;
        if ( hasHeldBinding() ) {
          // Android sends hover-exit just before contact. Allow that transition,
          // but cancel promptly if the pen actually leaves proximity.
          mHandler.removeCallbacks( mHoverExit );
          mHandler.postDelayed( mHoverExit, TDSetting.getSPenTransitionMs() );
        } else cancel();
        return true;
      default: return false;
    }
  }
}
