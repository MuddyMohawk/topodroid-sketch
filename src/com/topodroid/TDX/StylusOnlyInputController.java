package com.topodroid.TDX;

import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.KeyboardShortcutGroup;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SearchEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ZoomButtonsController;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/** Session-wide pen protection. Every guarded surface reads this single state. */
public final class StylusOnlyInputController
{
  public static final String PREFERENCE = "DISTOX_STYLUS_ONLY_INPUT";
  static final long ESCAPE_HOLD_MS = 5000;
  private static final Handler handler = new Handler( Looper.getMainLooper() );
  private static final ArrayList<WeakReference<Guard>> guards = new ArrayList<>();
  private static final ArrayList<WeakReference<ProtectedZoomController>> zooms = new ArrayList<>();
  private static Context appContext;
  private static boolean enabled;
  private static int dispatchDepth;
  private static WeakReference<Activity> foreground = new WeakReference<>( null );

  private interface Guard { void cancel(); void refresh(); }

  private StylusOnlyInputController() { }

  public static boolean isEnabled() { return enabled; }

  public static int zoomControlMode() { return enabled ? 2 : com.topodroid.prefs.TDSetting.mZoomCtrl; }

  public static void initialize( Application app )
  {
    appContext = app.getApplicationContext();
    enabled = false;
    PreferenceManager.getDefaultSharedPreferences( app ).edit().putBoolean( PREFERENCE, false ).apply();
    app.registerActivityLifecycleCallbacks( new Application.ActivityLifecycleCallbacks() {
      @Override public void onActivityCreated( Activity activity, Bundle state ) { guardWindow( activity.getWindow() ); }
      @Override public void onActivityStarted( Activity activity ) { guardWindow( activity.getWindow() ); }
      @Override public void onActivityResumed( Activity activity ) {
        foreground = new WeakReference<>( activity );
        refreshActivity( activity );
      }
      @Override public void onActivityPaused( Activity activity ) { cancelRecovery(); }
      @Override public void onActivityStopped( Activity activity ) { }
      @Override public void onActivitySaveInstanceState( Activity activity, Bundle state ) { }
      @Override public void onActivityDestroyed( Activity activity ) {
        if ( foreground.get() == activity ) foreground.clear();
      }
    } );
  }

  public static void setEnabled( final boolean value )
  {
    // Finish the enabling click before canceling old gestures or rebuilding its settings row.
    if ( dispatchDepth > 0 || Looper.myLooper() != Looper.getMainLooper() ) {
      handler.post( () -> setEnabled( value ) );
      return;
    }
    if ( enabled == value ) return;
    cancelRecovery();
    for ( Guard guard : liveGuards() ) guard.cancel();
    enabled = value;
    PreferenceManager.getDefaultSharedPreferences( appContext ).edit().putBoolean( PREFERENCE, value ).apply();
    ActionKeyBindingManager.cancelVolumeDownGesture();
    for ( Guard guard : liveGuards() ) guard.refresh();
    refreshZooms();
    refreshActivity( foreground.get() );
    com.topodroid.prefs.TDPrefActivity.reloadPreferences();
    Toast.makeText( appContext, value ? R.string.stylus_only_enabled : R.string.stylus_only_disabled,
        value ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT ).show();
  }

  private static void refreshActivity( Activity activity )
  {
    if ( activity instanceof DrawingWindow ) ( (DrawingWindow)activity ).refreshStylusOnlyUi();
    if ( activity instanceof SketchWindow ) ( (SketchWindow)activity ).refreshStylusOnlyUi();
    if ( activity instanceof OverviewWindow ) ( (OverviewWindow)activity ).refreshStylusOnlyUi();
    if ( activity instanceof PlotReloadWindow ) ( (PlotReloadWindow)activity ).refreshStylusOnlyUi();
  }

  private static ArrayList<Guard> liveGuards()
  {
    ArrayList<Guard> live = new ArrayList<>();
    for ( int i = guards.size() - 1; i >= 0; --i ) {
      Guard guard = guards.get( i ).get();
      if ( guard == null ) guards.remove( i );
      else live.add( guard );
    }
    return live;
  }

  public static void guardWindow( Window window )
  {
    if ( window == null || window.getCallback() instanceof ProtectedWindowCallback ) return;
    ProtectedWindowCallback guard = new ProtectedWindowCallback( window );
    window.setCallback( guard );
    guards.add( new WeakReference<Guard>( guard ) );
    guard.refresh();
  }

