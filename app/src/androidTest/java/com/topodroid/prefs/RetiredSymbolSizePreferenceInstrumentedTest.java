package com.topodroid.prefs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith( AndroidJUnit4.class )
@SuppressWarnings( "deprecation" )
public class RetiredSymbolSizePreferenceInstrumentedTest
{
  @Test public void symbolSize_isHiddenButRetainedForCompatibilityStorage()
  {
    for ( TDPrefKey key : TDPrefKey.mMain ) {
      assertFalse( "Retired Symbol size must not appear in the settings UI",
          "DISTOX_SYMBOL_SIZE".equals( key.key ) );
    }
    assertTrue( TDPrefKey.checkKeyGroup( "DISTOX_SYMBOL_SIZE", 1 << TDPrefKey.UI ) );
    assertFalse( TDPrefKey.checkKeyGroup( "DISTOX_SYMBOL_SIZE", 1 << TDPrefKey.DR ) );
  }

  @Test public void symbolSize_hasNoToolbarEffect()
  {
    float oldSymbolSize = TDSetting.mSymbolSize;
    float oldToolbarSize = TDSetting.mItemButtonSize;
    try {
      TDSetting.setSymbolSize( 4.75f );
      assertEquals( 4.75f, TDSetting.mSymbolSize, 0.0001f );
      assertEquals( oldToolbarSize, TDSetting.mItemButtonSize, 0.0001f );
    } finally {
      TDSetting.mSymbolSize = oldSymbolSize;
      TDSetting.mItemButtonSize = oldToolbarSize;
    }
  }
}
