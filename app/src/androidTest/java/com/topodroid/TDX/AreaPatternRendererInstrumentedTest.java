package com.topodroid.TDX;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.RectF;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.topodroid.prefs.TDSetting;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;

/** Raster-level invariants for the programmatic bedrock area pattern. */
@RunWith( AndroidJUnit4.class )
public class AreaPatternRendererInstrumentedTest
{
  private static final int WIDTH = 640;
  private static final int HEIGHT = 480;
  private static final float SCALE = 4.0f;
  private static final AreaLinePattern BEDROCK = AreaLinePattern.bedrock(
      0.0f, 0xcc888888, 0.85f, 17.0f, 48.0f, 0.0f );
  private static final AreaLinePattern SUMP = AreaLinePattern.crosshatch(
      -35.0f, 0x663366ff, 5.0f, 10.0f, 0.0f, SymbolLibrary.WATER );

  private Context mContext;
  private Context mPreviousContext;
  private int mPreviousWithLevels;
  private int mPreviousDisplayLevel;

  @Before
  public void setUp()
  {
    mPreviousContext = TDInstance.context;
    mPreviousWithLevels = TDSetting.mWithLevels;
    mPreviousDisplayLevel = DrawingLevel.getDisplayLevel();
    mContext = InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
    TDInstance.setContext( mContext );
    TopoDroidApp.installSymbols( true );
    BrushManager.reloadAreaLibrary( mContext.getResources() );
  }

  @After
  public void tearDown()
  {
    TDSetting.mWithLevels = mPreviousWithLevels;
    DrawingLevel.setDisplayLevel( mPreviousDisplayLevel );
    TDInstance.context = mPreviousContext;
  }

  @Test
  public void bedrockPattern_isWorldAnchoredIrregularAndHardClipped() throws Exception
  {
    DrawingAreaPath first = rectangle( 15.0f, 15.0f, 145.0f, 105.0f );
    DrawingAreaPath second = rectangle( 55.0f, 35.0f, 155.0f, 115.0f );
    Bitmap a = renderTransparent( first );
    Bitmap b = renderTransparent( second );

    // The same world-space overlap must contain the same pixels even though the two
    // independently rendered area bounds start at different coordinates.
    // Stay clear of both independently antialiased clip edges; the pattern itself is
    // identical there too, but clip coverage is intentionally region-shape-dependent.
    int overlapLeft = Math.round( 55.0f * SCALE ) + 8;
    int overlapTop = Math.round( 35.0f * SCALE ) + 8;
    int overlapRight = Math.round( 145.0f * SCALE ) - 8;
    int overlapBottom = Math.round( 105.0f * SCALE ) - 8;
    for ( int y = overlapTop; y < overlapBottom; ++y ) {
      for ( int x = overlapLeft; x < overlapRight; ++x ) {
        // Independently bounded saveLayers can quantize the outer antialias fringe by
        // a few alpha levels, so compare visible ink occupancy rather than exact fringe alpha.
        assertEquals( "Bedrock pixels drifted from the absolute world grid at " + x + "," + y,
                      Color.alpha( a.getPixel( x, y ) ) > 96,
                      Color.alpha( b.getPixel( x, y ) ) > 96 );
      }
    }

    int left = Math.round( 15.0f * SCALE );
    int top = Math.round( 15.0f * SCALE );
    int right = Math.round( 145.0f * SCALE );
    int bottom = Math.round( 105.0f * SCALE );
    int edgeInk = 0;
    for ( int y = 0; y < HEIGHT; ++y ) {
      for ( int x = 0; x < WIDTH; ++x ) {
        int alpha = Color.alpha( a.getPixel( x, y ) );
        if ( x < left - 2 || x > right + 2 || y < top - 2 || y > bottom + 2 ) {
          assertEquals( "Bedrock ink escaped the area clip at " + x + "," + y, 0, alpha );
        }
        if ( alpha > 96 && ( x == left || x == left + 1 || x == right - 1 || x == right ) ) {
          ++edgeInk;
        }
      }
    }
    assertTrue( "Expected bedding lines to be visibly truncated at the left/right area edge", edgeInk > 6 );

    // Horizontal bedding lines cover most of the rectangle width. Their screen-space
    // gaps must not all match: the authored course stamp deliberately varies thickness.
    ArrayList< Integer > rows = new ArrayList<>();
    for ( int y = top + 3; y < bottom - 3; ++y ) {
      int ink = 0;
      for ( int x = left + 3; x < right - 3; ++x ) {
        if ( Color.alpha( a.getPixel( x, y ) ) > 96 ) ++ink;
      }
      if ( ink > ( right - left ) / 2 && ( rows.isEmpty() || y - rows.get( rows.size()-1 ) > 2 ) ) {
        rows.add( y );
      }
    }
    assertTrue( "Too few bedrock courses rendered", rows.size() >= 6 );
    boolean varied = false;
    int firstGap = rows.get( 1 ) - rows.get( 0 );
    for ( int k = 2; k < rows.size(); ++k ) {
      if ( Math.abs( ( rows.get( k ) - rows.get( k-1 ) ) - firstGap ) >= 3 ) {
        varied = true;
        break;
      }
    }
    assertTrue( "Bedrock courses unexpectedly have uniform thickness", varied );

    writeVisualProof();
    a.recycle();
    b.recycle();
  }

