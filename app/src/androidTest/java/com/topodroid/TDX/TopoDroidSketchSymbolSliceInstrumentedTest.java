package com.topodroid.TDX;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.Base64;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.topodroid.types.PointScale;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@RunWith( AndroidJUnit4.class )
@LargeTest
public class TopoDroidSketchSymbolSliceInstrumentedTest
{
  private static final int WIDTH = 1440;
  private static final int HEIGHT = 8300;
  private static final float LEFT = 260.0f;
  private static final float COL = 370.0f;
  private static final float LINE_ROW = 70.0f;
  private static final float DIRECTIONAL_LINE_ROW = 112.0f;
  private static final float POINT_ROW = 94.0f;
  private static final RectF BBOX = new RectF( -20.0f, -20.0f, WIDTH + 20.0f, HEIGHT + 20.0f );

  private Context mContext;
  private Context mPreviousContext;
  private Instrumentation mInstrumentation;

  @Before
  public void setUp()
  {
    mPreviousContext = TDInstance.context;
    mInstrumentation = InstrumentationRegistry.getInstrumentation();
    mContext = mInstrumentation.getTargetContext().getApplicationContext();
    TDInstance.setContext( mContext );
    TDPath.clearSymbols();
    TopoDroidApp.installSymbols( true );
    BrushManager.reloadPointLibrary( mContext, mContext.getResources() );
    BrushManager.reloadLineLibrary( mContext.getResources() );
  }

  @After
  public void tearDown()
  {
    TDPath.clearSymbols();
    TopoDroidApp.installSymbols( true );
    BrushManager.reloadPointLibrary( mContext, mContext.getResources() );
    BrushManager.reloadLineLibrary( mContext.getResources() );
    TDInstance.context = mPreviousContext;
  }

