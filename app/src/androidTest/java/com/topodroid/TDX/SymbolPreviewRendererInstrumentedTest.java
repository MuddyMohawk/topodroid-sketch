package com.topodroid.TDX;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.topodroid.prefs.TDSetting;
import com.topodroid.types.SymbolType;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith( AndroidJUnit4.class )
@LargeTest
public class SymbolPreviewRendererInstrumentedTest
{
  private Context mContext;
  private Context mPreviousContext;
  private float mPreviousToolbarSize;

  @Before public void setUp()
  {
    mPreviousContext = TDInstance.context;
    mPreviousToolbarSize = TDSetting.mItemButtonSize;
    mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    TDInstance.setContext( mContext.getApplicationContext() );
    TopoDroidApp.installSymbols( true );
    BrushManager.reloadPointLibrary( mContext, mContext.getResources() );
    BrushManager.reloadLineLibrary( mContext.getResources() );
    BrushManager.reloadAreaLibrary( mContext.getResources() );
  }

  @After public void tearDown()
  {
    TDSetting.mItemButtonSize = mPreviousToolbarSize;
    TDInstance.context = mPreviousContext;
  }

  @Test public void representativeProductionSymbols_renderInsideEveryPreviewBox()
  {
    assertPreviewFits( SymbolType.POINT, BrushManager.getPointIndexByThName( "boulder" ), 48, 48 );
    assertPreviewFits( SymbolType.POINT, BrushManager.getPointIndexByThName( SymbolLibrary.CLAY ), 48, 48 );
    assertPreviewFits( SymbolType.LINE, BrushManager.getLineIndexByThName( SymbolLibrary.WALL ), 96, 48 );
    assertPreviewFits( SymbolType.LINE, BrushManager.getLineIndexByThName( "dashed" ), 96, 48 );
    assertPreviewFits( SymbolType.LINE, BrushManager.getLineIndexByThName( "dotted" ), 96, 48 );
    assertPreviewFits( SymbolType.LINE, BrushManager.getLineIndexByThName( SymbolLibrary.FLOWSTONE ), 96, 48 );
    assertPreviewFits( SymbolType.LINE, BrushManager.getLineIndexByThName( SymbolLibrary.PIT ), 96, 48 );
    assertPreviewFits( SymbolType.LINE, BrushManager.getLineIndexByThName( SymbolLibrary.SLOPE ), 96, 48 );
    assertPreviewFits( SymbolType.LINE, BrushManager.getLineIndexByThName( SymbolLibrary.SLOPE_FAN ), 96, 48 );
    assertPreviewFits( SymbolType.LINE, BrushManager.getLineIndexByThName( SymbolLibrary.LINE_WITH_ARROW ), 96, 48 );
    assertPreviewFits( SymbolType.LINE, BrushManager.getLineIndexByThName( SymbolLibrary.DASHED_LINE_WITH_ARROW ), 96, 48 );
    assertPreviewFits( SymbolType.LINE, BrushManager.getLineIndexByThName( SymbolLibrary.INTERMITTENT_DOTTED_ARROW ), 96, 48 );
    assertPreviewFits( SymbolType.LINE, BrushManager.getLineIndexByThName( SymbolLibrary.CEILING_MEANDER ), 96, 48 );
    assertPreviewFits( SymbolType.LINE, BrushManager.getLineIndexByThName( SymbolLibrary.SECTION ), 96, 48 );
    assertPreviewFits( SymbolType.AREA, BrushManager.getAreaIndexByThName( SymbolLibrary.WATER ), 48, 48 );
    assertPreviewFits( SymbolType.AREA, BrushManager.getAreaIndexByThName( SymbolLibrary.CLAY ), 48, 48 );
    assertPreviewFits( SymbolType.AREA, BrushManager.getAreaIndexByThName( SymbolLibrary.BEDROCK ), 48, 48 );
  }

  @Test public void everyEnabledSymbol_hasNonClippedInk()
  {
    for ( int index = 0; index < BrushManager.getPointLibSize(); ++index ) {
      Symbol symbol = BrushManager.getPointByIndex( index );
      if ( symbol != null && symbol.isEnabled() ) assertPreviewFits( SymbolType.POINT, index, 48, 48 );
    }
    for ( int index = 0; index < BrushManager.getLineLibSize(); ++index ) {
      Symbol symbol = BrushManager.getLineByIndex( index );
      if ( symbol != null && symbol.isEnabled() ) assertPreviewFits( SymbolType.LINE, index, 96, 48 );
    }
    for ( int index = 0; index < BrushManager.getAreaLibSize(); ++index ) {
      Symbol symbol = BrushManager.getAreaByIndex( index );
      if ( symbol != null && symbol.isEnabled() ) assertPreviewFits( SymbolType.AREA, index, 48, 48 );
    }
  }

