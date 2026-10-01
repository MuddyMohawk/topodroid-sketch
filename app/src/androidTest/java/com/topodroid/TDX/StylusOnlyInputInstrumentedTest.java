package com.topodroid.TDX;

import static org.junit.Assert.*;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.widget.PopupWindow;
import android.widget.ZoomButtonsController;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Until;
import androidx.test.uiautomator.UiObject2;
import com.topodroid.prefs.TDPrefActivity;
import com.topodroid.prefs.TDPrefCat;
import com.topodroid.prefs.TDSetting;
import com.topodroid.ui.MyDialog;
import com.topodroid.ui.MyKeyboard;
import com.topodroid.ui.StylusOnlySpinner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@RunWith( AndroidJUnit4.class )
public class StylusOnlyInputInstrumentedTest
{
  @Before @After public void resetMode() { main( () -> StylusOnlyInputController.setEnabled( false ) ); }

  private static void main( Runnable action ) { InstrumentationRegistry.getInstrumentation().runOnMainSync( action ); }
  private static Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
  private static ActivityScenario<TDPrefActivity> launch()
  {
    Intent intent = new Intent( context(), TDPrefActivity.class );
    intent.putExtra( TDPrefCat.PREF_CATEGORY, TDPrefCat.PREF_CATEGORY_ALL );
    return ActivityScenario.launch( intent );
  }

  private static MotionEvent pointer( int action, int tool, float x, float y )
  {
    return pointer( action, tool, x, y, SystemClock.uptimeMillis() );
  }

  private static MotionEvent pointer( int action, int tool, float x, float y, long downTime )
  {
    MotionEvent.PointerProperties props = new MotionEvent.PointerProperties();
    props.id = 7; props.toolType = tool;
    MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
    coords.x = x; coords.y = y; coords.pressure = 1; coords.toolMajor = 100;
    long now = SystemClock.uptimeMillis();
    return MotionEvent.obtain( downTime, now, action, 1, new MotionEvent.PointerProperties[] { props },
        new MotionEvent.PointerCoords[] { coords }, 0, 0, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0 );
  }

  private static void tap( Window window, View view, int tool )
  {
    int[] location = new int[2]; view.getLocationInWindow( location );
    for ( int action : new int[] { MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP } ) {
      MotionEvent event = pointer( action, tool, location[0] + view.getWidth()/2f, location[1] + view.getHeight()/2f );
      window.getCallback().dispatchTouchEvent( event ); event.recycle();
    }
  }

  private static TextView findText( View root, String text )
  {
    if ( root instanceof TextView && text.contentEquals( ( (TextView)root ).getText() ) ) return (TextView)root;
    if ( root instanceof ViewGroup ) {
      ViewGroup group = (ViewGroup)root;
      for ( int i = 0; i < group.getChildCount(); ++i ) {
        TextView match = findText( group.getChildAt( i ), text );
        if ( match != null ) return match;
      }
    }
    return null;
  }

  private static CheckBox settingCheck( TDPrefActivity activity )
  {
    TextView title = findText( activity.getWindow().getDecorView(), activity.getString( R.string.pref_stylus_only_title ) );
    assertNotNull( title );
    return ( (View)title.getParent() ).findViewById( R.id.checkbox );
  }

