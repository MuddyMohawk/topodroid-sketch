package com.topodroid.TDX;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.topodroid.prefs.TDPrefActivity;
import com.topodroid.prefs.TDPrefCat;
import java.util.ArrayList;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Isolated canvas and overlay fixtures; no survey is opened or saved. */
@RunWith( AndroidJUnit4.class )
public class StylusPinchInstrumentedTest
{
  private static final int F = MotionEvent.TOOL_TYPE_FINGER;
  private static final int P = MotionEvent.TOOL_TYPE_STYLUS;
  private static final int E = MotionEvent.TOOL_TYPE_ERASER;
  private static final int SECOND_DOWN = MotionEvent.ACTION_POINTER_DOWN | ( 1 << 8 );
  private static final int SECOND_UP = MotionEvent.ACTION_POINTER_UP | ( 1 << 8 );

  @Before @After public void reset()
  {
    InstrumentationRegistry.getInstrumentation().runOnMainSync( () -> {
      StylusOnlyInputController.setEnabled( false );
      StylusOnlyInputController.setAllowPinch( false );
    } );
  }

  private static final class Fixture implements AutoCloseable
  {
    final ActivityScenario<TDPrefActivity> scenario;
    final ArrayList<float[]> scales = new ArrayList<>();
    final ArrayList<Integer> penActions = new ArrayList<>();
    View canvas;
    Button toolbar;
    int clicks;
    int[] origin = new int[2];
    Fixture() {
      Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
      Intent intent = new Intent( context, TDPrefActivity.class );
      intent.putExtra( TDPrefCat.PREF_CATEGORY, TDPrefCat.PREF_CATEGORY_PLOT );
      scenario = ActivityScenario.launch( intent );
      scenario.onActivity( activity -> {
        FrameLayout root = new FrameLayout( activity );
        canvas = new View( activity ); canvas.setClickable( true );
        canvas.setOnTouchListener( ( v, event ) -> { penActions.add( event.getActionMasked() ); return true; } );
        root.addView( canvas, new FrameLayout.LayoutParams( -1, -1 ) );
        toolbar = new Button( activity ); toolbar.setText( "Overlay toolbar" );
        toolbar.setOnClickListener( v -> ++ clicks );
        root.addView( toolbar, new FrameLayout.LayoutParams( -1, 150, Gravity.TOP ) );
        activity.setContentView( root );
        StylusOnlyInputController.setSketchPinchZoom( activity, canvas, ( f, x, y ) -> scales.add( new float[] { f, x, y } ) );
        StylusOnlyInputController.setAllowPinch( true );
        StylusOnlyInputController.setEnabled( true );
      } );
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      scenario.onActivity( activity -> {
        assertTrue( canvas.hasWindowFocus() );
        canvas.getLocationInWindow( origin );
      } );
    }
    void send( MotionEvent event ) {
      scenario.onActivity( activity -> activity.getWindow().getCallback().dispatchTouchEvent( event ) );
      event.recycle();
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    void fingers( int action, float... xy ) {
      int[] ids = xy.length == 2 ? new int[] { 7 } : new int[] { 7, 42 };
      int[] tools = xy.length == 2 ? new int[] { F } : new int[] { F, F };
      send( event( action, ids, tools, origin, xy ) );
    }
    void start() { fingers( MotionEvent.ACTION_DOWN, 300, 400 ); fingers( SECOND_DOWN, 300, 400, 500, 400 ); }
    void expand() { fingers( MotionEvent.ACTION_MOVE, 300, 400, 600, 400 ); }
    float scale() { float factor = 1; for ( float[] sample : scales ) factor *= sample[0]; return factor; }
    @Override public void close() { scenario.close(); }
  }

  private static MotionEvent event( int action, int[] ids, int[] tools, int[] origin, float... xy )
  {
    MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[ids.length];
    MotionEvent.PointerCoords[] coords = coordinates( origin, xy );
    for ( int i = 0; i < ids.length; ++i ) {
      properties[i] = new MotionEvent.PointerProperties(); properties[i].id = ids[i]; properties[i].toolType = tools[i];
    }
    long now = SystemClock.uptimeMillis();
    return MotionEvent.obtain( now, now, action, ids.length, properties, coords,
        0, 0, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0 );
  }

  private static MotionEvent.PointerCoords[] coordinates( int[] origin, float... xy )
  {
    MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[xy.length / 2];
    for ( int i = 0; i < coords.length; ++i ) {
      coords[i] = new MotionEvent.PointerCoords();
      coords[i].x = origin[0] + xy[i * 2]; coords[i].y = origin[1] + xy[i * 2 + 1]; coords[i].pressure = 1;
    }
    return coords;
  }

