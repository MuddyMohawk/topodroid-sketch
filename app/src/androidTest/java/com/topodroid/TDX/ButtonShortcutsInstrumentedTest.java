package com.topodroid.TDX;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.withTagValue;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

import android.app.Activity;
import android.app.Dialog;
import android.app.Instrumentation.ActivityMonitor;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.view.KeyEvent;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.topodroid.prefs.TDPrefActivity;
import com.topodroid.prefs.TDPrefCat;
import com.topodroid.prefs.TDPrefHelper;
import com.topodroid.prefs.TDSetting;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

@RunWith( AndroidJUnit4.class )
public class ButtonShortcutsInstrumentedTest
{
  // Independent oracle: storage order in this test is single, long, double.
  private static final String[] KEYS = {
    "DISTOX_SPEN_SINGLE_CLICK", "DISTOX_SPEN_LONG_CLICK", "DISTOX_SPEN_DOUBLE_CLICK",
    "DISTOX_ACTIVE_KEY_SINGLE_PRESS", "DISTOX_ACTIVE_KEY_LONG_PRESS", "DISTOX_ACTIVE_KEY_DOUBLE_PRESS",
    "DISTOX_VOLUME_UP_SINGLE_PRESS", "DISTOX_VOLUME_UP_LONG_PRESS", "DISTOX_VOLUME_UP_DOUBLE_PRESS",
    "DISTOX_VOLUME_DOWN_SINGLE_PRESS", "DISTOX_VOLUME_DOWN_LONG_PRESS", "DISTOX_VOLUME_DOWN_DOUBLE_PRESS"
  };
  private static final int[] DEFAULTS = { 1, 3, 5, 6, 3, 5, 0, 0, 0, 0, 0, 0 };
  private static final String[] TIMING_KEYS = { "DISTOX_BUTTON_HOLD_MS", "DISTOX_BUTTON_DOUBLE_MS", "DISTOX_SPEN_TRANSITION_MS" };
  private final Map<String, String> mSaved = new HashMap<>();
  private int[] mRuntime;
  private int[] mTiming;
  private int mLevel;

  private Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
  private SharedPreferences prefs() { return PreferenceManager.getDefaultSharedPreferences( context() ); }
  private void main( Runnable run ) { InstrumentationRegistry.getInstrumentation().runOnMainSync( run ); }
  private UiDevice device() { return UiDevice.getInstance( InstrumentationRegistry.getInstrumentation() ); }

  @Before public void defaults()
  {
    TDInstance.setContext( context().getApplicationContext() );
    mRuntime = runtimeValues();
    mTiming = timingValues();
    for ( int level = 0; level <= TDLevel.DEBUG; ++level ) if ( TDLevel.atLevel( level ) ) mLevel = level;
    for ( String key : KEYS ) mSaved.put( key, prefs().getString( key, null ) );
    for ( String key : TIMING_KEYS ) mSaved.put( key, prefs().getString( key, null ) );
    main( () -> {
      StylusOnlyInputController.setEnabled( false ); TDLevel.setLevel( context(), TDLevel.TESTER );
      for ( int i = 0; i < KEYS.length; ++i ) save( KEYS[i], DEFAULTS[i] );
      for ( String key : TIMING_KEYS ) save( key, -1 );
    } );
  }

  @After public void restore()
  {
    main( () -> {
      for ( int i = 0; i < KEYS.length; ++i ) save( KEYS[i], mRuntime[i] );
      for ( int i = 0; i < TIMING_KEYS.length; ++i ) save( TIMING_KEYS[i], mTiming[i] );
      SharedPreferences.Editor editor = prefs().edit();
      for ( String key : KEYS ) {
        if ( mSaved.get( key ) == null ) editor.remove( key ); else editor.putString( key, mSaved.get( key ) );
      }
      for ( String key : TIMING_KEYS ) {
        if ( mSaved.get( key ) == null ) editor.remove( key ); else editor.putString( key, mSaved.get( key ) );
      }
      editor.commit(); TDLevel.setLevel( context(), mLevel ); StylusOnlyInputController.setEnabled( false );
    } );
  }

