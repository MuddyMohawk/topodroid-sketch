package com.topodroid.TDX;

import static org.junit.Assert.assertTrue;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.RectF;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.topodroid.prefs.TDSetting;
import com.topodroid.types.PointScale;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

/** Rendering regression cases for placed cross-section overlays. */
@RunWith( AndroidJUnit4.class )
public class DrawingOutlinePathInstrumentedTest
{
  private static final AreaLinePattern BEDROCK = AreaLinePattern.bedrock(
      0.0f, 0xcc888888, 0.85f, 17.0f, 48.0f, 0.0f );

  @Test
  public void placedSection_drawsStyledBedrockPatternsInsteadOfFlatArea()
  {
    SymbolAreaLibrary saved_library = BrushManager.mAreaLib;
    boolean saved_darken = TDSetting.mAreaOverlapDarken;
    int saved_levels = TDSetting.mWithLevels;
    Bitmap bitmap = null;
    try {
      Resources resources = InstrumentationRegistry.getInstrumentation().getTargetContext().getResources();
      BrushManager.mAreaLib = new SymbolAreaLibrary( resources ) {
        @Override AreaLinePattern getAreaLinePattern( int index ) {
          return ( index == 0 ) ? DrawingOutlinePathInstrumentedTest.BEDROCK : null;
        }
        @Override boolean hasPatternedAreas() { return true; }
      };
      TDSetting.mAreaOverlapDarken = true;
      TDSetting.mWithLevels = 0;

      DrawingAreaPath red_area = new DrawingAreaPath( 0, 1, "viewport-bedrock-red", true, 0 );
      red_area.setSketchBrushStyle( SketchBrushStyle.of(
          SketchBrushStyle.DEFAULT_WEIGHT_STANDARD, 1.0f, 1.0f, 0xff2020 ) );
      red_area.addStartPoint( 20.0f, 20.0f );
      red_area.addPoint( 90.0f, 20.0f );
      red_area.addPoint( 90.0f, 120.0f );
      red_area.addPoint( 20.0f, 120.0f );
      red_area.closePath();

      DrawingAreaPath blue_area = new DrawingAreaPath( 0, 2, "viewport-bedrock-blue", true, 0 );
      blue_area.setSketchBrushStyle( SketchBrushStyle.of(
          SketchBrushStyle.DEFAULT_WEIGHT_STANDARD, 1.0f, 1.0f, 0x2040ff ) );
      blue_area.addStartPoint( 110.0f, 20.0f );
      blue_area.addPoint( 180.0f, 20.0f );
      blue_area.addPoint( 180.0f, 120.0f );
      blue_area.addPoint( 110.0f, 120.0f );
      blue_area.closePath();

      ArrayList< DrawingPath > sketch = new ArrayList<>();
      sketch.add( red_area );
      sketch.add( blue_area );
      RectF viewport = new RectF( 0.0f, 0.0f, 200.0f, 150.0f );
      DrawingPointPath section_point = new DrawingPointPath( 0, 100.0f, 75.0f, PointScale.SCALE_M, 0 );
      DrawingOutlinePath overlay = new DrawingOutlinePath(
          "viewport-bedrock", section_point, viewport, null, sketch, null, 0 );

      bitmap = Bitmap.createBitmap( 400, 300, Bitmap.Config.ARGB_8888 );
      bitmap.eraseColor( Color.WHITE );
      Matrix matrix = new Matrix();
      matrix.setScale( 2.0f, 2.0f );
      overlay.draw( new Canvas( bitmap ), matrix, 1.0f, viewport, false );

      int white = 0;
      int ink = 0;
      int red_ink = 0;
      int blue_ink = 0;
      for ( int y = 44; y < 236; ++y ) {
        for ( int x = 44; x < 356; ++x ) {
          int color = bitmap.getPixel( x, y );
          if ( color == Color.WHITE ) ++white;
          if ( Color.red( color ) < 230 ) ++ink;
          if ( x < 176 && Color.red( color ) > Color.green( color ) + 80
                       && Color.red( color ) > Color.blue( color ) + 80 ) ++red_ink;
          if ( x > 224 && Color.blue( color ) > Color.red( color ) + 80
                       && Color.blue( color ) > Color.green( color ) + 80 ) ++blue_ink;
        }
      }
      assertTrue( "Placed bedrock was painted as a solid translucent area", white > 30000 );
      assertTrue( "Placed bedrock pattern produced no visible linework", ink > 200 );
      assertTrue( "First placed bedrock ignored its red toolbar color", red_ink > 100 );
      assertTrue( "Second placed bedrock inherited the first area's color", blue_ink > 100 );
    } finally {
      if ( bitmap != null ) bitmap.recycle();
      BrushManager.mAreaLib = saved_library;
      TDSetting.mAreaOverlapDarken = saved_darken;
      TDSetting.mWithLevels = saved_levels;
    }
  }
}
