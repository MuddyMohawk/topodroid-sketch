package com.topodroid.TDX;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.topodroid.prefs.TDSetting;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith( AndroidJUnit4.class )
@SmallTest
public class SketchBrushStyleInstrumentedTest
{
  @Test
  public void storeAndParseRoundTrip_preservesStyleFields()
  {
    SketchBrushStyle style = SketchBrushStyle.of( 2.0f, 1.25f, 0.5f, 0x123456 );

    String options = SketchBrushStyleCodec.storeInOptions( "-foo bar", style );
    SketchBrushStyle parsed = SketchBrushStyleCodec.fromOptions( options );

    assertNotNull( parsed );
    assertTrue( parsed.hasWeight() );
    assertTrue( parsed.hasPointScale() );
    assertTrue( parsed.hasOpacity() );
    assertTrue( parsed.hasColor() );
    assertEquals( 2.0f, parsed.weightOr( 0.0f ), 0.0001f );
    assertEquals( 1.25f, parsed.pointScaleOr( 0.0f ), 0.0001f );
    assertEquals( 0.5f, parsed.opacityOr( 0.0f ), 0.0001f );
    assertEquals( 0x123456, parsed.colorOr( 0 ) );
  }

  @Test
  public void stripOptions_removesOnlySketchBrushToken()
  {
    SketchBrushStyle style = SketchBrushStyle.of( 5.0f, 1.0f, 1.0f );
    String options = SketchBrushStyleCodec.storeInOptions( "-scrap cave-1 -scale l", style );

    assertEquals( "-scrap cave-1 -scale l", SketchBrushStyleCodec.stripOptions( options ) );
    assertEquals( "-scrap cave-1 -scale l", SketchBrushStyleCodec.exportOptions( options ) );
    assertNull( SketchBrushStyleCodec.fromOptions( SketchBrushStyleCodec.stripOptions( options ) ) );
  }

  @Test
  public void storeInOptions_replacesExistingBrushTokens()
  {
    String messy = "-foo bar -tdx-brush w=1.0000,s=1.0000 -baz qux -tdx-brush w=2.0000";
    String options = SketchBrushStyleCodec.storeInOptions( messy, SketchBrushStyle.of( 5.0f, 1.2f, 0.75f ) );

    assertEquals( 1, countToken( options, "-tdx-brush" ) );
    assertTrue( options.contains( "-foo bar" ) );
    assertTrue( options.contains( "-baz qux" ) );
    SketchBrushStyle parsed = SketchBrushStyleCodec.fromOptions( options );
    assertNotNull( parsed );
    assertEquals( 5.0f, parsed.weightOr( 0.0f ), 0.0001f );
    assertEquals( 1.2f, parsed.pointScaleOr( 0.0f ), 0.0001f );
    assertEquals( 0.75f, parsed.opacityOr( 0.0f ), 0.0001f );
  }

  @Test
  public void fromOptions_duplicateBrushTokensUsesLastToken()
  {
    SketchBrushStyle parsed = SketchBrushStyleCodec.fromOptions(
      "-tdx-brush w=1.0000,s=1.0000 -foo bar -tdx-brush w=5.0000,s=1.5000,o=0.5000" );

    assertNotNull( parsed );
    assertEquals( 5.0f, parsed.weightOr( 0.0f ), 0.0001f );
    assertEquals( 1.5f, parsed.pointScaleOr( 0.0f ), 0.0001f );
    assertEquals( 0.5f, parsed.opacityOr( 0.0f ), 0.0001f );
  }

  @Test
  public void malformedOptionValue_ignoresInvalidFields()
  {
    SketchBrushStyle parsed = SketchBrushStyleCodec.fromOptions( "-tdx-brush w=abc,s=1.5000,o=3.0000,c=nothex" );

    assertNotNull( parsed );
    assertFalse( parsed.hasWeight() );
    assertTrue( parsed.hasPointScale() );
    assertTrue( parsed.hasOpacity() );
    assertFalse( parsed.hasColor() );
    assertEquals( 1.5f, parsed.pointScaleOr( 0.0f ), 0.0001f );
    assertEquals( 1.0f, parsed.opacityOr( 0.0f ), 0.0001f );
  }