  private void save( String key, int value )
  {
    TDSetting.updatePreference( new TDPrefHelper( context() ), TDPrefCat.PREF_CATEGORY_SPEN, key, Integer.toString( value ) );
  }

  @Test public void defaultGridHasCorrectGestureOrderAndAnnouncements()
  {
    try ( ActivityScenario<ButtonShortcutsActivity> scenario = ActivityScenario.launch( ButtonShortcutsActivity.class ) ) {
      scenario.onActivity( activity -> {
        View root = activity.getWindow().getDecorView();
        for ( int i = 0; i < KEYS.length; ++i ) {
          View cell = root.findViewWithTag( KEYS[i] ); assertNotNull( cell ); assertTrue( cell.isClickable() ); assertTrue( cell.isFocusable() );
          assertTrue( cell.getBackground() instanceof GradientDrawable );
          if ( DEFAULTS[i] == 0 ) assertEquals( 0, ( (ViewGroup)cell ).getChildCount() );
        }
        assertEquals( "S Pen button double-press: Back", root.findViewWithTag( KEYS[2] ).getContentDescription().toString() );
        assertEquals( "S Pen button hold: Next line preset", root.findViewWithTag( KEYS[1] ).getContentDescription().toString() );
        assertEquals( "Active Key press: Eraser on/off", root.findViewWithTag( KEYS[3] ).getContentDescription().toString() );
        assertTrue( root.findViewWithTag( KEYS[0] ).getLeft() < root.findViewWithTag( KEYS[2] ).getLeft() );
        assertTrue( root.findViewWithTag( KEYS[2] ).getLeft() < root.findViewWithTag( KEYS[1] ).getLeft() );
      } );
    }
  }

  @Test public void choosingActionsUpdatesEveryMatchingRuntimeFieldAndPersistedKey()
  {
    try ( ActivityScenario<ButtonShortcutsActivity> scenario = ActivityScenario.launch( ButtonShortcutsActivity.class ) ) {
      for ( int i = 0; i < KEYS.length; ++i ) {
        final String key = KEYS[i]; final int index = i;
        scenario.onActivity( activity -> activity.getWindow().getDecorView().findViewWithTag( key ).performClick() );
        choose( 2 );
        assertEquals( key, "2", prefs().getString( key, "" ) );
        assertEquals( 2, runtimeValues()[index] );
        for ( int j = 0; j < KEYS.length; ++j ) assertEquals( j <= i ? 2 : DEFAULTS[j], runtimeValues()[j] );
        scenario.onActivity( activity -> assertTrue( activity.getWindow().getDecorView().findViewWithTag( key ).getContentDescription().toString().endsWith( ": Redo" ) ) );
      }
      scenario.recreate();
      scenario.onActivity( activity -> {
        assertNull( picker( activity ) );
        for ( String key : KEYS ) assertTrue( activity.getWindow().getDecorView().findViewWithTag( key ).getContentDescription().toString().endsWith( ": Redo" ) );
      } );
    }
  }

  @Test public void nothingClearsCellAndNotesTrackEachButtonGroup()
  {
    try ( ActivityScenario<ButtonShortcutsActivity> scenario = ActivityScenario.launch( ButtonShortcutsActivity.class ) ) {
      scenario.onActivity( activity -> assertNull( text( activity.getWindow().getDecorView(), R.string.button_shortcuts_note_volume ) ) );
      scenario.onActivity( activity -> activity.getWindow().getDecorView().findViewWithTag( KEYS[9] ).performClick() ); choose( 1 );
      scenario.onActivity( activity -> assertNotNull( text( activity.getWindow().getDecorView(), R.string.button_shortcuts_note_volume ) ) );
      scenario.onActivity( activity -> activity.getWindow().getDecorView().findViewWithTag( KEYS[9] ).performClick() ); choose( 0 );
      assertEquals( "0", prefs().getString( KEYS[9], "" ) );
      scenario.onActivity( activity -> {
        assertEquals( 0, ( (ViewGroup)activity.getWindow().getDecorView().findViewWithTag( KEYS[9] ) ).getChildCount() );
        assertNull( text( activity.getWindow().getDecorView(), R.string.button_shortcuts_note_volume ) );
      } );
      for ( int i = 0; i < 6; ++i ) {
        final String key = KEYS[i]; scenario.onActivity( activity -> activity.getWindow().getDecorView().findViewWithTag( key ).performClick() ); choose( 0 );
      }
      scenario.onActivity( activity -> {
        assertNull( text( activity.getWindow().getDecorView(), R.string.button_shortcuts_note_pen ) );
        assertNull( text( activity.getWindow().getDecorView(), R.string.button_shortcuts_note_active ) );
      } );
    }
  }

