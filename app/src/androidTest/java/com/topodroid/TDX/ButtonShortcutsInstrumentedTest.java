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
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

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

@RunWith( com.topodroid.TDX.QaJUnit4.class )
@com.topodroid.TDX.qa.TestTier(com.topodroid.TDX.qa.TestTier.Tier.UI)
@com.topodroid.TDX.QaFixtureRule.Database
@com.topodroid.qa.TestFeatures({com.topodroid.qa.TestFeatures.Feature.DRAWING})
public class ButtonShortcutsInstrumentedTest
{
  @org.junit.Rule public final com.topodroid.TDX.QaFixtureRule qaFixture = new com.topodroid.TDX.QaFixtureRule();
  // Independent oracle: storage order in this test is single, long, double.
  private static final String[] KEYS = {
    "DISTOX_SPEN_SINGLE_CLICK", "DISTOX_SPEN_LONG_CLICK", "DISTOX_SPEN_DOUBLE_CLICK",
    "DISTOX_ACTIVE_KEY_SINGLE_PRESS", "DISTOX_ACTIVE_KEY_LONG_PRESS", "DISTOX_ACTIVE_KEY_DOUBLE_PRESS",
    "DISTOX_VOLUME_UP_SINGLE_PRESS", "DISTOX_VOLUME_UP_LONG_PRESS", "DISTOX_VOLUME_UP_DOUBLE_PRESS",
    "DISTOX_VOLUME_DOWN_SINGLE_PRESS", "DISTOX_VOLUME_DOWN_LONG_PRESS", "DISTOX_VOLUME_DOWN_DOUBLE_PRESS"
  };
  private static final int[] DEFAULTS = { 1, 3, 5, 6, 3, 5, 0, 0, 0, 0, 0, 0 };
  private final Map<String, String> mSaved = new HashMap<>();
  private int[] mRuntime;
  private int mLevel;

  private Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
  private SharedPreferences prefs() { return PreferenceManager.getDefaultSharedPreferences( context() ); }
  private void main( Runnable run ) { InstrumentationRegistry.getInstrumentation().runOnMainSync( run ); }
  private UiDevice device() { return UiDevice.getInstance( InstrumentationRegistry.getInstrumentation() ); }

  @Before public void defaults()
  {
    TDInstance.setContext( context().getApplicationContext() );
    mRuntime = runtimeValues();
    for ( int level = 0; level <= TDLevel.DEBUG; ++level ) if ( TDLevel.atLevel( level ) ) mLevel = level;
    for ( String key : KEYS ) mSaved.put( key, prefs().getString( key, null ) );
    main( () -> {
      TDLevel.setLevel( context(), TDLevel.TESTER );
      for ( int i = 0; i < KEYS.length; ++i ) save( KEYS[i], DEFAULTS[i] );
    } );
  }

  @After public void restore()
  {
    main( () -> {
      for ( int i = 0; i < KEYS.length; ++i ) save( KEYS[i], mRuntime[i] );
      SharedPreferences.Editor editor = prefs().edit();
      for ( String key : KEYS ) {
        if ( mSaved.get( key ) == null ) editor.remove( key ); else editor.putString( key, mSaved.get( key ) );
      }
      editor.commit(); TDLevel.setLevel( context(), mLevel );
    } );
  }

  private void save( String key, int value )
  {
    TDSetting.updatePreference( new TDPrefHelper( context() ), TDPrefCat.PREF_CATEGORY_SPEN, key, Integer.toString( value ) );
  }

  @Test public void representativeBindingsPersistWithoutChangingOtherKeys()
  {
    int[] expected = DEFAULTS.clone();
    try ( ActivityScenario<ButtonShortcutsActivity> scenario = ActivityScenario.launch( ButtonShortcutsActivity.class ) ) {
      // One representative from pen, Active Key and volume bindings. The
      // invariant is isolation and persistence, not every settings-screen cell.
      for ( int i : new int[] { 0, 4, 6 } ) {
        final String key = KEYS[i]; final int index = i;
        scenario.onActivity( activity -> activity.getWindow().getDecorView().findViewWithTag( key ).performClick() );
        choose( 2 );
        assertEquals( key, "2", prefs().getString( key, "" ) );
        assertEquals( 2, runtimeValues()[index] );
        expected[i] = 2;
        assertArrayEquals( "Changing one binding must preserve every other binding", expected, runtimeValues() );
      }
      scenario.onActivity( activity -> {
        activity.getWindow().getDecorView().findViewWithTag( KEYS[9] ).performClick();
        picker( activity ).cancel();
      } );
      assertArrayEquals( "Cancel must not write a binding", expected, runtimeValues() );
      scenario.recreate();
      main( () -> TDSetting.loadSecondaryPreferences( new TDPrefHelper( context() ) ) );
      assertArrayEquals( "Bindings must survive activity recreation and preference reload", expected, runtimeValues() );
      for ( int i = 0; i < KEYS.length; ++i ) assertEquals( KEYS[i], Integer.toString( expected[i] ), prefs().getString( KEYS[i], "" ) );
    }
  }