  @Test
  public void defaultRawSymbolPack_usesTopoDroidSketchZipRoot() throws Exception
  {
    Set< String > entries = new HashSet<>();
    int fileCount = 0;
    int crossbarLineCount = 0;
    int dashedCrossbarLineCount = 0;
    int dogtoothSparLineCount = 0;
    int dogtoothSparPointCount = 0;
    int gypsumCrystalsCount = 0;
    int jointLineCount = 0;
    int reverseFaultLineCount = 0;
    int talusLineCount = 0;
    int thrustLineCount = 0;
    int slopeLineCount = 0;
    int slopeFanLineCount = 0;
    int lineWithArrowCount = 0;
    int dashedLineWithArrowCount = 0;
    int intermittentDottedArrowCount = 0;

    ZipInputStream zip = new ZipInputStream(
      mContext.getResources().openRawResource( R.raw.symbols_topodroid_sketch ) );
    try {
      ZipEntry entry;
      while ( (entry = zip.getNextEntry()) != null ) {
        String name = entry.getName();
        if ( name.equals( "symbols_topodroid_sketch/line/crossbar-line" ) ) ++crossbarLineCount;
        if ( name.equals( "symbols_topodroid_sketch/line/crossbar-line-dashed" ) ) ++dashedCrossbarLineCount;
        if ( name.equals( "symbols_topodroid_sketch/line/dogtooth-spar" ) ) ++dogtoothSparLineCount;
        if ( name.equals( "symbols_topodroid_sketch/point/dogtooth-spar" ) ) ++dogtoothSparPointCount;
        if ( name.equals( "symbols_topodroid_sketch/point/gypsum-crystals" ) ) ++gypsumCrystalsCount;
        if ( name.equals( "symbols_topodroid_sketch/line/joint" ) ) ++jointLineCount;
        if ( name.equals( "symbols_topodroid_sketch/line/reverse-fault" ) ) ++reverseFaultLineCount;
        if ( name.equals( "symbols_topodroid_sketch/line/talus" ) ) ++talusLineCount;
        if ( name.equals( "symbols_topodroid_sketch/line/thrust" ) ) ++thrustLineCount;
        if ( name.equals( "symbols_topodroid_sketch/line/slope" ) ) ++slopeLineCount;
        if ( name.equals( "symbols_topodroid_sketch/line/slope-fan" ) ) ++slopeFanLineCount;
        if ( name.equals( "symbols_topodroid_sketch/line/line-with-arrow" ) ) ++lineWithArrowCount;
        if ( name.equals( "symbols_topodroid_sketch/line/dashed-line-with-arrow" ) ) ++dashedLineWithArrowCount;
        if ( name.equals( "symbols_topodroid_sketch/line/intermittent-dotted-arrow" ) ) ++intermittentDottedArrowCount;
        assertTrue( "Default symbol pack entry should use symbols_topodroid_sketch root: " + name,
                    name.startsWith( "symbols_topodroid_sketch/" ) );
        assertTrue( "Default symbol pack should not use old symbols_nss root: " + name,
                    ! name.startsWith( "symbols_nss/" ) );
        if ( ! entry.isDirectory() ) {
          entries.add( name );
          ++fileCount;
        }
      }
    } finally {
      zip.close();
    }

    assertTrue( "Default TopoDroid Sketch symbol pack is unexpectedly small", fileCount > 40 );
    assertTrue( "Missing sketch pit line in default raw pack",
                entries.contains( "symbols_topodroid_sketch/line/pit" ) );
    assertTrue( "Missing sketch ceiling-ledge line in default raw pack",
                entries.contains( "symbols_topodroid_sketch/line/chimney" ) );
    assertTrue( "Missing smart passage-height point in default raw pack",
                entries.contains( "symbols_topodroid_sketch/point/passage-height" ) );
    assertTrue( "Missing smart pit-depth point in default raw pack",
                entries.contains( "symbols_topodroid_sketch/point/pit-depth" ) );
    assertTrue( "Missing smart bedding-attitude point in default raw pack",
                entries.contains( "symbols_topodroid_sketch/point/bedding-attitude" ) );
    assertTrue( "Missing true-scale caver point in default raw pack",
                entries.contains( "symbols_topodroid_sketch/point/caver" ) );
    assertTrue( "Missing paired-rail ceiling-channel line in default raw pack",
                entries.contains( "symbols_topodroid_sketch/line/ceiling-meander" ) );
    assertTrue( "Missing paired-rail floor-channel line in default raw pack",
                entries.contains( "symbols_topodroid_sketch/line/floor-meander" ) );
    assertEquals( "Crossbar Line should occur exactly once in the default raw pack", 1, crossbarLineCount );
    assertEquals( "Dashed Crossbar Line should occur exactly once in the default raw pack", 1, dashedCrossbarLineCount );
    assertEquals( "Dogtooth Spar line should occur exactly once in the default raw pack", 1, dogtoothSparLineCount );
    assertEquals( "Dogtooth Spar point should be removed from the default raw pack", 0, dogtoothSparPointCount );
    assertEquals( "Gypsum Crystals should occur exactly once in the default raw pack", 1, gypsumCrystalsCount );
    assertEquals( "Joint should occur exactly once in the default raw pack", 1, jointLineCount );
    assertEquals( "Reverse Fault should occur exactly once in the default raw pack", 1, reverseFaultLineCount );
    assertEquals( "Talus should occur exactly once in the default raw pack", 1, talusLineCount );
    assertEquals( "Thrust Fault should occur exactly once in the default raw pack", 1, thrustLineCount );
    assertEquals( "Slope should occur exactly once in the default raw pack", 1, slopeLineCount );
    assertEquals( "Slope fan should occur exactly once in the default raw pack", 1, slopeFanLineCount );
    assertEquals( "Line with arrow should occur exactly once in the default raw pack", 1, lineWithArrowCount );
    assertEquals( "Dashed line with arrow should occur exactly once in the default raw pack", 1, dashedLineWithArrowCount );
    assertEquals( "Intermittent Dotted Arrow should occur exactly once in the default raw pack", 1, intermittentDottedArrowCount );
    assertTrue( "Generic Spar remains deferred",
                ! entries.contains( "symbols_topodroid_sketch/point/spar" ) );
    assertTrue( "Generic Crystals should not be restored as a duplicate",
                ! entries.contains( "symbols_topodroid_sketch/point/crystal" ) );
    assertTrue( "Missing sketch flowstone line in default raw pack",
                entries.contains( "symbols_topodroid_sketch/line/flowstone" ) );
    assertTrue( "Missing sketch pool-fingers line in default raw pack",
                entries.contains( "symbols_topodroid_sketch/line/pool-fingers" ) );
    assertTrue( "Missing sketch shelfstone line in default raw pack",
                entries.contains( "symbols_topodroid_sketch/line/shelfstone" ) );
    assertTrue( "Missing sketch gypsum-wall-crust line in default raw pack",
                entries.contains( "symbols_topodroid_sketch/line/gypsum-wall-crust" ) );
    assertTrue( "Flowstone Covered Wall is a composite, not a standalone line symbol",
                ! entries.contains( "symbols_topodroid_sketch/line/flowstone-covered-wall" ) );
    assertTrue( "Missing sketch sand point in default raw pack",
                entries.contains( "symbols_topodroid_sketch/point/sand" ) );
    assertTrue( "Missing sketch debris point in default raw pack",
                entries.contains( "symbols_topodroid_sketch/point/debris=small" ) );
    assertTrue( "Missing sketch leaf-litter point in default raw pack",
                entries.contains( "symbols_topodroid_sketch/point/leaf-litter-organic" ) );
    assertTrue( "Missing sketch calcite-rafts point in default raw pack",
                entries.contains( "symbols_topodroid_sketch/point/calcite-rafts" ) );
    assertTrue( "Missing sketch folia point in default raw pack",
                entries.contains( "symbols_topodroid_sketch/point/folia" ) );
    assertTrue( "Missing sketch bat-skeleton point in default raw pack",
                entries.contains( "symbols_topodroid_sketch/point/bat-skeleton" ) );
    assertTrue( "Missing sketch midden point in default raw pack",
                entries.contains( "symbols_topodroid_sketch/point/midden" ) );
    assertTrue( "Missing sketch rusticles point in default raw pack",
                entries.contains( "symbols_topodroid_sketch/point/rusticles" ) );
    assertTrue( "Missing sketch vegetation point in default raw pack",
                entries.contains( "symbols_topodroid_sketch/point/vegetation" ) );
    assertTrue( "Retired green-plants ID remains in default raw pack",
                ! entries.contains( "symbols_topodroid_sketch/point/green-plants" ) );
    assertTrue( "Missing sketch mudcrack point in default raw pack",
                entries.contains( "symbols_topodroid_sketch/point/mudcrack" ) );
    assertTrue( "Missing sketch subaqueous-helictites point in default raw pack",
                entries.contains( "symbols_topodroid_sketch/point/subaqueous-helictites" ) );
    assertTrue( "Missing sketch gypsum-dripholes point in default raw pack",
                entries.contains( "symbols_topodroid_sketch/point/gypsum-dripholes" ) );
    assertTrue( "Missing sketch sediment-cone point in default raw pack",
                entries.contains( "symbols_topodroid_sketch/point/sediment-cone" ) );
    assertTrue( "Missing traced rounded breakdown point in default raw pack",
                entries.contains( "symbols_topodroid_sketch/point/boulder" ) );
    assertTrue( "Missing traced angular breakdown point in default raw pack",
                entries.contains( "symbols_topodroid_sketch/point/angular-block" ) );
    assertTrue( "Missing traced bedding-slab point in default raw pack",
                entries.contains( "symbols_topodroid_sketch/point/bedding-slab" ) );
    String caver = readRawSymbolEntry( "symbols_topodroid_sketch/point/caver" );
    assertTrue( "Caver should use a custom Therion identity", caver.contains( "\nth_name u:caver\n" ) );
    assertTrue( "Caver should not be manually orientable", caver.contains( "\norientation no\n" ) );
    assertTrue( "Caver should not use generic point scaling", caver.contains( "\nscalable no\n" ) );
    String[] breakdownEntries = {
      "symbols_topodroid_sketch/point/boulder",
      "symbols_topodroid_sketch/point/angular-block",
      "symbols_topodroid_sketch/point/bedding-slab"
    };
    for ( String breakdownEntry : breakdownEntries ) {
      String symbol = readRawSymbolEntry( breakdownEntry );
      assertTrue( breakdownEntry + " should declare affine editing", symbol.contains( "sketch_affine yes" ) );
      assertTrue( breakdownEntry + " should declare breakdown occlusion",
                  symbol.contains( "sketch_occlude breakdown" ) );
      assertTrue( breakdownEntry + " structural ink should match same-weight lines",
                  symbol.contains( "sketch_stroke_scale 1" ) );
      assertTrue( breakdownEntry + " should keep optional shading at quarter stroke weight",
                  symbol.contains( "detail_path 0.25" ) );
    }
    assertTrue( "Rounded breakdown picker name is missing",
                readRawSymbolEntry( breakdownEntries[0] ).contains( "name breakdown:_round" ) );
    assertTrue( "Square breakdown picker name is missing",
                readRawSymbolEntry( breakdownEntries[1] ).contains( "name breakdown:_square" ) );
    assertTrue( "Rectangle breakdown picker name is missing",
                readRawSymbolEntry( breakdownEntries[2] ).contains( "name breakdown:_rectangle" ) );
    assertEquals( "breakdown: round",
                  BrushManager.getPointName( BrushManager.getPointIndexByThName( "boulder" ) ) );
    assertEquals( "breakdown: square",
                  BrushManager.getPointName( BrushManager.getPointIndexByThName( "angular-block" ) ) );
    assertEquals( "breakdown: rectangle",
                  BrushManager.getPointName( BrushManager.getPointIndexByThName( "bedding-slab" ) ) );
    assertTrue( "Pool Fingers is deferred to a future line symbol",
                ! entries.contains( "symbols_topodroid_sketch/point/pool-fingers" ) );
    assertTrue( "Shelfstone is deferred to a future line symbol",
                ! entries.contains( "symbols_topodroid_sketch/point/shelfstone" ) );
    assertTrue( "Splash Ring is a composite, not a standalone point symbol",
                ! entries.contains( "symbols_topodroid_sketch/point/splash-ring" ) );
    assertTrue( "Gypsum Wall Crust is deferred to a future line symbol",
                ! entries.contains( "symbols_topodroid_sketch/point/gypsum-wall-crust" ) );
    assertTrue( "Missing sketch bedrock area in default raw pack",
                entries.contains( "symbols_topodroid_sketch/area/bedrock" ) );
    assertTrue( "Missing sketch sump area in default raw pack",
                entries.contains( "symbols_topodroid_sketch/area/sump" ) );

    String sump = readRawSymbolEntry( "symbols_topodroid_sketch/area/sump" );
    assertTrue( "Sump should use the standard Therion identity", sump.contains( "\nth_name sump\n" ) );
    assertTrue( "Sump should use the water group", sump.contains( "\ngroup water\n" ) );
    assertTrue( "Sump should declare mirrored crosshatching that replaces water",
                sump.contains( "\nline_pattern crosshatch angle -35" )
                    && sump.contains( " replaces water\n" ) );
    assertTrue( "Sump should remain hard-clipped with no boundary fade",
                sump.contains( " spacing 10.0 fade 0.0 replaces water\n" ) );

    String flowstone = readRawSymbolEntry( "symbols_topodroid_sketch/line/flowstone" );
    String shelfstone = readRawSymbolEntry( "symbols_topodroid_sketch/line/shelfstone" );
    String[] flowstoneArcs = {
      "cubicTo 0.56 1.68 2.8 1.68 3.36 0",
      "cubicTo 3.92 1.68 6.16 1.68 6.72 0"
    };
    for ( String arc : flowstoneArcs ) {
      assertTrue( "Flowstone reference arc is missing: " + arc, flowstone.contains( arc ) );
    }
    String[] doubledMirroredShelfstoneArcs = {
      "cubicTo 1.12 -3.36 5.6 -3.36 6.72 0",
      "cubicTo 7.84 -3.36 12.32 -3.36 13.44 0"
    };
    for ( String arc : doubledMirroredShelfstoneArcs ) {
      assertTrue( "Shelfstone must double and mirror the Flowstone outer-arc geometry: " + arc,
                  shelfstone.contains( arc ) );
    }
    String[] separatedShelfstoneInterior = {
      "moveTo 2.52 -0.42", "cubicTo 2.8 -1.26 3.92 -1.26 4.2 -0.42",
      "moveTo 9.24 -0.42", "cubicTo 9.52 -1.26 10.64 -1.26 10.92 -0.42"
    };
    for ( String command : separatedShelfstoneInterior ) {
      assertTrue( "Shelfstone must retain a centered, separated interior arc: " + command,
                  shelfstone.contains( command ) );
    }

    String dogtoothSpar = readRawSymbolEntry( "symbols_topodroid_sketch/line/dogtooth-spar" );
    assertTrue( "Dogtooth Spar must be a line symbol", dogtoothSpar.contains( "\nsymbol line\n" ) );
    assertTrue( "Dogtooth Spar must use standard line width", dogtoothSpar.contains( "\nwidth 2\n" ) );
    assertTrue( "Dogtooth Spar must remain carrier-free", ! dogtoothSpar.contains( "\n  carrier " ) );
    assertTrue( "Dogtooth Spar must define matching effect and sketch geometry",
                dogtoothSpar.contains( "\neffect\n" ) && dogtoothSpar.contains( "\nsketch_effect 1\n" ) );
    assertEquals( "Dogtooth Spar must retain all five source paths in both renderers", 10,
                  countOccurrences( dogtoothSpar, "moveTo " ) );
    assertEquals( "Each Dogtooth Spar source path must retain its two segments in both renderers", 20,
                  countOccurrences( dogtoothSpar, "lineTo " ) );
    String[] dogtoothCommands = {
      "moveTo 0 -1.097", "lineTo 0.993 2.982", "lineTo 4.261 0.368",
      "moveTo 3.897 -4.019", "lineTo 5.116 3.171", "lineTo 10.722 -2.316",
      "moveTo 13.77 0.977", "lineTo 10.358 3.9", "lineTo 8.654 0.242",
      "moveTo 12.796 -1.097", "lineTo 15.844 4.019", "lineTo 19.131 -1.461",
      "moveTo 21.815 -1.951", "lineTo 22.5 2.781", "lineTo 18.283 0.732"
    };
    for ( String command : dogtoothCommands ) {
      assertEquals( "Dogtooth Spar source command must match in effect and sketch geometry: " + command, 2,
                    countOccurrences( dogtoothSpar, command ) );
    }

    String gypsumCrystals = readRawSymbolEntry( "symbols_topodroid_sketch/point/gypsum-crystals" );
    String gypsumWallCrust = readRawSymbolEntry( "symbols_topodroid_sketch/line/gypsum-wall-crust" );
    assertTrue( "Gypsum Crystals must be non-orientable", gypsumCrystals.contains( "\norientation no\n" ) );
    assertEquals( "Gypsum Crystals must contain one three-stroke rosette", 3,
                  countOccurrences( gypsumCrystals, "\n  moveTo " ) );
    assertEquals( "Gypsum Crystals must contain one three-stroke rosette", 3,
                  countOccurrences( gypsumCrystals, "\n  lineTo " ) );
    String[] singleCrystal = {
      "moveTo -5.631 -9.742", "lineTo 5.608 9.742",
      "moveTo -11.25 0", "lineTo 11.25 0",
      "moveTo -5.631 9.742", "lineTo 5.631 -9.719"
    };
    for ( String command : singleCrystal ) {
      assertTrue( "Gypsum Crystals single-rosette command is missing: " + command,
                  gypsumCrystals.contains( command ) );
    }
    String[] compactWallCrystal = {
      "moveTo -0.268 -1.041", "lineTo 1.858 -4.728",
      "moveTo -1.331 -2.88", "lineTo 2.926 -2.885",
      "moveTo -0.268 -4.728", "lineTo 1.863 -1.041"
    };
    for ( String command : compactWallCrystal ) {
      assertTrue( "Gypsum Wall Crust must retain the compact mirrored rosette: " + command,
                  gypsumWallCrust.contains( command ) );
    }
    assertTrue( "Gypsum Wall Crust must add repeat spacing between rosettes",
                gypsumWallCrust.contains( "moveTo -3.1 0" ) );

    String crossbarLine = readRawSymbolEntry( "symbols_topodroid_sketch/line/crossbar-line" );
    String dashedCrossbarLine = readRawSymbolEntry( "symbols_topodroid_sketch/line/crossbar-line-dashed" );
    assertTrue( "Crossbar Line must retain the Pit width", crossbarLine.contains( "width 2" ) );
    assertTrue( "Crossbar Line must retain the Pit repeat advance", crossbarLine.contains( "lineTo 4.2 1" ) );
    assertTrue( "Crossbar Line must retain the Pit crossbar width", crossbarLine.contains( "moveTo 1.7 -3.4" )
                && crossbarLine.contains( "lineTo 2.7 -3.4" ) );
    assertTrue( "Crossbar Line must extend symmetrically through the carrier",
                crossbarLine.contains( "lineTo 2.7 4.4" ) );
    assertTrue( "Crossbar Line carrier must remain solid", ! crossbarLine.contains( "\ndash " ) );
    assertTrue( "Dashed Crossbar Line must retain the Ceiling Ledge width",
                dashedCrossbarLine.contains( "width 2" ) );
    assertTrue( "Dashed Crossbar Line must retain the Ceiling Ledge dash cycle",
                dashedCrossbarLine.contains( "dash 4.2 1.4" ) );
    assertTrue( "Dashed Crossbar Line must retain the Ceiling Ledge repeat and crossbar width",
                dashedCrossbarLine.contains( "lineTo 4.2 1" )
                && dashedCrossbarLine.contains( "moveTo 1.7 -1.7" )
                && dashedCrossbarLine.contains( "lineTo 2.7 -1.7" ) );
    assertTrue( "Dashed Crossbar Line must extend symmetrically through the carrier",
                dashedCrossbarLine.contains( "lineTo 2.7 2.7" ) );
    assertEquals( "Crossbar Line must be symmetric around its carrier", 0, hachureDirection( crossbarLine ) );
    assertEquals( "Dashed Crossbar Line must be symmetric around its carrier", 0,
                  hachureDirection( dashedCrossbarLine ) );

    String slope = readRawSymbolEntry( "symbols_topodroid_sketch/line/slope" );
    String slopeFan = readRawSymbolEntry( "symbols_topodroid_sketch/line/slope-fan" );
    for ( String symbol : new String[] { slope, slopeFan } ) {
      assertTrue( "Slope variants must retain Pit's width", symbol.contains( "\nwidth 2\n" ) );
      assertTrue( "Slope variants must use the slope group", symbol.contains( "\ngroup slope\n" ) );
      assertTrue( "Slope variants must remain carrier-free", ! symbol.contains( "\n  carrier " ) );
      assertTrue( "Slope variants must reserve Pit's 4.2 repeat",
                  symbol.contains( "moveTo 0 0" ) && symbol.contains( "moveTo 4.2 0" ) );
      assertTrue( "Slope variants must retain Pit's one-unit hachure width and placement",
                  symbol.contains( "moveTo 1.7 0" ) && symbol.contains( "lineTo 2.7 0" ) );
      assertEquals( "Slope variants must duplicate the 5.1-deep base hachure in fallback and Sketch geometry",
                    2, countOccurrences( symbol, "lineTo 2.7 -5.1" ) );
    }
    assertTrue( "Slope must take the standard identity", slope.contains( "\nth_name slope\n" ) );
    assertTrue( "Slope fan must use its custom full identity", slopeFan.contains( "\nth_name u:slope-fan\n" ) );
    assertTrue( "Slope fan must declare its approved cosine envelope",
                slopeFan.contains( "\n  envelope cosine 3 1 10\n" ) );
    assertEquals( "Slope must face the Pit side", hachureDirection( readRawSymbolEntry(
        "symbols_topodroid_sketch/line/pit" ) ), hachureDirection( slope ) );
    assertEquals( "Slope fan must face the Pit side", hachureDirection( slope ), hachureDirection( slopeFan ) );

    int slopeIndex = BrushManager.getLineIndexByThName( SymbolLibrary.SLOPE );
    int slopeFanIndex = BrushManager.getLineIndexByThName( SymbolLibrary.SLOPE_FAN );
    assertTrue( "Slope must load into the line library", slopeIndex >= 0 );
    assertTrue( "Slope fan must load into the line library", slopeFanIndex >= 0 );
    assertEquals( "Slope palette label", "slope", BrushManager.getLineName( slopeIndex ) );
    assertEquals( "Slope fan palette label", "slope fan", BrushManager.getLineName( slopeFanIndex ) );
    LineSymbolEffect slopeFanEffect = BrushManager.getLineEffect( slopeFanIndex );
    assertNotNull( "Slope fan must retain its line effect", slopeFanEffect );
    assertTrue( "Slope fan parser must retain the envelope", slopeFanEffect.hasEnvelope() );
    assertEquals( 3.0f, slopeFanEffect.envelopeDefault(), 0.001f );
    assertEquals( 1.0f, slopeFanEffect.envelopeMin(), 0.001f );
    assertEquals( 10.0f, slopeFanEffect.envelopeMax(), 0.001f );

    String lineWithArrow = readRawSymbolEntry( "symbols_topodroid_sketch/line/line-with-arrow" );
    String dashedLineWithArrow = readRawSymbolEntry( "symbols_topodroid_sketch/line/dashed-line-with-arrow" );
    for ( String symbol : new String[] { lineWithArrow, dashedLineWithArrow } ) {
      assertTrue( "Arrow lines must use the custom user identity", symbol.contains( "\nth_name u:" ) );
      assertTrue( "Arrow lines must use the user group", symbol.contains( "\ngroup user\n" ) );
      assertTrue( "Arrow lines must use water blue", symbol.contains( "\ncolor 0x6699ff 0xff\n" ) );
      assertTrue( "Arrow lines must match the standard width", symbol.contains( "\nwidth 2\n" ) );
      assertTrue( "Arrow lines must remain plain fallbacks for older renderers", ! symbol.contains( "\neffect\n" ) );
      assertTrue( "Arrow lines must stop the carrier at the arrow notch",
                  symbol.contains( "\n  terminal end inset 6\n" ) );
      assertTrue( "Arrow lines must declare one terminating stamp",
                  symbol.contains( "\n  terminal_stamp\n" ) && symbol.contains( "\n  endterminal_stamp\n" ) );
      assertTrue( "Arrow lines must carry one full-width line", symbol.contains( "\n  carrier -0.5 0.5\n" ) );
      assertTrue( "Arrow lines must retain the filled reference dart",
                  symbol.contains( "moveTo -8 -4" ) && symbol.contains( "lineTo 0 0" )
                  && symbol.contains( "lineTo -8 4" ) && symbol.contains( "lineTo -6 0" ) );
    }
    assertTrue( "Solid arrow line must use its approved identity",
                lineWithArrow.contains( "\nth_name u:line-with-arrow\n" ) );
    assertTrue( "Dashed arrow line must use its approved identity",
                dashedLineWithArrow.contains( "\nth_name u:dashed-line-with-arrow\n" ) );
    assertTrue( "Solid arrow line must not declare a dash rhythm", ! lineWithArrow.contains( "\ndash " ) );
    assertTrue( "Dashed arrow line must borrow the standard Dashed rhythm",
                dashedLineWithArrow.contains( "\ndash 6 4\n" ) );

    int lineWithArrowIndex = BrushManager.getLineIndexByThName( SymbolLibrary.LINE_WITH_ARROW );
    int dashedLineWithArrowIndex = BrushManager.getLineIndexByThName( SymbolLibrary.DASHED_LINE_WITH_ARROW );
    assertTrue( "Line with arrow must load into the line library", lineWithArrowIndex >= 0 );
    assertTrue( "Dashed line with arrow must load into the line library", dashedLineWithArrowIndex >= 0 );
    assertEquals( "line with arrow", BrushManager.getLineName( lineWithArrowIndex ) );
    assertEquals( "dashed line with arrow", BrushManager.getLineName( dashedLineWithArrowIndex ) );
    assertEquals( 0xff6699ff, BrushManager.getLineColor( lineWithArrowIndex ) );
    assertEquals( 0xff6699ff, BrushManager.getLineColor( dashedLineWithArrowIndex ) );
    assertTrue( "Solid arrow parser must retain the terminal effect",
                BrushManager.getLineEffect( lineWithArrowIndex ).hasTerminalEnd() );
    assertTrue( "Dashed arrow parser must retain the terminal effect",
                BrushManager.getLineEffect( dashedLineWithArrowIndex ).hasTerminalEnd() );
    assertEquals( 6.0f, BrushManager.getLineEffect( lineWithArrowIndex ).terminalInset(), 0.001f );
    assertEquals( 6.0f, BrushManager.getLineEffect( dashedLineWithArrowIndex ).terminalInset(), 0.001f );
    float[] dashedArrowRhythm = BrushManager.getLineDashBase( dashedLineWithArrowIndex );
    assertNotNull( "Dashed arrow parser must retain its dash rhythm", dashedArrowRhythm );
    assertEquals( 6.0f, dashedArrowRhythm[0], 0.001f );
    assertEquals( 4.0f, dashedArrowRhythm[1], 0.001f );

    String intermittentDottedArrow = readRawSymbolEntry(
        "symbols_topodroid_sketch/line/intermittent-dotted-arrow" );
    assertTrue( intermittentDottedArrow.contains( "\nname Intermittent_Dotted_Arrow\n" ) );
    assertTrue( intermittentDottedArrow.contains( "\nth_name u:intermittent-dotted-arrow\n" ) );
    assertTrue( intermittentDottedArrow.contains( "\ngroup user\n" ) );
    assertTrue( intermittentDottedArrow.contains( "\ncolor 0x6699ff 0xff\n" ) );
    assertTrue( intermittentDottedArrow.contains( "\nwidth 2\n" ) );
    assertTrue( intermittentDottedArrow.contains( "\ndash 18 12\n" ) );
    assertTrue( intermittentDottedArrow.contains( "\n  gap_stamp\n" )
                && intermittentDottedArrow.contains( "\n  endgap_stamp\n" ) );
    assertEquals( "Intermittent Dotted Arrow must have four equal gap dots", 4,
                  countOccurrences( intermittentDottedArrow, "addCircle " ) );
    assertTrue( intermittentDottedArrow.contains( "\n  terminal end inset 6\n" ) );
    assertTrue( "Intermittent Dotted Arrow must remain a plain dashed fallback",
                ! intermittentDottedArrow.contains( "\neffect\n" ) );

    int intermittentDottedArrowIndex = BrushManager.getLineIndexByThName(
        SymbolLibrary.INTERMITTENT_DOTTED_ARROW );
    assertTrue( "Intermittent Dotted Arrow must load into the line library",
                intermittentDottedArrowIndex >= 0 );
    assertEquals( "Intermittent Dotted Arrow", BrushManager.getLineName( intermittentDottedArrowIndex ) );
    assertEquals( 0xff6699ff, BrushManager.getLineColor( intermittentDottedArrowIndex ) );
    LineSymbolEffect intermittentEffect = BrushManager.getLineEffect( intermittentDottedArrowIndex );
    assertNotNull( intermittentEffect );
    assertTrue( intermittentEffect.hasTerminalEnd() );
    assertEquals( 6.0f, intermittentEffect.terminalInset(), 0.001f );
    float[] intermittentRhythm = BrushManager.getLineDashBase( intermittentDottedArrowIndex );
    assertNotNull( intermittentRhythm );
    assertEquals( 18.0f, intermittentRhythm[0], 0.001f );
    assertEquals( 12.0f, intermittentRhythm[1], 0.001f );

    String talus = readRawSymbolEntry( "symbols_topodroid_sketch/line/talus" );
    assertTrue( "Talus must retain the Pit width", talus.contains( "\nwidth 2\n" ) );
    assertTrue( "Talus must remain in the floor group", talus.contains( "\ngroup floor\n" ) );
    assertTrue( "Talus must be carrier-free", ! talus.contains( "\n  carrier " ) );
    assertTrue( "Talus must reserve a 10.0 repeat for its 1.5x visible gap",
                talus.contains( "moveTo 0 0" ) && talus.contains( "moveTo 10 0" ) );
    String[] talusOpenRectangle = {
      "moveTo 3.5 0", "lineTo 3.5 3.4", "lineTo 6.5 3.4", "lineTo 6.5 0"
    };
    for ( String command : talusOpenRectangle ) {
      assertEquals( "Talus effect and sketch stamp must match: " + command, 2,
                    countOccurrences( talus, command ) );
    }
    assertTrue( "Talus rectangles must leave the path-side end open",
                ! talus.contains( "lineTo 3.5 0" ) );
    assertTrue( "Talus rectangles must face the corrected side of the path",
                talus.contains( "lineTo 3.5 3.4" ) && ! talus.contains( "lineTo 3.5 -3.4" ) );
    int talusIndex = BrushManager.getLineIndexByThName( "talus" );
    assertTrue( "Talus must load into the line library", talusIndex >= 0 );
    SymbolLine talusSymbol = BrushManager.getLineByIndex( talusIndex );
    assertNotNull( "Talus line symbol must be available", talusSymbol );
    assertNotNull( "Talus line symbol must retain its repeat effect", talusSymbol.mLineEffect );
    java.lang.reflect.Field advanceField = LineSymbolEffect.class.getDeclaredField( "mAdvance" );
    advanceField.setAccessible( true );
    assertEquals( "Talus parser must honor the trailing empty repeat space", 10.0f,
                  advanceField.getFloat( talusSymbol.mLineEffect ), 0.001f );

    String joint = readRawSymbolEntry( "symbols_topodroid_sketch/line/joint" );
    assertTrue( "Joint must use the requested purple", joint.contains( "\ncolor 0x7030a0 0xff\n" ) );
    assertTrue( "Joint must use a centered one-line-width carrier",
                joint.contains( "\n  carrier -0.5 0.5\n" ) );
    String[] jointSquare = {
      "moveTo 4.5 -1.5", "lineTo 7.5 -1.5", "lineTo 7.5 1.5",
      "lineTo 4.5 1.5", "lineTo 4.5 -1.5"
    };
    for ( String command : jointSquare ) {
      assertEquals( "Joint effect and filled sketch stamp must match: " + command, 2,
                    countOccurrences( joint, command ) );
    }
    assertTrue( "Joint must reserve a 12.0 repeat for a three-square clear gap",
                joint.contains( "moveTo 0 0" ) && joint.contains( "moveTo 12 0" ) );
    int jointIndex = BrushManager.getLineIndexByThName( "joint" );
    assertTrue( "Joint must load into the line library", jointIndex >= 0 );
    SymbolLine jointSymbol = BrushManager.getLineByIndex( jointIndex );
    assertNotNull( "Joint line symbol must be available", jointSymbol );
    assertNotNull( "Joint line symbol must retain its repeat effect", jointSymbol.mLineEffect );
    assertEquals( "Joint parser must honor the 12.0 line-width repeat", 12.0f,
                  advanceField.getFloat( jointSymbol.mLineEffect ), 0.001f );

    String thrust = readRawSymbolEntry( "symbols_topodroid_sketch/line/thrust" );
    assertTrue( "Thrust Fault must use the requested purple", thrust.contains( "\ncolor 0x7030a0 0xff\n" ) );
    assertTrue( "Thrust Fault must remain in the fault group", thrust.contains( "\ngroup fault\n" ) );
    assertTrue( "Thrust Fault must use the same one-line-width carrier as Pit",
                thrust.contains( "\n  carrier 0 1\n" ) );
    String[] thrustTriangle = {
      "moveTo 6 0", "lineTo 8 -3.4", "lineTo 10 0", "lineTo 6 0"
    };
    for ( String command : thrustTriangle ) {
      assertTrue( "Thrust Fault filled triangle command is missing: " + command,
                  thrust.contains( command ) );
    }
    assertTrue( "Thrust Fault must reserve a 16.0 repeat for a three-base-width clear gap",
                thrust.contains( "lineTo 16 1" ) && thrust.contains( "lineTo 16 0" ) );
    int thrustIndex = BrushManager.getLineIndexByThName( "thrust" );
    assertTrue( "Thrust Fault must load into the line library", thrustIndex >= 0 );
    SymbolLine thrustSymbol = BrushManager.getLineByIndex( thrustIndex );
    assertNotNull( "Thrust Fault line symbol must be available", thrustSymbol );
    assertNotNull( "Thrust Fault line symbol must retain its repeat effect", thrustSymbol.mLineEffect );
    assertEquals( "Thrust Fault parser must honor the 16.0 line-width repeat", 16.0f,
                  advanceField.getFloat( thrustSymbol.mLineEffect ), 0.001f );

    String reverseFault = readRawSymbolEntry( "symbols_topodroid_sketch/line/reverse-fault" );
    assertTrue( "Reverse Fault must use the requested purple",
                reverseFault.contains( "\ncolor 0x7030a0 0xff\n" ) );
    assertTrue( "Reverse Fault must remain in the fault group",
                reverseFault.contains( "\ngroup fault\n" ) );
    assertTrue( "Reverse Fault must use the same one-line-width carrier as Pit",
                reverseFault.contains( "\n  carrier 0 1\n" ) );
    String[] reverseFaultHalfSquare = {
      "moveTo 4.5 0", "lineTo 7.5 0", "lineTo 7.5 -1.5",
      "lineTo 4.5 -1.5", "lineTo 4.5 0"
    };
    for ( String command : reverseFaultHalfSquare ) {
      assertTrue( "Reverse Fault filled half-square command is missing: " + command,
                  reverseFault.contains( command ) );
    }
    assertTrue( "Reverse Fault must reserve Joint's 12.0 repeat for a three-base-width clear gap",
                reverseFault.contains( "lineTo 12 1" ) && reverseFault.contains( "lineTo 12 0" ) );
    int reverseFaultIndex = BrushManager.getLineIndexByThName( "reverse-fault" );
    assertTrue( "Reverse Fault must load into the line library", reverseFaultIndex >= 0 );
    SymbolLine reverseFaultSymbol = BrushManager.getLineByIndex( reverseFaultIndex );
    assertNotNull( "Reverse Fault line symbol must be available", reverseFaultSymbol );
    assertNotNull( "Reverse Fault line symbol must retain its repeat effect", reverseFaultSymbol.mLineEffect );
    assertEquals( "Reverse Fault parser must honor Joint's 12.0 line-width repeat", 12.0f,
                  advanceField.getFloat( reverseFaultSymbol.mLineEffect ), 0.001f );
  }

