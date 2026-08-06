/* @file SlopeFanStyleCodec.java
 *
 * @brief Private persistence for a Slope fan placement's peak multiplier
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

import java.util.Locale;

final class SlopeFanStyleCodec
{
  private static final float EPSILON = 0.0001f;

  private SlopeFanStyleCodec() { }

  /** @return a stored finite value, or NaN when absent/malformed. */
  static float fromOptions( String options )
  {
    String value = SketchPrivateOptions.getOptionValue( options, SketchPrivateOptions.OPTION_SLOPE_FAN );
    if ( value == null || value.length() == 0 ) return Float.NaN;
    try {
      float peak = Float.parseFloat( value );
      return ( Float.isNaN( peak ) || Float.isInfinite( peak ) ) ? Float.NaN : peak;
    } catch ( NumberFormatException e ) {
      return Float.NaN;
    }
  }

  static String storeInOptions( String options, float peak, float default_peak )
  {
    String stripped = stripOptions( options );
    if ( Float.isNaN( peak ) || Float.isInfinite( peak ) || Math.abs( peak - default_peak ) <= EPSILON ) {
      return stripped;
    }
    String value = String.format( Locale.US, "%.4f", peak );
    return SketchPrivateOptions.storeOption( stripped, SketchPrivateOptions.OPTION_SLOPE_FAN, value );
  }

  static String stripOptions( String options )
  {
    return SketchPrivateOptions.stripOption( options, SketchPrivateOptions.OPTION_SLOPE_FAN );
  }
}