  @Test
  public void crosshatch_drawsMirroredFamiliesAsOneOpacityLayer()
  {
    DrawingAreaPath area = rectangle( 18.0f, 16.0f, 142.0f, 104.0f );
    Bitmap crosshatch = renderPattern( SUMP, area );
    Bitmap negative = renderPattern( AreaLinePattern.parallel(
        -35.0f, SUMP.mColor, SUMP.mWidthScale, SUMP.mSpacingScale, 0.0f ), area );
    Bitmap positive = renderPattern( AreaLinePattern.parallel(
         35.0f, SUMP.mColor, SUMP.mWidthScale, SUMP.mSpacingScale, 0.0f ), area );

    int crossOnly = 0;
    int expectedAlpha = Color.alpha( SUMP.mColor );
    for ( int y = 0; y < HEIGHT; ++y ) {
      for ( int x = 0; x < WIDTH; ++x ) {
        int crossAlpha = Color.alpha( crosshatch.getPixel( x, y ) );
        boolean expectedSolidInk = Color.alpha( negative.getPixel( x, y ) ) > 32
                                || Color.alpha( positive.getPixel( x, y ) ) > 32;
        boolean crossSolidInk = crossAlpha > 32;
        if ( expectedSolidInk ) {
          assertTrue( "Crosshatch omitted solid mirrored-stripe ink at " + x + "," + y,
                      crossAlpha > 8 );
        }
        if ( crossSolidInk ) {
          assertTrue( "Crosshatch introduced ink outside both mirrored families at " + x + "," + y,
                      Color.alpha( negative.getPixel( x, y ) ) > 8
                          || Color.alpha( positive.getPixel( x, y ) ) > 8 );
        }
        assertTrue( "Crosshatch intersections accumulated opacity at " + x + "," + y,
                    crossAlpha <= expectedAlpha );
        if ( crossAlpha > 8 && Color.alpha( negative.getPixel( x, y ) ) <= 8 ) ++crossOnly;
      }
    }
    assertTrue( "Mirrored positive-angle stripes were not visible", crossOnly > 100 );
    crosshatch.recycle();
    negative.recycle();
    positive.recycle();
  }

  @Test
  public void overlappingSumpMembers_unionWithoutDarkening()
  {
    DrawingAreaPath area = rectangle( 20.0f, 18.0f, 140.0f, 102.0f );
    Bitmap single = renderPattern( SUMP, area );
    ArrayList< DrawingAreaPath > duplicates = new ArrayList<>();
    duplicates.add( area );
    duplicates.add( rectangle( 20.0f, 18.0f, 140.0f, 102.0f ) );
    Bitmap doubled = renderPattern( SUMP, duplicates );
    assertBitmapsEqual( "Duplicate sump members changed the union render", single, doubled );
    single.recycle();
    doubled.recycle();
  }

