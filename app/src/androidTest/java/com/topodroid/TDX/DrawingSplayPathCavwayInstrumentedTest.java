package com.topodroid.TDX;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;

import android.graphics.Paint;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.topodroid.dev.cavway.CavwayConst;
import com.topodroid.util.TDColor;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith( AndroidJUnit4.class )
public class DrawingSplayPathCavwayInstrumentedTest
{
  @Test
  public void cavwaySplayPaint_mapsKnownFlagsToSpecialColors()
  {
    assertCavwayColor( CavwayConst.FLAG_FEATURE, TDColor.SPLAY_FEATURE );
    assertCavwayColor( CavwayConst.FLAG_RIDGE, TDColor.SPLAY_RIDGE );
    assertCavwayColor( CavwayConst.FLAG_BACKSIGHT, TDColor.SPLAY_BACKSIGHT );
    assertCavwayColor( CavwayConst.FLAG_GENERIC, TDColor.SPLAY_GENERIC );
  }

  @Test
  public void cavwaySplayPaintCopy_doesNotMutateSharedPaintAlpha()
  {
    DBlock block = blockWithCavwayFlag( CavwayConst.FLAG_FEATURE );
    Paint shared = DrawingSplayPath.getCavwaySplayPaint( block );
    Paint copy = DrawingSplayPath.makeCavwaySplayPaint( block );

    assertNotNull( shared );
    assertNotNull( copy );
    assertNotSame( shared, copy );

    int sharedAlpha = shared.getAlpha();
    int sharedColor = shared.getColor();
    assertEquals( sharedColor, copy.getColor() );

    copy.setAlpha( 0 );

    assertEquals( sharedAlpha, shared.getAlpha() );
    assertEquals( sharedColor, shared.getColor() );
  }

  private static void assertCavwayColor( int flag, int color )
  {
    Paint paint = DrawingSplayPath.getCavwaySplayPaint( blockWithCavwayFlag( flag ) );

    assertNotNull( paint );
    assertEquals( color, paint.getColor() );
  }

  private static DBlock blockWithCavwayFlag( int flag )
  {
    DBlock block = new DBlock();
    block.setFlagFully( ((long)flag) << 16 );
    return block;
  }
}
