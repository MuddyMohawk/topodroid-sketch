package com.topodroid.TDX;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
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
import com.topodroid.types.PointScale;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@RunWith( AndroidJUnit4.class )
public class SketchTextObjectInstrumentedTest
{
  private static final int TDR_VERSION = 602055;

  private Context mPreviousContext;
  private float mPreviousGrid;
  private float mPreviousLineThickness;
  private int mPreviousLevels;

  @Before
  public void setUp()
  {
    mPreviousContext = TDInstance.context;
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
    TDInstance.setContext( context );
    TopoDroidApp.installSymbols( true );
    BrushManager.reloadPointLibrary( context, context.getResources() );
    mPreviousGrid = TDSetting.mUnitGrid;
    mPreviousLineThickness = TDSetting.mLineThickness;
    TDSetting.mLineThickness = 1.0f;
    mPreviousLevels = TDSetting.mWithLevels;
    TDSetting.mWithLevels = 0;
  }

  @After
  public void tearDown()
  {
    TDSetting.mUnitGrid = mPreviousGrid;
    TDSetting.mLineThickness = mPreviousLineThickness;
    TDSetting.mWithLevels = mPreviousLevels;
    TDInstance.context = mPreviousContext;
  }

  @Test
  public void codecAndTdrRoundTrip_preserveCompleteStyleAndPublicOptions() throws Exception
  {
    SketchTextStyle style = SketchTextStyle.of(
      SketchFontRegistry.FONT_ARCHITECTS_DAUGHTER,
      SketchTextStyle.SizeMode.WORLD,
      0.75f,
      5.5f,
      true,
      true,
      true,
      SketchTextStyle.Alignment.RIGHT,
      0x8044ccff );
    String options = SketchTextStyleCodec.storeInOptions( "-id styled-label", style );
    DrawingLabelPath original = new DrawingLabelPath(
      "Upper\nLower", 12.0f, 24.0f, PointScale.SCALE_L, options, 3 );
    original.setOrientation( 37.0 );

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    DataOutputStream output = new DataOutputStream( bytes );
    original.toDataStream( output, 3 );
    output.flush();

    DataInputStream input = new DataInputStream( new ByteArrayInputStream( bytes.toByteArray() ) );
    assertEquals( 'T', input.read() );
    DrawingLabelPath loaded = DrawingLabelPath.loadDataStream( TDR_VERSION, input, 0.0f, 0.0f );
    assertNotNull( loaded );
    assertTrue( loaded.hasExplicitTextStyle() );
    assertEquals( style, loaded.getTextStyle() );
    assertEquals( "Upper\nLower", loaded.getPointText() );
    assertTrue( loaded.getOptions().contains( "-id styled-label" ) );
    assertTrue( loaded.getOptions().contains( "-tdx-text" ) );
  }