  @Test
  public void sumpReplacesWater_independentOfDrawOrderAndStyle()
  {
    int waterType = BrushManager.getAreaIndexByThName( SymbolLibrary.WATER );
    int sumpType = BrushManager.getAreaIndexByThName( SymbolLibrary.SUMP );
    assertTrue( "Missing packaged water area", waterType >= 0 );
    assertTrue( "Missing packaged sump area", sumpType >= 0 );

    DrawingAreaPath water = rectangle( waterType, 8.0f, 8.0f, 152.0f, 112.0f );
    DrawingAreaPath sump = rectangle( sumpType, 48.0f, 28.0f, 122.0f, 92.0f );
    ArrayList< DrawingAreaPath > waterThenSump = commands( water, sump );
    ArrayList< DrawingAreaPath > sumpThenWater = commands( sump, water );
    Bitmap forward = renderCommands( waterThenSump );
    Bitmap reverse = renderCommands( sumpThenWater );
    Bitmap sumpOnly = renderCommands( commands( sump ) );
    Bitmap waterOnly = renderCommands( commands( water ) );

    assertBitmapsEqual( "Sump replacement depends on command order", forward, reverse );
    int inset = 3;
    int left = Math.round( 48.0f * SCALE ) + inset;
    int top = Math.round( 28.0f * SCALE ) + inset;
    int right = Math.round( 122.0f * SCALE ) - inset;
    int bottom = Math.round( 92.0f * SCALE ) - inset;
    for ( int y = 0; y < HEIGHT; ++y ) {
      for ( int x = 0; x < WIDTH; ++x ) {
        if ( x >= left && x < right && y >= top && y < bottom ) {
          assertEquals( "Water remained beneath sump at " + x + "," + y,
                        sumpOnly.getPixel( x, y ), forward.getPixel( x, y ) );
        } else if ( x < left - inset || x >= right + inset || y < top - inset || y >= bottom + inset ) {
          assertEquals( "Water changed outside sump at " + x + "," + y,
                        waterOnly.getPixel( x, y ), forward.getPixel( x, y ) );
        }
      }
    }

    forward.recycle();
    reverse.recycle();
    sumpOnly.recycle();
    waterOnly.recycle();
  }

  @Test
  public void hiddenSump_doesNotMaskVisibleWater()
  {
    int waterType = BrushManager.getAreaIndexByThName( SymbolLibrary.WATER );
    int sumpType = BrushManager.getAreaIndexByThName( SymbolLibrary.SUMP );
    DrawingAreaPath water = rectangle( waterType, 8.0f, 8.0f, 152.0f, 112.0f );
    DrawingAreaPath sump = rectangle( sumpType, 48.0f, 28.0f, 122.0f, 92.0f );
    sump.mLevel = DrawingLevel.LEVEL_CEIL;
    TDSetting.mWithLevels = 2;
    DrawingLevel.setDisplayLevel( DrawingLevel.LEVEL_WATER );

    Bitmap combined = renderCommands( commands( water, sump ) );
    Bitmap expected = renderCommands( commands( water ) );
    assertBitmapsEqual( "A level-hidden sump masked visible water", expected, combined );
    combined.recycle();
    expected.recycle();
  }

  @Test
  public void sumpReplacement_doesNotMaskClayOrBedrock()
  {
    assertUnrelatedPatternSurvives( SymbolLibrary.CLAY );
    assertUnrelatedPatternSurvives( SymbolLibrary.BEDROCK );
  }

  @Test
  public void bedrockOrientation_rotatesPerAreaWithoutAngleBleed()
  {
    int bedrockType = BrushManager.getAreaIndexByThName( SymbolLibrary.BEDROCK );
    assertTrue( "Missing packaged bedrock area", bedrockType >= 0 );
    assertTrue( "Bedrock area should support per-placement orientation",
                BrushManager.isAreaOrientable( bedrockType ) );

    DrawingAreaPath horizontalProbe = rectangle( bedrockType, 20.0f, 20.0f, 140.0f, 100.0f );
    DrawingAreaPath tiltedProbe = rectangle( bedrockType, 20.0f, 20.0f, 140.0f, 100.0f );
    tiltedProbe.setOrientation( 32.0 );
    Bitmap horizontalProbeBitmap = renderCommands( commands( horizontalProbe ) );
    Bitmap tiltedProbeBitmap = renderCommands( commands( tiltedProbe ) );
    assertBitmapsDifferent( "Nonzero bedrock orientation did not rotate the courses",
                            horizontalProbeBitmap, tiltedProbeBitmap, 1000 );
    horizontalProbeBitmap.recycle();
    tiltedProbeBitmap.recycle();

    DrawingAreaPath horizontal = rectangle( bedrockType, 10.0f, 10.0f, 80.0f, 110.0f );
    DrawingAreaPath tilted = rectangle( bedrockType, 80.0f, 10.0f, 150.0f, 110.0f );
    tilted.setOrientation( 32.0 );

    Bitmap horizontalOnly = renderCommands( commands( horizontal ) );
    Bitmap tiltedOnly = renderCommands( commands( tilted ) );
    Bitmap combined = renderCommands( commands( horizontal, tilted ) );

    assertRegionEquals( "Horizontal bedrock changed beside a tilted area",
        horizontalOnly, combined, 12.0f, 12.0f, 78.0f, 108.0f );
    assertRegionEquals( "Tilted bedrock inherited the horizontal area's angle",
        tiltedOnly, combined, 82.0f, 12.0f, 148.0f, 108.0f );

    horizontalOnly.recycle();
    tiltedOnly.recycle();
    combined.recycle();
  }