  @Test public void settingsToggleBadgeAndBackgroundStateStaySynchronized()
  {
    try ( ActivityScenario<TDPrefActivity> scenario = launch() ) {
      scenario.onActivity( activity -> {
        CheckBox checkbox = settingCheck( activity );
        assertFalse( checkbox.isChecked() );
        checkbox.requestRectangleOnScreen( new android.graphics.Rect( 0, 0, checkbox.getWidth(), checkbox.getHeight() ), true );
      } );
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      scenario.onActivity( activity -> tap( activity.getWindow(), settingCheck( activity ), MotionEvent.TOOL_TYPE_STYLUS ) );
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      scenario.onActivity( activity -> {
        assertTrue( StylusOnlyInputController.isEnabled() );
        assertTrue( settingCheck( activity ).isChecked() );
        TextView badge = findText( activity.getWindow().getDecorView(), "Pen only" );
        assertNotNull( badge ); assertTrue( badge.isShown() );
        assertFalse( badge.isClickable() ); assertFalse( badge.isFocusable() );
        settingCheck( activity ).requestRectangleOnScreen( new android.graphics.Rect( 0, 0,
            settingCheck( activity ).getWidth(), settingCheck( activity ).getHeight() ), true );
      } );
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      UiDevice device = UiDevice.getInstance( InstrumentationRegistry.getInstrumentation() );
      assertTrue( device.wait( Until.hasObject( By.text( "Pen only" ) ), 3000 ) );
      device.takeScreenshot(
          new java.io.File( context().getExternalFilesDir( null ), "stylus-settings.png" ) );
      scenario.moveToState( androidx.lifecycle.Lifecycle.State.STARTED );
      scenario.moveToState( androidx.lifecycle.Lifecycle.State.RESUMED );
      scenario.onActivity( activity -> {
        assertTrue( StylusOnlyInputController.isEnabled() );
        tap( activity.getWindow(), settingCheck( activity ), MotionEvent.TOOL_TYPE_FINGER );
        assertTrue( StylusOnlyInputController.isEnabled() );
        tap( activity.getWindow(), settingCheck( activity ), MotionEvent.TOOL_TYPE_STYLUS );
      } );
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      scenario.onActivity( activity -> {
        assertFalse( StylusOnlyInputController.isEnabled() ); assertFalse( settingCheck( activity ).isChecked() );
        assertFalse( findText( activity.getWindow().getDecorView(), "Pen only" ).isShown() );
      } );
    }
  }

  @Test public void builtInKeyboardRejectsFingerAndAcceptsPen()
  {
    try ( ActivityScenario<TDPrefActivity> scenario = launch() ) {
      AtomicReference<EditText> edit = new AtomicReference<>();
      AtomicReference<KeyboardView> keyboard = new AtomicReference<>();
      scenario.onActivity( activity -> {
        StylusOnlyInputController.setEnabled( true );
        LinearLayout root = new LinearLayout( activity ); root.setOrientation( LinearLayout.VERTICAL );
        EditText field = new EditText( activity );
        KeyboardView keys = new KeyboardView( activity, null );
        MyKeyboard input = new MyKeyboard( activity, keys, R.xml.my_keyboard_base, -1 );
        MyKeyboard.registerEditText( input, field, 0 );
        root.addView( field ); root.addView( keys ); activity.setContentView( root );
        field.requestFocus(); field.performClick(); edit.set( field ); keyboard.set( keys );
      } );
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      scenario.onActivity( activity -> {
        KeyboardView keys = keyboard.get();
        Keyboard.Key seven = keys.getKeyboard().getKeys().get( 0 );
        int[] where = new int[2]; keys.getLocationInWindow( where );
        String before = edit.get().getText().toString();
        for ( int tool : new int[] { MotionEvent.TOOL_TYPE_FINGER, MotionEvent.TOOL_TYPE_STYLUS } ) {
          for ( int action : new int[] { MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP } ) {
            MotionEvent event = pointer( action, tool, where[0] + seven.x + seven.width/2f, where[1] + seven.y + seven.height/2f );
            activity.getWindow().getCallback().dispatchTouchEvent( event ); event.recycle();
          }
          if ( tool == MotionEvent.TOOL_TYPE_FINGER ) assertEquals( before, edit.get().getText().toString() );
          else assertTrue( edit.get().getText().toString().contains( "7" ) );
        }
      } );
    }
  }