  @Test public void optionalPinchNeverDeliversFingerTouchesOrSingleFingerDrags()
  {
    try ( Fixture f = new Fixture() ) {
      f.scenario.onActivity( a -> StylusOnlyInputController.setAllowPinch( false ) );
      f.start(); f.expand(); assertTrue( f.scales.isEmpty() ); assertTrue( f.penActions.isEmpty() );
      f.scenario.onActivity( a -> StylusOnlyInputController.setAllowPinch( true ) );
      f.expand(); assertTrue( f.scales.isEmpty() ); // Enabling cannot resume existing contacts.
      f.fingers( MotionEvent.ACTION_DOWN, 300, 400 );
      f.fingers( MotionEvent.ACTION_MOVE, 600, 400 );
      f.fingers( MotionEvent.ACTION_UP, 600, 400 );
      assertTrue( f.scales.isEmpty() ); assertTrue( f.penActions.isEmpty() );
      f.start(); f.expand(); assertEquals( 1.5f, f.scale(), 0.001f );
      assertTrue( f.penActions.isEmpty() ); assertEquals( 450, f.scales.get( 0 )[1], 0.001f );
      f.fingers( SECOND_UP, 300, 400, 600, 400 );
      f.fingers( MotionEvent.ACTION_MOVE, 700, 400 );
      f.fingers( MotionEvent.ACTION_UP, 700, 400 );
      assertTrue( f.penActions.isEmpty() ); assertEquals( 1.5f, f.scale(), 0.001f );
    }
  }

  @Test public void toolbarContactsCannotStartPinchOrClick()
  {
    try ( Fixture f = new Fixture() ) {
      f.fingers( MotionEvent.ACTION_DOWN, 300, 75 );
      f.fingers( SECOND_DOWN, 300, 400, 500, 400 ); f.expand();
      assertTrue( f.scales.isEmpty() );
      f.fingers( MotionEvent.ACTION_DOWN, 300, 400 );
      f.fingers( SECOND_DOWN, 300, 400, 500, 75 ); f.expand();
      assertTrue( f.scales.isEmpty() );
      f.fingers( MotionEvent.ACTION_DOWN, 300, 75 ); f.fingers( MotionEvent.ACTION_UP, 300, 75 );
      assertEquals( 0, f.clicks );
      f.start(); f.expand(); assertEquals( 1.5f, f.scale(), 0.001f );
    }
  }

  @Test public void twoFingerTranslationAndSmallJitterDoNotZoom()
  {
    try ( Fixture f = new Fixture() ) {
      f.start();
      f.fingers( MotionEvent.ACTION_MOVE, 400, 450, 600, 450 );
      f.fingers( MotionEvent.ACTION_MOVE, 400, 450, 601, 450 );
      assertTrue( f.scales.isEmpty() ); assertTrue( f.penActions.isEmpty() );
    }
  }

  @Test public void pointerIdsAndBatchedMovementHistoryPreserveScale()
  {
    try ( Fixture f = new Fixture() ) {
      f.start();
      MotionEvent move = event( MotionEvent.ACTION_MOVE, new int[] { 42, 7 }, new int[] { F, F }, f.origin,
          540, 400, 300, 400 );
      move.addBatch( move.getEventTime() + 1, coordinates( f.origin, 600, 400, 300, 400 ), 0 );
      move.addBatch( move.getEventTime() + 1, coordinates( f.origin, 660, 400, 300, 400 ), 0 );
      f.send( move );
      assertEquals( 3, f.scales.size() ); assertEquals( 1.8f, f.scale(), 0.001f );
      assertTrue( f.penActions.isEmpty() );
    }
  }

  @Test public void thirdFingerAndNonFingerToolsCancelUntilFreshPress()
  {
    try ( Fixture f = new Fixture() ) {
      f.start();
      f.send( event( MotionEvent.ACTION_POINTER_DOWN | ( 2 << 8 ), new int[] { 7, 42, 90 }, new int[] { F, F, F }, f.origin,
          300, 400, 500, 400, 700, 400 ) );
      f.expand(); assertTrue( f.scales.isEmpty() );
      for ( int tool : new int[] { MotionEvent.TOOL_TYPE_MOUSE, MotionEvent.TOOL_TYPE_UNKNOWN } ) {
        f.fingers( MotionEvent.ACTION_DOWN, 300, 400 );
        f.send( event( SECOND_DOWN, new int[] { 7, 42 }, new int[] { F, tool }, f.origin, 300, 400, 500, 400 ) );
        f.expand(); assertTrue( f.scales.isEmpty() );
      }
      f.start(); f.expand(); assertEquals( 1.5f, f.scale(), 0.001f );
    }
  }

