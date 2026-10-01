package com.topodroid.TDX;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.Intent;
import android.graphics.Paint;
import android.graphics.PointF;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.topodroid.prefs.TDSetting;
import com.topodroid.types.SymbolType;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Real canvas and input dispatch, entirely in memory: never opens or saves a survey. */
@RunWith(AndroidJUnit4.class)
public class HeldNavigationInstrumentedTest
{
  private interface Check { void run() throws Exception; }
  private static void main(Check check) {
    InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
      try { check.run(); } catch (Exception e) { throw new AssertionError(e); }
    });
  }
  private static Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
  private static Field field(Object target, String name) throws Exception {
    for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
      try { Field f = type.getDeclaredField(name); f.setAccessible(true); return f; }
      catch (NoSuchFieldException ignored) { }
    }
    throw new NoSuchFieldException(name);
  }
  private static void set(Object target, String name, Object value) throws Exception { field(target, name).set(target, value); }
  private static Object get(Object target, String name) throws Exception { return field(target, name).get(target); }
  private static final String[] SETTINGS = {
    "mSPenSingleClickAction", "mSPenLongClickAction", "mSPenDoubleClickAction",
    "mActiveKeySinglePressAction", "mActiveKeyLongPressAction", "mActiveKeyDoublePressAction",
    "mVolumeUpSinglePressAction", "mVolumeUpLongPressAction", "mVolumeUpDoublePressAction",
    "mVolumeDownSinglePressAction", "mVolumeDownLongPressAction", "mVolumeDownDoublePressAction",
    "mButtonHoldMs", "mButtonDoubleMs", "mSPenTransitionMs"
  };
  private final int[] saved = new int[SETTINGS.length];
  private boolean savedStylusOnly;
  private BareDrawing drawing;
  private ActiveKeyGestureHelper keyHelper;
  @Before public void setup() {
    main(() -> {
      TDInstance.setContext(context().getApplicationContext());
      savedStylusOnly = StylusOnlyInputController.isEnabled();
      for (int i = 0; i < SETTINGS.length; ++i) {
        Field f = TDSetting.class.getField(SETTINGS[i]); saved[i] = f.getInt(null); f.setInt(null, i < 12 ? 0 : -1);
      }
    });
  }
  @After public void cleanup() {
    main(() -> {
      if (drawing != null) {
        ActionKeyBindingManager.unregisterHost(drawing);
        ((SPenGestureHelper)get(drawing, "mSPenGestureHelper")).cancel();
      }
      if (keyHelper != null) keyHelper.cancel();
      StylusOnlyInputController.setEnabled(savedStylusOnly);
      for (int i = 0; i < SETTINGS.length; ++i) TDSetting.class.getField(SETTINGS[i]).setInt(null, saved[i]);
    });
  }
  private static class BareDrawing extends DrawingWindow {
    @Override public void checkZoomBtnsCtrl() { }
    @Override public void setTheTitle() { }
    @Override public WindowManager getWindowManager() { return (WindowManager)context().getSystemService(Context.WINDOW_SERVICE); }
  }
  private BareDrawing canvas() throws Exception {
    drawing = new BareDrawing();
    DrawingSurface surface = new DrawingSurface(context(), null);
    set(surface, "commandManager", new DrawingCommandManager(0, "held-navigation-test"));
    surface.makePreviewPath(DrawingPath.DRAWING_PATH_LINE, new Paint());
    set(drawing, "mDrawingSurface", surface); set(drawing, "mZoom", 1f);
    set(drawing, "mOffset", new PointF(0, 0)); set(drawing, "mMode", DrawingWindow.MODE_DRAW);
    set(drawing, "mSymbol", SymbolType.LINE); set(drawing, "mCurrentBrush", new DrawingPenBrush());
    return drawing;
  }
  private static MotionEvent event(int action, int tool, int buttons, float x, float y) {
    MotionEvent.PointerProperties p = new MotionEvent.PointerProperties(); p.id = 0; p.toolType = tool;
    MotionEvent.PointerCoords c = new MotionEvent.PointerCoords(); c.x = x; c.y = y; c.pressure = 1;
    long t = SystemClock.uptimeMillis();
    return MotionEvent.obtain(t, t, action, 1, new MotionEvent.PointerProperties[]{p},
      new MotionEvent.PointerCoords[]{c}, 0, buttons, 1, 1, 0, 0, android.view.InputDevice.SOURCE_STYLUS, 0);
  }
  private void touch(int action, int tool, int buttons, float x, float y) throws Exception {
    MotionEvent e = event(action, tool, buttons, x, y);
    try { assertTrue(drawing.onTouch((DrawingSurface)get(drawing, "mDrawingSurface"), e)); } finally { e.recycle(); }
  }
  private void hover(int action, int buttons) throws Exception {
    hover(action, buttons, 100, 200);
  }
  private void hover(int action, int buttons, float x, float y) throws Exception {
    MotionEvent e = event(action, MotionEvent.TOOL_TYPE_STYLUS, buttons, x, y);
    try { drawing.onGenericMotion((DrawingSurface)get(drawing, "mDrawingSurface"), e); } finally { e.recycle(); }
  }
  private static void key(int code, boolean down) {
    KeyEvent e = new KeyEvent(down ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP, code);
    if (down) assertTrue(ActionKeyBindingManager.onKeyDown(code, e));
    else assertTrue(ActionKeyBindingManager.onKeyUp(code, e));
  }
  private final Object recoveryOwner = new Object();
  private void recoveryKey(boolean down, long downTime, long eventTime) {
    KeyEvent e = new KeyEvent(downTime, eventTime, down ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_DOWN, 0);
    assertTrue(StylusOnlyInputController.key(recoveryOwner, e, event -> event.getAction() == KeyEvent.ACTION_DOWN
      ? ActionKeyBindingManager.onKeyDown(event.getKeyCode(), event) : ActionKeyBindingManager.onKeyUp(event.getKeyCode(), event)));
  }
  private HeldNavigationController navigation() throws Exception { return (HeldNavigationController)get(drawing, "mHeldNavigation"); }

  @Test public void pressPanMovesCanvasWithPenOrFingerAndReleaseKeepsToolWithoutCommitting() {
    main(() -> {
      canvas(); TDSetting.mVolumeUpSinglePressAction = TDSetting.SPEN_ACTION_HOLD_PAN;
      // Immediate Press must own the gesture despite other assignments on the same key.
      TDSetting.mVolumeUpLongPressAction = TDSetting.SPEN_ACTION_TOGGLE_ERASE;
      TDSetting.mVolumeUpDoublePressAction = TDSetting.SPEN_ACTION_BACK;
      ActionKeyBindingManager.registerHost(drawing);
      key(KeyEvent.KEYCODE_VOLUME_UP, true);
      touch(MotionEvent.ACTION_DOWN, MotionEvent.TOOL_TYPE_STYLUS, 0, 100, 200);
      touch(MotionEvent.ACTION_MOVE, MotionEvent.TOOL_TYPE_STYLUS, 0, 160, 240);
      PointF offset = (PointF)get(drawing, "mOffset"); assertEquals(60, offset.x, .001f); assertEquals(40, offset.y, .001f);
      key(KeyEvent.KEYCODE_VOLUME_UP, false);
      touch(MotionEvent.ACTION_MOVE, MotionEvent.TOOL_TYPE_STYLUS, 0, 200, 300);
      touch(MotionEvent.ACTION_UP, MotionEvent.TOOL_TYPE_STYLUS, 0, 200, 300);
      assertEquals(60, offset.x, .001f); assertFalse(navigation().active());
      assertEquals(DrawingWindow.MODE_DRAW, get(drawing, "mMode")); assertEquals(SymbolType.LINE, get(drawing, "mSymbol"));
      assertNull(get(drawing, "mCurrentLinePath")); assertFalse((Boolean)get(drawing, "mModified"));
      key(KeyEvent.KEYCODE_VOLUME_UP, true);
      touch(MotionEvent.ACTION_DOWN, MotionEvent.TOOL_TYPE_FINGER, 0, 50, 50);
      touch(MotionEvent.ACTION_MOVE, MotionEvent.TOOL_TYPE_FINGER, 0, 80, 100);
      touch(MotionEvent.ACTION_UP, MotionEvent.TOOL_TYPE_FINGER, 0, 80, 100);
      key(KeyEvent.KEYCODE_VOLUME_UP, false);
      assertEquals(90, offset.x, .001f); assertEquals(90, offset.y, .001f);
    });
  }

  @Test public void verticalZoomAnchorsContactAndRespectsFixedZoom() {
    main(() -> {
      canvas(); drawing.beginHeldAction(0, TDSetting.SPEN_ACTION_HOLD_ZOOM);
      float distance = 160 * context().getResources().getDisplayMetrics().density;
      touch(MotionEvent.ACTION_DOWN, MotionEvent.TOOL_TYPE_STYLUS, 0, 100, 500);
      touch(MotionEvent.ACTION_MOVE, MotionEvent.TOOL_TYPE_STYLUS, 0, 900, 500 - distance);
      assertEquals(2f, drawing.zoom(), .001f);
      PointF offset = (PointF)get(drawing, "mOffset"); assertEquals(-50, offset.x, .001f); assertEquals(-250, offset.y, .001f);
      touch(MotionEvent.ACTION_MOVE, MotionEvent.TOOL_TYPE_STYLUS, 0, 900, 500);
      assertEquals(1f, drawing.zoom(), .001f);
      ((DrawingSurface)get(drawing, "mDrawingSurface")).setFixedZoom(1);
      touch(MotionEvent.ACTION_MOVE, MotionEvent.TOOL_TYPE_STYLUS, 0, 900, 500 - distance);
      assertEquals(1f, drawing.zoom(), .001f); drawing.endHeldAction(0);
      touch(MotionEvent.ACTION_UP, MotionEvent.TOOL_TYPE_STYLUS, 0, 900, 500 - distance);
      assertFalse((Boolean)get(drawing, "mModified"));
    });
  }

  @Test public void activationDuringStrokeCancelsPreviewAndConsumesRemainder() {
    main(() -> {
      canvas(); DrawingLinePath path = new DrawingLinePath(0, 0); path.addStartPoint(10, 20);
      set(drawing, "mCurrentLinePath", path); set(drawing, "mTouchActive", true);
      drawing.beginHeldAction(0, TDSetting.SPEN_ACTION_HOLD_PAN);
      assertNull(get(drawing, "mCurrentLinePath"));
      touch(MotionEvent.ACTION_MOVE, MotionEvent.TOOL_TYPE_STYLUS, 0, 100, 200);
      touch(MotionEvent.ACTION_MOVE, MotionEvent.TOOL_TYPE_STYLUS, 0, 120, 230);
      drawing.endHeldAction(0); touch(MotionEvent.ACTION_UP, MotionEvent.TOOL_TYPE_STYLUS, 0, 120, 230);
      assertFalse((Boolean)get(drawing, "mModified")); assertEquals(1, path.size());
    });
  }

  @Test public void penPressSurvivesHoverToContactAndReleaseWhileTouching() {
    main(() -> {
      canvas(); TDSetting.mSPenSingleClickAction = TDSetting.SPEN_ACTION_HOLD_PAN;
      hover(MotionEvent.ACTION_HOVER_ENTER, MotionEvent.BUTTON_STYLUS_PRIMARY);
      assertTrue(navigation().active()); hover(MotionEvent.ACTION_HOVER_EXIT, MotionEvent.BUTTON_STYLUS_PRIMARY);
      touch(MotionEvent.ACTION_DOWN, MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.BUTTON_STYLUS_PRIMARY, 100, 200);
      touch(MotionEvent.ACTION_MOVE, MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.BUTTON_STYLUS_PRIMARY, 150, 260);
      touch(MotionEvent.ACTION_MOVE, MotionEvent.TOOL_TYPE_STYLUS, 0, 180, 280);
      assertFalse(navigation().active()); touch(MotionEvent.ACTION_UP, MotionEvent.TOOL_TYPE_STYLUS, 0, 180, 280);
      PointF offset = (PointF)get(drawing, "mOffset"); assertEquals(50, offset.x, .001f); assertEquals(60, offset.y, .001f);
      assertFalse((Boolean)get(drawing, "mModified"));
    });
  }

  @Test public void penPressedBeforeContactZoomsOnHoverAndOnFirstContactDrag() {
    main(() -> {
      canvas(); TDSetting.mSPenSingleClickAction = TDSetting.SPEN_ACTION_HOLD_ZOOM;
      float distance = 160 * context().getResources().getDisplayMetrics().density;
      hover(MotionEvent.ACTION_HOVER_ENTER, 0, 100, 700);
      hover(MotionEvent.ACTION_BUTTON_PRESS, MotionEvent.BUTTON_STYLUS_PRIMARY, 100, 700);
      hover(MotionEvent.ACTION_HOVER_MOVE, MotionEvent.BUTTON_STYLUS_PRIMARY, 100, 700 - distance);
      assertEquals("Registered hover press must zoom without first touching the screen", 2f, drawing.zoom(), .001f);
      hover(MotionEvent.ACTION_HOVER_EXIT, MotionEvent.BUTTON_STYLUS_PRIMARY, 100, 700 - distance);
      touch(MotionEvent.ACTION_DOWN, MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.BUTTON_STYLUS_PRIMARY, 300, 600);
      assertEquals("Changing from hover to contact must not jump", 2f, drawing.zoom(), .001f);
      touch(MotionEvent.ACTION_MOVE, MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.BUTTON_STYLUS_PRIMARY, 300, 600 - distance);
      assertEquals("First drag after touching with the button already held must zoom", 4f, drawing.zoom(), .001f);
      touch(MotionEvent.ACTION_UP, MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.BUTTON_STYLUS_PRIMARY, 300, 600 - distance);
      hover(MotionEvent.ACTION_HOVER_ENTER, MotionEvent.BUTTON_STYLUS_PRIMARY, 300, 600 - distance);
      hover(MotionEvent.ACTION_HOVER_MOVE, MotionEvent.BUTTON_STYLUS_PRIMARY, 300, 600);
      assertEquals(2f, drawing.zoom(), .001f);
      hover(MotionEvent.ACTION_BUTTON_RELEASE, 0, 300, 600);
      hover(MotionEvent.ACTION_HOVER_MOVE, 0, 300, 600 - distance);
      assertEquals("Release must stop hover zoom immediately", 2f, drawing.zoom(), .001f);
      assertFalse(navigation().active()); assertFalse((Boolean)get(drawing, "mModified"));
      assertEquals(DrawingWindow.MODE_DRAW, get(drawing, "mMode"));
    });
  }

  @Test public void heldHoverPanIgnoresFingersAndResetsAnchorBetweenContactAndHover() {
    main(() -> {
      canvas(); TDSetting.mSPenSingleClickAction = TDSetting.SPEN_ACTION_HOLD_PAN;
      hover(MotionEvent.ACTION_BUTTON_PRESS, MotionEvent.BUTTON_STYLUS_PRIMARY, 100, 200);
      MotionEvent finger = event(MotionEvent.ACTION_HOVER_MOVE, MotionEvent.TOOL_TYPE_FINGER, 0, 500, 600);
      assertFalse(drawing.onGenericMotion((DrawingSurface)get(drawing, "mDrawingSurface"), finger)); finger.recycle();
      hover(MotionEvent.ACTION_HOVER_MOVE, MotionEvent.BUTTON_STYLUS_PRIMARY, 140, 230);
      PointF offset = (PointF)get(drawing, "mOffset"); assertEquals(40, offset.x, .001f); assertEquals(30, offset.y, .001f);
      touch(MotionEvent.ACTION_DOWN, MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.BUTTON_STYLUS_PRIMARY, 400, 500);
      hover(MotionEvent.ACTION_HOVER_MOVE, MotionEvent.BUTTON_STYLUS_PRIMARY, 600, 700);
      assertEquals("Hover samples during contact must not move the canvas", 40, offset.x, .001f);
      touch(MotionEvent.ACTION_MOVE, MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.BUTTON_STYLUS_PRIMARY, 430, 520);
      touch(MotionEvent.ACTION_UP, MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.BUTTON_STYLUS_PRIMARY, 430, 520);
      hover(MotionEvent.ACTION_HOVER_MOVE, MotionEvent.BUTTON_STYLUS_PRIMARY, 800, 900);
      assertEquals(70, offset.x, .001f); assertEquals(50, offset.y, .001f);
      hover(MotionEvent.ACTION_HOVER_MOVE, MotionEvent.BUTTON_STYLUS_PRIMARY, 820, 940);
      assertEquals(90, offset.x, .001f); assertEquals(90, offset.y, .001f);
      hover(MotionEvent.ACTION_BUTTON_RELEASE, 0);
      assertFalse((Boolean)get(drawing, "mModified"));
    });
  }

  @Test public void buttonBeforeDragReachesZoomThroughWindowInBothInputModes() {
    try (ActivityScenario<ButtonShortcutsActivity> scenario = ActivityScenario.launch(ButtonShortcutsActivity.class)) {
      final View[] view = new View[1];
      scenario.onActivity(activity -> {
        try {
          canvas(); TDSetting.mSPenSingleClickAction = TDSetting.SPEN_ACTION_HOLD_ZOOM;
          DrawingSurface surface = (DrawingSurface)get(drawing, "mDrawingSurface");
          view[0] = new View(activity); view[0].setClickable(true);
          view[0].setOnGenericMotionListener((v, e) -> drawing.onGenericMotion(surface, e));
          view[0].setOnTouchListener((v, e) -> drawing.onTouch(surface, e));
          activity.setContentView(view[0]);
        } catch (Exception e) { throw new AssertionError(e); }
      });
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      for (boolean penOnly : new boolean[]{false, true}) {
        scenario.onActivity(activity -> {
          try {
            StylusOnlyInputController.setEnabled(penOnly);
            int[] origin = new int[2]; view[0].getLocationInWindow(origin);
            float x = origin[0] + view[0].getWidth() / 2f;
            float y = origin[1] + view[0].getHeight() * .75f;
            float distance = 160 * activity.getResources().getDisplayMetrics().density;
            float initialZoom = drawing.zoom();
            for (int action : new int[]{MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_BUTTON_PRESS,
                MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_HOVER_EXIT, MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP, MotionEvent.ACTION_BUTTON_RELEASE}) {
              boolean move = action == MotionEvent.ACTION_HOVER_MOVE || action == MotionEvent.ACTION_MOVE;
              MotionEvent e = event(action, MotionEvent.TOOL_TYPE_STYLUS,
                action == MotionEvent.ACTION_HOVER_ENTER || action == MotionEvent.ACTION_BUTTON_RELEASE ? 0 : MotionEvent.BUTTON_STYLUS_PRIMARY,
                x, move ? y - distance : y);
              try {
                if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP)
                  activity.getWindow().getCallback().dispatchTouchEvent(e);
                else activity.getWindow().getCallback().dispatchGenericMotionEvent(e);
              } finally { e.recycle(); }
            }
            assertEquals("Hover and contact drag must both reach zoom; stylus-only=" + penOnly, initialZoom * 4, drawing.zoom(), .001f);
            assertFalse(navigation().active()); assertFalse((Boolean)get(drawing, "mModified"));
          } catch (Exception e) { throw new AssertionError(e); }
        });
      }
    }
  }

  @Test public void penHoldReservesContactUntilDelayAndLeavingProximityCancels() {
    main(() -> {
      canvas(); TDSetting.mSPenLongClickAction = TDSetting.SPEN_ACTION_HOLD_PAN;
      touch(MotionEvent.ACTION_DOWN, MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.BUTTON_STYLUS_PRIMARY, 100, 200);
      assertFalse(navigation().active()); assertNull(get(drawing, "mCurrentLinePath"));
    });
    SystemClock.sleep(ViewConfiguration.getLongPressTimeout() + 80);
    main(() -> {
      assertTrue(navigation().active());
      touch(MotionEvent.ACTION_MOVE, MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.BUTTON_STYLUS_PRIMARY, 120, 220);
      touch(MotionEvent.ACTION_MOVE, MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.BUTTON_STYLUS_PRIMARY, 140, 250);
      touch(MotionEvent.ACTION_UP, MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.BUTTON_STYLUS_PRIMARY, 140, 250);
      assertTrue(navigation().active()); hover(MotionEvent.ACTION_HOVER_EXIT, MotionEvent.BUTTON_STYLUS_PRIMARY);
    });
    SystemClock.sleep(ViewConfiguration.getTapTimeout() + 80);
    main(() -> assertFalse(navigation().active()));
  }

  @Test public void briefPenHoldDuringStrokeDiscardsPreviewEvenBeforeNavigationStarts() {
    main(() -> {
      canvas(); TDSetting.mSPenLongClickAction = TDSetting.SPEN_ACTION_HOLD_PAN;
      for (boolean generic : new boolean[]{false, true}) {
        DrawingLinePath path = new DrawingLinePath(0, 0); path.addStartPoint(10, 20);
        set(drawing, "mCurrentLinePath", path); set(drawing, "mTouchActive", true);
        DrawingSurface surface = (DrawingSurface)get(drawing, "mDrawingSurface");
        surface.setLiveItem(new DrawingPointPath(0, 10, 20, 0, 0));
        if (generic) {
          hover(MotionEvent.ACTION_BUTTON_PRESS, MotionEvent.BUTTON_STYLUS_PRIMARY);
          hover(MotionEvent.ACTION_BUTTON_RELEASE, 0);
        } else {
          touch(MotionEvent.ACTION_MOVE, MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.BUTTON_STYLUS_PRIMARY, 20, 30);
          touch(MotionEvent.ACTION_MOVE, MotionEvent.TOOL_TYPE_STYLUS, 0, 30, 40);
        }
        assertFalse(navigation().active()); assertNull(get(drawing, "mCurrentLinePath"));
        assertNull(get(surface, "mLiveItem"));
        touch(MotionEvent.ACTION_UP, MotionEvent.TOOL_TYPE_STYLUS, 0, 30, 40);
        assertFalse((Boolean)get(drawing, "mModified"));
      }
    });
  }

  @Test public void hardwareHoldUsesDelayAndCancelOrHostChangeAlwaysEndsNavigation() {
    main(() -> {
      canvas(); TDSetting.mVolumeDownLongPressAction = TDSetting.SPEN_ACTION_HOLD_ZOOM;
      ActionKeyBindingManager.registerHost(drawing); key(KeyEvent.KEYCODE_VOLUME_DOWN, true);
      assertFalse(navigation().active());
    });
    SystemClock.sleep(ViewConfiguration.getLongPressTimeout() + 80);
    main(() -> {
      assertTrue(navigation().active()); key(KeyEvent.KEYCODE_VOLUME_DOWN, false); assertFalse(navigation().active());
      TDSetting.mVolumeDownSinglePressAction = TDSetting.SPEN_ACTION_HOLD_PAN;
      key(KeyEvent.KEYCODE_VOLUME_DOWN, true);
      touch(MotionEvent.ACTION_DOWN, MotionEvent.TOOL_TYPE_STYLUS, 0, 100, 200);
      touch(MotionEvent.ACTION_CANCEL, MotionEvent.TOOL_TYPE_STYLUS, 0, 100, 200);
      assertFalse(navigation().active()); key(KeyEvent.KEYCODE_VOLUME_DOWN, false);
      key(KeyEvent.KEYCODE_VOLUME_DOWN, true); ActionKeyBindingManager.cancelGestures(drawing); assertFalse(navigation().active());
      key(KeyEvent.KEYCODE_VOLUME_DOWN, true); drawing.onWindowFocusChanged(false); assertFalse(navigation().active());
      key(KeyEvent.KEYCODE_VOLUME_DOWN, true); assertTrue(navigation().active());
      ActionKeyBindingManager.registerHost(action -> true); assertFalse(navigation().active());
      ActionKeyBindingManager.registerHost(drawing);
    });
  }

  @Test public void activeKeyBroadcastAndDirectReportsDoNotDoubleStartOrLeaveStuckHold() {
    main(() -> {
      canvas(); TDSetting.mActiveKeySinglePressAction = TDSetting.SPEN_ACTION_HOLD_PAN;
      ActionKeyBindingManager.registerHost(drawing);
      Intent report = new Intent(ActionKeyBindingManager.ACTION_HARD_KEY_REPORT)
        .putExtra(ActionKeyBindingManager.EXTRA_KEY_CODE, ActionKeyBindingManager.KEYCODE_PTT)
        .putExtra(ActionKeyBindingManager.EXTRA_KEY_REPORT_TYPE, ActionKeyBindingManager.KEY_ACTION_DOWN);
      ActionKeyBindingManager.onBroadcastIntent(report); key(ActionKeyBindingManager.KEYCODE_PTT, true);
      assertTrue(navigation().active());
      report.putExtra(ActionKeyBindingManager.EXTRA_KEY_REPORT_TYPE, ActionKeyBindingManager.KEY_ACTION_UP);
      ActionKeyBindingManager.onBroadcastIntent(report); key(ActionKeyBindingManager.KEYCODE_PTT, false);
      assertFalse(navigation().active());
    });
  }

  @Test public void customHoldAndDoubleTimingsApplyToExistingRecognizerOnNextGesture() {
    List<Integer> triggers = new ArrayList<>();
    main(() -> {
      keyHelper = new ActiveKeyGestureHelper(triggers::add, () -> true);
      TDSetting.mButtonHoldMs = 150;
      keyHelper.onKeyDown(SystemClock.uptimeMillis());
      TDSetting.mButtonHoldMs = 2000; // An in-flight hold retains its starting delay.
      assertTrue(triggers.isEmpty());
    });
    SystemClock.sleep(300);
    main(() -> {
      assertEquals(java.util.Arrays.asList(2), triggers);
      keyHelper.onKeyUp(SystemClock.uptimeMillis());
      TDSetting.mButtonDoubleMs = 150;
      keyHelper.onKeyDown(SystemClock.uptimeMillis()); keyHelper.onKeyUp(SystemClock.uptimeMillis());
      TDSetting.mButtonDoubleMs = 1000; // The pending first press keeps its original window.
      assertEquals(java.util.Arrays.asList(2), triggers);
    });
    SystemClock.sleep(300);
    main(() -> {
      assertEquals(java.util.Arrays.asList(2, 1), triggers);
      long t = SystemClock.uptimeMillis();
      keyHelper.onKeyDown(t); keyHelper.onKeyUp(t + 10);
      keyHelper.onKeyDown(t + 650); keyHelper.onKeyUp(t + 660);
      assertEquals(java.util.Arrays.asList(2, 1, 3), triggers);
    });
  }

  @Test public void customPenTransitionGapKeepsImmediateZoomAcrossDelayedContactAndStillCancels() {
    main(() -> {
      canvas(); TDSetting.mSPenSingleClickAction = TDSetting.SPEN_ACTION_HOLD_ZOOM;
      TDSetting.mButtonHoldMs = 2000; TDSetting.mButtonDoubleMs = 1000; TDSetting.mSPenTransitionMs = 500;
      hover(MotionEvent.ACTION_BUTTON_PRESS, MotionEvent.BUTTON_STYLUS_PRIMARY);
      assertTrue("Press navigation remains immediate with long custom delays", navigation().active());
      hover(MotionEvent.ACTION_HOVER_EXIT, MotionEvent.BUTTON_STYLUS_PRIMARY);
    });
    SystemClock.sleep(180); // Longer than Android's normal hover-to-touch allowance.
    main(() -> {
      assertTrue(navigation().active());
      float distance = 160 * context().getResources().getDisplayMetrics().density;
      touch(MotionEvent.ACTION_DOWN, MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.BUTTON_STYLUS_PRIMARY, 100, 700);
      touch(MotionEvent.ACTION_MOVE, MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.BUTTON_STYLUS_PRIMARY, 100, 700 - distance);
      assertEquals(2f, drawing.zoom(), .001f);
      touch(MotionEvent.ACTION_UP, MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.BUTTON_STYLUS_PRIMARY, 100, 700 - distance);
      hover(MotionEvent.ACTION_BUTTON_RELEASE, 0); assertFalse(navigation().active());
      hover(MotionEvent.ACTION_BUTTON_PRESS, MotionEvent.BUTTON_STYLUS_PRIMARY);
      hover(MotionEvent.ACTION_HOVER_EXIT, MotionEvent.BUTTON_STYLUS_PRIMARY);
    });
    SystemClock.sleep(650);
    main(() -> { assertFalse(navigation().active()); assertFalse((Boolean)get(drawing, "mModified")); });
  }

  @Test public void ordinaryPressDoubleAndLongTriggersStillDispatchOnce() {
    List<Integer> triggers = new ArrayList<>();
    main(() -> {
      keyHelper = new ActiveKeyGestureHelper(triggers::add, () -> true);
      long t = SystemClock.uptimeMillis(); keyHelper.onKeyDown(t); keyHelper.onKeyUp(t + 10);
      keyHelper.onKeyDown(t + 30); keyHelper.onKeyUp(t + 40);
      assertEquals(java.util.Arrays.asList(3), triggers);
      keyHelper.onKeyDown(t + 50);
    });
    SystemClock.sleep(ViewConfiguration.getLongPressTimeout() + 80);
    main(() -> {
      keyHelper.onKeyUp(SystemClock.uptimeMillis()); assertEquals(java.util.Arrays.asList(3, 2), triggers);
      keyHelper.onKeyDown(SystemClock.uptimeMillis()); keyHelper.onKeyUp(SystemClock.uptimeMillis());
    });
    SystemClock.sleep(ViewConfiguration.getDoubleTapTimeout() + 80);
    main(() -> assertEquals(java.util.Arrays.asList(3, 2, 1), triggers));
  }

  @Test public void immediateHeldPressSuppressesOtherTriggersAndCancelEndsExactlyOnce() {
    List<String> events = new ArrayList<>();
    main(() -> {
      keyHelper = new ActiveKeyGestureHelper(t -> events.add("trigger" + t), () -> true,
        new ActiveKeyGestureHelper.HeldActionListener() {
          @Override public boolean onHeldActionStart(int trigger) { events.add("start" + trigger); return true; }
          @Override public void onHeldActionEnd() { events.add("end"); }
        });
      keyHelper.onKeyDown(SystemClock.uptimeMillis()); keyHelper.onKeyDown(SystemClock.uptimeMillis());
    });
    SystemClock.sleep(ViewConfiguration.getLongPressTimeout() + 80);
    main(() -> {
      assertEquals(java.util.Arrays.asList("start1"), events);
      keyHelper.cancel(); keyHelper.cancel(); keyHelper.onKeyUp(SystemClock.uptimeMillis());
      assertEquals(java.util.Arrays.asList("start1", "end"), events);
    });
  }

  @Test public void stylusOnlyRecoveryKeyAllowsImmediatePanAndRetainsFiveSecondEscape() {
    main(() -> {
      canvas(); TDSetting.mVolumeDownSinglePressAction = TDSetting.SPEN_ACTION_HOLD_PAN;
      StylusOnlyInputController.setEnabled(true); ActionKeyBindingManager.registerHost(drawing);
      long t = SystemClock.uptimeMillis(); recoveryKey(true, t, t); assertTrue(navigation().active());
      touch(MotionEvent.ACTION_DOWN, MotionEvent.TOOL_TYPE_STYLUS, 0, 100, 100);
      touch(MotionEvent.ACTION_MOVE, MotionEvent.TOOL_TYPE_STYLUS, 0, 120, 130);
      recoveryKey(false, t, t + 100); assertFalse(navigation().active()); assertTrue(StylusOnlyInputController.isEnabled());
      touch(MotionEvent.ACTION_UP, MotionEvent.TOOL_TYPE_STYLUS, 0, 120, 130);
      recoveryKey(true, t + 200, t + 200); assertTrue(navigation().active());
      recoveryKey(false, t + 200, t + 200 + StylusOnlyInputController.ESCAPE_HOLD_MS);
      assertFalse(StylusOnlyInputController.isEnabled()); assertFalse(navigation().active());
      assertEquals(20f, ((PointF)get(drawing, "mOffset")).x, .001f);
    });
  }

  @Test public void stylusOnlyRecoveryKeyAllowsDelayedZoomAndCanceledReleaseStopsIt() {
    final long[] downTime = new long[1];
    main(() -> {
      canvas(); TDSetting.mVolumeDownLongPressAction = TDSetting.SPEN_ACTION_HOLD_ZOOM;
      StylusOnlyInputController.setEnabled(true); ActionKeyBindingManager.registerHost(drawing);
      downTime[0] = SystemClock.uptimeMillis(); recoveryKey(true, downTime[0], downTime[0]); assertFalse(navigation().active());
    });
    SystemClock.sleep(ViewConfiguration.getLongPressTimeout() + 80);
    main(() -> {
      assertTrue(navigation().active());
      KeyEvent canceled = KeyEvent.changeFlags(new KeyEvent(downTime[0], SystemClock.uptimeMillis(), KeyEvent.ACTION_UP,
        KeyEvent.KEYCODE_VOLUME_DOWN, 0), KeyEvent.FLAG_CANCELED);
      StylusOnlyInputController.key(recoveryOwner, canceled, event -> false);
      assertFalse(navigation().active()); assertTrue(StylusOnlyInputController.isEnabled());
    });
  }

  @Test public void stylusOnlyFilterBlocksFingerPanButAllowsPenNavigation() {
    main(() -> {
      canvas(); drawing.beginHeldAction(0, TDSetting.SPEN_ACTION_HOLD_PAN);
      StylusInputFilter filter = new StylusInputFilter();
      StylusInputFilter.Dispatch dispatch = e -> drawing.onTouch((DrawingSurface)uncheckedGet(drawing, "mDrawingSurface"), e);
      for (int tool : new int[]{MotionEvent.TOOL_TYPE_FINGER, MotionEvent.TOOL_TYPE_STYLUS}) {
        for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP}) {
          MotionEvent e = event(action, tool, 0, action == MotionEvent.ACTION_DOWN ? 100 : 150, 200);
          filter.touch(e, true, dispatch); e.recycle();
        }
        assertEquals(tool == MotionEvent.TOOL_TYPE_FINGER ? 0 : 50, ((PointF)get(drawing, "mOffset")).x, .001f);
      }
      drawing.endHeldAction(0);
    });
  }
  private static Object uncheckedGet(Object target, String name) {
    try { return get(target, name); } catch (Exception e) { throw new AssertionError(e); }
  }
}
