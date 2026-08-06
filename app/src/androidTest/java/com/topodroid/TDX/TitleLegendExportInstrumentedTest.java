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
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.topodroid.prefs.TDPrefHelper;
import com.topodroid.prefs.TDSetting;
import com.topodroid.types.PointScale;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;

@RunWith( AndroidJUnit4.class )
public class TitleLegendExportInstrumentedTest
{
  private static final int BACKGROUND = 0xff202124;

  private Context mContext;
  private Context mPreviousContext;

  @Before public void setUp()
  {
    mPreviousContext = TDInstance.context;
    mContext = InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
    TDInstance.setContext( mContext );
    TDSetting.loadSecondaryPreferences( new TDPrefHelper( mContext ) );
    TDPath.clearSymbols();
    TopoDroidApp.installSymbols( true );
    BrushManager.reloadPointLibrary( mContext, mContext.getResources() );
    BrushManager.reloadLineLibrary( mContext.getResources() );
    BrushManager.reloadAreaLibrary( mContext.getResources() );
  }

  @After public void tearDown()
  {
    TDInstance.context = mPreviousContext;
  }

  @Test public void mixedLegend_rendersThreeTwoTwoAndWritesVisualArtifact() throws Exception
  {
    ArrayList< TitleLegendPointState.Row > rows = new ArrayList<>();
    rows.add( installedRow( TitleLegendPointState.Kind.POINT, "stalactite" ) );
    rows.add( installedRow( TitleLegendPointState.Kind.POINT, "pit-depth" ).withPreview(
      new TitleLegendPointState.Preview( TitleLegendPointState.WeightMode.STANDARD,
        1.0f, 1.0f, 0.0f, "12", null ) ) );
    rows.add( installedRow( TitleLegendPointState.Kind.POINT, "gypsum-flower" ).withPreview(
      new TitleLegendPointState.Preview( TitleLegendPointState.WeightMode.STANDARD,
        1.0f, 1.15f, 25.0f, null, null ) ) );
    rows.add( installedRow( TitleLegendPointState.Kind.LINE, "wall" ).withPreview(
      new TitleLegendPointState.Preview( TitleLegendPointState.WeightMode.CUSTOM,
        1.7f, 1.0f, 0.0f, null, null ) ) );
    rows.add( installedRow( TitleLegendPointState.Kind.AREA, "water" ) );
    rows.add( TitleLegendPointState.Row.custom().withLabel( "Hand-drawn formation" ) );
    rows.add( new TitleLegendPointState.Row( "fixture-unresolved", TitleLegendPointState.Kind.POINT,
      "u:missing-legend-fixture", "Legacy local symbol", null,
      TitleLegendPointState.Preview.standard() ) );

    int type = BrushManager.getPointIndexByThName( TitleLegendPointBehavior.THERION_NAME );
    assertTrue( type >= 0 );
    DrawingSemanticPointPath point = (DrawingSemanticPointPath)DrawingPointFactory.createPlacement(
      type, 30.0f, 30.0f, PointScale.SCALE_M, 0 );
    TitleLegendPointState state = new TitleLegendPointState( "mixed-render-fixture", false, true,
      2, 3, SketchTextStyle.defaultStyle(), rows, Collections.< String >emptySet() );
    point.setSpecialState( state, true );

    TitleLegendLayout layout = (TitleLegendLayout)point.preparedSpecialState();
    assertNotNull( layout );
    assertEquals( 3, layout.capacity.renderedColumns );
    assertEquals( 3, layout.capacity.rowsInColumn( 0 ) );
    assertEquals( 2, layout.capacity.rowsInColumn( 1 ) );
    assertEquals( 2, layout.capacity.rowsInColumn( 2 ) );

    RectF bounds = point.exactSpecialBounds( false );
    int width = Math.max( 1, (int)Math.ceil( bounds.right + 30.0f ) );
    int height = Math.max( 1, (int)Math.ceil( bounds.bottom + 30.0f ) );
    Bitmap bitmap = Bitmap.createBitmap( width, height, Bitmap.Config.ARGB_8888 );
    Canvas canvas = new Canvas( bitmap );
    canvas.drawColor( BACKGROUND );
    point.draw( canvas, new Matrix(), 1.0f, new RectF( 0.0f, 0.0f, width, height ) );
    assertTrue( countNonWhite( bitmap ) > 1000 );

    File directory = mContext.getExternalFilesDir( "test-artifacts" );
    assertNotNull( directory );
    assertTrue( directory.exists() || directory.mkdirs() );
    File artifact = new File( directory, "title_legend_mixed_render.png" );
    FileOutputStream output = new FileOutputStream( artifact, false );
    assertTrue( bitmap.compress( Bitmap.CompressFormat.PNG, 100, output ) );
    output.close();
    bitmap.recycle();
    assertTrue( artifact.isFile() && artifact.length() > 1000 );
  }