  private void choose( int value ) {
    final Dialog[] owner={null};
    final android.graphics.Rect bounds=new android.graphics.Rect();
    final boolean[] requested={false},ready={false};
    final android.graphics.Rect[] previous={null};
    final int[] stable={0},samples={0};
    final long[] stableSince={0};
    final java.util.concurrent.atomic.AtomicBoolean cancelled=new java.util.concurrent.atomic.AtomicBoolean();
    final java.util.concurrent.atomic.AtomicReference<Throwable> probeError=new java.util.concurrent.atomic.AtomicReference<>();
    final java.util.concurrent.CountDownLatch positioned=new java.util.concurrent.CountDownLatch(1);
    final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
    long started=android.os.SystemClock.uptimeMillis();
    final long deadline=started+5000;
    final Runnable[] probe={null};
    probe[0]=() -> {
      if(cancelled.get())return;
      try {
          samples[0]++;
          boolean visible=false;
          View frameView=null;
          for(android.app.Activity current:androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)) {
            if(!(current instanceof ButtonShortcutsActivity))continue;
            Dialog dialog=picker((ButtonShortcutsActivity)current);
            if(dialog==null || !dialog.isShowing())continue;
            owner[0]=dialog;
            View decor=dialog.getWindow().getDecorView();
            frameView=decor;
            View choice=decor.findViewWithTag("action:"+value);
            if(choice==null || !choice.isShown() || !choice.isEnabled() || choice.getHeight()==0)continue;
            if(!requested[0]) {
              choice.requestRectangleOnScreen(new android.graphics.Rect(0,0,choice.getWidth(),choice.getHeight()),true);
              requested[0]=true;
            }
            android.graphics.Rect visibleBounds=new android.graphics.Rect();
            visible=decor.hasWindowFocus() && choice.getGlobalVisibleRect(visibleBounds) && visibleBounds.height()==choice.getHeight() && visibleBounds.width()==choice.getWidth();
            // getGlobalVisibleRect is relative to the dialog's view root;
            // bottom-aligned pickers have different window origins. Inject
            // using actual display coordinates, after checking clipping.
            int[] screen=new int[2];choice.getLocationOnScreen(screen);
            bounds.set(screen[0],screen[1],screen[0]+choice.getWidth(),screen[1]+choice.getHeight());
          }
          long now=android.os.SystemClock.uptimeMillis();
          if(visible) {
            if(previous[0]!=null && previous[0].equals(bounds))stable[0]++;
            else{stable[0]=1;stableSince[0]=now;}
            previous[0]=new android.graphics.Rect(bounds);
            // Same stability contract: >=3 observations spanning >=100ms.
            ready[0]=stable[0]>=3 && now-stableSince[0]>=100 && now<=deadline;
          } else {stable[0]=0;previous[0]=null;}
          if(ready[0] || now>=deadline){positioned.countDown();return;}
          if(frameView!=null)frameView.postOnAnimation(probe[0]);else handler.postDelayed(probe[0],16);
      } catch(Throwable error){probeError.set(error);positioned.countDown();}
    };
    handler.post(probe[0]);
    try {
      try{positioned.await(Math.max(1,deadline-android.os.SystemClock.uptimeMillis()),java.util.concurrent.TimeUnit.MILLISECONDS);}
      catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new AssertionError("Interrupted waiting for shortcut layout",interrupted);}
      if(probeError.get()!=null)throw new AssertionError("Cannot inspect shortcut picker",probeError.get());
      assertTrue("Shortcut action did not become focused, fully visible and stable within five seconds: action:"+value+" bounds="+bounds+" samples="+samples[0],ready[0]);
      long readyAt=android.os.SystemClock.uptimeMillis();
      assertTrue("Shortcut action touch was not injected",device().click(bounds.centerX(),bounds.centerY()));
      long touchedAt=android.os.SystemClock.uptimeMillis();
      long dismissDeadline=android.os.SystemClock.uptimeMillis()+5000;
      final boolean[] dismissed={false};
      while(android.os.SystemClock.uptimeMillis()<dismissDeadline) {
        main(() -> dismissed[0]=owner[0]!=null && !owner[0].isShowing());
        if(dismissed[0]) {
          android.os.Bundle progress=new android.os.Bundle();
          progress.putString("stream","QA_STEP|shortcut-action:"+value+"|readyMs="+(readyAt-started)+"|touchMs="+(touchedAt-readyAt)+"|dismissMs="+(android.os.SystemClock.uptimeMillis()-touchedAt)+"|samples="+samples[0]+"|screen="+bounds+"\n");
          InstrumentationRegistry.getInstrumentation().sendStatus(0,progress);
          return;
        }
        android.os.SystemClock.sleep(50);
      }
      fail("Shortcut picker did not dismiss after selecting action:"+value);
    } catch(RuntimeException | AssertionError failure) {
      // ActivityScenario owns the parent. Close its child before its close()
      // runs, while preserving the selection assertion if cleanup also fails.
      cancelled.set(true);
      try{main(() -> {if(owner[0]!=null && owner[0].isShowing())owner[0].dismiss();});}
      catch(Throwable cleanup){failure.addSuppressed(cleanup);}
      throw failure;
    } finally {
      cancelled.set(true);handler.removeCallbacks(probe[0]);
    }
  }
  private Dialog picker( ButtonShortcutsActivity activity ) {
    try { Field field = ButtonShortcutsActivity.class.getDeclaredField( "mPicker" ); field.setAccessible( true ); return (Dialog)field.get( activity ); }
    catch ( Exception e ) { throw new AssertionError( e ); }
  }
  private int[] runtimeValues() {
    return new int[] { TDSetting.mSPenSingleClickAction, TDSetting.mSPenLongClickAction, TDSetting.mSPenDoubleClickAction,
      TDSetting.mActiveKeySinglePressAction, TDSetting.mActiveKeyLongPressAction, TDSetting.mActiveKeyDoublePressAction,
      TDSetting.mVolumeUpSinglePressAction, TDSetting.mVolumeUpLongPressAction, TDSetting.mVolumeUpDoublePressAction,
      TDSetting.mVolumeDownSinglePressAction, TDSetting.mVolumeDownLongPressAction, TDSetting.mVolumeDownDoublePressAction };
  }
}
