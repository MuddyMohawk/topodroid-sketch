/* @file ActiveKeyGestureHelper.java
 *
 * @author MuddyMohawk
 * @date apr 2026
 *
 * @brief Shared recognizer for Samsung Active Key single/long/double press gestures
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

import com.topodroid.prefs.TDSetting;

import android.os.Handler;
import android.os.Looper;
import android.view.ViewConfiguration;

public class ActiveKeyGestureHelper
{
  public static final int TRIGGER_SINGLE_PRESS = 1;
  public static final int TRIGGER_LONG_PRESS   = 2;
  public static final int TRIGGER_DOUBLE_PRESS = 3;

  public interface TriggerListener
  {
    void onActiveKeyTrigger( int trigger );
  }

  private final Handler mHandler = new Handler( Looper.getMainLooper() );
  private final int mLongPressTimeout = ViewConfiguration.getLongPressTimeout();
  private final int mDoubleTapTimeout = ViewConfiguration.getDoubleTapTimeout();
  private final TriggerListener mListener;

  private boolean mButtonDown = false;
  private boolean mLongPressTriggered = false;
  private boolean mPendingSinglePress = false;
  private long mPendingSinglePressTime = 0L;

  private final Runnable mLongPressRunnable = new Runnable() {
    @Override
    public void run()
    {
      if ( ! mButtonDown ) return;
      mLongPressTriggered = true;
      cancelPendingSinglePress();
      dispatchTrigger( TRIGGER_LONG_PRESS );
    }
  };

  private final Runnable mSinglePressRunnable = new Runnable() {
    @Override
    public void run()
    {
      flushPendingSinglePress();
    }
  };

  public ActiveKeyGestureHelper( TriggerListener listener )
  {
    mListener = listener;
  }

  private void dispatchTrigger( int trigger )
  {
    if ( mListener != null ) mListener.onActiveKeyTrigger( trigger );
  }

  private void cancelLongPress()
  {
    mHandler.removeCallbacks( mLongPressRunnable );
  }

  private void cancelPendingSinglePress()
  {
    mHandler.removeCallbacks( mSinglePressRunnable );
    mPendingSinglePress = false;
    mPendingSinglePressTime = 0L;
  }

  private void flushPendingSinglePress()
  {
    if ( ! mPendingSinglePress ) return;
    cancelPendingSinglePress();
    dispatchTrigger( TRIGGER_SINGLE_PRESS );
  }

  public void cancel()
  {
    cancelLongPress();
    cancelPendingSinglePress();
    mButtonDown = false;
    mLongPressTriggered = false;
  }

  private void scheduleSinglePress( long eventTime )
  {
    if ( TDSetting.mActiveKeyDoublePressAction == TDSetting.SPEN_ACTION_NONE ) {
      dispatchTrigger( TRIGGER_SINGLE_PRESS );
      return;
    }
    cancelPendingSinglePress();
    mPendingSinglePress = true;
    mPendingSinglePressTime = eventTime;
    mHandler.postDelayed( mSinglePressRunnable, mDoubleTapTimeout );
  }

  public void onKeyDown( long eventTime )
  {
    if ( mPendingSinglePress && eventTime - mPendingSinglePressTime > mDoubleTapTimeout ) {
      flushPendingSinglePress();
    }
    if ( mButtonDown ) return;
    mButtonDown = true;
    mLongPressTriggered = false;
    cancelLongPress();
    mHandler.postDelayed( mLongPressRunnable, mLongPressTimeout );
  }

  public void onKeyUp( long eventTime )
  {
    if ( ! mButtonDown ) return;
    mButtonDown = false;
    cancelLongPress();
    if ( mLongPressTriggered ) {
      mLongPressTriggered = false;
      return;
    }
    if ( mPendingSinglePress ) {
      if ( eventTime - mPendingSinglePressTime <= mDoubleTapTimeout ) {
        cancelPendingSinglePress();
        dispatchTrigger( TRIGGER_DOUBLE_PRESS );
      } else {
        flushPendingSinglePress();
        scheduleSinglePress( eventTime );
      }
      return;
    }
    scheduleSinglePress( eventTime );
  }
}