  @Test public void everyEnabledSymbol_fitsSmallToolbarHeight()
  {
    for ( int index = 0; index < BrushManager.getPointLibSize(); ++index ) {
      Symbol symbol = BrushManager.getPointByIndex( index );
      if ( symbol != null && symbol.isEnabled() ) assertPreviewFits( SymbolType.POINT, index, 48, 16 );
    }
    for ( int index = 0; index < BrushManager.getLineLibSize(); ++index ) {
      Symbol symbol = BrushManager.getLineByIndex( index );
      if ( symbol != null && symbol.isEnabled() ) assertPreviewFits( SymbolType.LINE, index, 96, 16 );
    }
    for ( int index = 0; index < BrushManager.getAreaLibSize(); ++index ) {
      Symbol symbol = BrushManager.getAreaByIndex( index );
      if ( symbol != null && symbol.isEnabled() ) assertPreviewFits( SymbolType.AREA, index, 48, 16 );
    }
  }

  @Test public void toolbarSize_doesNotChangeRenderingForTheSameMeasuredBox()
  {
    int line = BrushManager.getLineIndexByThName( SymbolLibrary.FLOWSTONE );
    long expected = 0L;
    float[] sizes = { 1.0f, 2.5f, 5.0f };
    for ( int k = 0; k < sizes.length; ++k ) {
      TDSetting.mItemButtonSize = sizes[k];
      Bitmap bitmap = render( SymbolType.LINE, line, 96, 32 );
      long hash = alphaHash( bitmap );
      if ( k == 0 ) expected = hash; else assertEquals( expected, hash );
    }
  }

  @Test public void nonToolbarBoxes_keepFixedReadableDimensions()
  {
    float density = mContext.getResources().getDisplayMetrics().density;
    int expectedSquare = Math.round( 48.0f * density );
    int expectedLine = Math.round( 96.0f * density );
    float[] sizes = { 1.0f, 2.5f, 5.0f };
    for ( float size : sizes ) {
      TDSetting.mItemButtonSize = size;
      assertEquals( expectedSquare, SymbolPreviewButton.fixedBoxWidthPx( mContext, SymbolType.POINT ) );
      assertEquals( expectedSquare, SymbolPreviewButton.fixedBoxWidthPx( mContext, SymbolType.AREA ) );
      assertEquals( expectedLine, SymbolPreviewButton.fixedBoxWidthPx( mContext, SymbolType.LINE ) );
      assertEquals( expectedSquare, SymbolPreviewButton.fixedBoxHeightPx( mContext ) );
    }
  }

  @Test public void orientation_remeasuresAnOrientablePoint()
  {
    int index = firstOrientablePoint();
    assertTrue( "No orientable point symbol installed", index >= 0 );
    Symbol symbol = BrushManager.getPointByIndex( index );
    Bitmap before = render( SymbolType.POINT, index, 48, 48 );
    symbol.setAngle( symbol.getAngle() + 45.0f );
    Bitmap after = render( SymbolType.POINT, index, 48, 48 );
    assertTrue( "Rotating an orientable point should change its preview", alphaHash( before ) != alphaHash( after ) );
  }

  @Test public void patternedAreaScenes_keepReadableRowsAndOpaqueFadeCore()
  {
    float ink = TDSetting.inkUnit();

    int bedrockIndex = BrushManager.getAreaIndexByThName( SymbolLibrary.BEDROCK );
    AreaLinePattern bedrock = BrushManager.getAreaLinePattern( bedrockIndex );
    SymbolPreviewRenderer bedrockRenderer = renderer( SymbolType.AREA, bedrockIndex );
    RectF bedrockFrame = bedrockRenderer.getSceneFrameForTest();
    float bedrockHeight = bedrockFrame.height() - 2.0f * ink;
    assertTrue( "Bedrock preview should show at least four production rows",
        bedrockHeight >= 4.0f * bedrock.mSpacingScale * ink );
    assertTrue( "Bedrock preview should not zoom out to dozens of illegible rows",
        bedrockHeight <= 6.0f * bedrock.mSpacingScale * ink );

    int waterIndex = BrushManager.getAreaIndexByThName( SymbolLibrary.WATER );
    AreaLinePattern water = BrushManager.getAreaLinePattern( waterIndex );
    SymbolPreviewRenderer waterRenderer = renderer( SymbolType.AREA, waterIndex );
    RectF waterFrame = waterRenderer.getSceneFrameForTest();
    float waterHeight = waterFrame.height() - 2.0f * ink;
    float readableCore = waterHeight - 2.0f * water.mFadeScale * ink;
    assertTrue( "Water preview should retain at least three fully legible stripe rows",
        readableCore >= 3.0f * water.mSpacingScale * ink - 0.01f );

    assertPreviewFits( SymbolType.AREA, bedrockIndex, 48, 48 );
    assertPreviewFits( SymbolType.AREA, waterIndex, 48, 48 );
  }

