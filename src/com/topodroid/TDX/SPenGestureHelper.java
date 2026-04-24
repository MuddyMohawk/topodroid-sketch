/* @file SPenGestureHelper.java
 *
 * @author MuddyMohawk
 * @date apr 2026
 *
 * @brief Shared hover-button recognizer for passive S Pen actions
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

import com.topodroid.prefs.TDSetting;

import android.os.Handler;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

public class SPenGestureHelper
{
  public static final int TRIGGER_SINGLE_CLICK = 1;
  public static final int TRIGGER_LONG_CLICK   = 2;
  public static final int TRIGGER_DOUBLE_CLICK = 3;

  private static final int SPEN_BUTTON_MASK = MotionEvent.BUTTON_STYLUS_PRIMARY | MotionEvent.BUTTON_STYLUS_SECONDARY;

  public interface TriggerListener
  {
    void onSPenTrigger( int trigger );
  }

  private final Handler mHandler = new Handler();
  private final int mLongPressTimeout = ViewConfiguration.getLongPressTimeout();
  private final int mDoubleTapTimeout = ViewConfiguration.getDoubleTapTimeout();
  private final TriggerListener mListener;

  private boolean mButtonDown = false;
  private boolean mLongClickTriggered = false;
  private boolean mPendingSingleClick = false;
  private long mPendingSingleClickTime = 0L;
  private int mLastHoverButtons = 0;

  private final Runnable mLongClickRunnable = new Runnable() {
    @Override
    public void run()
    {
      if ( ! mButtonDown ) return;
      mLongClickTriggered = true;
      cancelPendingSingleClick();
      dispatchTrigger( TRIGGER_LONG_CLICK );
    }
  };

  private final Runnable mSingleClickRunnable = new Runnable() {
    @Override
    public void run()
    {
      flushPendingSingleClick();
    }
  };

  public SPenGestureHelper( TriggerListener listener )
  {
    mListener = listener;
  }

  public static int getConfiguredAction( int trigger )
  {
    switch ( trigger ) {
      case TRIGGER_SINGLE_CLICK: return TDSetting.mSPenSingleClickAction;
      case TRIGGER_LONG_CLICK:   return TDSetting.mSPenLongClickAction;
      case TRIGGER_DOUBLE_CLICK: return TDSetting.mSPenDoubleClickAction;
      default:                   return TDSetting.SPEN_ACTION_NONE;
    }
  }

  private void dispatchTrigger( int trigger )
  {
    if ( mListener != null ) mListener.onSPenTrigger( trigger );
  }

  private static boolean isActionButtonPressed( int buttons )
  {
    return ( buttons & SPEN_BUTTON_MASK ) != 0;
  }

  private static boolean isStylusToolEvent( MotionEvent event )
  {
    int np = event.getPointerCount();
    for ( int i = 0; i < np; ++i ) {
      int toolType = event.getToolType( i );
      if ( toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER ) {
        return true;
      }
    }
    return false;
  }

  private void cancelLongClick()
  {
    mHandler.removeCallbacks( mLongClickRunnable );
  }

  private void cancelPendingSingleClick()
  {
    mHandler.removeCallbacks( mSingleClickRunnable );
    mPendingSingleClick = false;
    mPendingSingleClickTime = 0L;
  }

  private void flushPendingSingleClick()
  {
    if ( ! mPendingSingleClick ) return;
    cancelPendingSingleClick();
    dispatchTrigger( TRIGGER_SINGLE_CLICK );
  }

  private void cancelGesture()
  {
    cancelLongClick();
    cancelPendingSingleClick();
    mButtonDown = false;
    mLongClickTriggered = false;
    mLastHoverButtons = 0;
  }

  public void cancel()
  {
    cancelGesture();
  }

  private void startButtonGesture( long eventTime )
  {
    if ( mPendingSingleClick && eventTime - mPendingSingleClickTime > mDoubleTapTimeout ) {
      flushPendingSingleClick();
    }
    if ( mButtonDown ) return;
    mButtonDown = true;
    mLongClickTriggered = false;
    cancelLongClick();
    mHandler.postDelayed( mLongClickRunnable, mLongPressTimeout );
  }

  private void scheduleSingleClick( long eventTime )
  {
    if ( TDSetting.mSPenDoubleClickAction == TDSetting.SPEN_ACTION_NONE ) {
      dispatchTrigger( TRIGGER_SINGLE_CLICK );
      return;
    }
    cancelPendingSingleClick();
    mPendingSingleClick = true;
    mPendingSingleClickTime = eventTime;
    mHandler.postDelayed( mSingleClickRunnable, mDoubleTapTimeout );
  }

  private void finishButtonGesture( long eventTime )
  {
    if ( ! mButtonDown ) return;
    mButtonDown = false;
    cancelLongClick();
    if ( mLongClickTriggered ) {
      mLongClickTriggered = false;
      return;
    }
    if ( mPendingSingleClick ) {
      if ( eventTime - mPendingSingleClickTime <= mDoubleTapTimeout ) {
        cancelPendingSingleClick();
        dispatchTrigger( TRIGGER_DOUBLE_CLICK );
      } else {
        flushPendingSingleClick();
        scheduleSingleClick( eventTime );
      }
      return;
    }
    scheduleSingleClick( eventTime );
  }

  private boolean updateButtonState( int buttons, long eventTime )
  {
    boolean pressed = isActionButtonPressed( buttons );
    if ( pressed ) {
      if ( ! mButtonDown ) {
        startButtonGesture( eventTime );
        return true;
      }
    } else if ( mButtonDown ) {
      finishButtonGesture( eventTime );
      return true;
    }
    return false;
  }

  public boolean onGenericMotion( MotionEvent rawEvent, boolean touchActive )
  {
    if ( ! isStylusToolEvent( rawEvent ) || touchActive ) return false;

    int action = rawEvent.getAction() & MotionEvent.ACTION_MASK;
    int buttons = rawEvent.getButtonState() & SPEN_BUTTON_MASK;
    boolean handled = false;

    switch ( action ) {
      case MotionEvent.ACTION_BUTTON_PRESS:
      case MotionEvent.ACTION_BUTTON_RELEASE:
        handled = updateButtonState( buttons, rawEvent.getEventTime() );
        break;
      case MotionEvent.ACTION_HOVER_ENTER:
      case MotionEvent.ACTION_HOVER_MOVE:
        if ( isActionButtonPressed( mLastHoverButtons ) != isActionButtonPressed( buttons ) ) {
          handled = updateButtonState( buttons, rawEvent.getEventTime() );
        }
        break;
      case MotionEvent.ACTION_HOVER_EXIT:
        if ( mButtonDown ) {
          cancelGesture();
          handled = true;
        }
        break;
      default:
        break;
    }

    mLastHoverButtons = buttons;
    return handled;
  }
}
