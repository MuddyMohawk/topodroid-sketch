package com.topodroid.TDX;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.topodroid.prefs.TDSetting;
import com.topodroid.types.PointScale;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

/** Swept-segment eraser cases, unit-style on a bare Scrap (no UI harness).
 *
 *  A fast drag delivers sparse touch samples (Android coalesces move events
 *  when the UI thread is busy); the eraser must cover the whole segment swept
 *  between consecutive samples, not just the sample points - the pre-fix
 *  behavior left un-erased gaps ("inconsistent erasing"). eraseAt must also
 *  report whether anything changed, so the surface invalidates the scene
 *  cache only on real mutations.
 */
@RunWith( AndroidJUnit4.class )
public class EraseSweepInstrumentedTest
{
  private static final float ZOOM = 1.0f;
  private static final float ERASE_SIZE = 10.0f; // erase radius = mCloseCutoff + ERASE_SIZE/ZOOM ~ 10 scene units
  private static final float ROW_SPACING = 30.0f; // > erase radius: items are NOT covered by neighboring samples
  private static final int   ROW_COUNT = 9;

  @Before
  public void pinSettings()
  {
    TDSetting.mWithLevels = 0;
    TDSetting.mEraseReferenceImages = false;
  }

  /** @return a scrap with ROW_COUNT point items at (i*ROW_SPACING, 0) */
  private static Scrap scrapWithPointRow()
  {
    Scrap scrap = new Scrap( 0, "erase-sweep-test" );
    for ( int i = 0; i < ROW_COUNT; ++i ) {
      scrap.addCommand( new DrawingPointPath( 0, i * ROW_SPACING, 0f, PointScale.SCALE_M, 0 ) );
    }
    return scrap;
  }

  private static int itemCount( Scrap scrap )
  {
    ArrayList< DrawingPath > items = new ArrayList<>();
    scrap.addCommand( items );
    return items.size();
  }

  @Test
  public void fastDragErasesEveryItemAlongTheSweptSegment()
  {
    Scrap scrap = scrapWithPointRow();
    assertEquals( ROW_COUNT, itemCount( scrap ) );

    // one coalesced move event: previous sample before the row, current sample
    // past its end - every item lies BETWEEN the samples, none within the
    // erase radius of either sample point
    EraseCommand cmd = new EraseCommand();
    boolean erased = scrap.eraseAt( -ROW_SPACING, 0f, ROW_COUNT * ROW_SPACING, 0f,
                                    ZOOM, cmd, Drawing.FILTER_ALL, ERASE_SIZE );

    assertTrue( "swept erase must report a change", erased );
    assertEquals( "swept erase must remove every item along the segment", 0, itemCount( scrap ) );
    assertEquals( ROW_COUNT, cmd.size() );
  }

  @Test
  public void dragOverEmptyCanvasReportsNoChange()
  {
    Scrap scrap = scrapWithPointRow();

    // sweep parallel to the row but far outside the erase radius
    EraseCommand cmd = new EraseCommand();
    boolean erased = scrap.eraseAt( -ROW_SPACING, 100f, ROW_COUNT * ROW_SPACING, 100f,
                                    ZOOM, cmd, Drawing.FILTER_ALL, ERASE_SIZE );

    assertFalse( "no-op erase must not report a change", erased );
    assertEquals( ROW_COUNT, itemCount( scrap ) );
    assertEquals( 0, cmd.size() );
  }

  @Test
  public void stationarySampleErasesOnlyItsOwnDisk()
  {
    Scrap scrap = scrapWithPointRow();

    // degenerate segment (down event): only the middle item is in the disk
    float mid = ( ROW_COUNT / 2 ) * ROW_SPACING;
    EraseCommand cmd = new EraseCommand();
    boolean erased = scrap.eraseAt( mid, 0f, mid, 0f, ZOOM, cmd, Drawing.FILTER_ALL, ERASE_SIZE );

    assertTrue( erased );
    assertEquals( ROW_COUNT - 1, itemCount( scrap ) );
    assertEquals( 1, cmd.size() );
  }

  @Test
  public void filteredDragLeavesOtherItemTypesReported()
  {
    Scrap scrap = scrapWithPointRow();

    // line filter must not erase point items - and must say nothing changed
    EraseCommand cmd = new EraseCommand();
    boolean erased = scrap.eraseAt( -ROW_SPACING, 0f, ROW_COUNT * ROW_SPACING, 0f,
                                    ZOOM, cmd, Drawing.FILTER_LINE, ERASE_SIZE );

    assertFalse( erased );
    assertEquals( ROW_COUNT, itemCount( scrap ) );
  }
}
