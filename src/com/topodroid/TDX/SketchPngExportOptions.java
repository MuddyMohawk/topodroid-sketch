/* @file SketchPngExportOptions.java
 *
 * @author MuddyMohawk
 * @date apr 2026
 *
 * @brief TopoDroid sketch PNG export options
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

class SketchPngExportOptions
{
  private static final float DEFAULT_BITMAP_SCALE_FACTOR = 1.0f;
  private static final float MIN_BITMAP_SCALE_FACTOR = 0.25f;
  private static final float MAX_BITMAP_SCALE_FACTOR = 4.0f;

  final boolean includeStations;
  final boolean includeLegs;
  final boolean includeSplays;
  final boolean includeGrid;
  final boolean includeScaleBar;
  final boolean includeNorthArrow;
  final boolean transparentBackground;
  final boolean overwriteExisting;
  final float bitmapScaleFactor;
  final String filename;

  private static float clampBitmapScaleFactor( float scale )
  {
    if ( ! ( scale > 0 ) ) return DEFAULT_BITMAP_SCALE_FACTOR;
    if ( scale < MIN_BITMAP_SCALE_FACTOR ) return MIN_BITMAP_SCALE_FACTOR;
    if ( scale > MAX_BITMAP_SCALE_FACTOR ) return MAX_BITMAP_SCALE_FACTOR;
    return scale;
  }

  SketchPngExportOptions( boolean stations, boolean legs, boolean splays, boolean grid,
                          boolean scaleBar, boolean northArrow, boolean transparent,
                          boolean overwrite, float bitmapScale, String name )
  {
    includeStations = stations;
    includeLegs = legs;
    includeSplays = splays;
    includeGrid = grid;
    includeScaleBar = scaleBar;
    includeNorthArrow = northArrow;
    transparentBackground = transparent;
    overwriteExisting = overwrite;
    bitmapScaleFactor = clampBitmapScaleFactor( bitmapScale );
    filename = name;
  }
}