  @Test public void activityFilterCancelsHeldFingerAndSurvivesRecreation()
  {
    try ( ActivityScenario<TDPrefActivity> scenario = launch() ) {
      AtomicInteger clicks = new AtomicInteger();
      AtomicReference<Button> button = new AtomicReference<>();
      scenario.onActivity( activity -> {
        Button target = new Button( activity ); target.setText( "Target" );
        target.setOnClickListener( view -> clicks.incrementAndGet() );
        activity.setContentView( target ); button.set( target );
      } );
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      scenario.onActivity( activity -> {
        tap( activity.getWindow(), button.get(), MotionEvent.TOOL_TYPE_FINGER );
      } );
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      assertEquals( 1, clicks.get() );
      scenario.onActivity( activity -> {
        StylusOnlyInputController.setEnabled( true );
        activity.setContentView( button.get() );
      } );
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      scenario.onActivity( activity -> {
        tap( activity.getWindow(), button.get(), MotionEvent.TOOL_TYPE_FINGER );
        assertEquals( 1, clicks.get() );
        tap( activity.getWindow(), button.get(), MotionEvent.TOOL_TYPE_STYLUS );
      } );
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      assertEquals( 2, clicks.get() );
      scenario.recreate();
      scenario.onActivity( activity -> assertTrue( StylusOnlyInputController.isEnabled() ) );
    }
  }

  @Test public void customAlertAndDatePickerDialogsRejectFingerButAcceptPen()
  {
    try ( ActivityScenario<TDPrefActivity> scenario = launch() ) {
      for ( int kind = 0; kind < 3; ++kind ) {
        final int type = kind;
        AtomicInteger accepted = new AtomicInteger();
        AtomicReference<Dialog> dialog = new AtomicReference<>();
        AtomicReference<Button> button = new AtomicReference<>();
        scenario.onActivity( activity -> {
          StylusOnlyInputController.setEnabled( true );
          if ( type == 0 ) {
            MyDialog custom = new MyDialog( activity, null, 0 );
            Button target = new Button( activity ); target.setText( "Accept" );
            target.setOnClickListener( view -> accepted.incrementAndGet() );
            custom.setContentView( target ); custom.show(); dialog.set( custom ); button.set( target );
          } else if ( type == 1 ) {
            AlertDialog alert = new AlertDialog.Builder( activity ).setPositiveButton( "Accept", ( d, which ) -> accepted.incrementAndGet() ).create();
            StylusOnlyInputController.guardDialog( alert ).show(); dialog.set( alert ); button.set( alert.getButton( AlertDialog.BUTTON_POSITIVE ) );
          } else {
            DatePickerDialog date = new DatePickerDialog( activity, ( picker, year, month, day ) -> accepted.incrementAndGet(), 2026, 9, 1 );
            StylusOnlyInputController.guardDialog( date ).show(); dialog.set( date ); button.set( date.getButton( AlertDialog.BUTTON_POSITIVE ) );
          }
        } );
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        scenario.onActivity( activity -> {
          tap( dialog.get().getWindow(), button.get(), MotionEvent.TOOL_TYPE_FINGER );
          assertTrue( dialog.get().isShowing() ); assertEquals( 0, accepted.get() );
          tap( dialog.get().getWindow(), button.get(), MotionEvent.TOOL_TYPE_STYLUS );
        } );
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertEquals( 1, accepted.get() );
        scenario.onActivity( activity -> dialog.get().dismiss() );
      }
    }
  }

  @Test public void popupBlocksFingerClicksAndOutsideDismissal()
  {
    try ( ActivityScenario<TDPrefActivity> scenario = launch() ) {
      AtomicReference<PopupWindow> popup = new AtomicReference<>();
      AtomicInteger clicks = new AtomicInteger();
      scenario.onActivity( activity -> {
        StylusOnlyInputController.setEnabled( true );
        Button target = new Button( activity ); target.setText( "Popup" );
        target.setOnClickListener( view -> clicks.incrementAndGet() );
        PopupWindow value = StylusOnlyInputController.guardPopup( new PopupWindow( target, 250, 200, true ) );
        value.setBackgroundDrawable( new android.graphics.drawable.ColorDrawable( 0xff222222 ) );
        value.setOutsideTouchable( true );
        value.showAtLocation( activity.getWindow().getDecorView(), Gravity.CENTER, 0, 0 );
        popup.set( value );
      } );
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      UiDevice.getInstance( InstrumentationRegistry.getInstrumentation() ).click( 30, 100 );
      for ( int tool : new int[] { MotionEvent.TOOL_TYPE_FINGER, MotionEvent.TOOL_TYPE_STYLUS } ) {
        scenario.onActivity( activity -> {
          assertTrue( popup.get().isShowing() );
          View content = popup.get().getContentView();
          View target = ( (ViewGroup)content ).getChildAt( 0 );
          for ( int action : new int[] { MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP } ) {
            MotionEvent event = pointer( action, tool, target.getLeft() + target.getWidth()/2f,
                target.getTop() + target.getHeight()/2f );
            content.dispatchTouchEvent( event ); event.recycle();
          }
        } );
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertEquals( tool == MotionEvent.TOOL_TYPE_FINGER ? 0 : 1, clicks.get() );
      }
      scenario.onActivity( activity -> popup.get().dismiss() );
    }
  }