  @Test public void cancelScrimBackAndCurrentChoiceNeverChangeTheBinding()
  {
    try ( ActivityScenario<ButtonShortcutsActivity> scenario = ActivityScenario.launch( ButtonShortcutsActivity.class ) ) {
      scenario.onActivity( activity -> activity.getWindow().getDecorView().findViewWithTag( KEYS[0] ).performClick() );
      onView( withText( R.string.button_shortcuts_cancel ) ).inRoot( isDialog() ).perform( click() );
      scenario.onActivity( activity -> assertNull( picker( activity ) ) );
      scenario.onActivity( activity -> activity.getWindow().getDecorView().findViewWithTag( KEYS[0] ).performClick() );
      device().pressBack(); InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      scenario.onActivity( activity -> assertNull( picker( activity ) ) );
      scenario.onActivity( activity -> activity.getWindow().getDecorView().findViewWithTag( KEYS[0] ).performClick() );
      device().waitForIdle();
      int[] scrim = new int[2];
      scenario.onActivity( activity -> {
        int[] location = new int[2]; picker( activity ).getWindow().getDecorView().getLocationOnScreen( location );
        scrim[0] = device().getDisplayWidth() / 2; scrim[1] = Math.max( 1, location[1] / 2 );
      } );
      device().click( scrim[0], scrim[1] ); device().waitForIdle();
      scenario.onActivity( activity -> assertNull( picker( activity ) ) );
      scenario.onActivity( activity -> activity.getWindow().getDecorView().findViewWithTag( KEYS[0] ).performClick() ); choose( 1 );
      assertEquals( "1", prefs().getString( KEYS[0], "" ) ); assertEquals( 1, TDSetting.mSPenSingleClickAction );
      scenario.onActivity( activity -> activity.getWindow().getDecorView().findViewWithTag( KEYS[0] ).performClick() );
      scenario.recreate(); scenario.onActivity( activity -> assertNull( picker( activity ) ) );
      assertEquals( "1", prefs().getString( KEYS[0], "" ) );
    }
  }

  @Test public void placeholdersAreInertAndPickerExposesSelectionAndAcceptedCopy()
  {
    try ( ActivityScenario<ButtonShortcutsActivity> scenario = ActivityScenario.launch( ButtonShortcutsActivity.class ) ) {
      scenario.onActivity( activity -> {
        View root = activity.getWindow().getDecorView();
        assertInert( text( root, R.string.button_shortcuts_scope_data ) ); assertInert( text( root, R.string.button_shortcuts_scope_sketch ) );
        assertInert( description( root, activity.getString( R.string.button_shortcuts_touch_later ) ) );
        for ( String key : TIMING_KEYS ) {
          assertTrue( root.findViewWithTag( key ).isClickable() ); assertTrue( root.findViewWithTag( key ).isFocusable() );
        }
        root.findViewWithTag( KEYS[9] ).performClick();
        View panel = picker( activity ).getWindow().getDecorView();
        assertTrue( panel.findViewWithTag( "action:0" ).isSelected() );
        assertTrue( panel.findViewWithTag( "action:0" ).getContentDescription().toString().contains( "Clear all three shortcuts" ) );
        assertNull( "Retired tool-type action must not be offered", panel.findViewWithTag( "action:4" ) );
        int selected = 0;
        for ( int value : new int[] { 0, 1, 2, 3, 5, 6, 7, 8, 9, 10 } ) {
          View option = panel.findViewWithTag( "action:" + value ); assertNotNull( option );
          if ( option.isSelected() ) ++selected;
        }
        assertEquals( 1, selected );
      } );
    }
  }

