package com.topodroid.TDX;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;


import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith( com.topodroid.TDX.QaJUnit4.class )
@LargeTest
@com.topodroid.TDX.qa.TestTier(com.topodroid.TDX.qa.TestTier.Tier.FAST)
@com.topodroid.qa.TestFeatures({com.topodroid.qa.TestFeatures.Feature.SYMBOLS, com.topodroid.qa.TestFeatures.Feature.DRAWING})
public class ToolbarRowsInstrumentedTest
{
  @org.junit.Rule public final com.topodroid.TDX.QaFixtureRule qaFixture = new com.topodroid.TDX.QaFixtureRule();
  private Context mPreviousContext;

  @Before
  public void setUp()
  {
    mPreviousContext = TDInstance.context;
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    TDInstance.setContext( context.getApplicationContext() );
    QaFixtureRule.ensureSymbols();
    BrushManager.reloadPointLibrary( context, context.getResources() );
    BrushManager.reloadLineLibrary( context.getResources() );
    BrushManager.reloadAreaLibrary( context.getResources() );
  }

  @After
  public void tearDown()
  {
    TDInstance.context = mPreviousContext;
  }

  @Test
  public void installedPackResolvesEveryFreshProfileReference()
  {
    ToolsetProfile profile = ToolsetProfile.freshDefault();
    for ( int row = 0; row < ToolsetProfile.ROW_COUNT; ++row ) {
      for ( int slot = 0; slot < ToolsetProfile.ROW_CAPACITY; ++slot ) {
        ToolsetProfile.Slot ref = profile.mRows[row][slot];
        if ( ref != null ) assertNotNull( "Missing row symbol " + ref.mFullThName, ToolsetCatalog.resolve( ref ) );
      }
    }
    for ( ToolsetProfile.Slot ref : profile.mQuick ) {
      if ( ref != null ) assertNotNull( "Missing quick symbol " + ref.mFullThName, ToolsetCatalog.resolve( ref ) );
    }
  }

  @Test
  public void movableQuickSwitcherRoundTripsWithProfileJson() throws Exception
  {
    ToolsetProfile restored = ToolsetProfile.fromJson( ToolsetProfile.freshDefault().toJson() );
    assertNotNull( restored );
    assertTrue( ToolsetProfile.isQuickSwitcher( restored.mRows[1][7] ) );
  }

  private static void assertSlot( ToolsetProfile.Slot slot, int type, String name )
  {
    assertNotNull( slot );
    assertEquals( type, slot.mType );
    assertEquals( name, slot.mFullThName );
  }
}