  @Test public void penAndEraserTakePriorityInBothPointerOrders()
  {
    try ( Fixture f = new Fixture() ) {
      for ( int pen : new int[] { P, E } ) {
        f.send( event( MotionEvent.ACTION_DOWN, new int[] { 7 }, new int[] { pen }, f.origin, 300, 400 ) );
        f.send( event( SECOND_DOWN, new int[] { 7, 42 }, new int[] { pen, F }, f.origin, 300, 400, 500, 400 ) );
        f.send( event( MotionEvent.ACTION_MOVE, new int[] { 7, 42 }, new int[] { pen, F }, f.origin, 310, 400, 650, 400 ) );
        f.send( event( SECOND_UP, new int[] { 7, 42 }, new int[] { pen, F }, f.origin, 310, 400, 650, 400 ) );
        f.send( event( MotionEvent.ACTION_UP, new int[] { 7 }, new int[] { pen }, f.origin, 310, 400 ) );
        assertTrue( f.scales.isEmpty() );
        assertEquals( Integer.valueOf( MotionEvent.ACTION_UP ), f.penActions.get( f.penActions.size() - 1 ) );
        f.fingers( MotionEvent.ACTION_DOWN, 300, 400 );
        f.send( event( SECOND_DOWN, new int[] { 7, 42 }, new int[] { F, pen }, f.origin, 300, 400, 500, 400 ) );
        f.send( event( SECOND_UP, new int[] { 7, 42 }, new int[] { F, pen }, f.origin, 300, 400, 500, 400 ) );
        f.fingers( SECOND_DOWN, 300, 400, 500, 400 ); f.expand();
        assertTrue( f.scales.isEmpty() );
      }
      f.start(); f.expand();
      int count = f.scales.size();
      f.send( event( MotionEvent.ACTION_POINTER_DOWN | ( 2 << 8 ), new int[] { 7, 42, 90 }, new int[] { F, F, P }, f.origin,
          300, 400, 600, 400, 700, 400 ) );
      assertEquals( Integer.valueOf( MotionEvent.ACTION_DOWN ), f.penActions.get( f.penActions.size() - 1 ) );
      f.expand(); assertEquals( count, f.scales.size() );
    }
  }

  @Test public void cancellationFocusLossBackgroundAndOptionChangesRequireFreshPress()
  {
    try ( Fixture f = new Fixture() ) {
      for ( int reason = 0; reason < 5; ++ reason ) {
        f.start();
        if ( reason == 0 ) f.fingers( MotionEvent.ACTION_CANCEL, 300, 400, 500, 400 );
        if ( reason == 1 ) f.scenario.onActivity( a -> {
          a.getWindow().getCallback().onWindowFocusChanged( false );
          a.getWindow().getCallback().onWindowFocusChanged( true );
        } );
        if ( reason == 2 ) f.scenario.onActivity( a -> {
          StylusOnlyInputController.setEnabled( false ); StylusOnlyInputController.setEnabled( true );
        } );
        if ( reason == 3 ) f.scenario.onActivity( a -> {
          StylusOnlyInputController.setAllowPinch( false ); StylusOnlyInputController.setAllowPinch( true );
        } );
        if ( reason == 4 ) {
          f.scenario.moveToState( androidx.lifecycle.Lifecycle.State.STARTED );
          f.scenario.moveToState( androidx.lifecycle.Lifecycle.State.RESUMED );
        }
        f.expand(); assertTrue( f.scales.isEmpty() );
      }
      f.start(); f.expand(); assertEquals( 1.5f, f.scale(), 0.001f );
    }
  }

  @Test public void unlockedModeKeepsOrdinaryTouchDispatch()
  {
    try ( Fixture f = new Fixture() ) {
      f.scenario.onActivity( a -> StylusOnlyInputController.setEnabled( false ) );
      f.start(); f.expand();
      assertTrue( f.scales.isEmpty() );
      assertEquals( 3, f.penActions.size() );
      assertEquals( Integer.valueOf( MotionEvent.ACTION_POINTER_DOWN ), f.penActions.get( 1 ) );
    }
  }
}
