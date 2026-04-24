/* @file ActionKeyBindingManager.java
 *
 * @author MuddyMohawk
 * @date apr 2026
 *
 * @brief Central dispatcher for Samsung Active Key bindings
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

import com.topodroid.prefs.TDSetting;

import android.content.Intent;
import android.os.SystemClock;
import android.view.KeyEvent;

public class ActionKeyBindingManager
{
  public static final String ACTION_HARD_KEY_REPORT = "com.samsung.android.knox.intent.action.HARD_KEY_REPORT";
  public static final String EXTRA_KEY_CODE         = "com.samsung.android.knox.intent.extra.KEY_CODE";
  public static final String EXTRA_KEY_REPORT_TYPE  = "com.samsung.android.knox.intent.extra.KEY_REPORT_TYPE";
  public static final String META_HARD_KEY_PRESS    = "com.samsung.android.knox.intent.action.HARD_KEY_PRESS";

  public static final int KEYCODE_PTT         = 1015;
  public static final int KEYCODE_EMERGENCY   = 1079;
  public static final int KEY_ACTION_DOWN     = 1;
  public static final int KEY_ACTION_UP       = 2;

  private static final long SOURCE_PRIORITY_WINDOW = 1000L;
  private static final long DUPLICATE_EDGE_WINDOW = 300L;
  private static final int SOURCE_NONE = 0;
  private static final int SOURCE_DIRECT = 1;
  private static final int SOURCE_BROADCAST = 2;

  private static final ActionKeyBindingManager mInstance = new ActionKeyBindingManager();

  private final ActiveKeyGestureHelper mGestureHelper = new ActiveKeyGestureHelper(
    new ActiveKeyGestureHelper.TriggerListener() {
      @Override
      public void onActiveKeyTrigger( int trigger )
      {
        dispatchTrigger( trigger );
      }
    }
  );

  private ActionBindingHost mHost = null;
  private int mPreferredSource = SOURCE_NONE;
  private long mPreferredSourceUntil = 0L;
  private int mLastEdgeType = 0;
  private int mLastEdgeSource = SOURCE_NONE;
  private long mLastEdgeTime = 0L;

  private ActionKeyBindingManager() {}

  public static void registerHost( ActionBindingHost host )
  {
    mInstance.doRegisterHost( host );
  }

  public static void unregisterHost( ActionBindingHost host )
  {
    mInstance.doUnregisterHost( host );
  }

  public static boolean onKeyDown( int code, KeyEvent event )
  {
    return mInstance.doOnKeyDown( code, event );
  }

  public static boolean onKeyUp( int code, KeyEvent event )
  {
    return mInstance.doOnKeyUp( code, event );
  }

  public static void onBroadcastIntent( Intent intent )
  {
    mInstance.doOnBroadcastIntent( intent );
  }

  private synchronized void doRegisterHost( ActionBindingHost host )
  {
    if ( mHost != host ) {
      mGestureHelper.cancel();
    }
    mHost = host;
  }

  private synchronized void doUnregisterHost( ActionBindingHost host )
  {
    if ( mHost == host ) {
      mHost = null;
      mGestureHelper.cancel();
    }
  }

  private synchronized boolean hasHost()
  {
    return mHost != null;
  }

  private synchronized boolean isDuplicateEdge( int source, int edgeType, long when )
  {
    return ( mLastEdgeType == edgeType )
        && ( mLastEdgeSource != SOURCE_NONE )
        && ( mLastEdgeSource != source )
        && ( when - mLastEdgeTime <= DUPLICATE_EDGE_WINDOW );
  }

  private synchronized void recordEdge( int source, int edgeType, long when )
  {
    mLastEdgeSource = source;
    mLastEdgeType   = edgeType;
    mLastEdgeTime   = when;
  }

  private synchronized boolean ignoreOtherSource( int source, long when )
  {
    return ( mPreferredSource != SOURCE_NONE )
        && ( mPreferredSource != source )
        && ( when < mPreferredSourceUntil );
  }

  private synchronized void preferSource( int source, long when )
  {
    mPreferredSource = source;
    mPreferredSourceUntil = when + SOURCE_PRIORITY_WINDOW;
  }

  private void dispatchTrigger( int trigger )
  {
    int action = TDSetting.SPEN_ACTION_NONE;
    switch ( trigger ) {
      case ActiveKeyGestureHelper.TRIGGER_SINGLE_PRESS:
        action = TDSetting.mActiveKeySinglePressAction;
        break;
      case ActiveKeyGestureHelper.TRIGGER_LONG_PRESS:
        action = TDSetting.mActiveKeyLongPressAction;
        break;
      case ActiveKeyGestureHelper.TRIGGER_DOUBLE_PRESS:
        action = TDSetting.mActiveKeyDoublePressAction;
        break;
      default:
        break;
    }
    if ( action == TDSetting.SPEN_ACTION_NONE ) return;

    ActionBindingHost host;
    synchronized ( this ) {
      host = mHost;
    }
    if ( host != null ) {
      host.handleActionBindingAction( action );
    }
  }

  private boolean doOnKeyDown( int code, KeyEvent event )
  {
    if ( code != KEYCODE_PTT ) return false;
    long now = ( event != null ) ? event.getEventTime() : SystemClock.uptimeMillis();
    if ( ! hasHost() ) return false;
    if ( event != null && event.getRepeatCount() > 0 ) return true;
    if ( ignoreOtherSource( SOURCE_DIRECT, now ) ) return true;
    if ( isDuplicateEdge( SOURCE_DIRECT, KEY_ACTION_DOWN, now ) ) return true;
    preferSource( SOURCE_DIRECT, now );
    recordEdge( SOURCE_DIRECT, KEY_ACTION_DOWN, now );
    mGestureHelper.onKeyDown( now );
    return true;
  }

  private boolean doOnKeyUp( int code, KeyEvent event )
  {
    if ( code != KEYCODE_PTT ) return false;
    long now = ( event != null ) ? event.getEventTime() : SystemClock.uptimeMillis();
    if ( ! hasHost() ) return false;
    if ( ignoreOtherSource( SOURCE_DIRECT, now ) ) return true;
    if ( isDuplicateEdge( SOURCE_DIRECT, KEY_ACTION_UP, now ) ) return true;
    preferSource( SOURCE_DIRECT, now );
    recordEdge( SOURCE_DIRECT, KEY_ACTION_UP, now );
    mGestureHelper.onKeyUp( now );
    return true;
  }

  private void doOnBroadcastIntent( Intent intent )
  {
    if ( intent == null ) return;
    if ( ! ACTION_HARD_KEY_REPORT.equals( intent.getAction() ) ) return;

    int keyCode = intent.getIntExtra( EXTRA_KEY_CODE, 0 );
    int reportType = intent.getIntExtra( EXTRA_KEY_REPORT_TYPE, 0 );
    if ( keyCode != KEYCODE_PTT ) return;
    if ( ! hasHost() ) return;

    long now = SystemClock.uptimeMillis();
    if ( reportType == KEY_ACTION_DOWN ) {
      if ( ignoreOtherSource( SOURCE_BROADCAST, now ) ) return;
      if ( isDuplicateEdge( SOURCE_BROADCAST, KEY_ACTION_DOWN, now ) ) return;
      preferSource( SOURCE_BROADCAST, now );
      recordEdge( SOURCE_BROADCAST, KEY_ACTION_DOWN, now );
      mGestureHelper.onKeyDown( now );
    } else if ( reportType == KEY_ACTION_UP ) {
      if ( ignoreOtherSource( SOURCE_BROADCAST, now ) ) return;
      if ( isDuplicateEdge( SOURCE_BROADCAST, KEY_ACTION_UP, now ) ) return;
      preferSource( SOURCE_BROADCAST, now );
      recordEdge( SOURCE_BROADCAST, KEY_ACTION_UP, now );
      mGestureHelper.onKeyUp( now );
    }
  }
}