  @Test public void floatingZoomIsGuardedAndRestoresHiddenPreference()
  {
    try ( ActivityScenario<TDPrefActivity> scenario = launch() ) {
      AtomicReference<ZoomButtonsController> zoom = new AtomicReference<>();
      AtomicInteger zoomClicks = new AtomicInteger();
      scenario.onActivity( activity -> {
        StylusOnlyInputController.setEnabled( true );
        FrameLayout owner = new FrameLayout( activity ); activity.setContentView( owner );
        ZoomButtonsController controls = StylusOnlyInputController.createZoomControls( owner );
        controls.setOnZoomListener( new ZoomButtonsController.OnZoomListener() {
          @Override public void onVisibilityChanged( boolean visible ) { }
          @Override public void onZoom( boolean in ) { zoomClicks.incrementAndGet(); }
        } );
        controls.setVisible( false ); zoom.set( controls );
      } );
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      for ( int tool : new int[] { MotionEvent.TOOL_TYPE_FINGER, MotionEvent.TOOL_TYPE_STYLUS } ) {
        scenario.onActivity( activity -> {
          assertTrue( zoom.get().isVisible() );
        ViewGroup container = zoom.get().getContainer();
        ViewGroup guard = (ViewGroup)container.getChildAt( 0 );
        ViewGroup buttons = (ViewGroup)zoom.get().getZoomControls();
        View target = buttons.getChildAt( 1 );
        float x = target.getLeft() + target.getWidth()/2f;
        float y = target.getTop() + target.getHeight()/2f;
          for ( int action : new int[] { MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP } ) {
            MotionEvent event = pointer( action, tool, x, y ); guard.dispatchTouchEvent( event ); event.recycle();
          }
        } );
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertEquals( tool == MotionEvent.TOOL_TYPE_FINGER ? 0 : 1, zoomClicks.get() );
      }
      scenario.onActivity( activity -> {
        zoom.get().setVisible( false );
        StylusOnlyInputController.setEnabled( false );
        assertFalse( zoom.get().isVisible() );
      } );
    }
  }

  @Test public void escapePreservesShortAndDoubleBindingsAndSuppressesIncompleteHolds()
  {
    main( () -> {
      StylusOnlyInputController.setEnabled( true );
      ArrayList<Integer> keys = new ArrayList<>(); Object owner = new Object();
      StylusOnlyInputController.StylusKeyDispatch target = event -> { keys.add( event.getAction() ); return true; };
      long now = SystemClock.uptimeMillis();
      StylusOnlyInputController.key( owner, new KeyEvent( now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, 0 ), target );
      StylusOnlyInputController.key( owner, new KeyEvent( now, now+50, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_DOWN, 0 ), target );
      assertEquals( 2, keys.size() );
      StylusOnlyInputController.key( owner, new KeyEvent( now, now+100, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, 0 ), target );
      StylusOnlyInputController.key( owner, new KeyEvent( now, now+1100, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_DOWN, 0 ), target );
      assertEquals( 2, keys.size() ); assertTrue( StylusOnlyInputController.isEnabled() );
    } );
  }

