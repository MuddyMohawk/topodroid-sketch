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
  private static final int HEIGHT = 5844;
  private static final float LEFT = 260.0f;
  private static final float COL = 370.0f;
  private static final float LINE_ROW = 70.0f;
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

    ZipInputStream zip = new ZipInputStream(
      mContext.getResources().openRawResource( R.raw.symbols_topodroid_sketch ) );
    try {
      ZipEntry entry;
      while ( (entry = zip.getNextEntry()) != null ) {
        String name = entry.getName();
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
    assertTrue( "Missing sketch flowstone line in default raw pack",
                entries.contains( "symbols_topodroid_sketch/line/flowstone" ) );
    assertTrue( "Missing sketch sand point in default raw pack",
                entries.contains( "symbols_topodroid_sketch/point/sand" ) );
    assertTrue( "Missing sketch debris point in default raw pack",
                entries.contains( "symbols_topodroid_sketch/point/debris=small" ) );
    assertTrue( "Missing sketch leaf-litter point in default raw pack",
                entries.contains( "symbols_topodroid_sketch/point/leaf-litter-organic" ) );
  }

  @Test
  public void pitAndCeilingLedgeHachuresPointSameDirectionInDefaultRawPack() throws Exception
  {
    int pitDirection = hachureDirection( readRawSymbolEntry( "symbols_topodroid_sketch/line/pit" ) );
    int ceilingLedgeDirection = hachureDirection( readRawSymbolEntry( "symbols_topodroid_sketch/line/chimney" ) );

    assertTrue( "Could not parse pit hachure direction", pitDirection != 0 );
    assertTrue( "Could not parse ceiling-ledge hachure direction", ceilingLedgeDirection != 0 );
    assertEquals( "Pit and ceiling-ledge hachures should point to the same side while drawing",
                  pitDirection, ceilingLedgeDirection );
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
    drawLineRow( canvas, label, "Dotted", "dotted", y ); y += LINE_ROW;
    drawLineRow( canvas, label, "User", SymbolLibrary.USER, y ); y += LINE_ROW;
    drawLineRow( canvas, label, "Section", SymbolLibrary.SECTION, y ); y += LINE_ROW;
    drawLineRow( canvas, label, "Wall", SymbolLibrary.WALL, y ); y += LINE_ROW;
    drawLineRow( canvas, label, "Dripline", "dripline", y ); y += LINE_ROW;
    drawLineRow( canvas, label, "Flowstone", "flowstone", y ); y += LINE_ROW;
    drawLineRow( canvas, label, "Ceiling ledge", "chimney", y ); y += LINE_ROW;
    drawLineRow( canvas, label, "Ceiling channel", "ceiling-meander", y ); y += LINE_ROW;
    drawLineRow( canvas, label, "Pit", "pit", y ); y += LINE_ROW;
    drawLineRow( canvas, label, "Floor channel", "floor-meander", y ); y += LINE_ROW;
    drawLineRow( canvas, label, "Water flow", "water-flow", y ); y += LINE_ROW + 32.0f;
    assertLineMissing( "arrow" );
    assertLineMissing( "border" );
    assertLineMissing( "rock-border" );
    assertLineMissing( "slope" );
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
    drawPointRow( canvas, label, "Small rocks", "debris:small", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Pebbles", "pebbles", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Bones", "bones", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Boxwork", "boxwork", y, 0.0, false ); y += POINT_ROW;
    drawPointRow( canvas, label, "Calcite crust", "calcite-crust", y, 0.0, false ); y += POINT_ROW;
    drawPointRow( canvas, label, "Calcite spar", "calcite-spar", y, 0.0, false ); y += POINT_ROW;
    drawPointRow( canvas, label, "Cave pearl", "cave-pearl", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Chert", "chert", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Column", "column", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Gypsum crystals", "gypsum-crystals", y, 0.0 ); y += POINT_ROW;
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
    drawPointRow( canvas, label, "Gyp rim vent", "gypsum-rim-vent", y, 0.0, false ); y += POINT_ROW + 34.0f;

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

  private void assertLineMissing( String thName )
  {
    assertTrue( "Unexpected TopoDroid Sketch line symbol " + thName, BrushManager.getLineIndexByThName( thName ) < 0 );
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

    if ( carrierCount == 0 || stampMinY == Float.POSITIVE_INFINITY || stampMaxY == Float.NEGATIVE_INFINITY ) return 0;
    float carrierCenter = carrierCenterSum / carrierCount;
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

  private void drawPointRow( Canvas canvas, Paint label, String title, String thName, float y, double orientation )
  {
    drawPointRow( canvas, label, title, thName, y, orientation, true );
  }

  private void drawPointRow( Canvas canvas, Paint label, String title, String thName, float y, double orientation, boolean expectOrientable )
  {
    int pointType = BrushManager.getPointIndexByThName( thName );
    assertTrue( "Missing TopoDroid Sketch point symbol " + thName, pointType >= 0 );
    assertTrue( "Unexpected orientable state for TopoDroid Sketch point " + thName,
      BrushManager.isPointOrientable( pointType ) == expectOrientable );
    canvas.drawText( title, 18.0f, y + 7.0f, label );
    drawStyledPoint( canvas, pointType, LEFT + 105.0f, y, style( SketchBrushStyle.DEFAULT_WEIGHT_THIN, 1.0f ), orientation );
    drawStyledPoint( canvas, pointType, LEFT + COL + 105.0f, y, style( SketchBrushStyle.DEFAULT_WEIGHT_STANDARD, 1.0f ), orientation );
    drawStyledPoint( canvas, pointType, LEFT + 2.0f * COL + 105.0f, y, style( SketchBrushStyle.DEFAULT_WEIGHT_THICK, 1.0f ), orientation );
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
