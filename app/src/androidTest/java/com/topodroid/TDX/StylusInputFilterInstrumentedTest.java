package com.topodroid.TDX;

import static org.junit.Assert.*;

import android.view.InputDevice;
import android.view.MotionEvent;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.ArrayList;

@RunWith( AndroidJUnit4.class )
public class StylusInputFilterInstrumentedTest
{
  private final StylusInputFilter filter = new StylusInputFilter();
  private final ArrayList<MotionEvent> delivered = new ArrayList<>();
  private final StylusInputFilter.Dispatch target = event -> { delivered.add( MotionEvent.obtain( event ) ); return true; };

  @After public void cleanup() { for ( MotionEvent event : delivered ) event.recycle(); }

  static MotionEvent event( int action, int... tools )
  {
    MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[tools.length];
    MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[tools.length];
    for ( int i = 0; i < tools.length; ++i ) {
      properties[i] = new MotionEvent.PointerProperties();
      properties[i].id = i + 4;
      properties[i].toolType = tools[i];
      coords[i] = new MotionEvent.PointerCoords();
      coords[i].x = 40 + 100 * i;
      coords[i].y = 50 + 100 * i;
      coords[i].pressure = 0.6f;
      coords[i].toolMajor = 80; // A large pen tip must still be accepted.
    }
    return MotionEvent.obtain( 100, 120, action, tools.length, properties, coords,
        0, MotionEvent.BUTTON_STYLUS_PRIMARY, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0 );
  }

  private void touch( int action, int... tools )
  {
    MotionEvent event = event( action, tools );
    try { filter.touch( event, true, target ); } finally { event.recycle(); }
  }

  @Test public void rejectsFingerMouseAndUnknownTools()
  {
    for ( int tool : new int[] { MotionEvent.TOOL_TYPE_FINGER, MotionEvent.TOOL_TYPE_MOUSE, MotionEvent.TOOL_TYPE_UNKNOWN } ) {
      touch( MotionEvent.ACTION_DOWN, tool );
      touch( MotionEvent.ACTION_MOVE, tool );
      touch( MotionEvent.ACTION_UP, tool );
    }
    assertTrue( delivered.isEmpty() );
  }

  @Test public void acceptsPenAndEraserRegardlessOfContactSize()
  {
    for ( int tool : new int[] { MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_ERASER } ) {
      touch( MotionEvent.ACTION_DOWN, tool );
      touch( MotionEvent.ACTION_UP, tool );
    }
    assertEquals( 4, delivered.size() );
    assertEquals( MotionEvent.TOOL_TYPE_ERASER, delivered.get( 3 ).getToolType( 0 ) );
    assertEquals( 80, delivered.get( 0 ).getToolMajor(), 0 );
  }

  @Test public void fingerFirstStillAllowsPenAndDoesNotCreatePinch()
  {
    touch( MotionEvent.ACTION_DOWN, MotionEvent.TOOL_TYPE_FINGER );
    touch( MotionEvent.ACTION_POINTER_DOWN | (1 << 8), MotionEvent.TOOL_TYPE_FINGER, MotionEvent.TOOL_TYPE_STYLUS );
    touch( MotionEvent.ACTION_MOVE, MotionEvent.TOOL_TYPE_FINGER, MotionEvent.TOOL_TYPE_STYLUS );
    touch( MotionEvent.ACTION_POINTER_UP | (1 << 8), MotionEvent.TOOL_TYPE_FINGER, MotionEvent.TOOL_TYPE_STYLUS );
    touch( MotionEvent.ACTION_UP, MotionEvent.TOOL_TYPE_FINGER );
    assertEquals( 3, delivered.size() );
    assertEquals( MotionEvent.ACTION_DOWN, delivered.get( 0 ).getAction() );
    assertEquals( MotionEvent.ACTION_MOVE, delivered.get( 1 ).getAction() );
    assertEquals( MotionEvent.ACTION_UP, delivered.get( 2 ).getAction() );
    for ( MotionEvent event : delivered ) {
      assertEquals( 1, event.getPointerCount() );
      assertEquals( 5, event.getPointerId( 0 ) );
      assertEquals( 140, event.getX(), 0 );
    }
  }

