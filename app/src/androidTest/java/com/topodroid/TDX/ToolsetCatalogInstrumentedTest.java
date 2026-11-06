package com.topodroid.TDX;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import com.topodroid.types.SymbolType;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith( com.topodroid.TDX.QaJUnit4.class )
@com.topodroid.TDX.qa.TestTier(com.topodroid.TDX.qa.TestTier.Tier.FAST)
@com.topodroid.qa.TestFeatures({com.topodroid.qa.TestFeatures.Feature.SYMBOLS})
public class ToolsetCatalogInstrumentedTest
{
  @org.junit.Rule public final com.topodroid.TDX.QaFixtureRule qaFixture = new com.topodroid.TDX.QaFixtureRule();
  @Test public void installedCatalogHasValidCategoriesSearchAndRequiredActions()
  {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    TDInstance.setContext( context.getApplicationContext() );
    QaFixtureRule.ensureSymbols();
    BrushManager.reloadPointLibrary( context, context.getResources() );
    BrushManager.reloadLineLibrary( context.getResources() );
    BrushManager.reloadAreaLibrary( context.getResources() );

    ArrayList< ToolsetCatalog.Entry > entries = ToolsetCatalog.all();
    assertFalse( "Installed catalog must contain symbols", entries.isEmpty() );
    HashMap< String, Integer > counts = new HashMap<>();
    for ( String category : ToolsetCategory.orderedIds() ) counts.put( category, 0 );
    for ( ToolsetCatalog.Entry entry : entries ) {
      assertTrue( counts.containsKey( entry.mCategory ) );
      counts.put( entry.mCategory, counts.get( entry.mCategory ) + 1 );
      assertTrue( entry.matches( entry.mSymbol.getName() ) );
      assertTrue( entry.matches( entry.mSymbol.getFullThName() ) );
    }

    ToolsetCatalog.Entry air = find( entries, SymbolType.POINT, SymbolLibrary.AIR_DRAUGHT );
    assertNotNull( air );
    assertTrue( air.matches( "draft airflow" ) );
    assertNotNull( find( entries, SymbolType.POINT, SymbolLibrary.STATION ) );
    assertNotNull( find( entries, SymbolType.POINT, SymbolLibrary.PHOTO ) );
    assertNotNull( find( entries, SymbolType.POINT, SymbolLibrary.AUDIO ) );
    assertCategory( entries, SymbolType.POINT, "passage-height", ToolsetCategory.TEXT_MARKS );
    assertCategory( entries, SymbolType.POINT, "u:pit-depth", ToolsetCategory.TEXT_MARKS );
    assertCategory( entries, SymbolType.POINT, "bedding-slab", ToolsetCategory.SPELEOCLASTS );
    assertCategory( entries, SymbolType.LINE, "dripline", ToolsetCategory.PASSAGES );
    assertCategory( entries, SymbolType.POINT, "bones", ToolsetCategory.BIOLOGY );
    assertCategory( entries, SymbolType.POINT, "invertebrate-fossils", ToolsetCategory.BIOLOGY );
    assertCategory( entries, SymbolType.POINT, "midden", ToolsetCategory.BIOLOGY );
    assertCategory( entries, SymbolType.POINT, "archeo-excavation", ToolsetCategory.OTHER );
    assertCategory( entries, SymbolType.POINT, SymbolLibrary.CONTINUATION, ToolsetCategory.TEXT_MARKS );
    ToolsetCatalog.Entry quickSwitcher = find( entries, SymbolType.UNDEF, ToolsetProfile.QUICK_SWITCHER_NAME );
    assertNotNull( quickSwitcher );
    assertEquals( ToolsetCategory.OTHER, quickSwitcher.mCategory );
    assertTrue( quickSwitcher.matches( "quick palette chooser" ) );
  }

  private static void assertCategory( ArrayList< ToolsetCatalog.Entry > entries, int type, String name, String category )
  {
    ToolsetCatalog.Entry entry = find( entries, type, name );
    assertNotNull( entry );
    assertEquals( category, entry.mCategory );
  }

  private static ToolsetCatalog.Entry find( ArrayList< ToolsetCatalog.Entry > entries, int type, String name )
  {
    for ( ToolsetCatalog.Entry entry : entries ) if ( entry.mType == type && name.equals( entry.mSymbol.getFullThName() ) ) return entry;
    return null;
  }
}
