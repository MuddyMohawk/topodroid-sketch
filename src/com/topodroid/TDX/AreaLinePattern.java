/* @file AreaLinePattern.java
 *
 * @author MuddyMohawk
 * @date jul 2026
 *
 * @brief TopoDroid Sketch area stripe-fill metadata
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

import com.topodroid.util.TDLog;

/** immutable stripe-fill parameters of an area symbol, from a "line_pattern" file line
 *
 * Grammar (one line in the area symbol file):
 *   line_pattern parallel [angle A] [color 0xRRGGBB 0xAA] [width W] [spacing S] [fade F]
 *
 * width, spacing (stripe period, center to center) and fade (boundary fade depth,
 * 0 = off) are ink units, the same system as line weights: scene units = value *
 * TDSetting.inkUnit(). angle is degrees in the y-down scene sense. Stripes anchor to
 * absolute scene coordinates (see AreaPatternRenderer). Unknown tokens are logged and
 * skipped so future keys degrade gracefully; a malformed number voids the whole
 * pattern and the symbol falls back to its plain fill.
 */
class AreaLinePattern
{
  static final String TYPE_PARALLEL = "parallel";

  static final float DEFAULT_ANGLE   = -35.0f;
  static final int   DEFAULT_COLOR   = 0x663366ff;
  static final float DEFAULT_WIDTH   = 5.0f;
  static final float DEFAULT_SPACING = 10.0f;
  static final float DEFAULT_FADE    = 0.0f;

  final float mAngle;        // stripe direction [degrees, y-down scene sense]
  final int   mColor;        // stripe ARGB color
  final float mWidthScale;   // stripe stroke width [ink units]
  final float mSpacingScale; // stripe period, center to center [ink units]
  final float mFadeScale;    // boundary fade depth [ink units], 0 = no fade

  private AreaLinePattern( float angle, int color, float width_scale, float spacing_scale, float fade_scale )
  {
    mAngle        = angle;
    mColor        = color;
    mWidthScale   = width_scale;
    mSpacingScale = spacing_scale;
    mFadeScale    = fade_scale;
  }

  /** @return a parallel-stripe pattern, with non-positive/invalid metrics replaced by defaults
   */
  static AreaLinePattern parallel( float angle, int color, float width_scale, float spacing_scale, float fade_scale )
  {
    return new AreaLinePattern( angle, color,
        positiveOrDefault( width_scale,   DEFAULT_WIDTH ),
        positiveOrDefault( spacing_scale, DEFAULT_SPACING ),
        positiveOrDefault( fade_scale,    DEFAULT_FADE ) );
  }

  /** parse the tokens that follow the "line_pattern" key
   * @param vals   whitespace-split symbol-file line
   * @param start  index of the first token after "line_pattern"
   * @return the pattern, or null if the line is unusable (the symbol keeps its plain fill)
   */
  static AreaLinePattern parse( String[] vals, int start )
  {
    if ( vals == null ) return null;
    start = nextToken( vals, start );
    if ( start >= vals.length ) return null;
    if ( ! TYPE_PARALLEL.equals( vals[start] ) ) {
      TDLog.e( "Unsupported area line_pattern type: " + vals[start] );
      return null;
    }
    float angle   = DEFAULT_ANGLE;
    int   color   = DEFAULT_COLOR;
    float width   = DEFAULT_WIDTH;
    float spacing = DEFAULT_SPACING;
    float fade    = DEFAULT_FADE;
    for ( int k = nextToken( vals, start+1 ); k < vals.length; k = nextToken( vals, k+1 ) ) {
      String key = vals[k];
      try {
        if ( "angle".equals( key ) ) {
          k = nextToken( vals, k+1 );
          if ( k < vals.length ) angle = Float.parseFloat( vals[k] );
        } else if ( "color".equals( key ) ) {
          k = nextToken( vals, k+1 );
          if ( k >= vals.length ) return null;
          int rgb = Integer.decode( vals[k] );
          k = nextToken( vals, k+1 );
          if ( k >= vals.length ) return null;
          int alpha = Integer.decode( vals[k] );
          color = ( ( alpha & 0xff ) << 24 ) | ( rgb & 0x00ffffff );
        } else if ( "width".equals( key ) ) {
          k = nextToken( vals, k+1 );
          if ( k < vals.length ) width = Float.parseFloat( vals[k] );
        } else if ( "spacing".equals( key ) ) {
          k = nextToken( vals, k+1 );
          if ( k < vals.length ) spacing = Float.parseFloat( vals[k] );
        } else if ( "fade".equals( key ) ) {
          k = nextToken( vals, k+1 );
          if ( k < vals.length ) fade = Float.parseFloat( vals[k] );
        } else {
          TDLog.e( "Unknown area line_pattern token: " + key );
        }
      } catch ( NumberFormatException e ) {
        TDLog.e( "Malformed area line_pattern token " + key + ": " + e.getMessage() );
        return null;
      }
    }
    return parallel( angle, color, width, spacing, fade );
  }

  private static float positiveOrDefault( float value, float fallback )
  {
    return ( value > 0.0f && ! Float.isNaN( value ) && ! Float.isInfinite( value ) )? value : fallback;
  }

  private static int nextToken( String[] vals, int start )
  {
    if ( start < 0 ) start = 0;
    while ( start < vals.length && ( vals[start] == null || vals[start].length() == 0 ) ) ++start;
    return start;
  }
}