  @Test
  public void lineWeight_isIndependentFromBoldAndUsesPointLikeFootprintScale()
  {
    SketchTextStyle thin_style = SketchTextStyle.of(
      SketchFontRegistry.FONT_DEFAULT, SketchTextStyle.SizeMode.WORLD, 1.0f, 1.0f,
      false, false, false, SketchTextStyle.Alignment.LEFT, Color.WHITE );
    SketchTextStyle standard_style = thin_style.withLineWeight( 2.0f );
    SketchTextStyle thick_style = thin_style.withLineWeight( 5.0f );
    SketchTextStyle bold_thin_style = thin_style.withEmphasis( true, false, false );
    assertEquals( 1.0f, bold_thin_style.lineWeight(), 0.001f );
    assertFalse( thick_style.bold() );

    String encoded = SketchTextStyleCodec.encode( thick_style );
    assertTrue( encoded.contains( "w=5.0000" ) );
    assertEquals( 5.0f, SketchTextStyleCodec.decode( encoded ).lineWeight(), 0.001f );

    DrawingLabelPath thin = new DrawingLabelPath(
      "Weight", 8.0f, 28.0f, PointScale.SCALE_M, null, 0, thin_style );
    DrawingLabelPath thick = new DrawingLabelPath(
      "Weight", 8.0f, 28.0f, PointScale.SCALE_M, null, 0, thick_style );
    assertEquals( 0.5f * DrawingUtil.SCALE_FIX, thin.lineHeightScene( 1.0f ), 0.001f );
    assertEquals( DrawingUtil.SCALE_FIX,
                  labelWithStyle( standard_style ).lineHeightScene( 1.0f ), 0.001f );
    assertEquals( 2.5f * DrawingUtil.SCALE_FIX, thick.lineHeightScene( 1.0f ), 0.001f );
    assertTrue( thick.textBoundsScene( 1.0f ).width() > thin.textBoundsScene( 1.0f ).width() );
    DrawingLabelPath screen_standard = labelWithStyle( SketchTextStyle.of(
      SketchFontRegistry.FONT_DEFAULT, SketchTextStyle.SizeMode.SCREEN, 24.0f, 2.0f,
      false, false, false, SketchTextStyle.Alignment.LEFT, Color.WHITE ) );
    DrawingLabelPath screen_thick = labelWithStyle( SketchTextStyle.of(
      SketchFontRegistry.FONT_DEFAULT, SketchTextStyle.SizeMode.SCREEN, 24.0f, 5.0f,
      false, false, false, SketchTextStyle.Alignment.LEFT, Color.WHITE ) );
    assertEquals( 2.5f,
                  screen_thick.getTextExportLineHeightScene()
                    / screen_standard.getTextExportLineHeightScene(),
                  0.001f );

    Matrix matrix = new Matrix();
    matrix.setScale( 4.0f, 4.0f );
    RectF bbox = new RectF( -100.0f, -100.0f, 800.0f, 500.0f );
    Bitmap thin_bitmap = Bitmap.createBitmap( 800, 400, Bitmap.Config.ARGB_8888 );
    Bitmap thick_bitmap = Bitmap.createBitmap( 800, 400, Bitmap.Config.ARGB_8888 );
    thin.draw( new Canvas( thin_bitmap ), matrix, 0.25f, bbox );
    thick.draw( new Canvas( thick_bitmap ), matrix, 0.25f, bbox );
    assertTrue( countOpaquePixels( thick_bitmap ) > countOpaquePixels( thin_bitmap ) );

    SketchTextStyle equivalent_thick_style = SketchTextStyle.of(
      SketchFontRegistry.FONT_DEFAULT, SketchTextStyle.SizeMode.WORLD, 0.4f, 5.0f,
      false, false, false, SketchTextStyle.Alignment.LEFT, Color.WHITE );
    DrawingLabelPath standard = new DrawingLabelPath(
      "Weight", 8.0f, 28.0f, PointScale.SCALE_M, null, 0, standard_style );
    DrawingLabelPath equivalent_thick = new DrawingLabelPath(
      "Weight", 8.0f, 28.0f, PointScale.SCALE_M, null, 0, equivalent_thick_style );
    Bitmap standard_bitmap = Bitmap.createBitmap( 800, 400, Bitmap.Config.ARGB_8888 );
    Bitmap equivalent_bitmap = Bitmap.createBitmap( 800, 400, Bitmap.Config.ARGB_8888 );
    standard.draw( new Canvas( standard_bitmap ), matrix, 0.25f, bbox );
    equivalent_thick.draw( new Canvas( equivalent_bitmap ), matrix, 0.25f, bbox );
    assertTrue( "line weight added an outline instead of only scaling the text",
                standard_bitmap.sameAs( equivalent_bitmap ) );

    thin_bitmap.recycle();
    thick_bitmap.recycle();
    standard_bitmap.recycle();
    equivalent_bitmap.recycle();
  }