  @Test public void timingSlidersSaveReloadClampAndResetToSystemDefaults() throws Exception
  {
    int[] minimum = { 50, 50, 0 }, maximum = { 2000, 1000, 1000 }, chosen = new int[3];
    VisualTestSupport captures = new VisualTestSupport( "button_shortcuts_timing" );
    try ( ActivityScenario<ButtonShortcutsActivity> scenario = ActivityScenario.launch( ButtonShortcutsActivity.class ) ) {
      for ( int i = 0; i < TIMING_KEYS.length; ++i ) {
        final int index = i;
        scenario.onActivity( activity -> {
          View row = activity.getWindow().getDecorView().findViewWithTag( TIMING_KEYS[index] );
          assertTrue( row.getContentDescription().toString().contains( "System default" ) ); row.performClick();
          View panel = timingPicker( activity ).getWindow().getDecorView();
          SeekBar slider = panel.findViewWithTag( "timing:slider" );
          slider.setProgress( 0 ); slider.requestFocus();
          assertTrue( slider.dispatchKeyEvent( new KeyEvent( KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT ) ) );
          slider.dispatchKeyEvent( new KeyEvent( KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT ) );
          chosen[index] = minimum[index] + slider.getProgress() * 25;
          assertEquals( chosen[index], timingValues()[index] );
          assertEquals( Integer.toString( chosen[index] ), prefs().getString( TIMING_KEYS[index], "" ) );
          assertTrue( ( (TextView)panel.findViewWithTag( "timing:value" ) ).getText().toString().contains( chosen[index] + " ms" ) );
        } );
        if ( i == 2 ) { device().waitForIdle(); captures.captureScreen( "timing-slider.png" ); }
        scenario.onActivity( activity -> timingPicker( activity ).dismiss() );
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      }
      main( () -> TDSetting.loadSecondaryPreferences( new TDPrefHelper( context() ) ) );
      assertArrayEquals( chosen, timingValues() ); scenario.recreate();
      for ( int i = 0; i < TIMING_KEYS.length; ++i ) {
        final int index = i;
        scenario.onActivity( activity -> {
          View row = activity.getWindow().getDecorView().findViewWithTag( TIMING_KEYS[index] );
          assertTrue( row.getContentDescription().toString().contains( chosen[index] + " ms" ) );
          save( TIMING_KEYS[index], 5000 ); assertEquals( maximum[index], timingValues()[index] );
          assertEquals( Integer.toString( maximum[index] ), prefs().getString( TIMING_KEYS[index], "" ) );
          save( TIMING_KEYS[index], 1 ); assertEquals( Math.max( 1, minimum[index] ), timingValues()[index] );
          row.performClick();
          timingPicker( activity ).getWindow().getDecorView().findViewWithTag( "timing:default" ).performClick();
          assertEquals( TIMING_KEYS[index], -1, timingValues()[index] ); assertEquals( "-1", prefs().getString( TIMING_KEYS[index], "" ) );
          assertTrue( row.getContentDescription().toString().contains( "System default" ) );
          timingPicker( activity ).dismiss();
        } );
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      }
      main( () -> TDSetting.loadSecondaryPreferences( new TDPrefHelper( context() ) ) );
      assertArrayEquals( new int[] { -1, -1, -1 }, timingValues() );
      assertEquals( android.view.ViewConfiguration.getLongPressTimeout(), TDSetting.getButtonHoldMs() );
      assertEquals( android.view.ViewConfiguration.getDoubleTapTimeout(), TDSetting.getButtonDoubleMs() );
      assertEquals( android.view.ViewConfiguration.getTapTimeout(), TDSetting.getSPenTransitionMs() );
      scenario.onActivity( activity -> findScroll( activity.getWindow().getDecorView() ).fullScroll( View.FOCUS_DOWN ) );
      device().waitForIdle(); captures.captureScreen( "timing-defaults.png" );
    } finally { captures.finish(); }
  }