  @Test
  public void sameAngleBedrockAreas_renderAsOneGroup()
  {
    int bedrockType = BrushManager.getAreaIndexByThName( SymbolLibrary.BEDROCK );
    DrawingAreaPath area = rectangle( bedrockType, 20.0f, 18.0f, 140.0f, 102.0f );
    DrawingAreaPath duplicate = rectangle( bedrockType, 20.0f, 18.0f, 140.0f, 102.0f );
    area.setOrientation( 32.0 );
    duplicate.setOrientation( 32.0 );

    Bitmap single = renderCommands( commands( area ) );
    Bitmap doubled = renderCommands( commands( area, duplicate ) );
    assertBitmapsEqual( "Same-angle bedrock areas were painted as separate groups", single, doubled );
    single.recycle();
    doubled.recycle();
  }

  private static Bitmap renderTransparent( DrawingAreaPath area )
  {
    Bitmap bitmap = Bitmap.createBitmap( WIDTH, HEIGHT, Bitmap.Config.ARGB_8888 );
    bitmap.eraseColor( Color.TRANSPARENT );
    draw( new Canvas( bitmap ), area, new RectF( 0.0f, 0.0f, WIDTH / SCALE, HEIGHT / SCALE ) );
    return bitmap;
  }

  private static Bitmap renderPattern( AreaLinePattern pattern, DrawingAreaPath area )
  {
    ArrayList< DrawingAreaPath > members = new ArrayList<>();
    members.add( area );
    return renderPattern( pattern, members );
  }

  private static Bitmap renderPattern( AreaLinePattern pattern, ArrayList< DrawingAreaPath > members )
  {
    Bitmap bitmap = Bitmap.createBitmap( WIDTH, HEIGHT, Bitmap.Config.ARGB_8888 );
    bitmap.eraseColor( Color.TRANSPARENT );
    Matrix matrix = new Matrix();
    matrix.setScale( SCALE, SCALE );
    AreaPatternRenderer.drawGroup( new Canvas( bitmap ), matrix,
        new RectF( 0.0f, 0.0f, WIDTH / SCALE, HEIGHT / SCALE ),
        pattern, members, false, 1.0f, pattern.mColor );
    return bitmap;
  }

  private static Bitmap renderCommands( ArrayList< DrawingAreaPath > commands )
  {
    Bitmap bitmap = Bitmap.createBitmap( WIDTH, HEIGHT, Bitmap.Config.ARGB_8888 );
    bitmap.eraseColor( Color.TRANSPARENT );
    Matrix matrix = new Matrix();
    matrix.setScale( SCALE, SCALE );
    Scrap.drawPatternedAreaGroups( new Canvas( bitmap ), matrix,
        new RectF( 0.0f, 0.0f, WIDTH / SCALE, HEIGHT / SCALE ), false, commands );
    return bitmap;
  }

  private static ArrayList< DrawingAreaPath > commands( DrawingAreaPath... areas )
  {
    ArrayList< DrawingAreaPath > result = new ArrayList<>();
    for ( DrawingAreaPath area : areas ) result.add( area );
    return result;
  }

  private static void assertBitmapsEqual( String message, Bitmap expected, Bitmap actual )
  {
    assertEquals( message + " width", expected.getWidth(), actual.getWidth() );
    assertEquals( message + " height", expected.getHeight(), actual.getHeight() );
    for ( int y = 0; y < expected.getHeight(); ++y ) {
      for ( int x = 0; x < expected.getWidth(); ++x ) {
        assertEquals( message + " at " + x + "," + y,
                      expected.getPixel( x, y ), actual.getPixel( x, y ) );
      }
    }
  }

  private static void assertRegionEquals( String message, Bitmap expected, Bitmap actual,
                                          float left, float top, float right, float bottom )
  {
    int x0 = Math.round( left * SCALE );
    int y0 = Math.round( top * SCALE );
    int x1 = Math.round( right * SCALE );
    int y1 = Math.round( bottom * SCALE );
    for ( int y = y0; y < y1; ++y ) {
      for ( int x = x0; x < x1; ++x ) {
        assertEquals( message + " at " + x + "," + y,
                      expected.getPixel( x, y ), actual.getPixel( x, y ) );
      }
    }
  }