  @Test public void fingerJoiningAndLeavingDoesNotEndPenGesture()
  {
    touch( MotionEvent.ACTION_DOWN, MotionEvent.TOOL_TYPE_STYLUS );
    touch( MotionEvent.ACTION_POINTER_DOWN | (1 << 8), MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_FINGER );
    touch( MotionEvent.ACTION_POINTER_UP | (1 << 8), MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_FINGER );
    touch( MotionEvent.ACTION_UP, MotionEvent.TOOL_TYPE_STYLUS );
    assertEquals( 4, delivered.size() );
    assertEquals( MotionEvent.ACTION_MOVE, delivered.get( 1 ).getAction() );
    assertEquals( MotionEvent.ACTION_MOVE, delivered.get( 2 ).getAction() );
    assertEquals( MotionEvent.ACTION_UP, delivered.get( 3 ).getAction() );
  }

  @Test public void pointerIndexChangesKeepPenIdentity()
  {
    touch( MotionEvent.ACTION_DOWN, MotionEvent.TOOL_TYPE_FINGER );
    touch( MotionEvent.ACTION_POINTER_DOWN | (1 << 8), MotionEvent.TOOL_TYPE_FINGER, MotionEvent.TOOL_TYPE_STYLUS );
    touch( MotionEvent.ACTION_POINTER_UP, MotionEvent.TOOL_TYPE_FINGER, MotionEvent.TOOL_TYPE_STYLUS );
    MotionEvent original = event( MotionEvent.ACTION_MOVE, MotionEvent.TOOL_TYPE_STYLUS );
    MotionEvent.PointerProperties props = new MotionEvent.PointerProperties();
    original.getPointerProperties( 0, props );
    props.id = 5;
    MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
    original.getPointerCoords( 0, coords );
    MotionEvent moved = MotionEvent.obtain( 100, 130, MotionEvent.ACTION_MOVE, 1,
        new MotionEvent.PointerProperties[] { props }, new MotionEvent.PointerCoords[] { coords }, 0, 0, 1, 1, 0, 0, InputDevice.SOURCE_STYLUS, 0 );
    filter.touch( moved, true, target );
    original.recycle(); moved.recycle();
    assertEquals( MotionEvent.ACTION_MOVE, delivered.get( 2 ).getAction() );
    assertEquals( 5, delivered.get( 2 ).getPointerId( 0 ) );
  }

  @Test public void preservesHistoryPressureAndPenButton()
  {
    touch( MotionEvent.ACTION_DOWN, MotionEvent.TOOL_TYPE_STYLUS );
    MotionEvent move = event( MotionEvent.ACTION_MOVE, MotionEvent.TOOL_TYPE_STYLUS );
    move.addBatch( 130, 300, 400, 0.9f, 0.2f, 0 );
    filter.touch( move, true, target );
    move.recycle();
    MotionEvent result = delivered.get( 1 );
    assertEquals( 1, result.getHistorySize() );
    assertEquals( 40, result.getHistoricalX( 0 ), 0 );
    assertEquals( 300, result.getX(), 0 );
    assertEquals( 0.9f, result.getPressure(), 0.001f );
    assertEquals( MotionEvent.BUTTON_STYLUS_PRIMARY, result.getButtonState() );
    assertEquals( 120, result.getDownTime() );
  }

  @Test public void cancellationDoesNotDeliverAnUpOrAcceptTrailingMove()
  {
    touch( MotionEvent.ACTION_DOWN, MotionEvent.TOOL_TYPE_STYLUS );
    filter.cancel( target );
    touch( MotionEvent.ACTION_MOVE, MotionEvent.TOOL_TYPE_STYLUS );
    touch( MotionEvent.ACTION_UP, MotionEvent.TOOL_TYPE_STYLUS );
    assertEquals( 2, delivered.size() );
    assertEquals( MotionEvent.ACTION_CANCEL, delivered.get( 1 ).getAction() );
    touch( MotionEvent.ACTION_DOWN, MotionEvent.TOOL_TYPE_STYLUS );
    assertEquals( 3, delivered.size() );
  }