  @Test
  public void pitAndCeilingLedgeHachuresPointSameDirectionInDefaultRawPack() throws Exception
  {
    int pitDirection = hachureDirection( readRawSymbolEntry( "symbols_topodroid_sketch/line/pit" ) );
    int ceilingLedgeDirection = hachureDirection( readRawSymbolEntry( "symbols_topodroid_sketch/line/chimney" ) );
    int poolFingersDirection = hachureDirection( readRawSymbolEntry( "symbols_topodroid_sketch/line/pool-fingers" ) );
    int gypsumWallCrustDirection = hachureDirection( readRawSymbolEntry( "symbols_topodroid_sketch/line/gypsum-wall-crust" ) );
    int thrustDirection = hachureDirection( readRawSymbolEntry( "symbols_topodroid_sketch/line/thrust" ) );
    int reverseFaultDirection = hachureDirection( readRawSymbolEntry( "symbols_topodroid_sketch/line/reverse-fault" ) );

    assertTrue( "Could not parse pit hachure direction", pitDirection != 0 );
    assertTrue( "Could not parse ceiling-ledge hachure direction", ceilingLedgeDirection != 0 );
    assertEquals( "Pit and ceiling-ledge hachures should point to the same side while drawing",
                  pitDirection, ceilingLedgeDirection );
    assertEquals( "Pool Fingers should point to the Pit side while drawing",
                  pitDirection, poolFingersDirection );
    assertEquals( "Gypsum Wall Crust should point to the Pit side while drawing",
                  pitDirection, gypsumWallCrustDirection );
    assertEquals( "Thrust Fault teeth should point to the Pit side while drawing",
                  pitDirection, thrustDirection );
    assertEquals( "Reverse Fault tabs should point to the Pit side while drawing",
                  pitDirection, reverseFaultDirection );
  }

