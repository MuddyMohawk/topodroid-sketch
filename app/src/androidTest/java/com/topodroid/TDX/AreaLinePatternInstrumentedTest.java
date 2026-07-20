package com.topodroid.TDX;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

/** AreaLinePattern parser cases plus the SymbolArea file-parsing hook.
 *  Unit-style: no UI harness, no fixture import.
 */
@RunWith( AndroidJUnit4.class )
public class AreaLinePatternInstrumentedTest
{
  private static AreaLinePattern parse( String line )
  {
    return AreaLinePattern.parse( line.split( " " ), 0 );
  }

  @Test
  public void parsesWaterGrammar()
  {
    AreaLinePattern p = parse( "parallel angle -35 color 0x3366ff 0x66 width 5.0 spacing 10.0 fade 25.0" );
    assertNotNull( p );
    assertEquals( AreaLinePattern.TYPE_PARALLEL, p.mType );
    assertEquals( -35.0f, p.mAngle, 0.001f );
    assertEquals( 0x663366ff, p.mColor );
    assertEquals(  5.0f, p.mWidthScale,   0.001f );
    assertEquals( 10.0f, p.mSpacingScale, 0.001f );
    assertEquals( 25.0f, p.mFadeScale,    0.001f );
  }

  @Test
  public void parsesClayDashesGrammar()
  {
    AreaLinePattern p = parse( "dashes angle 0 color 0xe0d5c0 0xcc width 0.75 dash 5.0 spacing 5.5 period 13.0 fade 5.0" );
    assertNotNull( p );
    assertEquals( AreaLinePattern.TYPE_DASHES, p.mType );
    assertEquals( 0.0f, p.mAngle, 0.001f );
    assertEquals( 0xcce0d5c0, p.mColor );
    assertEquals( 0.75f, p.mWidthScale,   0.001f );
    assertEquals(  5.5f, p.mSpacingScale, 0.001f );
    assertEquals(  5.0f, p.mDashScale,    0.001f );
    assertEquals( 13.0f, p.mPeriodScale,  0.001f );
    assertEquals(  5.0f, p.mFadeScale,    0.001f );
  }

  @Test
  public void parsesBedrockGrammar_withoutBoundaryFade()
  {
    AreaLinePattern p = parse( "bedrock angle 0 color 0x888888 0xcc width 0.85 spacing 17.0 period 48.0 fade 0.0" );
    assertNotNull( p );
    assertEquals( AreaLinePattern.TYPE_BEDROCK, p.mType );
    assertEquals( 0.0f, p.mAngle, 0.001f );
    assertEquals( 0xcc888888, p.mColor );
    assertEquals(  0.85f, p.mWidthScale,   0.001f );
    assertEquals( 17.0f,  p.mSpacingScale, 0.001f );
    assertEquals( 48.0f,  p.mPeriodScale,  0.001f );
    assertEquals(  0.0f,  p.mFadeScale,    0.001f );
  }

  @Test
  public void minimalDashesGetsDashDefaults()
  {
    AreaLinePattern p = parse( "dashes" );
    assertNotNull( p );
    assertEquals( AreaLinePattern.TYPE_DASHES, p.mType );
    assertEquals( AreaLinePattern.DEFAULT_DASH,   p.mDashScale,   0.001f );
    assertEquals( AreaLinePattern.DEFAULT_PERIOD, p.mPeriodScale, 0.001f );
  }

  @Test
  public void retiredJitterTokenIsIgnoredGracefully()
  {
    // "jitter" (dropped when the dash stamp replaced free jitter) must not void a
    // pattern coming from a stale symbol file
    AreaLinePattern p = parse( "dashes jitter 4.0 width 2.0" );
    assertNotNull( p );
    assertEquals( 2.0f, p.mWidthScale, 0.001f );
  }

  @Test
  public void minimalLineGetsDefaults_fadeStaysOff()
  {
    AreaLinePattern p = parse( "parallel" );
    assertNotNull( p );
    assertEquals( AreaLinePattern.DEFAULT_ANGLE,   p.mAngle,        0.001f );
    assertEquals( AreaLinePattern.DEFAULT_COLOR,   p.mColor );
    assertEquals( AreaLinePattern.DEFAULT_WIDTH,   p.mWidthScale,   0.001f );
    assertEquals( AreaLinePattern.DEFAULT_SPACING, p.mSpacingScale, 0.001f );
    assertEquals( 0.0f, p.mFadeScale, 0.001f );
  }

  @Test
  public void unknownTokensAreSkipped()
  {
    // legacy tokens from the reverted 2026-07-03 grammar must not void the pattern
    AreaLinePattern p = parse( "parallel anchor world overlap union width 3.0" );
    assertNotNull( p );
    assertEquals( 3.0f, p.mWidthScale, 0.001f );
  }

  @Test
  public void blankTokensAreTolerated()
  {
    // symbol files are split on single spaces; runs of spaces produce empty tokens
    AreaLinePattern p = parse( "parallel  angle  -45   spacing  8.0" );
    assertNotNull( p );
    assertEquals( -45.0f, p.mAngle, 0.001f );
    assertEquals( 8.0f, p.mSpacingScale, 0.001f );
  }

  @Test
  public void nonPositiveMetricsFallBackToDefaults()
  {
    AreaLinePattern p = parse( "parallel width -2.0 spacing 0 fade -1" );
    assertNotNull( p );
    assertEquals( AreaLinePattern.DEFAULT_WIDTH,   p.mWidthScale,   0.001f );
    assertEquals( AreaLinePattern.DEFAULT_SPACING, p.mSpacingScale, 0.001f );
    assertEquals( 0.0f, p.mFadeScale, 0.001f );
  }

  @Test
  public void rejectsUnsupportedTypeAndMalformedNumbers()
  {
    assertNull( parse( "crosshatch angle -35" ) );
    assertNull( parse( "parallel width abc" ) );
    assertNull( parse( "parallel color 0x3366ff" ) ); // color needs rgb + alpha
    assertNull( AreaLinePattern.parse( new String[0], 0 ) );
    assertNull( AreaLinePattern.parse( null, 0 ) );
  }

  @Test
  public void symbolAreaFileParsingWiresThePattern() throws Exception
  {
    File dir = InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir();
    File file = new File( dir, "test-area-water" );
    Writer w = new OutputStreamWriter( new FileOutputStream( file ), "ISO-8859-1" );
    try {
      w.write( "encoding utf-8\n" );
      w.write( "symbol area\n" );
      w.write( "name water\n" );
      w.write( "th_name water\n" );
      w.write( "group water\n" );
      w.write( "color 0x3366ff 0x66\n" );
      w.write( "close-horizontal\n" );
      w.write( "level 7\n" );
      w.write( "line_pattern parallel angle -35 color 0x3366ff 0x66 width 5.0 spacing 10.0 fade 25.0\n" );
      w.write( "endsymbol\n" );
    } finally {
      w.close();
    }
    try {
      SymbolArea symbol = new SymbolArea( file.getPath(), file.getName(), "name-en", "ISO-8859-1" );
      AreaLinePattern p = symbol.getLinePattern();
      assertNotNull( "line_pattern not parsed from area symbol file", p );
      assertEquals( -35.0f, p.mAngle, 0.001f );
      assertEquals( 0x663366ff, p.mColor );
      assertEquals( 25.0f, p.mFadeScale, 0.001f );
      assertEquals( "water", symbol.getThName() );
    } finally {
      //noinspection ResultOfMethodCallIgnored
      file.delete();
    }
  }
}
