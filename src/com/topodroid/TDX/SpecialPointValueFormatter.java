/* @file SpecialPointValueFormatter.java
 *
 * @brief Shared unit-neutral formatting for survey-derived special-point values
 */
package com.topodroid.TDX;

import java.util.Locale;

final class SpecialPointValueFormatter
{
  private SpecialPointValueFormatter() { }

  static String halfUnit( float value )
  {
    float rounded = Math.round( Math.max( 0.0f, value ) * 2.0f ) / 2.0f;
    if ( Math.abs( rounded - Math.round( rounded ) ) < 0.001f ) {
      return String.format( Locale.US, "%d", Math.round( rounded ) );
    }
    return String.format( Locale.US, "%.1f", rounded );
  }
}