  @Test public void heldNavigationIsOfferedForPressAndHoldOnlyAndSurvivesReload()
  {
    try ( ActivityScenario<ButtonShortcutsActivity> scenario = ActivityScenario.launch( ButtonShortcutsActivity.class ) ) {
      for ( int row = 0; row < 4; ++row ) {
        final int base = row * 3;
        scenario.onActivity( activity -> activity.getWindow().getDecorView().findViewWithTag( KEYS[base] ).performClick() ); choose( 7 );
        assertEquals( "7", prefs().getString( KEYS[base], "" ) );
        scenario.onActivity( activity -> activity.getWindow().getDecorView().findViewWithTag( KEYS[base + 1] ).performClick() ); choose( 8 );
        assertEquals( "8", prefs().getString( KEYS[base + 1], "" ) );
        scenario.onActivity( activity -> {
          View root = activity.getWindow().getDecorView();
          String note = activity.getString( R.string.button_shortcuts_note_press_navigation, activity.getString( ButtonShortcutBinding.BUTTON_NAMES[base / 3] ) );
          assertNotNull( findText( root, note ) );
          root.findViewWithTag( KEYS[base + 2] ).performClick();
          View panel = picker( activity ).getWindow().getDecorView();
          assertNull( panel.findViewWithTag( "action:7" ) ); assertNull( panel.findViewWithTag( "action:8" ) ); assertNull( panel.findViewWithTag( "action:9" ) );
          picker( activity ).dismiss();
        } );
      }
      main( () -> TDSetting.loadSecondaryPreferences( new TDPrefHelper( context() ) ) );
      scenario.recreate();
      scenario.onActivity( activity -> {
        assertNotNull( text( activity.getWindow().getDecorView(), R.string.button_shortcuts_note_pen_navigation ) );
        for ( int row = 0; row < 4; ++row ) {
          assertEquals( 7, runtimeValues()[row * 3] ); assertEquals( 8, runtimeValues()[row * 3 + 1] );
          assertTrue( activity.getWindow().getDecorView().findViewWithTag( KEYS[row * 3] ).getContentDescription().toString().endsWith( ": Hold to zoom" ) );
        }
      } );
    }
  }

  @Test public void holdEraseIsAvailableForPressAndHoldOnAllButtonsAndSurvivesReload()
  {
    try ( ActivityScenario<ButtonShortcutsActivity> scenario = ActivityScenario.launch( ButtonShortcutsActivity.class ) ) {
      for ( int row = 0; row < 4; ++row ) {
        final int base = row * 3;
        scenario.onActivity( activity -> activity.getWindow().getDecorView().findViewWithTag( KEYS[base] ).performClick() ); choose( 9 );
        scenario.onActivity( activity -> activity.getWindow().getDecorView().findViewWithTag( KEYS[base + 1] ).performClick() ); choose( 9 );
        assertEquals( "9", prefs().getString( KEYS[base], "" ) ); assertEquals( "9", prefs().getString( KEYS[base + 1], "" ) );
        scenario.onActivity( activity -> {
          View root = activity.getWindow().getDecorView();
          assertTrue( root.findViewWithTag( KEYS[base] ).getContentDescription().toString().endsWith( ": Hold to erase" ) );
          root.findViewWithTag( KEYS[base + 2] ).performClick();
          assertNull( picker( activity ).getWindow().getDecorView().findViewWithTag( "action:9" ) );
          picker( activity ).dismiss();
        } );
      }
      main( () -> TDSetting.loadSecondaryPreferences( new TDPrefHelper( context() ) ) ); scenario.recreate();
      scenario.onActivity( activity -> {
        assertNotNull( text( activity.getWindow().getDecorView(), R.string.button_shortcuts_note_pen_navigation ) );
        for ( int row = 0; row < 4; ++row ) {
          assertEquals( 9, runtimeValues()[row * 3] ); assertEquals( 9, runtimeValues()[row * 3 + 1] );
        }
      } );
    }
  }