  private static void assertBitmapsDifferent( String message, Bitmap first, Bitmap second, int minimum )
  {
    int differences = 0;
    for ( int y = 0; y < first.getHeight(); ++y ) {
      for ( int x = 0; x < first.getWidth(); ++x ) {
        if ( first.getPixel( x, y ) != second.getPixel( x, y ) ) ++differences;
      }
    }
    assertTrue( message + ": only " + differences + " pixels changed", differences >= minimum );
  }

  private static void assertUnrelatedPatternSurvives( String thName )
  {
    int sumpType = BrushManager.getAreaIndexByThName( SymbolLibrary.SUMP );
    int otherType = BrushManager.getAreaIndexByThName( thName );
    assertTrue( "Missing sump area", sumpType >= 0 );
    assertTrue( "Missing unrelated area " + thName, otherType >= 0 );
    DrawingAreaPath sump = rectangle( sumpType, 42.0f, 24.0f, 126.0f, 96.0f );
    DrawingAreaPath other = rectangle( otherType, 8.0f, 8.0f, 152.0f, 112.0f );
    Bitmap sumpOnly = renderCommands( commands( sump ) );
    Bitmap otherOnly = renderCommands( commands( other ) );
    Bitmap combined = renderCommands( commands( sump, other ) );

    int preserved = 0;
    int left = Math.round( 42.0f * SCALE ) + 8;
    int top = Math.round( 24.0f * SCALE ) + 8;
    int right = Math.round( 126.0f * SCALE ) - 8;
    int bottom = Math.round( 96.0f * SCALE ) - 8;
    for ( int y = top; y < bottom; ++y ) {
      for ( int x = left; x < right; ++x ) {
        if ( Color.alpha( sumpOnly.getPixel( x, y ) ) == 0
            && Color.alpha( otherOnly.getPixel( x, y ) ) > 64 ) {
          assertEquals( thName + " was masked by sump at " + x + "," + y,
                        otherOnly.getPixel( x, y ), combined.getPixel( x, y ) );
          ++preserved;
        }
      }
    }
    assertTrue( "No preserved " + thName + " samples were found inside sump", preserved > 20 );
    sumpOnly.recycle();
    otherOnly.recycle();
    combined.recycle();
  }

  private static void draw( Canvas canvas, DrawingAreaPath area, RectF bbox )
  {
    Matrix matrix = new Matrix();
    matrix.setScale( SCALE, SCALE );
    ArrayList< DrawingAreaPath > members = new ArrayList<>();
    members.add( area );
    AreaPatternRenderer.drawGroup( canvas, matrix, bbox, BEDROCK, members, false, 1.0f, BEDROCK.mColor );
  }

  private static DrawingAreaPath rectangle( float left, float top, float right, float bottom )
  {
    return rectangle( 0, left, top, right, bottom );
  }

  private static DrawingAreaPath rectangle( int type, float left, float top, float right, float bottom )
  {
    DrawingAreaPath area = new DrawingAreaPath( type, 1, "area-pattern-test", true, 0 );
    area.addStartPoint( left, top );
    area.addPoint( right, top );
    area.addPoint( right, bottom );
    area.addPoint( left, bottom );
    area.closePath();
    return area;
  }

  private static void writeVisualProof() throws Exception
  {
    Bitmap bitmap = Bitmap.createBitmap( 800, 600, Bitmap.Config.ARGB_8888 );
    bitmap.eraseColor( Color.WHITE );
    DrawingAreaPath area = new DrawingAreaPath( 0, 1, "bedrock-proof", true, 0 );
    area.addStartPoint( 22.0f, 18.0f );
    area.addPoint( 176.0f, 27.0f );
    area.addPoint( 186.0f, 64.0f );
    area.addPoint( 165.0f, 126.0f );
    area.addPoint( 106.0f, 142.0f );
    area.addPoint( 37.0f, 131.0f );
    area.addPoint( 13.0f, 82.0f );
    area.closePath();
    draw( new Canvas( bitmap ), area, new RectF( 0.0f, 0.0f, 200.0f, 150.0f ) );

    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    File dir = context.getExternalFilesDir( "test-artifacts" );
    assertTrue( "No external artifact directory", dir != null );
    assertTrue( "Could not create external artifact directory", dir.exists() || dir.mkdirs() );
    File file = new File( dir, "bedrock-area-pattern.png" );
    FileOutputStream output = new FileOutputStream( file );
    try {
      assertTrue( "Could not encode bedrock visual proof", bitmap.compress( Bitmap.CompressFormat.PNG, 100, output ) );
    } finally {
      output.close();
      bitmap.recycle();
    }
  }
}