  @Test public void pdfCanvasPath_rasterizesCompleteLegendWithInvertedInk() throws Exception
  {
    ArrayList< TitleLegendPointState.Row > rows = new ArrayList<>();
    rows.add( installedRow( TitleLegendPointState.Kind.POINT, "stalactite" ) );
    rows.add( installedRow( TitleLegendPointState.Kind.LINE, "wall" ) );
    rows.add( installedRow( TitleLegendPointState.Kind.AREA, "water" ) );
    int type = BrushManager.getPointIndexByThName( TitleLegendPointBehavior.THERION_NAME );
    DrawingSemanticPointPath point = (DrawingSemanticPointPath)DrawingPointFactory.createPlacement(
      type, 24.0f, 24.0f, PointScale.SCALE_M, 0 );
    point.setSpecialState( new TitleLegendPointState( "pdf-canvas-fixture", false, true,
      1, 3, SketchTextStyle.defaultStyle(), rows, Collections.< String >emptySet() ), true );
    RectF bounds = point.exactSpecialBounds( false );
    int width = Math.max( 240, (int)Math.ceil( bounds.right + 24.0f ) );
    int height = Math.max( 180, (int)Math.ceil( bounds.bottom + 24.0f ) );

    File directory = mContext.getExternalFilesDir( "test-artifacts" );
    assertNotNull( directory );
    assertTrue( directory.exists() || directory.mkdirs() );
    File artifact = new File( directory, "title_legend_canvas_parity.pdf" );
    PdfDocument document = new PdfDocument();
    PdfDocument.Page page = document.startPage(
      new PdfDocument.PageInfo.Builder( width, height, 1 ).create() );
    page.getCanvas().drawColor( Color.WHITE );
    point.draw( page.getCanvas(), new Matrix(), 1.0f,
      new RectF( 0.0f, 0.0f, width, height ), 1 );
    document.finishPage( page );
    FileOutputStream output = new FileOutputStream( artifact, false );
    document.writeTo( output );
    output.close();
    document.close();
    assertTrue( artifact.length() > 1000 );

    ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(
      artifact, ParcelFileDescriptor.MODE_READ_ONLY );
    PdfRenderer renderer = new PdfRenderer( descriptor );
    assertEquals( 1, renderer.getPageCount() );
    PdfRenderer.Page rendered_page = renderer.openPage( 0 );
    Bitmap bitmap = Bitmap.createBitmap( width, height, Bitmap.Config.ARGB_8888 );
    bitmap.eraseColor( Color.WHITE );
    rendered_page.render( bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY );
    assertTrue( countDifferent( bitmap, Color.WHITE ) > 500 );
    bitmap.recycle();
    rendered_page.close();
    renderer.close();
    descriptor.close();
  }

  private static TitleLegendPointState.Row installedRow( TitleLegendPointState.Kind kind,
                                                          String thName )
  {
    int index;
    String fullName;
    String displayName;
    PlotSymbolUsageSnapshot.Kind usageKind;
    if ( kind == TitleLegendPointState.Kind.LINE ) {
      index = BrushManager.getLineIndexByThName( thName );
      fullName = BrushManager.getLineFullThName( index );
      displayName = BrushManager.getLineName( index );
      usageKind = PlotSymbolUsageSnapshot.Kind.LINE;
    } else if ( kind == TitleLegendPointState.Kind.AREA ) {
      index = BrushManager.getAreaIndexByThName( thName );
      fullName = BrushManager.getAreaFullThName( index );
      displayName = BrushManager.getAreaName( index );
      usageKind = PlotSymbolUsageSnapshot.Kind.AREA;
    } else {
      index = BrushManager.getPointIndexByThName( thName );
      fullName = BrushManager.getPointFullThName( index );
      displayName = BrushManager.getPointName( index );
      usageKind = PlotSymbolUsageSnapshot.Kind.POINT;
    }
    assertTrue( "Missing fixture symbol " + thName, index >= 0 );
    return TitleLegendPointState.Row.fromEntry(
      new PlotSymbolUsageSnapshot.Entry( usageKind, fullName, displayName, index ) );
  }

  private static int countNonWhite( Bitmap bitmap )
  {
    return countDifferent( bitmap, BACKGROUND );
  }

  private static int countDifferent( Bitmap bitmap, int color )
  {
    int[] pixels = new int[ bitmap.getWidth() * bitmap.getHeight() ];
    bitmap.getPixels( pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight() );
    int count = 0;
    for ( int pixel : pixels ) if ( pixel != color ) ++count;
    return count;
  }
}