  @Test public void reverseLastLineIsAvailableForEveryButtonGestureAndSurvivesReload()
  {
    try ( ActivityScenario<ButtonShortcutsActivity> scenario = ActivityScenario.launch( ButtonShortcutsActivity.class ) ) {
      for ( String key : KEYS ) {
        scenario.onActivity( activity -> activity.getWindow().getDecorView().findViewWithTag( key ).performClick() ); choose( 10 );
        assertEquals( "10", prefs().getString( key, "" ) );
        scenario.onActivity( activity -> assertTrue( activity.getWindow().getDecorView().findViewWithTag( key )
          .getContentDescription().toString().endsWith( ": Reverse last line" ) ) );
      }
      main( () -> TDSetting.loadSecondaryPreferences( new TDPrefHelper( context() ) ) ); scenario.recreate();
      scenario.onActivity( activity -> {
        for ( int action : runtimeValues() ) assertEquals( 10, action );
      } );
    }
  }

  @Test public void retiredToolTypeAssignmentsReadAsNothingAndCanBeReassigned()
  {
    main( () -> {
      SharedPreferences.Editor editor = prefs().edit();
      for ( String key : KEYS ) editor.putString( key, "4" );
      editor.commit();
      TDSetting.loadSecondaryPreferences( new TDPrefHelper( context() ) );
    } );
    for ( int value : runtimeValues() ) assertEquals( 0, value );
    try ( ActivityScenario<ButtonShortcutsActivity> scenario = ActivityScenario.launch( ButtonShortcutsActivity.class ) ) {
      scenario.onActivity( activity -> {
        View root = activity.getWindow().getDecorView();
        for ( String key : KEYS ) {
          View cell = root.findViewWithTag( key );
          assertEquals( 0, ( (ViewGroup)cell ).getChildCount() );
          assertTrue( cell.getContentDescription().toString().endsWith( ": not set" ) );
          assertEquals( "Reading retired assignments must not migrate saved preferences", "4", prefs().getString( key, "" ) );
        }
        assertNull( text( root, R.string.button_shortcuts_note_volume ) );
        root.findViewWithTag( KEYS[0] ).performClick();
        View panel = picker( activity ).getWindow().getDecorView();
        assertNull( panel.findViewWithTag( "action:4" ) );
        assertTrue( panel.findViewWithTag( "action:0" ).isSelected() );
      } );
      choose( 5 );
      assertEquals( "5", prefs().getString( KEYS[0], "" ) );
      assertEquals( 5, TDSetting.mSPenSingleClickAction );
      main( () -> save( KEYS[9], 4 ) );
      assertEquals( "0", prefs().getString( KEYS[9], "" ) );
      assertEquals( 0, TDSetting.mVolumeDownSinglePressAction );
    }
  }