  @Test public void canceledPenPointerUpBecomesCancel()
  {
    touch( MotionEvent.ACTION_DOWN, MotionEvent.TOOL_TYPE_STYLUS );
    MotionEvent.PointerProperties props = new MotionEvent.PointerProperties();
    props.id = 4; props.toolType = MotionEvent.TOOL_TYPE_STYLUS;
    MotionEvent up = MotionEvent.obtain( 100, 130, MotionEvent.ACTION_UP, 1,
        new MotionEvent.PointerProperties[] { props }, new MotionEvent.PointerCoords[] { new MotionEvent.PointerCoords() },
        0, 0, 1, 1, 0, 0, InputDevice.SOURCE_STYLUS, MotionEvent.FLAG_CANCELED );
    filter.touch( up, true, target ); up.recycle();
    assertEquals( MotionEvent.ACTION_CANCEL, delivered.get( 1 ).getAction() );
  }

  @Test public void disabledModePassesOriginalEventsAndGenericPenSurvivesLock()
  {
    MotionEvent finger = event( MotionEvent.ACTION_DOWN, MotionEvent.TOOL_TYPE_FINGER );
    filter.touch( finger, false, e -> { assertSame( finger, e ); return true; } );
    finger.recycle();
    MotionEvent penHover = event( MotionEvent.ACTION_HOVER_MOVE, MotionEvent.TOOL_TYPE_STYLUS );
    filter.generic( penHover, true, target ); penHover.recycle();
    MotionEvent mouseHover = event( MotionEvent.ACTION_HOVER_MOVE, MotionEvent.TOOL_TYPE_MOUSE );
    filter.generic( mouseHover, true, target ); mouseHover.recycle();
    assertEquals( 1, delivered.size() );
    assertEquals( MotionEvent.ACTION_HOVER_MOVE, delivered.get( 0 ).getAction() );
  }

  @Test public void lockingAndUnlockingRequireFreshPresses()
  {
    MotionEvent finger = event( MotionEvent.ACTION_DOWN, MotionEvent.TOOL_TYPE_FINGER );
    filter.touch( finger, false, target ); finger.recycle();
    filter.cancel( target ); // Locking cancels the old finger gesture.
    touch( MotionEvent.ACTION_UP, MotionEvent.TOOL_TYPE_FINGER );
    touch( MotionEvent.ACTION_DOWN, MotionEvent.TOOL_TYPE_STYLUS );
    filter.cancel( target ); // Unlocking cancels the active pen too.
    MotionEvent trailing = event( MotionEvent.ACTION_MOVE, MotionEvent.TOOL_TYPE_STYLUS );
    filter.touch( trailing, false, target ); trailing.recycle();
    assertEquals( 4, delivered.size() );
    assertEquals( MotionEvent.ACTION_CANCEL, delivered.get( 1 ).getAction() );
    assertEquals( MotionEvent.ACTION_CANCEL, delivered.get( 3 ).getAction() );
    MotionEvent fresh = event( MotionEvent.ACTION_DOWN, MotionEvent.TOOL_TYPE_FINGER );
    filter.touch( fresh, false, target ); fresh.recycle();
    assertEquals( 5, delivered.size() );
  }

  @Test public void fastPenMotionRetainsEveryHistoricalSampleWithoutDistanceLimits()
  {
    touch( MotionEvent.ACTION_DOWN, MotionEvent.TOOL_TYPE_STYLUS );
    MotionEvent move = event( MotionEvent.ACTION_MOVE, MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_FINGER );
    for ( int sample = 1; sample <= 8; ++sample ) {
      MotionEvent.PointerCoords pen = new MotionEvent.PointerCoords(), finger = new MotionEvent.PointerCoords();
      pen.x = sample * 1000; pen.y = sample * 2000; pen.pressure = sample/10f;
      filter.touch( move, true, target );
      move.addBatch( 120 + sample, new MotionEvent.PointerCoords[] { pen, finger }, 0 );
    }
    filter.touch( move, true, target ); move.recycle();
    MotionEvent result = delivered.get( delivered.size() - 1 );
    assertEquals( 8, result.getHistorySize() ); assertEquals( 1, result.getPointerCount() );
    assertEquals( 8000, result.getX(), 0 ); assertEquals( 16000, result.getY(), 0 );
    assertEquals( 120, result.getHistoricalEventTime( 0 ) );
    assertEquals( 7000, result.getHistoricalX( 7 ), 0 );
  }
}