  @Test
  public void legacyContentEdit_doesNotMigrateUntilFormattingChanges()
  {
    DrawingLabelPath legacy = new DrawingLabelPath(
      "old", 0.0f, 0.0f, PointScale.SCALE_M, "-id legacy-label", 0 );
    assertFalse( legacy.hasExplicitTextStyle() );

    SketchTextStyle compatibility = legacy.getTextStyleForEditor();
    legacy.applyTextEdit( "new wording", compatibility, false, 15.0,
                          DrawingLevel.LEVEL_DEFAULT, "-id legacy-label" );
    assertFalse( legacy.hasExplicitTextStyle() );
    assertFalse( legacy.getOptions().contains( "-tdx-text" ) );

    SketchTextStyle formatted = compatibility.withEmphasis( true, false, false );
    legacy.applyTextEdit( "new wording", formatted, true, 15.0,
                          DrawingLevel.LEVEL_DEFAULT, "-id legacy-label" );
    assertTrue( legacy.hasExplicitTextStyle() );
    assertTrue( legacy.getOptions().contains( "-tdx-text" ) );
  }

  @Test
  public void sizeModes_obeyGridWorldAndScreenInvariants()
  {
    TDSetting.mUnitGrid = 0.6096f;
    DrawingLabelPath automatic = labelWithStyle( SketchTextStyle.of(
      SketchFontRegistry.FONT_DEFAULT, SketchTextStyle.SizeMode.AUTO_GRID, 1.0f,
      false, false, false, SketchTextStyle.Alignment.LEFT, Color.WHITE ) );
    DrawingLabelPath world = labelWithStyle( SketchTextStyle.of(
      SketchFontRegistry.FONT_DEFAULT, SketchTextStyle.SizeMode.WORLD, 1.25f,
      false, false, false, SketchTextStyle.Alignment.LEFT, Color.WHITE ) );
    DrawingLabelPath screen = labelWithStyle( SketchTextStyle.of(
      SketchFontRegistry.FONT_DEFAULT, SketchTextStyle.SizeMode.SCREEN, 24.0f,
      false, false, false, SketchTextStyle.Alignment.LEFT, Color.WHITE ) );

    assertEquals( TDSetting.mUnitGrid * DrawingUtil.SCALE_FIX,
                  automatic.lineHeightScene( 0.25f ), 0.001f );
    assertEquals( 1.25f * DrawingUtil.SCALE_FIX,
                  world.lineHeightScene( 4.0f ), 0.001f );
    assertEquals( 24.0f * TopoDroidApp.getDisplayDensity() * 0.5f,
                  screen.lineHeightScene( 0.5f ), 0.001f );
  }

  @Test
  public void foregroundSnapshot_drawsTextAboveLaterCommandsAndTracksUndoRedo()
  {
    Scrap scrap = new Scrap( 0, "text-z-order" );
    DrawingLabelPath label = new DrawingLabelPath(
      "FRONT", 18.0f, 80.0f, PointScale.SCALE_M, null, 0,
      SketchTextStyle.of( SketchFontRegistry.FONT_DEFAULT, SketchTextStyle.SizeMode.SCREEN, 48.0f,
                          true, false, false, SketchTextStyle.Alignment.LEFT, 0xffff2020 ) );
    label.setOrientation( 37.0 );
    scrap.addCommand( label );
    scrap.addCommand( new CoverPath() );

    Bitmap bitmap = Bitmap.createBitmap( 320, 140, Bitmap.Config.ARGB_8888 );
    Canvas canvas = new Canvas( bitmap );
    RectF bbox = new RectF( 0.0f, 0.0f, 320.0f, 140.0f );
    scrap.drawAll( canvas, new Matrix(), 1.0f, bbox, false );
    assertEquals( 0, countRedPixels( bitmap ) );
    scrap.drawTextOverlays( canvas, new Matrix(), 1.0f, bbox, false );
    assertTrue( "foreground pass did not restore text over later cave content",
                countRedPixels( bitmap ) > 100 );

    RectF text_bounds = label.textBoundsScene( 1.0f );
    float hit_x = text_bounds.centerX();
    float hit_y = text_bounds.centerY();
    SelectionSet selected = scrap.getItemsAt(
      hit_x, hit_y, 1.0f, Drawing.FILTER_POINT,
      false, false, false, null, new Selection(), 1.0f );
    assertSame( label, selected.mHotItem.mItem );

    scrap.undo(); // cover
    scrap.undo(); // label
    SelectionSet after_undo = scrap.getItemsAt(
      hit_x, hit_y, 1.0f, Drawing.FILTER_POINT,
      false, false, false, null, new Selection(), 1.0f );
    assertEquals( 0, after_undo.size() );
    scrap.redo();
    SelectionSet after_redo = scrap.getItemsAt(
      hit_x, hit_y, 1.0f, Drawing.FILTER_POINT,
      false, false, false, null, new Selection(), 1.0f );
    assertSame( label, after_redo.mHotItem.mItem );
    bitmap.recycle();
  }