  @Test public void deviceEntryAndSettingsSearchLaunchTheNewActivity()
  {
    Intent intent = new Intent( context(), TDPrefActivity.class ); intent.putExtra( TDPrefCat.PREF_CATEGORY, TDPrefCat.PREF_CATEGORY_DEVICE );
    try ( ActivityScenario<TDPrefActivity> scenario = ActivityScenario.launch( intent ) ) {
      ActivityMonitor monitor = InstrumentationRegistry.getInstrumentation().addMonitor( ButtonShortcutsActivity.class.getName(), null, false );
      try {
        scenario.onActivity( activity -> {
          View entry = text( activity.getWindow().getDecorView(), R.string.pref_action_key_bindings_title ); assertNotNull( entry );
          while ( ! entry.hasOnClickListeners() && entry.getParent() instanceof View ) entry = (View)entry.getParent();
          assertTrue( entry.hasOnClickListeners() ); entry.performClick();
        } );
        Activity opened = InstrumentationRegistry.getInstrumentation().waitForMonitorWithTimeout( monitor, 5000 ); assertNotNull( opened );
        main( opened::finish ); InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        InstrumentationRegistry.getInstrumentation().removeMonitor( monitor );
        monitor = InstrumentationRegistry.getInstrumentation().addMonitor( ButtonShortcutsActivity.class.getName(), null, false );
        scenario.onActivity( activity -> activity.findViewById( R.id.layout ).performLongClick() );
        UiObject2 search = device().wait( Until.findObject( By.res( context().getPackageName(), "name" ) ), 3000 );
        assertNotNull( search ); search.setText( "hovering" );
        device().findObject( By.res( context().getPackageName(), "btn_search" ) ).click();
        UiObject2 results = device().wait( Until.findObject( By.res( context().getPackageName(), "result" )
          .hasDescendant( By.text( context().getString( R.string.title_settings_spen ) ) ) ), 3000 );
        assertNotNull( "Button shortcuts must appear in the search results list", results );
        results.findObject( By.text( context().getString( R.string.title_settings_spen ) ) ).click();
        Activity searched = InstrumentationRegistry.getInstrumentation().waitForMonitorWithTimeout( monitor, 5000 ); assertNotNull( searched );
        main( searched::finish );
      } finally { InstrumentationRegistry.getInstrumentation().removeMonitor( monitor ); }
    }
  }

  @Test public void defaultMainAndPickerMatchGoldens() throws Exception
  {
    VisualTestSupport support = new VisualTestSupport( "button_shortcuts" );
    try {
      support.prepareForPhysicalCompatCase(); support.launchMainWindow();
      main( () -> { for ( int i = 0; i < KEYS.length; ++i ) save( KEYS[i], DEFAULTS[i] ); } );
      try ( ActivityScenario<ButtonShortcutsActivity> scenario = ActivityScenario.launch( ButtonShortcutsActivity.class ) ) {
        device().waitForIdle(); support.captureAndAssertScreen( "button_shortcuts_main.png" );
        scenario.onActivity( activity -> activity.getWindow().getDecorView().findViewWithTag( KEYS[0] ).performClick() );
        device().waitForIdle(); support.captureAndAssertScreen( "button_shortcuts_picker.png" );
      }
    } finally { support.finish(); }
  }

  @Test public void contentAndPickerFitTheCurrentWindowWithoutHorizontalScrolling() throws Exception
  {
    VisualTestSupport captures = new VisualTestSupport( "button_shortcuts_layout" );
    String size = context().getResources().getConfiguration().screenWidthDp + "dp";
    try ( ActivityScenario<ButtonShortcutsActivity> scenario = ActivityScenario.launch( ButtonShortcutsActivity.class ) ) {
      device().waitForIdle(); captures.captureScreen( "main-" + size + ".png" );
      scenario.onActivity( activity -> {
        View root = activity.findViewById( android.R.id.content ); assertFitsWidth( root, root.getWidth() );
        if ( activity.getResources().getConfiguration().fontScale == 1.0f ) {
          assertEquals( "Double-press header must fit on one line", 1, text( root, R.string.button_shortcuts_col_double ).getLineCount() );
        }
        activity.getWindow().getDecorView().findViewWithTag( KEYS[0] ).performClick();
      } );
      device().waitForIdle();
      captures.captureScreen( "picker-" + size + ".png" );
      scenario.onActivity( activity -> {
        View root = picker( activity ).getWindow().getDecorView(); assertFitsWidth( root, root.getWidth() );
        assertTrue( root.getHeight() <= activity.getWindow().getDecorView().getHeight() );
        TextView cancel = text( root, R.string.button_shortcuts_cancel );
        assertTrue( cancel.getGlobalVisibleRect( new android.graphics.Rect() ) );
        ScrollView scroll = findScroll( root ); assertNotNull( scroll ); assertTrue( scroll.getHeight() > 0 );
      } );
      choose( 0 );
    } finally { captures.finish(); }
  }