  @Test public void guardedSpinnerChooserRejectsFingerSelection()
  {
    try ( ActivityScenario<TDPrefActivity> scenario = launch() ) {
      AtomicReference<StylusOnlySpinner> spinner = new AtomicReference<>();
      scenario.onActivity( activity -> {
        StylusOnlyInputController.setEnabled( true );
        StylusOnlySpinner value = new StylusOnlySpinner( activity );
        value.setAdapter( new ArrayAdapter<String>( activity, android.R.layout.simple_list_item_single_choice,
            new String[] { "First choice", "Second choice" } ) );
        activity.setContentView( value ); spinner.set( value );
      } );
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      scenario.onActivity( activity -> spinner.get().performClick() );
      UiDevice device = UiDevice.getInstance( InstrumentationRegistry.getInstrumentation() );
      UiObject2 choice = device.wait( Until.findObject( By.text( "Second choice" ) ), 3000 );
      assertNotNull( choice );
      android.graphics.Rect bounds = choice.getVisibleBounds();
      device.click( bounds.centerX(), bounds.centerY() );
      scenario.onActivity( activity -> assertEquals( 0, spinner.get().getSelectedItemPosition() ) );
      long downTime = SystemClock.uptimeMillis();
      for ( int action : new int[] { MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP } ) {
        MotionEvent event = pointer( action, MotionEvent.TOOL_TYPE_STYLUS, bounds.centerX(), bounds.centerY(), downTime );
        event.setSource( InputDevice.SOURCE_STYLUS );
        assertTrue( InstrumentationRegistry.getInstrumentation().getUiAutomation().injectInputEvent( event, true ) );
        event.recycle();
      }
      assertTrue( device.wait( Until.gone( By.text( "First choice" ) ), 3000 ) );
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      scenario.onActivity( activity -> assertEquals( 1, spinner.get().getSelectedItemPosition() ) );
    }
  }

  @Test public void shortAndDoubleRecoveryKeyPressesReachConfiguredBindings()
  {
    main( () -> {
      int savedSingle = TDSetting.mVolumeDownSinglePressAction, savedDouble = TDSetting.mVolumeDownDoublePressAction;
      AtomicInteger actions = new AtomicInteger();
      ActionBindingHost host = action -> { actions.addAndGet( action ); return true; };
      try {
        TDSetting.mVolumeDownSinglePressAction = TDSetting.SPEN_ACTION_UNDO;
        TDSetting.mVolumeDownDoublePressAction = TDSetting.SPEN_ACTION_NONE;
        ActionKeyBindingManager.registerHost( host );
        StylusOnlyInputController.setEnabled( true );
        Object owner = new Object();
        StylusOnlyInputController.StylusKeyDispatch target = event -> event.getAction() == KeyEvent.ACTION_DOWN
            ? ActionKeyBindingManager.onKeyDown( event.getKeyCode(), event ) : ActionKeyBindingManager.onKeyUp( event.getKeyCode(), event );
        long now = SystemClock.uptimeMillis();
        StylusOnlyInputController.key( owner, new KeyEvent( now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, 0 ), target );
        StylusOnlyInputController.key( owner, new KeyEvent( now, now+30, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_DOWN, 0 ), target );
        assertEquals( TDSetting.SPEN_ACTION_UNDO, actions.get() );
        actions.set( 0 ); TDSetting.mVolumeDownDoublePressAction = TDSetting.SPEN_ACTION_REDO;
        for ( int i = 0; i < 2; ++i ) {
          StylusOnlyInputController.key( owner, new KeyEvent( now, now+i*100, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, 0 ), target );
          StylusOnlyInputController.key( owner, new KeyEvent( now, now+i*100+30, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_DOWN, 0 ), target );
        }
        assertEquals( TDSetting.SPEN_ACTION_REDO, actions.get() );
      } finally {
        ActionKeyBindingManager.unregisterHost( host );
        TDSetting.mVolumeDownSinglePressAction = savedSingle; TDSetting.mVolumeDownDoublePressAction = savedDouble;
      }
    } );
  }