  @Test
  public void boundsSelectedText_dragMovesAndRebuckets()
  {
    Scrap scrap = new Scrap( 0, "text-drag" );
    DrawingLabelPath label = new DrawingLabelPath(
      "MOVE ME", 40.0f, 55.0f, PointScale.SCALE_M, null, 0,
      SketchTextStyle.of( SketchFontRegistry.FONT_DEFAULT, SketchTextStyle.SizeMode.SCREEN, 32.0f,
                          false, false, false, SketchTextStyle.Alignment.LEFT, Color.WHITE ) );
    scrap.addCommand( label );

    RectF original_bounds = label.textBoundsScene( 1.0f );
    SelectionSet selected = scrap.getItemsAt(
      original_bounds.centerX(), original_bounds.centerY(), 1.0f, Drawing.FILTER_POINT,
      false, false, false, null, new Selection(), 1.0f );
    assertSame( label, selected.mHotItem.mItem );

    float original_x = label.cx;
    float original_y = label.cy;
    scrap.shiftHotItem( 17.0f, -9.0f,
                        java.util.Collections.< DrawingOutlinePath >emptyList(), new Selection() );
    assertEquals( original_x + 17.0f, label.cx, 0.001f );
    assertEquals( original_y - 9.0f, label.cy, 0.001f );

    RectF moved_bounds = label.textBoundsScene( 1.0f );
    SelectionSet moved_selection = scrap.getItemsAt(
      moved_bounds.centerX(), moved_bounds.centerY(), 1.0f, Drawing.FILTER_POINT,
      false, false, false, null, new Selection(), 1.0f );
    assertSame( label, moved_selection.mHotItem.mItem );
  }