  @Test
  public void topodroidSketchSymbolContactSheet_rendersProofSymbols() throws Exception
  {
    Bitmap bitmap = Bitmap.createBitmap( WIDTH, HEIGHT, Bitmap.Config.ARGB_8888 );
    bitmap.eraseColor( Color.BLACK );
    Canvas canvas = new Canvas( bitmap );

    Paint label = new Paint();
    label.setColor( 0xffbbbbbb );
    label.setTextSize( 22.0f );
    label.setAntiAlias( true );

    drawHeaders( canvas, label );

    float y = 90.0f;
    drawLineRow( canvas, label, "Dashed", "dashed", y ); y += LINE_ROW;
    drawDirectionalLineRow( canvas, label, "Line with arrow", SymbolLibrary.LINE_WITH_ARROW, y ); y += DIRECTIONAL_LINE_ROW;
    drawDirectionalLineRow( canvas, label, "Dashed line + arrow", SymbolLibrary.DASHED_LINE_WITH_ARROW, y ); y += DIRECTIONAL_LINE_ROW;
    drawDirectionalLineRow( canvas, label, "Intermittent dotted arrow", SymbolLibrary.INTERMITTENT_DOTTED_ARROW, y ); y += DIRECTIONAL_LINE_ROW;
    drawLineRow( canvas, label, "Dotted", "dotted", y ); y += LINE_ROW;
    drawLineRow( canvas, label, "User", SymbolLibrary.USER, y ); y += LINE_ROW;
    drawLineRow( canvas, label, "Section", SymbolLibrary.SECTION, y ); y += LINE_ROW;
    drawLineRow( canvas, label, "Wall", SymbolLibrary.WALL, y ); y += LINE_ROW;
    drawLineRow( canvas, label, "Dripline", "dripline", y ); y += LINE_ROW;
    drawLineRow( canvas, label, "Flowstone", "flowstone", y ); y += LINE_ROW;
    drawDirectionalLineRow( canvas, label, "Dogtooth spar", "dogtooth-spar", y ); y += DIRECTIONAL_LINE_ROW;
    drawDirectionalLineRow( canvas, label, "Pool fingers", "pool-fingers", y ); y += DIRECTIONAL_LINE_ROW;
    drawDirectionalLineRow( canvas, label, "Shelfstone", "shelfstone", y ); y += DIRECTIONAL_LINE_ROW;
    drawDirectionalLineRow( canvas, label, "Gyp wall crust", "gypsum-wall-crust", y ); y += DIRECTIONAL_LINE_ROW;
    drawLineRow( canvas, label, "Ceiling ledge", "chimney", y ); y += LINE_ROW;
    drawDirectionalLineRow( canvas, label, "Dashed crossbar", SymbolLibrary.CROSSBAR_LINE_DASHED, y ); y += DIRECTIONAL_LINE_ROW;
    drawLineRow( canvas, label, "Ceiling channel", "ceiling-meander", y ); y += LINE_ROW;
    drawLineRow( canvas, label, "Pit", "pit", y ); y += LINE_ROW;
    drawDirectionalLineRow( canvas, label, "Slope", SymbolLibrary.SLOPE, y ); y += DIRECTIONAL_LINE_ROW;
    drawDirectionalLineRow( canvas, label, "Slope fan", SymbolLibrary.SLOPE_FAN, y ); y += DIRECTIONAL_LINE_ROW;
    drawDirectionalLineRow( canvas, label, "Talus", "talus", y ); y += DIRECTIONAL_LINE_ROW;
    drawLineRow( canvas, label, "Joint", "joint", y ); y += LINE_ROW;
    drawDirectionalLineRow( canvas, label, "Thrust fault", "thrust", y ); y += DIRECTIONAL_LINE_ROW;
    drawDirectionalLineRow( canvas, label, "Reverse fault", "reverse-fault", y ); y += DIRECTIONAL_LINE_ROW;
    drawDirectionalLineRow( canvas, label, "Crossbar line", SymbolLibrary.CROSSBAR_LINE, y ); y += DIRECTIONAL_LINE_ROW;
    drawLineRow( canvas, label, "Floor channel", "floor-meander", y ); y += LINE_ROW;
    drawLineRow( canvas, label, "Water flow", "water-flow", y ); y += LINE_ROW + 32.0f;
    assertLineMissing( "arrow" );
    assertLineMissing( "border" );
    assertLineMissing( "rock-border" );
    assertLineMissing( "wall:clay" );
    assertLineMissing( "wall:ice" );
    assertLineMissing( "wall:presumed" );
    assertLineMissing( "water-flow:intermittent" );

    drawPointRow( canvas, label, "Sand", "sand", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Clay", "clay", y, 0.0, false ); y += POINT_ROW;
    drawPointRow( canvas, label, "Bedrock", "bedrock", y, 0.0, false ); y += POINT_ROW;
    drawPointRow( canvas, label, "Slope", "slope", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Air draught", "air-draught", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Anchor", "anchor", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Anastomosis", "anastomosis", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Anthodites", "anthodites", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Aragonite", "aragonite", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Archeo exc.", "archeo-excavation", y, 0.0, false ); y += POINT_ROW;
    drawPointRow( canvas, label, "Blocks", "blocks", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Breakdown: round", "boulder", y, 0.0, true, 4.0f ); y += POINT_ROW;
    drawPointRow( canvas, label, "Breakdown: square", "angular-block", y, 0.0, true, 4.0f ); y += POINT_ROW;
    drawPointRow( canvas, label, "Breakdown: rectangle", "bedding-slab", y, 0.0, true, 4.0f ); y += POINT_ROW;
    drawPointRow( canvas, label, "Small rocks", "debris:small", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Pebbles", "pebbles", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Bones", "bones", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Boxwork", "boxwork", y, 0.0, false ); y += POINT_ROW;
    drawPointRow( canvas, label, "Calcite crust", "calcite-crust", y, 0.0, false ); y += POINT_ROW;
    drawPointRow( canvas, label, "Calcite spar", "calcite-spar", y, 0.0, false ); y += POINT_ROW;
    assertPointMissing( "dogtooth-spar" );
    drawPointRow( canvas, label, "Cave pearl", "cave-pearl", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Chert", "chert", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Column", "column", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Gypsum crystals", "gypsum-crystals", y, 0.0, false ); y += POINT_ROW;
    drawPointRow( canvas, label, "Guano", "guano", y, 0.0, false ); y += POINT_ROW;
    drawPointRow( canvas, label, "Helictite", "helictite", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Lead", "continuation", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Popcorn", "popcorn", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Soda straw", "soda-straw", y, 0.0, false ); y += POINT_ROW;
    drawPointRow( canvas, label, "Stalactite", "stalactite", y, 0.0, false ); y += POINT_ROW;
    drawPointRow( canvas, label, "Stalagmite", "stalagmite", y, 0.0, false ); y += POINT_ROW;
    drawPointRow( canvas, label, "Stal. alt", "stalactite:alternate", y, 0.0, false ); y += POINT_ROW;
    drawPointRow( canvas, label, "Stalg. alt", "stalagmite:alternate", y, 0.0, false ); y += POINT_ROW;
    drawPointRow( canvas, label, "Water flow", "water-flow", y, 0.0 ); y += POINT_ROW + 34.0f;
    drawPointRow( canvas, label, "Corrosion res.", "corrosion-residue", y, 0.0, false ); y += POINT_ROW;
    drawPointRow( canvas, label, "Draperies", "curtain", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Broken form.", "broken-formation", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Gyp chandelier", "gypsum-chandelier", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Gypsum flower", "gypsum-flower", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Gypsum needles", "gypsum-needles", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Invert fossils", "invertebrate-fossils", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Leaf litter", "leaf-litter-organic", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Moonmilk", "moonmilk", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Pool spar", "pool-spar", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Frostwork", "frostwork", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Conulite", "conulite", y, 0.0, false ); y += POINT_ROW;
    drawPointRow( canvas, label, "Cave clouds", "mammalaries-cave-clouds", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Raft cone", "raft-cone", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Gypsum hair", "gypsum-hair", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Gyp rim vent", "gypsum-rim-vent", y, 0.0, false ); y += POINT_ROW;
    drawPointRow( canvas, label, "Calcite rafts", "calcite-rafts", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Folia", "folia", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Bat skeleton", "bat-skeleton", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Midden", "midden", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Rusticles", "rusticles", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Vegetation", "vegetation", y, 0.0, false ); y += POINT_ROW;
    drawPointRow( canvas, label, "Mud cracks", "mudcrack", y, 0.0, false ); y += POINT_ROW;
    drawPointRow( canvas, label, "Subaq. helictites", "subaqueous-helictites", y, 0.0, false ); y += POINT_ROW;
    drawPointRow( canvas, label, "Gyp dripholes", "gypsum-dripholes", y, 0.0, false ); y += POINT_ROW;
    drawPointRow( canvas, label, "Sediment cone", "sediment-cone", y, 0.0, false ); y += POINT_ROW + 34.0f;

    drawOpacityRow( canvas, label, y ); y += 108.0f;
    drawScaleRow( canvas, label, y );

    assertTrue( "TopoDroid Sketch symbol contact sheet is unexpectedly sparse", countForeground( bitmap ) > 45000 );
    File externalArtifact = new File( getExternalArtifactDir(), "topodroid-sketch-symbol-slice.png" );
    File internalArtifact = new File( getInternalArtifactDir(), "topodroid-sketch-symbol-slice.png" );
    byte[] png = encodeBitmap( bitmap );
    saveBytes( png, externalArtifact );
    saveBytes( png, internalArtifact );
    reportArtifacts( externalArtifact, internalArtifact );
    reportBase64Artifact( png );
    bitmap.recycle();
  }

  private void drawHeaders( Canvas canvas, Paint label )
  {
    canvas.drawText( "Thin W=1", LEFT, 36.0f, label );
    canvas.drawText( "Standard W=2", LEFT + COL, 36.0f, label );
    canvas.drawText( "Thick W=5", LEFT + 2.0f * COL, 36.0f, label );
    canvas.drawText( "TopoDroid Sketch", 18.0f, 36.0f, label );
  }

  private void drawLineRow( Canvas canvas, Paint label, String title, String thName, float y )
  {
    int lineType = BrushManager.getLineIndexByThName( thName );
    assertTrue( "Missing TopoDroid Sketch line symbol " + thName, lineType >= 0 );
    canvas.drawText( title, 18.0f, y + 7.0f, label );
    drawStyledLine( canvas, lineType, LEFT, y, lineStyle( thName, SketchBrushStyle.DEFAULT_WEIGHT_THIN ) );
    drawStyledLine( canvas, lineType, LEFT + COL, y, lineStyle( thName, SketchBrushStyle.DEFAULT_WEIGHT_STANDARD ) );
    drawStyledLine( canvas, lineType, LEFT + 2.0f * COL, y, lineStyle( thName, SketchBrushStyle.DEFAULT_WEIGHT_THICK ) );
  }

  private void drawDirectionalLineRow( Canvas canvas, Paint label, String title, String thName, float y )
  {
    int lineType = BrushManager.getLineIndexByThName( thName );
    assertTrue( "Missing TopoDroid Sketch line symbol " + thName, lineType >= 0 );
    canvas.drawText( title, 18.0f, y + 7.0f, label );
    canvas.drawText( "F", LEFT - 24.0f, y - 20.0f, label );
    canvas.drawText( "R", LEFT - 24.0f, y + 34.0f, label );
    drawStyledCurvedLine( canvas, lineType, LEFT, y - 25.0f,
      lineStyle( thName, SketchBrushStyle.DEFAULT_WEIGHT_THIN ), false );
    drawStyledCurvedLine( canvas, lineType, LEFT, y + 25.0f,
      lineStyle( thName, SketchBrushStyle.DEFAULT_WEIGHT_THIN ), true );
    drawStyledCurvedLine( canvas, lineType, LEFT + COL, y - 25.0f,
      lineStyle( thName, SketchBrushStyle.DEFAULT_WEIGHT_STANDARD ), false );
    drawStyledCurvedLine( canvas, lineType, LEFT + COL, y + 25.0f,
      lineStyle( thName, SketchBrushStyle.DEFAULT_WEIGHT_STANDARD ), true );
    drawStyledCurvedLine( canvas, lineType, LEFT + 2.0f * COL, y - 25.0f,
      lineStyle( thName, SketchBrushStyle.DEFAULT_WEIGHT_THICK ), false );
    drawStyledCurvedLine( canvas, lineType, LEFT + 2.0f * COL, y + 25.0f,
      lineStyle( thName, SketchBrushStyle.DEFAULT_WEIGHT_THICK ), true );
  }

  private void assertLineMissing( String thName )
  {
    assertTrue( "Unexpected TopoDroid Sketch line symbol " + thName, BrushManager.getLineIndexByThName( thName ) < 0 );
  }

  private void assertPointMissing( String thName )
  {
    assertTrue( "Unexpected TopoDroid Sketch point symbol " + thName, BrushManager.getPointIndexByThName( thName ) < 0 );
  }

  private String readRawSymbolEntry( String entryName ) throws Exception
  {
    ZipInputStream zip = new ZipInputStream(
      mContext.getResources().openRawResource( R.raw.symbols_topodroid_sketch ) );
    try {
      ZipEntry entry;
      while ( (entry = zip.getNextEntry()) != null ) {
        if ( entryName.equals( entry.getName() ) ) {
          return new String( readZipEntryBytes( zip ), StandardCharsets.UTF_8 );
        }
      }
    } finally {
      zip.close();
    }
    throw new AssertionError( "Missing raw symbol entry " + entryName );
  }

  private static byte[] readZipEntryBytes( ZipInputStream zip ) throws Exception
  {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    byte[] buffer = new byte[4096];
    int read;
    while ( (read = zip.read( buffer )) >= 0 ) output.write( buffer, 0, read );
    return output.toByteArray();
  }

  private static int countOccurrences( String text, String needle )
  {
    int count = 0;
    int offset = 0;
    while ( ( offset = text.indexOf( needle, offset ) ) >= 0 ) {
      ++count;
      offset += needle.length();
    }
    return count;
  }

  private static int hachureDirection( String symbol )
  {
    float carrierCenterSum = 0.0f;
    int carrierCount = 0;
    float stampMinY = Float.POSITIVE_INFINITY;
    float stampMaxY = Float.NEGATIVE_INFINITY;
    boolean inSketchEffect = false;
    boolean inStamp = false;

    String[] lines = symbol.split( "\\r?\\n" );
    for ( int i = 0; i < lines.length; ++i ) {
      String line = lines[i].trim();
      if ( line.length() == 0 || line.startsWith( "#" ) ) continue;
      String[] vals = line.split( "\\s+" );
      if ( vals.length == 0 ) continue;

      if ( vals[0].equals( "sketch_effect" ) ) {
        inSketchEffect = true;
      } else if ( inSketchEffect && vals[0].equals( "endsketch_effect" ) ) {
        break;
      } else if ( inSketchEffect && vals[0].equals( "carrier" ) && vals.length >= 3 ) {
        try {
          float y0 = Float.parseFloat( vals[1] );
          float y1 = Float.parseFloat( vals[2] );
          carrierCenterSum += 0.5f * ( y0 + y1 );
          ++carrierCount;
        } catch ( NumberFormatException e ) {
          return 0;
        }
      } else if ( inSketchEffect && vals[0].equals( "stamp" ) ) {
        inStamp = true;
      } else if ( inSketchEffect && vals[0].equals( "endstamp" ) ) {
        inStamp = false;
      } else if ( inStamp ) {
        int yIndex = -1;
        if ( ( vals[0].equals( "moveTo" ) || vals[0].equals( "lineTo" ) ) && vals.length >= 3 ) {
          yIndex = 2;
        }
        if ( yIndex >= 0 ) {
          try {
            float y = Float.parseFloat( vals[yIndex] );
            if ( y < stampMinY ) stampMinY = y;
            if ( y > stampMaxY ) stampMaxY = y;
          } catch ( NumberFormatException e ) {
            return 0;
          }
        }
      }
    }

    if ( stampMinY == Float.POSITIVE_INFINITY || stampMaxY == Float.NEGATIVE_INFINITY ) return 0;
    // Carrier-free hachure symbols are authored around the line path at y=0.
    float carrierCenter = ( carrierCount == 0 ) ? 0.0f : carrierCenterSum / carrierCount;
    float stampCenter = 0.5f * ( stampMinY + stampMaxY );
    if ( Math.abs( stampCenter - carrierCenter ) < 0.001f ) return 0;
    return ( stampCenter < carrierCenter ) ? -1 : 1;
  }

  private void drawStyledLine( Canvas canvas, int lineType, float x, float y, SketchBrushStyle style )
  {
    DrawingLinePath line = new DrawingLinePath( lineType, 0 );
    line.setSketchBrushStyle( style );
    line.addStartPoint( x, y );
    line.addPoint( x + 210.0f, y );
    line.computeUnitNormal();
    line.draw( canvas, new Matrix(), BBOX );
  }

  private void drawStyledCurvedLine( Canvas canvas, int lineType, float x, float y,
                                     SketchBrushStyle style, boolean reversed )
  {
    DrawingLinePath line = new DrawingLinePath( lineType, 0 );
    line.setSketchBrushStyle( style );
    line.addStartPoint( x, y );
    line.addPoint3( x + 55.0f, y - 16.0f, x + 155.0f, y + 16.0f, x + 210.0f, y );
    line.setReversed( reversed );
    line.computeUnitNormal();
    line.draw( canvas, new Matrix(), BBOX );
  }

  private void drawPointRow( Canvas canvas, Paint label, String title, String thName, float y, double orientation )
  {
    drawPointRow( canvas, label, title, thName, y, orientation, true );
  }

  private void drawPointRow( Canvas canvas, Paint label, String title, String thName, float y, double orientation, boolean expectOrientable )
  {
    drawPointRow( canvas, label, title, thName, y, orientation, expectOrientable, 1.0f );
  }

  private void drawPointRow( Canvas canvas, Paint label, String title, String thName, float y, double orientation,
                             boolean expectOrientable, float pointScale )
  {
    int pointType = BrushManager.getPointIndexByThName( thName );
    assertTrue( "Missing TopoDroid Sketch point symbol " + thName, pointType >= 0 );
    assertTrue( "Unexpected orientable state for TopoDroid Sketch point " + thName,
      BrushManager.isPointOrientable( pointType ) == expectOrientable );
    canvas.drawText( title, 18.0f, y + 7.0f, label );
    drawStyledPoint( canvas, pointType, LEFT + 105.0f, y, style( SketchBrushStyle.DEFAULT_WEIGHT_THIN, pointScale ), orientation );
    drawStyledPoint( canvas, pointType, LEFT + COL + 105.0f, y, style( SketchBrushStyle.DEFAULT_WEIGHT_STANDARD, pointScale ), orientation );
    drawStyledPoint( canvas, pointType, LEFT + 2.0f * COL + 105.0f, y, style( SketchBrushStyle.DEFAULT_WEIGHT_THICK, pointScale ), orientation );
  }

  private void drawScaleRow( Canvas canvas, Paint label, float y )
  {
    canvas.drawText( "Point S", 18.0f, y + 7.0f, label );
    int clay = BrushManager.getPointIndexByThName( "clay" );
    int slope = BrushManager.getPointIndexByThName( "slope" );
    assertTrue( "Missing TopoDroid Sketch clay point", clay >= 0 );
    assertTrue( "Missing TopoDroid Sketch slope point", slope >= 0 );
    drawStyledPoint( canvas, clay, LEFT + 105.0f, y, style( SketchBrushStyle.DEFAULT_WEIGHT_STANDARD, 0.6f ), 0.0 );
    drawStyledPoint( canvas, clay, LEFT + COL + 105.0f, y, style( SketchBrushStyle.DEFAULT_WEIGHT_STANDARD, 1.0f ), 0.0 );
    drawStyledPoint( canvas, slope, LEFT + 2.0f * COL + 105.0f, y, style( SketchBrushStyle.DEFAULT_WEIGHT_STANDARD, 1.6f ), 0.0 );
    canvas.drawText( "S=0.6", LEFT, y + 42.0f, label );
    canvas.drawText( "S=1.0", LEFT + COL, y + 42.0f, label );
    canvas.drawText( "S=1.6", LEFT + 2.0f * COL, y + 42.0f, label );
  }

  private void drawOpacityRow( Canvas canvas, Paint label, float y )
  {
    int user = BrushManager.getLineIndexByThName( SymbolLibrary.USER );
    assertTrue( "Missing user line symbol", user >= 0 );
    canvas.drawText( "Opacity", 18.0f, y + 7.0f, label );
    drawStyledLine( canvas, user, LEFT, y, style( SketchBrushStyle.DEFAULT_WEIGHT_STANDARD, 1.0f, 0.35f ) );
    drawStyledLine( canvas, user, LEFT + COL, y, style( SketchBrushStyle.DEFAULT_WEIGHT_STANDARD, 1.0f, 0.65f ) );
    drawStyledLine( canvas, user, LEFT + 2.0f * COL, y, style( SketchBrushStyle.DEFAULT_WEIGHT_STANDARD, 1.0f, 1.0f ) );
    canvas.drawText( "O=0.35", LEFT, y + 42.0f, label );
    canvas.drawText( "O=0.65", LEFT + COL, y + 42.0f, label );
    canvas.drawText( "O=1.0", LEFT + 2.0f * COL, y + 42.0f, label );
  }

  private void drawStyledPoint( Canvas canvas, int pointType, float x, float y, SketchBrushStyle style, double orientation )
  {
    DrawingPointPath point = new DrawingPointPath( pointType, x, y, PointScale.SCALE_M, 0 );
    point.setSketchBrushStyle( style );
    if ( orientation != 0.0 ) point.setOrientation( orientation );
    point.draw( canvas, new Matrix(), 1.0f, BBOX );
  }

  private SketchBrushStyle style( float weight, float pointScale )
  {
    return SketchBrushStyle.of( weight, pointScale, 1.0f );
  }

  private SketchBrushStyle lineStyle( String thName, float weight )
  {
    if ( SymbolLibrary.WALL.equals( thName ) ) return SketchBrushStyle.of( weight, 1.0f, 1.0f, Color.WHITE );
    return style( weight, 1.0f );
  }

  private SketchBrushStyle style( float weight, float pointScale, float opacity )
  {
    return SketchBrushStyle.of( weight, pointScale, opacity );
  }

  private File getExternalArtifactDir()
  {
    File root = mContext.getExternalFilesDir( "test-artifacts" );
    assertNotNull( "No external files dir for test artifacts", root );
    return ensureArtifactDir( root );
  }

  private File getInternalArtifactDir()
  {
    return ensureArtifactDir( new File( mContext.getFilesDir(), "test-artifacts" ) );
  }

  private File ensureArtifactDir( File root )
  {
    File dir = new File( root, "topodroid-sketch-symbol-slice" );
    assertTrue( "Failed to create artifact dir " + dir.getAbsolutePath(), dir.exists() || dir.mkdirs() );
    return dir;
  }

  private void reportArtifacts( File externalArtifact, File internalArtifact )
  {
    String message = "TopoDroid Sketch symbol artifact external=" + externalArtifact.getAbsolutePath() + "\n"
      + "TopoDroid Sketch symbol artifact internal=" + internalArtifact.getAbsolutePath() + "\n";
    sendInstrumentationStream( message );
    System.out.println( message );
  }

  private void reportBase64Artifact( byte[] png )
  {
    String encoded = Base64.encodeToString( png, Base64.NO_WRAP );
    sendInstrumentationStream( "SKETCH_SYMBOL_ARTIFACT_B64_BEGIN bytes=" + png.length + "\n" );
    int offset = 0;
    int chunk = 4000;
    while ( offset < encoded.length() ) {
      int end = Math.min( offset + chunk, encoded.length() );
      sendInstrumentationStream( "SKETCH_SYMBOL_ARTIFACT_B64 " + offset + " " + encoded.substring( offset, end ) + "\n" );
      offset = end;
    }
    sendInstrumentationStream( "SKETCH_SYMBOL_ARTIFACT_B64_END\n" );
  }

  private void sendInstrumentationStream( String message )
  {
    Bundle status = new Bundle();
    status.putString( Instrumentation.REPORT_KEY_STREAMRESULT, message );
    mInstrumentation.sendStatus( 0, status );
  }

  private static byte[] encodeBitmap( Bitmap bitmap ) throws Exception
  {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    assertTrue( "Failed to encode TopoDroid Sketch symbol contact sheet", bitmap.compress( Bitmap.CompressFormat.PNG, 100, output ) );
    return output.toByteArray();
  }

  private static void saveBytes( byte[] bytes, File file ) throws Exception
  {
    OutputStream output = new FileOutputStream( file );
    try {
      output.write( bytes );
    } finally {
      output.close();
    }
  }

  private static int countForeground( Bitmap bitmap )
  {
    int count = 0;
    for ( int y = 0; y < bitmap.getHeight(); ++y ) {
      for ( int x = 0; x < bitmap.getWidth(); ++x ) {
        if ( bitmap.getPixel( x, y ) != Color.BLACK ) ++count;
      }
    }
    return count;
  }
}