  private void choose( int value ) {
    device().waitForIdle();
    onView( withTagValue( is( (Object)( "action:" + value ) ) ) ).inRoot( isDialog() ).perform( scrollTo(), click() );
    device().waitForIdle();
  }
  private Dialog picker( ButtonShortcutsActivity activity ) {
    try { Field field = ButtonShortcutsActivity.class.getDeclaredField( "mPicker" ); field.setAccessible( true ); return (Dialog)field.get( activity ); }
    catch ( Exception e ) { throw new AssertionError( e ); }
  }
  private Dialog timingPicker( ButtonShortcutsActivity activity ) {
    try { Field field = ButtonShortcutsActivity.class.getDeclaredField( "mTimingPicker" ); field.setAccessible( true ); return (Dialog)field.get( activity ); }
    catch ( Exception e ) { throw new AssertionError( e ); }
  }
  private int[] timingValues() { return new int[] { TDSetting.mButtonHoldMs, TDSetting.mButtonDoubleMs, TDSetting.mSPenTransitionMs }; }
  private TextView text( View root, int resource ) {
    return findText( root, context().getString( resource ) );
  }
  private TextView findText( View root, String value ) {
    if ( root instanceof TextView && ( (TextView)root ).getText().toString().equals( value ) ) return (TextView)root;
    if ( root instanceof ViewGroup ) for ( int i = 0; i < ( (ViewGroup)root ).getChildCount(); ++i ) {
      TextView found = findText( ( (ViewGroup)root ).getChildAt( i ), value ); if ( found != null ) return found;
    }
    return null;
  }
  private View description( View root, String description ) {
    if ( description.contentEquals( root.getContentDescription() == null ? "" : root.getContentDescription() ) ) return root;
    if ( root instanceof ViewGroup ) for ( int i = 0; i < ( (ViewGroup)root ).getChildCount(); ++i ) {
      View found = description( ( (ViewGroup)root ).getChildAt( i ), description ); if ( found != null ) return found;
    }
    return null;
  }
  private void assertInert( View view ) {
    assertNotNull( view ); assertFalse( view.isClickable() ); assertFalse( view.isFocusable() ); assertFalse( view.isLongClickable() );
    if ( view instanceof ViewGroup ) for ( int i = 0; i < ( (ViewGroup)view ).getChildCount(); ++i ) assertInert( ( (ViewGroup)view ).getChildAt( i ) );
  }
  private void assertFitsWidth( View view, int windowWidth ) {
    assertTrue( "View wider than window: " + view, view.getWidth() <= windowWidth );
    if ( view instanceof ViewGroup ) for ( int i = 0; i < ( (ViewGroup)view ).getChildCount(); ++i ) {
      View child = ( (ViewGroup)view ).getChildAt( i );
      assertTrue( "Child overflows parent: " + child, child.getRight() <= view.getWidth() ); assertFitsWidth( child, windowWidth );
    }
  }
  private ScrollView findScroll( View view ) {
    if ( view instanceof ScrollView ) return (ScrollView)view;
    if ( view instanceof ViewGroup ) for ( int i = 0; i < ( (ViewGroup)view ).getChildCount(); ++i ) {
      ScrollView found = findScroll( ( (ViewGroup)view ).getChildAt( i ) ); if ( found != null ) return found;
    }
    return null;
  }
  private int[] runtimeValues() {
    return new int[] { TDSetting.mSPenSingleClickAction, TDSetting.mSPenLongClickAction, TDSetting.mSPenDoubleClickAction,
      TDSetting.mActiveKeySinglePressAction, TDSetting.mActiveKeyLongPressAction, TDSetting.mActiveKeyDoublePressAction,
      TDSetting.mVolumeUpSinglePressAction, TDSetting.mVolumeUpLongPressAction, TDSetting.mVolumeUpDoublePressAction,
      TDSetting.mVolumeDownSinglePressAction, TDSetting.mVolumeDownLongPressAction, TDSetting.mVolumeDownDoublePressAction };
  }
}