  @Test
  public void rendererPaint_appliesWeightOpacityAndColorWithoutMutatingSource()
  {
    float previous = TDSetting.mLineThickness;
    TDSetting.mLineThickness = 1.0f;
    try {
      Paint source = new Paint();
      source.setColor( 0xccffffff );
      source.setStyle( Paint.Style.STROKE );
      source.setStrokeWidth( 2.0f );

      Paint styled = SketchBrushRenderer.linePaint( source, SketchBrushStyle.of( 5.0f, 1.0f, 0.5f, 0x123456 ) );

      assertEquals( 2.0f, source.getStrokeWidth(), 0.0001f );
      assertEquals( 0xccffffff, source.getColor() );
      assertEquals( 5.0f * TDSetting.INK_UNIT_SCALE, styled.getStrokeWidth(), 0.0001f );
      assertEquals( 102, styled.getAlpha() );
      assertEquals( 0x123456, styled.getColor() & 0x00ffffff );
      assertEquals( 0x66123456,
          SketchBrushRenderer.styledColor( 0xccabcdef,
              SketchBrushStyle.of( 5.0f, 1.0f, 0.5f, 0x123456 ) ) );
    } finally {
      TDSetting.mLineThickness = previous;
    }
  }

  @Test
  public void pointRendererPaint_usesHalfWeightStroke()
  {
    float previous = TDSetting.mLineThickness;
    TDSetting.mLineThickness = 1.0f;
    try {
      Paint source = new Paint();
      source.setColor( 0xffffffff );
      source.setStyle( Paint.Style.STROKE );
      source.setStrokeWidth( 2.0f );

      Paint styled = SketchBrushRenderer.pointPaint( source, SketchBrushStyle.of( 5.0f, 1.0f, 1.0f ) );

      assertEquals( 2.0f, source.getStrokeWidth(), 0.0001f );
      assertEquals( 2.5f * TDSetting.INK_UNIT_SCALE, styled.getStrokeWidth(), 0.0001f );
    } finally {
      TDSetting.mLineThickness = previous;
    }
  }

  @Test
  public void lineSymbolEffect_scalesRepeatAdvanceFromWeight()
  {
    // world-space ink model: the paint stroke width (weight * line thickness)
    // is the pattern unit, so a heavier weight enlarges stamps and advance
    float previous = TDSetting.mLineThickness;
    TDSetting.mLineThickness = 1.0f;
    try {
      LineSymbolEffect effect = new LineSymbolEffect( rectanglePath( 0.0f, 0.0f, 8.0f, 2.0f ),
                                                      rectanglePath( 0.0f, 0.0f, 8.0f, -2.0f ),
                                                      10.0f, null );
      Path line = new Path();
      line.moveTo( 10.0f, 20.0f );
      line.lineTo( 160.0f, 20.0f );

      int standard = renderEffectAndCountRuns( effect, line, SketchBrushStyle.of( 2.0f, 1.0f, 1.0f ) );
      int thick = renderEffectAndCountRuns( effect, line, SketchBrushStyle.of( 5.0f, 1.0f, 1.0f ) );

      assertTrue( "Thick style should reduce repeat count by increasing advance: " + standard + " -> " + thick,
                  thick < standard );
    } finally {
      TDSetting.mLineThickness = previous;
    }
  }

  private static int renderEffectAndCountRuns( LineSymbolEffect effect, Path line, SketchBrushStyle style )
  {
    Bitmap bitmap = Bitmap.createBitmap( 180, 50, Bitmap.Config.ARGB_8888 );
    bitmap.eraseColor( Color.BLACK );
    Canvas canvas = new Canvas( bitmap );
    Paint paint = new Paint();
    paint.setColor( Color.WHITE );
    paint.setStyle( Paint.Style.STROKE );
    paint.setStrokeWidth( 1.0f );
    Paint styled = SketchBrushRenderer.linePaint( paint, style );
    assertTrue( effect.draw( canvas, line, styled, false ) );
    return countForegroundRuns( bitmap, 20 );
  }

  private static Path rectanglePath( float x0, float y0, float x1, float y1 )
  {
    Path path = new Path();
    path.moveTo( x0, y0 );
    path.lineTo( x1, y0 );
    path.lineTo( x1, y1 );
    path.lineTo( x0, y1 );
    path.close();
    return path;
  }

  private static int countForegroundRuns( Bitmap bitmap, int y )
  {
    int runs = 0;
    boolean inRun = false;
    for ( int x = 0; x < bitmap.getWidth(); ++x ) {
      boolean foreground = bitmap.getPixel( x, y ) != Color.BLACK;
      if ( foreground && ! inRun ) ++runs;
      inRun = foreground;
    }
    return runs;
  }

  private static int countToken( String options, String token )
  {
    int count = 0;
    for ( String part : options.split( "\\s+" ) ) {
      if ( token.equals( part ) ) ++count;
    }
    return count;
  }
}
