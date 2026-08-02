package com.topodroid.TDX;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

@RunWith( AndroidJUnit4.class )
public class SymbolOpacityParserInstrumentedTest
{
  @Test
  public void pointColorLineHonorsOptionalAlphaByte() throws Exception
  {
    File symbol = writeTempSymbol(
        "symbol point\n"
      + "name alpha_test\n"
      + "th_name alpha_test\n"
      + "color 0x00ff00 0x99\n"
      + "path\n"
      + "moveTo 0 0 lineTo 10 0\n"
      + "endpath\n"
      + "endsymbol\n" );

    SymbolPoint point = new SymbolPoint( symbol.getPath(), symbol.getName(), "name-en", "UTF-8" );

    assertEquals( 0x9900ff00, point.getPaint().getColor() );
  }

  @Test
  public void pointColorLineDefaultsToOpaqueWhenAlphaIsAbsent() throws Exception
  {
    File symbol = writeTempSymbol(
        "symbol point\n"
      + "name opaque_test\n"
      + "th_name opaque_test\n"
      + "color 0x00ff00\n"
      + "path\n"
      + "moveTo 0 0 lineTo 10 0\n"
      + "endpath\n"
      + "endsymbol\n" );

    SymbolPoint point = new SymbolPoint( symbol.getPath(), symbol.getName(), "name-en", "UTF-8" );

    assertEquals( 0xff00ff00, point.getPaint().getColor() );
  }

  @Test
  public void pointDetailPathHasIndependentStrokeScale() throws Exception
  {
    File symbol = writeTempSymbol(
        "symbol point\n"
      + "name detail_test\n"
      + "th_name detail_test\n"
      + "color 0xffffff\n"
      + "path\n"
      + "moveTo 0 0 lineTo 10 0\n"
      + "endpath\n"
      + "detail_path 0.25\n"
      + "moveTo 0 1 lineTo 10 1\n"
      + "enddetail_path\n"
      + "endsymbol\n" );

    SymbolPoint point = new SymbolPoint( symbol.getPath(), symbol.getName(), "name-en", "UTF-8" );

    assertNotNull( point.getOrigDetailPath() );
    assertFalse( point.getOrigDetailPath().isEmpty() );
    assertEquals( 0.25f, point.getDetailStrokeScale(), 0.0001f );
  }

  @Test
  public void pointSketchCapabilitiesValidateValuesAndClosedSilhouette() throws Exception
  {
    SymbolPoint valid = parseCapabilities( "sketch_affine yes\nsketch_occlude breakdown\n", closedPath() );
    assertTrue( valid.isAffine() );
    assertEquals( "breakdown", valid.defaultOccludeGroup() );
    assertNotNull( valid.getOrigOcclusionSilhouette() );

    SymbolPoint malformed = parseCapabilities( "sketch_affine maybe\nsketch_occlude bad/group\n", closedPath() );
    assertFalse( malformed.isAffine() );
    assertNull( malformed.defaultOccludeGroup() );
    assertNull( malformed.getOrigOcclusionSilhouette() );

    SymbolPoint empty = parseCapabilities( "sketch_affine no\nsketch_occlude\n", closedPath() );
    assertFalse( empty.isAffine() );
    assertNull( empty.defaultOccludeGroup() );

    SymbolPoint open = parseCapabilities( "sketch_affine yes\nsketch_occlude breakdown\n",
        "moveTo 0 0 lineTo 10 0 lineTo 10 10\n" );
    assertTrue( open.isAffine() );
    assertNull( open.defaultOccludeGroup() );
    assertNull( open.getOrigOcclusionSilhouette() );

    SymbolPoint duplicate = parseCapabilities(
        "sketch_affine yes\nsketch_affine no\nsketch_occlude other\nsketch_occlude breakdown\n", closedPath() );
    assertFalse( duplicate.isAffine() );
    assertEquals( "breakdown", duplicate.defaultOccludeGroup() );
  }

  private SymbolPoint parseCapabilities( String declarations, String path ) throws Exception
  {
    File symbol = writeTempSymbol(
        "symbol point\n"
      + "name capability_test\n"
      + "th_name capability_test\n"
      + declarations
      + "color 0xffffff\n"
      + "path\n"
      + path
      + "endpath\n"
      + "endsymbol\n" );
    return new SymbolPoint( symbol.getPath(), symbol.getName(), "name-en", "UTF-8" );
  }

  private static String closedPath()
  {
    return "moveTo 0 0 lineTo 10 0 lineTo 10 10 lineTo 0 10 lineTo 0 0\n";
  }

  private File writeTempSymbol( String text ) throws IOException
  {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    File file = File.createTempFile( "symbol-opacity-", ".sym", context.getCacheDir() );
    OutputStreamWriter writer = null;
    try {
      writer = new OutputStreamWriter( new FileOutputStream( file ), "UTF-8" );
      writer.write( text );
    } finally {
      if ( writer != null ) writer.close();
    }
    return file;
  }
}