  /** Use before showing dialogs not derived from MyDialog. Does not replace their listeners. */
  public static <T extends Dialog> T guardDialog( T dialog )
  {
    guardWindow( dialog.getWindow() );
    return dialog;
  }

  public static PopupWindow guardPopup( PopupWindow popup )
  {
    View content = popup.getContentView();
    if ( ! ( content instanceof ProtectedLayout ) ) {
      ProtectedLayout guard = new ProtectedLayout( content.getContext() );
      guard.addView( content, new FrameLayout.LayoutParams( ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.WRAP_CONTENT ) );
      popup.setContentView( guard );
      // Outside touches normally dismiss PopupWindow before the content receives them.
      popup.setTouchInterceptor( ( view, event ) -> {
        if ( ! enabled || event.getActionMasked() == MotionEvent.ACTION_CANCEL ) return false;
        for ( int i = 0; i < event.getPointerCount(); ++i ) {
          if ( StylusInputFilter.isPen( event, i ) ) return false;
        }
        return true;
      } );
    }
    return popup;
  }

  public static ZoomButtonsController createZoomControls( View owner )
  {
    ProtectedZoomController zoom = new ProtectedZoomController( owner );
    zooms.add( new WeakReference<>( zoom ) );
    return zoom;
  }

  private static void refreshZooms()
  {
    for ( int i = zooms.size() - 1; i >= 0; --i ) {
      ProtectedZoomController zoom = zooms.get( i ).get();
      if ( zoom == null ) zooms.remove( i );
      else zoom.refresh();
    }
  }

  private static boolean dispatchTouch( StylusInputFilter filter, MotionEvent event, StylusInputFilter.Dispatch target )
  {
    ++ dispatchDepth;
    try { return filter.touch( event, enabled, target ); }
    finally { -- dispatchDepth; }
  }

  private static boolean dispatchGeneric( StylusInputFilter filter, MotionEvent event, StylusInputFilter.Dispatch target )
  {
    ++ dispatchDepth;
    try { return filter.generic( event, enabled, target ); }
    finally { -- dispatchDepth; }
  }

  // Key events are held until release so no configured action can fire before recovery.
  private static KeyEvent escapeDown;
  private static Object escapeOwner;
  private static StylusKeyDispatch escapeTarget;
  private static boolean consumeEscapeUp;
  interface StylusKeyDispatch { boolean dispatch( KeyEvent event ); }

  private static final Runnable recover = () -> {
    if ( ! enabled || escapeDown == null ) return;
    escapeDown = null;
    escapeOwner = null;
    escapeTarget = null;
    setEnabled( false );
    consumeEscapeUp = true;
    Vibrator vibrator = (Vibrator)appContext.getSystemService( Context.VIBRATOR_SERVICE );
    if ( vibrator != null && vibrator.hasVibrator() ) vibrator.vibrate( 100 );
  };

  static boolean key( Object owner, KeyEvent event, StylusKeyDispatch target )
  {
    if ( event.getKeyCode() != KeyEvent.KEYCODE_VOLUME_DOWN ) return target.dispatch( event );
    if ( consumeEscapeUp && event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0 ) {
      consumeEscapeUp = false; // A new press cannot be the release of the recovered hold.
    }
    if ( consumeEscapeUp ) {
      if ( event.getAction() == KeyEvent.ACTION_UP ) consumeEscapeUp = false;
      return true;
    }
    if ( ! enabled ) return target.dispatch( event );
    if ( event.isCanceled() ) { cancelRecovery(); return true; }
    if ( event.getAction() == KeyEvent.ACTION_DOWN ) {
      if ( event.getRepeatCount() == 0 ) {
        cancelRecovery();
        escapeDown = new KeyEvent( event );
        escapeOwner = owner;
        escapeTarget = target;
        handler.postDelayed( recover, Math.max( 0, ESCAPE_HOLD_MS - ( SystemClock.uptimeMillis() - event.getEventTime() ) ) );
      }
      return true;
    }
    if ( event.getAction() == KeyEvent.ACTION_UP ) {
      KeyEvent down = escapeDown;
      StylusKeyDispatch dispatch = escapeTarget;
      boolean sameOwner = escapeOwner == owner;
      if ( down != null && sameOwner && ! event.isCanceled()
          && event.getEventTime() - down.getEventTime() >= ESCAPE_HOLD_MS ) {
        recover.run();
        consumeEscapeUp = false;
        return true;
      }
      cancelRecovery();
      if ( down != null && sameOwner && ! event.isCanceled()
          && event.getEventTime() - down.getEventTime() < ViewConfiguration.getLongPressTimeout() ) {
        dispatch.dispatch( down );
        dispatch.dispatch( event );
      }
      return true;
    }
    return true;
  }

