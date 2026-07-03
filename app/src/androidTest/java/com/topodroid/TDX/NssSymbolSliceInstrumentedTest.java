package com.topodroid.TDX;

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

@RunWith( AndroidJUnit4.class )
@LargeTest
public class NssSymbolSliceInstrumentedTest
{
  private static final int WIDTH = 1440;
  private static final int HEIGHT = 5750;
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
    TopoDroidApp.installSymbols( R.raw.symbols_nss, true );
    BrushManager.reloadPointLibrary( mContext, mContext.getResources() );
    BrushManager.reloadLineLibrary( mContext.getResources() );
  }

  @After
  public void tearDown()
  {
    TopoDroidApp.installSymbols( true );
    BrushManager.reloadPointLibrary( mContext, mContext.getResources() );
    BrushManager.reloadLineLibrary( mContext.getResources() );
    TDInstance.context = mPreviousContext;
  }

  @Test
  public void nssVerticalSliceContactSheet_rendersProofSymbols() throws Exception
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
    drawLineRow( canvas, label, "Wall", SymbolLibrary.WALL, y ); y += LINE_ROW;
    drawLineRow( canvas, label, "User detail", SymbolLibrary.USER, y ); y += LINE_ROW;
    drawLineRow( canvas, label, "Ledge/Pit", "pit", y ); y += LINE_ROW;
    drawLineRow( canvas, label, "Ceiling ledge", "chimney", y ); y += LINE_ROW;
    drawLineRow( canvas, label, "Flowstone", "flowstone", y ); y += LINE_ROW;
    drawLineRow( canvas, label, "Ceiling channel", "ceiling-meander", y ); y += LINE_ROW;
    drawLineRow( canvas, label, "Presumed wall", "wall:presumed", y ); y += LINE_ROW;
    drawLineRow( canvas, label, "Dripline", "dripline", y ); y += LINE_ROW;
    drawLineRow( canvas, label, "Conj. stream", "water-flow:intermittent", y ); y += LINE_ROW + 32.0f;

    drawPointRow( canvas, label, "Sand", "sand", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Clay", "clay", y, 0.0, false ); y += POINT_ROW;
    drawPointRow( canvas, label, "Bedrock", "bedrock", y, 0.0 ); y += POINT_ROW;
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
    drawPointRow( canvas, label, "Guano", "guano", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Helictite", "helictite", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Lead", "continuation", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Popcorn", "popcorn", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Soda straw", "soda-straw", y, 0.0, false ); y += POINT_ROW;
    drawPointRow( canvas, label, "Stalactite", "stalactite", y, 0.0, false ); y += POINT_ROW;
    drawPointRow( canvas, label, "Stalagmite", "stalagmite", y, 0.0, false ); y += POINT_ROW;
    drawPointRow( canvas, label, "Stal. alt", "stalactite:alternate", y, 0.0, false ); y += POINT_ROW;
    drawPointRow( canvas, label, "Stalg. alt", "stalagmite:alternate", y, 0.0, false ); y += POINT_ROW;
    drawPointRow( canvas, label, "Water flow", "water-flow", y, 0.0 ); y += POINT_ROW + 34.0f;
    drawPointRow( canvas, label, "Corrosion res.", "corrosion-residue", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Draperies", "curtain", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Broken form.", "broken-formation", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Gyp chandelier", "gypsum-chandelier", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Gypsum flower", "gypsum-flower", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Gypsum needles", "gypsum-needles", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Invert fossils", "invertebrate-fossils", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Moonmilk", "moonmilk", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Pool spar", "pool-spar", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Frostwork", "frostwork", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Conulite", "conulite", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Cave clouds", "mammalaries-cave-clouds", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Raft cone", "raft-cone", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Gypsum hair", "gypsum-hair", y, 0.0 ); y += POINT_ROW;
    drawPointRow( canvas, label, "Gyp rim vent", "gypsum-rim-vent", y, 0.0 ); y += POINT_ROW + 34.0f;

    drawOpacityRow( canvas, label, y ); y += 108.0f;
    drawScaleRow( canvas, label, y );

    assertTrue( "NSS contact sheet is unexpectedly sparse", countForeground( bitmap ) > 45000 );
    File externalArtifact = new File( getExternalArtifactDir(), "nss-vertical-slice.png" );
    File internalArtifact = new File( getInternalArtifactDir(), "nss-vertical-slice.png" );
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
    canvas.drawText( "NSS 1979", 18.0f, 36.0f, label );
  }

  private void drawLineRow( Canvas canvas, Paint label, String title, String thName, float y )
  {
    int lineType = BrushManager.getLineIndexByThName( thName );
    assertTrue( "Missing NSS line symbol " + thName, lineType >= 0 );
    canvas.drawText( title, 18.0f, y + 7.0f, label );
    drawStyledLine( canvas, lineType, LEFT, y, lineStyle( thName, SketchBrushStyle.DEFAULT_WEIGHT_THIN ) );
    drawStyledLine( canvas, lineType, LEFT + COL, y, lineStyle( thName, SketchBrushStyle.DEFAULT_WEIGHT_STANDARD ) );
    drawStyledLine( canvas, lineType, LEFT + 2.0f * COL, y, lineStyle( thName, SketchBrushStyle.DEFAULT_WEIGHT_THICK ) );
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
    assertTrue( "Missing NSS point symbol " + thName, pointType >= 0 );
    assertTrue( "Unexpected orientable state for NSS point " + thName,
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
    assertTrue( "Missing NSS clay point", clay >= 0 );
    assertTrue( "Missing NSS slope point", slope >= 0 );
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
    File dir = new File( root, "nss-symbol-slice" );
    assertTrue( "Failed to create artifact dir " + dir.getAbsolutePath(), dir.exists() || dir.mkdirs() );
    return dir;
  }

  private void reportArtifacts( File externalArtifact, File internalArtifact )
  {
    String message = "NSS vertical slice artifact external=" + externalArtifact.getAbsolutePath() + "\n"
      + "NSS vertical slice artifact internal=" + internalArtifact.getAbsolutePath() + "\n";
    sendInstrumentationStream( message );
    System.out.println( message );
  }

  private void reportBase64Artifact( byte[] png )
  {
    String encoded = Base64.encodeToString( png, Base64.NO_WRAP );
    sendInstrumentationStream( "NSS_ARTIFACT_B64_BEGIN bytes=" + png.length + "\n" );
    int offset = 0;
    int chunk = 4000;
    while ( offset < encoded.length() ) {
      int end = Math.min( offset + chunk, encoded.length() );
      sendInstrumentationStream( "NSS_ARTIFACT_B64 " + offset + " " + encoded.substring( offset, end ) + "\n" );
      offset = end;
    }
    sendInstrumentationStream( "NSS_ARTIFACT_B64_END\n" );
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
    assertTrue( "Failed to encode NSS contact sheet", bitmap.compress( Bitmap.CompressFormat.PNG, 100, output ) );
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
