package com.topodroid.TDX;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SketchPngExportOptionsTest
{
  @Test public void bitmapScaleClampsToSupportedRange()
  {
    assertEquals( 1.0f, options( Float.NaN ).bitmapScaleFactor, 0.0f );
    assertEquals( 1.0f, options( 0.0f ).bitmapScaleFactor, 0.0f );
    assertEquals( 0.05f, options( 0.001f ).bitmapScaleFactor, 0.0f );
    assertEquals( 2.0f, options( 2.0f ).bitmapScaleFactor, 0.0f );
    assertEquals( 4.0f, options( 100.0f ).bitmapScaleFactor, 0.0f );
  }

  private static SketchPngExportOptions options( float scale )
  {
    return new SketchPngExportOptions(
      true, true, true, true, true, true, true, false, scale, "test.png" );
  }
}