  private static void cancelRecovery()
  {
    handler.removeCallbacks( recover );
    escapeDown = null;
    escapeOwner = null;
    escapeTarget = null;
  }

  private static final class ProtectedLayout extends FrameLayout implements Guard
  {
    private final StylusInputFilter filter = new StylusInputFilter();
    ProtectedLayout( Context context ) {
      super( context );
      guards.add( new WeakReference<Guard>( this ) );
    }
    @Override public boolean dispatchTouchEvent( MotionEvent event ) {
      return dispatchTouch( filter, event, this::normalTouch );
    }
    private boolean normalTouch( MotionEvent event ) { return super.dispatchTouchEvent( event ); }
    @Override public boolean dispatchGenericMotionEvent( MotionEvent event ) {
      return dispatchGeneric( filter, event, super::dispatchGenericMotionEvent );
    }
    @Override public boolean dispatchKeyEvent( KeyEvent event ) { return key( this, event, super::dispatchKeyEvent ); }
    @Override public void onWindowFocusChanged( boolean focus ) {
      super.onWindowFocusChanged( focus );
      if ( ! focus ) { cancel(); cancelRecovery(); }
    }
    @Override protected void onDetachedFromWindow() { cancel(); cancelRecovery(); super.onDetachedFromWindow(); }
    @Override public void cancel() { filter.cancel( this::normalTouch ); }
    @Override public void refresh() { }
  }

  private static final class ProtectedZoomController extends ZoomButtonsController
  {
    private final View owner;
    private boolean requestedVisible;
    private boolean requestedAutoDismiss = true;
    private boolean refreshing;
    ProtectedZoomController( View view ) {
      super( view );
      owner = view;
      View controls = getZoomControls();
      ViewGroup container = getContainer();
      ViewGroup.LayoutParams params = controls.getLayoutParams();
      container.removeView( controls );
      ProtectedLayout guard = new ProtectedLayout( view.getContext() );
      guard.addView( controls );
      container.addView( guard, params );
      owner.addOnAttachStateChangeListener( new View.OnAttachStateChangeListener() {
        @Override public void onViewAttachedToWindow( View v ) { handler.post( () -> refresh() ); }
        @Override public void onViewDetachedFromWindow( View v ) { hide(); }
      } );
    }
    private void hide() { super.setVisible( false ); }
    @Override public void setVisible( boolean visible ) {
      if ( ! refreshing ) requestedVisible = visible;
      refresh();
    }
    @Override public void setAutoDismissed( boolean autoDismiss ) {
      requestedAutoDismiss = autoDismiss;
      super.setAutoDismissed( enabled ? false : autoDismiss );
    }
    void refresh() {
      if ( refreshing ) return;
      refreshing = true;
      try {
        super.setAutoDismissed( enabled ? false : requestedAutoDismiss );
        super.setVisible( owner.isShown() && owner.hasWindowFocus() && ( enabled || requestedVisible ) );
      } finally { refreshing = false; }
    }
  }

