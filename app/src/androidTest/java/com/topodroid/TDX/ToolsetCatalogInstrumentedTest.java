package com.topodroid.TDX;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.topodroid.types.SymbolType;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith( AndroidJUnit4.class )
public class ToolsetCatalogInstrumentedTest
{
  @Test public void defaultPack_hasExactlyOneHundredCategorizedSearchableSymbols()
  {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    TDInstance.setContext( context.getApplicationContext() );
    TopoDroidApp.installSymbols( true );
    BrushManager.reloadPointLibrary( context, context.getResources() );
    BrushManager.reloadLineLibrary( context.getResources() );
    BrushManager.reloadAreaLibrary( context.getResources() );

    ArrayList< ToolsetCatalog.Entry > entries = ToolsetCatalog.all();
    assertEquals( 100, entries.size() );
    HashMap< String, Integer > counts = new HashMap<>();
    for ( String category : ToolsetCategory.orderedIds() ) counts.put( category, 0 );
    for ( ToolsetCatalog.Entry entry : entries ) {
      assertTrue( counts.containsKey( entry.mCategory ) );
      counts.put( entry.mCategory, counts.get( entry.mCategory ) + 1 );
      assertTrue( entry.matches( entry.mSymbol.getName() ) );
      assertTrue( entry.matches( entry.mSymbol.getFullThName() ) );
    }
    assertEquals( Integer.valueOf( 15 ), counts.get( ToolsetCategory.PASSAGES ) );
    assertEquals( Integer.valueOf( 38 ), counts.get( ToolsetCategory.SPELEOTHEMS ) );
    assertEquals( Integer.valueOf( 15 ), counts.get( ToolsetCategory.SPELEOCLASTS ) );
    assertEquals( Integer.valueOf( 5 ), counts.get( ToolsetCategory.HYDROLOGY ) );
    assertEquals( Integer.valueOf( 6 ), counts.get( ToolsetCategory.GEOLOGY ) );
    assertEquals( Integer.valueOf( 7 ), counts.get( ToolsetCategory.BIOLOGY ) );
    assertEquals( Integer.valueOf( 4 ), counts.get( ToolsetCategory.EXTRAS ) );
    assertEquals( Integer.valueOf( 10 ), counts.get( ToolsetCategory.TEXT_MARKS ) );

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
    assertCategory( entries, SymbolType.POINT, "archeo-excavation", ToolsetCategory.EXTRAS );
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
