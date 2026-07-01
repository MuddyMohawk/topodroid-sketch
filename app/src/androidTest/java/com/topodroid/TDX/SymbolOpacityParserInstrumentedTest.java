package com.topodroid.TDX;

import static org.junit.Assert.assertEquals;

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
