package com.topodroid.TDX;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.topodroid.prefs.TDSetting;
import com.topodroid.types.SymbolType;
import com.topodroid.util.TDVersion;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Field;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Exercises real line rendering and history without opening or saving surveys. */
@RunWith(AndroidJUnit4.class)
public class ReverseLastLineInstrumentedTest
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
  private static class BareDrawing extends DrawingWindow { @Override public void setTheTitle() { } }
  private BareDrawing drawing;
  private DrawingSurface surface;
  private DrawingCommandManager commands;
  private int savedSingle, savedDouble, savedLevels;

  @Before public void setup() {
    main(() -> {
      TDInstance.setContext(context().getApplicationContext());
      BrushManager.reloadLineLibrary(context().getResources());
      savedSingle = TDSetting.mSPenSingleClickAction; savedDouble = TDSetting.mSPenDoubleClickAction;
      savedLevels = TDSetting.mWithLevels; TDSetting.mWithLevels = 0;
      TDSetting.mSPenSingleClickAction = TDSetting.SPEN_ACTION_REVERSE_LAST_LINE;
      TDSetting.mSPenDoubleClickAction = TDSetting.SPEN_ACTION_NONE;
      drawing = new BareDrawing(); surface = new DrawingSurface(context(), null);
      commands = new DrawingCommandManager(0, "reverse-last-line-test");
      set(surface, "commandManager", commands); set(drawing, "mDrawingSurface", surface);
      set(drawing, "mTh2Edit", true); // All drawing changes remain in memory.
      set(drawing, "mZoom", 1f); set(drawing, "mOffset", new PointF(0, 0));
      set(drawing, "mMode", DrawingWindow.MODE_DRAW); set(drawing, "mSymbol", SymbolType.LINE);
      set(drawing, "mCurrentBrush", new DrawingPenBrush());
    });
  }
  @After public void cleanup() {
    main(() -> {
      ((SPenGestureHelper)get(drawing, "mSPenGestureHelper")).cancel();
      TDSetting.mSPenSingleClickAction = savedSingle; TDSetting.mSPenDoubleClickAction = savedDouble;
      TDSetting.mWithLevels = savedLevels;
    });
  }
  private DrawingLinePath line() {
    int pit = BrushManager.getLineIndexByThName("pit"); assertTrue("Packaged pit symbol is required", pit >= 0);
    DrawingLinePath line = new DrawingLinePath(pit, 0);
    line.setSketchBrushStyle(SketchBrushStyle.of(4f, 1f, 1f, 0xffffff));
    line.addStartPoint(40, 120); line.addPoint(280, 120); line.computeUnitNormal();
    surface.addDrawingPath(line); return line;
  }
  private void reverse() { assertTrue(drawing.handleActionBindingAction(TDSetting.SPEN_ACTION_REVERSE_LAST_LINE)); }
  private static int markedSide(DrawingLinePath line) {
    Bitmap bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888);
    line.draw(new Canvas(bitmap), new Matrix(), 1f, new RectF(0, 0, 320, 240));
    int side = 0;
    for (int y = 0; y < 240; ++y) for (int x = 40; x < 280; ++x) {
      if ((bitmap.getPixel(x, y) >>> 24) != 0 && Math.abs(y - 120) > 2) side += y < 120 ? 1 : -1;
    }
    bitmap.recycle(); return side;
  }

  @Test public void shortcutFlipsPitMarkingsKeepsGeometryAndModeAndPersistsDirection() {
    main(() -> {
      DrawingLinePath older = line(), latest = line();
      surface.addDrawingPath(new DrawingPointPath(0, 400, 200, 0, 0));
      LinePoint first = latest.first(), last = latest.last();
      Object brush = get(drawing, "mCurrentBrush"); int before = markedSide(latest);
      assertTrue("The pit fixture must render visible markings on one side", before != 0);
      for (int mode : new int[]{DrawingWindow.MODE_DRAW, DrawingWindow.MODE_MOVE, DrawingWindow.MODE_EDIT, DrawingWindow.MODE_ERASE}) {
        set(drawing, "mMode", mode); reverse();
        assertTrue(latest.isReversed()); assertFalse(older.isReversed());
        assertTrue("The pit markings must change sides", before * markedSide(latest) < 0);
        assertSame(first, latest.first()); assertSame(last, latest.last());
        assertEquals(mode, get(drawing, "mMode")); assertSame(brush, get(drawing, "mCurrentBrush"));
        assertEquals(SymbolType.LINE, get(drawing, "mSymbol"));
        reverse(); assertFalse(latest.isReversed());
      }
      reverse();
      ByteArrayOutputStream bytes = new ByteArrayOutputStream(); latest.toDataStream(new DataOutputStream(bytes), 0);
      DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
      assertEquals('L', input.read());
      DrawingLinePath loaded = DrawingLinePath.loadDataStream(TDVersion.compatCode(), input, 0, 0);
      assertNotNull(loaded); assertTrue("Native save/load preserves the direction", loaded.isReversed());
      assertEquals(latest.first().x, loaded.first().x, .001f); assertEquals(latest.last().x, loaded.last().x, .001f);
    });
  }

  @Test public void reversalUndoRedoKeepsTheLineAndClearsAbandonedRedo() {
    main(() -> {
      DrawingLinePath latest = line(); reverse(); assertTrue(latest.isReversed());
      surface.undo(); assertFalse(latest.isReversed()); assertEquals(1, commands.getCommands().size());
      surface.redo(); assertTrue(latest.isReversed());
      surface.undo(); assertFalse(latest.isReversed());
      surface.undo(); assertEquals(0, commands.getCommands().size());
      surface.redo(); assertSame(latest, commands.getCommands().get(0)); assertFalse(latest.isReversed());
      surface.redo(); assertTrue(latest.isReversed());
      surface.undo(); reverse(); assertTrue(latest.isReversed()); assertFalse(surface.hasMoreRedo());
      DrawingLinePath newer = line(); reverse(); assertTrue(newer.isReversed()); assertTrue(latest.isReversed());
      surface.undo(); assertFalse(newer.isReversed()); assertTrue(latest.isReversed());
    });
  }

  @Test public void penPressReversesAndEmptyScrapOrActiveContactDoesNothing() {
    main(() -> {
      reverse(); assertFalse(surface.hasMoreUndo());
      DrawingLinePath latest = line();
      set(drawing, "mTouchActive", true); reverse(); assertFalse(latest.isReversed());
      set(drawing, "mTouchActive", false);
      long time = SystemClock.uptimeMillis();
      MotionEvent.PointerProperties p = new MotionEvent.PointerProperties(); p.toolType = MotionEvent.TOOL_TYPE_STYLUS;
      MotionEvent.PointerCoords c = new MotionEvent.PointerCoords(); c.x = 100; c.y = 120;
      for (int action : new int[]{MotionEvent.ACTION_BUTTON_PRESS, MotionEvent.ACTION_BUTTON_RELEASE}) {
        MotionEvent event = MotionEvent.obtain(time, time, action, 1, new MotionEvent.PointerProperties[]{p},
          new MotionEvent.PointerCoords[]{c}, 0, action == MotionEvent.ACTION_BUTTON_PRESS ? MotionEvent.BUTTON_STYLUS_PRIMARY : 0,
          1, 1, 0, 0, android.view.InputDevice.SOURCE_STYLUS, 0);
        try { drawing.onGenericMotion(surface, event); } finally { event.recycle(); }
      }
      assertTrue(latest.isReversed());
      surface.newScrapIndex(true); reverse(); assertFalse(surface.hasMoreUndo()); assertTrue(latest.isReversed());
    });
  }
}