  private void assertPreviewFits( int type, int index, int widthDp, int heightDp )
  {
    assertTrue( "Missing symbol index for type " + type, index >= 0 );
    Bitmap bitmap = render( type, index, widthDp, heightDp );
    Rect alpha = alphaBounds( bitmap );
    assertTrue( "Preview is empty for type/index " + type + "/" + index, ! alpha.isEmpty() );
    assertTrue( "Preview touches left edge for type/index " + type + "/" + index, alpha.left > 0 );
    assertTrue( "Preview touches top edge for type/index " + type + "/" + index, alpha.top > 0 );
    assertTrue( "Preview touches right edge for type/index " + type + "/" + index, alpha.right < bitmap.getWidth() );
    assertTrue( "Preview touches bottom edge for type/index " + type + "/" + index, alpha.bottom < bitmap.getHeight() );
  }

  private Bitmap render( int type, int index, int widthDp, int heightDp )
  {
    SymbolPreviewRenderer renderer = renderer( type, index );
    float density = mContext.getResources().getDisplayMetrics().density;
    int width = Math.max( 1, Math.round( widthDp * density ) );
    int height = Math.max( 1, Math.round( heightDp * density ) );
    Bitmap bitmap = Bitmap.createBitmap( width, height, Bitmap.Config.ARGB_8888 );
    Canvas canvas = new Canvas( bitmap );
    canvas.drawColor( Color.TRANSPARENT, PorterDuff.Mode.CLEAR );
    renderer.draw( canvas, new RectF( 0.0f, 0.0f, width, height ) );
    return bitmap;
  }

  private SymbolPreviewRenderer renderer( int type, int index )
  {
    Symbol symbol = symbol( type, index );
    assertNotNull( symbol );
    float density = mContext.getResources().getDisplayMetrics().density;
    SymbolPreviewRenderer renderer = SymbolPreviewRenderer.create( type, index, symbol, density );
    assertNotNull( "Missing preview renderer for type/index " + type + "/" + index, renderer );
    return renderer;
  }

  private Symbol symbol( int type, int index )
  {
    if ( type == SymbolType.POINT ) return BrushManager.getPointByIndex( index );
    if ( type == SymbolType.LINE ) return BrushManager.getLineByIndex( index );
    if ( type == SymbolType.AREA ) return BrushManager.getAreaByIndex( index );
    return null;
  }

  private int firstOrientablePoint()
  {
    for ( int index = 0; index < BrushManager.getPointLibSize(); ++index ) {
      if ( BrushManager.isPointOrientable( index ) ) return index;
    }
    return -1;
  }

  private static Rect alphaBounds( Bitmap bitmap )
  {
    int width = bitmap.getWidth();
    int height = bitmap.getHeight();
    int[] pixels = new int[ width * height ];
    bitmap.getPixels( pixels, 0, width, 0, 0, width, height );
    int left = width;
    int top = height;
    int right = -1;
    int bottom = -1;
    for ( int y = 0; y < height; ++y ) {
      for ( int x = 0; x < width; ++x ) {
        if ( ( pixels[y*width+x] >>> 24 ) != 0 ) {
          left = Math.min( left, x );
          top = Math.min( top, y );
          right = Math.max( right, x );
          bottom = Math.max( bottom, y );
        }
      }
    }
    return right < left ? new Rect() : new Rect( left, top, right + 1, bottom + 1 );
  }

  private static long alphaHash( Bitmap bitmap )
  {
    int width = bitmap.getWidth();
    int height = bitmap.getHeight();
    int[] pixels = new int[ width * height ];
    bitmap.getPixels( pixels, 0, width, 0, 0, width, height );
    long hash = 1469598103934665603L;
    for ( int pixel : pixels ) {
      hash ^= ( pixel >>> 24 );
      hash *= 1099511628211L;
    }
    return hash;
  }
}