  private static final class ProtectedWindowCallback implements Window.Callback, Guard
  {
    private final WeakReference<Window> window;
    private final Window.Callback delegate;
    private final StylusInputFilter filter = new StylusInputFilter();
    private TextView badge;
    private View content;
    private int contentTopPadding;
    ProtectedWindowCallback( Window value ) { window = new WeakReference<>( value ); delegate = value.getCallback(); }
    @Override public boolean dispatchTouchEvent( MotionEvent event ) { return dispatchTouch( filter, event, delegate::dispatchTouchEvent ); }
    @Override public boolean dispatchGenericMotionEvent( MotionEvent event ) { return dispatchGeneric( filter, event, delegate::dispatchGenericMotionEvent ); }
    @Override public boolean dispatchTrackballEvent( MotionEvent event ) { return enabled || delegate.dispatchTrackballEvent( event ); }
    @Override public boolean dispatchKeyEvent( KeyEvent event ) { return key( this, event, delegate::dispatchKeyEvent ); }
    @Override public boolean dispatchKeyShortcutEvent( KeyEvent event ) { return delegate.dispatchKeyShortcutEvent( event ); }
    @Override public void cancel() { filter.cancel( delegate::dispatchTouchEvent ); }
    @Override public void refresh() {
      Window value = window.get();
      if ( value == null ) return;
      View currentContent = value.findViewById( android.R.id.content );
      if ( currentContent != content ) {
        content = currentContent;
        if ( content != null ) contentTopPadding = content.getPaddingTop();
      }
      if ( badge == null && value.getDecorView() instanceof FrameLayout ) {
        FrameLayout decor = (FrameLayout)value.getDecorView();
        badge = new TextView( decor.getContext() );
        badge.setText( R.string.stylus_only_badge );
        badge.setTextColor( 0xffffdd66 );
        badge.setBackgroundColor( 0xdd202020 );
        badge.setTextSize( 12 );
        int padding = (int)( 6 * decor.getResources().getDisplayMetrics().density );
        badge.setPadding( padding, padding / 2, padding, padding / 2 );
        badge.setClickable( false );
        badge.setFocusable( false );
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams( ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END );
        params.setMargins( padding, padding, padding, padding );
        decor.addView( badge, params );
      }
      if ( badge != null ) badge.setVisibility( enabled ? View.VISIBLE : View.GONE );
      // Reserve a narrow band so the indicator never covers a toolbar or dialog choice.
      if ( content != null && badge != null ) {
        int band = badge.getLineHeight() + badge.getPaddingTop() + badge.getPaddingBottom()
            + (int)( 12 * badge.getResources().getDisplayMetrics().density );
        content.setPadding( content.getPaddingLeft(), contentTopPadding + ( enabled ? band : 0 ),
            content.getPaddingRight(), content.getPaddingBottom() );
      }
    }
    @Override public void onWindowFocusChanged( boolean focus ) {
      if ( ! focus ) { cancel(); cancelRecovery(); }
      delegate.onWindowFocusChanged( focus );
      refreshZooms();
    }
    @Override public void onAttachedToWindow() { delegate.onAttachedToWindow(); refresh(); }
    @Override public void onDetachedFromWindow() { cancel(); cancelRecovery(); delegate.onDetachedFromWindow(); }
    @Override public void onContentChanged() { delegate.onContentChanged(); refresh(); }
    @Override public boolean dispatchPopulateAccessibilityEvent( AccessibilityEvent event ) { return delegate.dispatchPopulateAccessibilityEvent( event ); }
    @Override public View onCreatePanelView( int id ) { return delegate.onCreatePanelView( id ); }
    @Override public boolean onCreatePanelMenu( int id, Menu menu ) { return delegate.onCreatePanelMenu( id, menu ); }
    @Override public boolean onPreparePanel( int id, View view, Menu menu ) { return delegate.onPreparePanel( id, view, menu ); }
    @Override public boolean onMenuOpened( int id, Menu menu ) { return delegate.onMenuOpened( id, menu ); }
    @Override public boolean onMenuItemSelected( int id, MenuItem item ) { return delegate.onMenuItemSelected( id, item ); }
    @Override public void onWindowAttributesChanged( WindowManager.LayoutParams attrs ) { delegate.onWindowAttributesChanged( attrs ); }
    @Override public void onPanelClosed( int id, Menu menu ) { delegate.onPanelClosed( id, menu ); }
    @Override public boolean onSearchRequested() { return delegate.onSearchRequested(); }
    @Override public boolean onSearchRequested( SearchEvent event ) { return delegate.onSearchRequested( event ); }
    @Override public ActionMode onWindowStartingActionMode( ActionMode.Callback callback ) { return delegate.onWindowStartingActionMode( callback ); }
    @Override public ActionMode onWindowStartingActionMode( ActionMode.Callback callback, int type ) { return delegate.onWindowStartingActionMode( callback, type ); }
    @Override public void onActionModeStarted( ActionMode mode ) { delegate.onActionModeStarted( mode ); }
    @Override public void onActionModeFinished( ActionMode mode ) { delegate.onActionModeFinished( mode ); }
    @Override public void onProvideKeyboardShortcuts( List<KeyboardShortcutGroup> groups, Menu menu, int device ) { delegate.onProvideKeyboardShortcuts( groups, menu, device ); }
    @Override public void onPointerCaptureChanged( boolean captured ) { delegate.onPointerCaptureChanged( captured ); }
  }
}