  @Test
  public void concurrentRendering_usesImmutableLayoutAndCallLocalPaint() throws Exception
  {
    final DrawingLabelPath label = new DrawingLabelPath(
      "Concurrent\nArchitects", 12.0f, 48.0f, PointScale.SCALE_M, null, 0,
      SketchTextStyle.of(
        SketchFontRegistry.FONT_ARCHITECTS_DAUGHTER,
        SketchTextStyle.SizeMode.WORLD,
        0.35f,
        true,
        true,
        true,
        SketchTextStyle.Alignment.CENTER,
        0xfff4d35e ) );
    label.setOrientation( 23.0 );

    ExecutorService pool = Executors.newFixedThreadPool( 4 );
    try {
      List< Callable< Integer > > calls = new ArrayList<>();
      for ( int thread = 0; thread < 4; ++thread ) {
        final int thread_index = thread;
        calls.add( () -> {
          int drawn = 0;
          for ( int iteration = 0; iteration < 20; ++iteration ) {
            Bitmap bitmap = Bitmap.createBitmap( 180, 100, Bitmap.Config.ARGB_8888 );
            Canvas canvas = new Canvas( bitmap );
            Matrix matrix = new Matrix();
            matrix.setScale( 0.8f + 0.1f * thread_index, 0.8f + 0.1f * thread_index );
            label.draw( canvas, matrix, 1.0f, new RectF( -100.0f, -100.0f, 300.0f, 200.0f ) );
            drawn += countOpaquePixels( bitmap );
            RectF bounds = label.textBoundsScene( 1.0f );
            assertTrue( label.hitText( bounds.centerX(), bounds.centerY(), 1.0f, 0.0f ) );
            bitmap.recycle();
          }
          return drawn;
        } );
      }
      List< Future< Integer > > results = pool.invokeAll( calls );
      for ( Future< Integer > result : results ) assertTrue( result.get() > 0 );
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  public void fontUtfAndExports_applyRequestedCompatibilityContract() throws Exception
  {
    try ( InputStream font = TDInstance.context.getAssets().open( "fonts/ArchitectsDaughter-Regular.ttf" ) ) {
      assertTrue( font.available() > 40000 );
    }
    SketchFontRegistry.ResolvedFont resolved = SketchFontRegistry.resolve(
      SketchFontRegistry.FONT_ARCHITECTS_DAUGHTER, true, true );
    assertNotNull( resolved.typeface );
    assertTrue( resolved.fakeBold );
    assertTrue( resolved.skewX < 0.0f );

    assertTrue( SketchTextInput.fitsModifiedUtf( repeat( 'a', 65535 ) ) );
    assertFalse( SketchTextInput.fitsModifiedUtf( repeat( 'a', 65536 ) ) );
    assertEquals( 2, SketchTextInput.modifiedUtfLength( "\u0000" ) );

    SketchTextStyle style = SketchTextStyle.of(
      SketchFontRegistry.FONT_ARCHITECTS_DAUGHTER, SketchTextStyle.SizeMode.AUTO_GRID, 1.0f,
      true, true, true, SketchTextStyle.Alignment.CENTER, 0x8044ccff );
    DrawingLabelPath label = new DrawingLabelPath(
      "A & \"B\"\n<C>", 4.0f, 8.0f, PointScale.SCALE_M,
      "-id export -tdx-brush w=5", 0, style );

    String therion = label.toTherion();
    assertFalse( therion.contains( "-tdx-text" ) );
    assertFalse( therion.contains( "-tdx-brush" ) );
    assertTrue( therion.contains( "<br>" ) );
    assertTrue( therion.contains( "<bf>" ) );
    assertTrue( therion.contains( "<it>" ) );
    assertTrue( therion.contains( "-align c" ) );

    StringWriter xml = new StringWriter();
    PrintWriter printer = new PrintWriter( xml );
    label.toTCsurvey( printer, "survey", "cave", "branch", null );
    printer.flush();
    assertFalse( xml.toString().contains( "-tdx-text" ) );
    assertFalse( xml.toString().contains( "-tdx-brush" ) );
    assertTrue( xml.toString().contains( "&amp;" ) );
    assertTrue( xml.toString().contains( "&quot;" ) );
    assertTrue( xml.toString().contains( "&#10;" ) );

    SketchTextStyle future = SketchTextStyleCodec.decode(
      "v=9,f=default,m=g,h=1.0000,b=0,i=0,u=0,a=l,c=ffffffff,x=future" );
    assertNotNull( future );
    assertEquals( SketchTextStyle.DEFAULT_LINE_WEIGHT, future.lineWeight(), 0.001f );
    assertTrue( SketchTextStyleCodec.encode( future ).contains( "x=future" ) );
  }

  @Test
  public void dxfAndXviExports_mapMultilineSizeAlignmentRotationAndColor() throws Exception
  {
    TDSetting.mUnitGrid = 0.6096f;
    SketchTextStyle style = SketchTextStyle.of(
      SketchFontRegistry.FONT_ARCHITECTS_DAUGHTER,
      SketchTextStyle.SizeMode.AUTO_GRID,
      1.0f,
      true,
      true,
      true,
      SketchTextStyle.Alignment.CENTER,
      0xff44ccff );
    DrawingLabelPath label = new DrawingLabelPath(
      "A\nB", 3.0f, 7.0f, PointScale.SCALE_M, null, 0, style );
    label.setOrientation( 30.0 );

    Class<?> drawing_dxf = Class.forName( "com.topodroid.io.dxf.DrawingDxf" );
    Method print_label = drawing_dxf.getDeclaredMethod(
      "printLabel",
      PrintWriter.class, int.class, int.class, DrawingLabelPath.class,
      float.class, float.class, float.class, float.class, float.class,
      String.class, float.class, float.class, float.class, int.class );
    print_label.setAccessible( true );
    StringWriter dxf_text = new StringWriter();
    PrintWriter dxf_writer = new PrintWriter( dxf_text );
    print_label.invoke(
      null, dxf_writer, 1, 0, label,
      3.0f, -7.0f, 330.0f, 0.4f, 1.0f / DrawingUtil.SCALE_FIX,
      "POINT", 0.0f, 0.0f, 0.0f, 0 );
    dxf_writer.flush();
    String dxf = dxf_text.toString();
    assertEquals( 2, occurrences( dxf, "\nTEXT" ) );
    assertTrue( dxf.contains( "  72" ) );
    assertTrue( dxf.contains( "330.00" ) );
    assertTrue( dxf.contains( "A" ) );
    assertTrue( dxf.contains( "B" ) );

    Method to_xvi = DrawingXvi.class.getDeclaredMethod(
      "toXvi", PrintWriter.class, DrawingPointPath.class, float.class, float.class );
    to_xvi.setAccessible( true );
    StringWriter xvi_text = new StringWriter();
    PrintWriter xvi_writer = new PrintWriter( xvi_text );
    to_xvi.invoke( null, xvi_writer, label, 0.0f, 0.0f );
    xvi_writer.flush();
    String xvi = xvi_text.toString();
    assertTrue( xvi.contains( "#44CCFF" ) );
    assertTrue( occurrences( xvi, "{ #44CCFF" ) >= 6 );
  }

  private DrawingLabelPath labelWithStyle( SketchTextStyle style )
  {
    return new DrawingLabelPath( "probe", 0.0f, 0.0f, PointScale.SCALE_M, null, 0, style );
  }

  private static int countRedPixels( Bitmap bitmap )
  {
    int count = 0;
    for ( int y = 0; y < bitmap.getHeight(); ++y ) {
      for ( int x = 0; x < bitmap.getWidth(); ++x ) {
        int color = bitmap.getPixel( x, y );
        if ( Color.red( color ) > Color.blue( color ) + 60
            && Color.red( color ) > Color.green( color ) + 60 ) ++count;
      }
    }
    return count;
  }

  private static int countOpaquePixels( Bitmap bitmap )
  {
    int count = 0;
    for ( int y = 0; y < bitmap.getHeight(); ++y ) {
      for ( int x = 0; x < bitmap.getWidth(); ++x ) {
        if ( Color.alpha( bitmap.getPixel( x, y ) ) > 0 ) ++count;
      }
    }
    return count;
  }

  private static String repeat( char ch, int count )
  {
    StringBuilder builder = new StringBuilder( count );
    for ( int i = 0; i < count; ++i ) builder.append( ch );
    return builder.toString();
  }

  private static int occurrences( String text, String value )
  {
    int count = 0;
    int index = 0;
    while ( ( index = text.indexOf( value, index ) ) >= 0 ) {
      ++count;
      index += value.length();
    }
    return count;
  }

  private static class CoverPath extends DrawingPath
  {
    CoverPath()
    {
      super( DrawingPath.DRAWING_PATH_POINT, null, 0 );
      setBBox( 0.0f, 320.0f, 0.0f, 140.0f );
      mLevel = DrawingLevel.LEVEL_DEFAULT;
    }

    @Override
    public void draw( Canvas canvas, Matrix matrix, float scale, RectF bbox )
    {
      canvas.drawColor( 0xff2040ff );
    }

    @Override
    public void draw( Canvas canvas, Matrix matrix, float scale, RectF bbox, int xor_color )
    {
      canvas.drawColor( 0xff2040ff );
    }
  }
}