  @Test public void escapeWorksWithoutAnyBindingAndConsumesRepeatedDownAndRelease()
  {
    AtomicInteger forwarded = new AtomicInteger(); Object owner = new Object();
    long down = SystemClock.uptimeMillis();
    main( () -> {
      StylusOnlyInputController.setEnabled( true );
      StylusOnlyInputController.key( owner, new KeyEvent( down, down, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, 0 ), event -> { forwarded.incrementAndGet(); return true; } );
      StylusOnlyInputController.key( owner, new KeyEvent( down, down+500, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, 1 ), event -> { forwarded.incrementAndGet(); return true; } );
    } );
    SystemClock.sleep( 5150 );
    main( () -> {
      assertFalse( StylusOnlyInputController.isEnabled() );
      StylusOnlyInputController.key( owner, new KeyEvent( down, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_DOWN, 0 ), event -> { forwarded.incrementAndGet(); return true; } );
      assertEquals( 0, forwarded.get() );
      assertFalse( PreferenceManager.getDefaultSharedPreferences( context() ).getBoolean( StylusOnlyInputController.PREFERENCE, true ) );
    } );
  }

  @Test public void canceledHoldAndFocusLossNeverUnlock()
  {
    try ( ActivityScenario<TDPrefActivity> scenario = launch() ) {
      scenario.onActivity( activity -> {
        StylusOnlyInputController.setEnabled( true ); long now = SystemClock.uptimeMillis();
        Window.Callback callback = activity.getWindow().getCallback();
        callback.dispatchKeyEvent( new KeyEvent( now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, 0 ) );
        callback.onWindowFocusChanged( false );
        callback.dispatchKeyEvent( new KeyEvent( now, now+5000, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_DOWN, 0 ) );
        assertTrue( StylusOnlyInputController.isEnabled() );
        callback.onWindowFocusChanged( true );
        callback.dispatchKeyEvent( new KeyEvent( now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, 0 ) );
        callback.dispatchKeyEvent( KeyEvent.changeFlags( new KeyEvent( now, now+5000, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_DOWN, 0 ), KeyEvent.FLAG_CANCELED ) );
        assertTrue( StylusOnlyInputController.isEnabled() );
      } );
    }
  }

  @Test public void dialogAndFocusedPopupHandleRecoveryBeforeTheirKeyListeners()
  {
    try ( ActivityScenario<TDPrefActivity> scenario = launch() ) {
      AtomicReference<Dialog> dialog = new AtomicReference<>();
      AtomicReference<PopupWindow> popup = new AtomicReference<>();
      AtomicInteger forwarded = new AtomicInteger();
      scenario.onActivity( activity -> {
        StylusOnlyInputController.setEnabled( true );
        AlertDialog value = StylusOnlyInputController.guardDialog( new AlertDialog.Builder( activity ).setMessage( "Recovery test" ).create() );
        value.setOnKeyListener( ( d, code, event ) -> { forwarded.incrementAndGet(); return true; } );
        value.show(); dialog.set( value );
      } );
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      scenario.onActivity( activity -> {
        long now = SystemClock.uptimeMillis(); Window.Callback callback = dialog.get().getWindow().getCallback();
        callback.dispatchKeyEvent( new KeyEvent( now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, 0 ) );
        callback.dispatchKeyEvent( new KeyEvent( now, now+5000, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_DOWN, 0 ) );
        assertFalse( StylusOnlyInputController.isEnabled() ); assertEquals( 0, forwarded.get() );
        dialog.get().dismiss();
      } );
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      scenario.onActivity( activity -> {
        StylusOnlyInputController.setEnabled( true );
        Button child = new Button( activity ); child.setText( "Recovery popup" ); child.setFocusableInTouchMode( true );
        child.setOnKeyListener( ( view, code, event ) -> { forwarded.incrementAndGet(); return true; } );
        PopupWindow value = StylusOnlyInputController.guardPopup( new PopupWindow( child, 250, 200, true ) );
        value.showAtLocation( activity.getWindow().getDecorView(), Gravity.CENTER, 0, 0 );
        child.requestFocus(); popup.set( value );
      } );
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      scenario.onActivity( activity -> {
        long now = SystemClock.uptimeMillis(); View root = popup.get().getContentView();
        root.dispatchKeyEvent( new KeyEvent( now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, 0 ) );
        root.dispatchKeyEvent( new KeyEvent( now, now+5000, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_DOWN, 0 ) );
        assertFalse( StylusOnlyInputController.isEnabled() ); assertEquals( 0, forwarded.get() );
        popup.get().dismiss();
      } );
    }
  }
}
