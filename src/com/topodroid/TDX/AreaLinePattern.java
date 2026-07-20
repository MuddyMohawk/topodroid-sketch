/* @file AreaLinePattern.java
 *
 * @author MuddyMohawk
 * @date jul 2026
 *
 * @brief TopoDroid Sketch area line-fill metadata (stripes / dashes / bedrock courses)
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

import com.topodroid.util.TDLog;

/** immutable line-fill parameters of an area symbol, from a "line_pattern" file line
 *
 * Grammar (one line in the area symbol file):
 *   line_pattern parallel [angle A] [color 0xRRGGBB 0xAA] [width W] [spacing S] [fade F]
 *   line_pattern dashes   [angle A] [color 0xRRGGBB 0xAA] [width W] [spacing S]
 *                         [dash D] [period P] [fade F]
 *   line_pattern bedrock  [angle A] [color 0xRRGGBB 0xAA] [width W] [spacing S]
 *                         [period P] [fade F]
 *
 * "parallel" fills the area with continuous stripes (water). "dashes" fills it with
 * short broken segments in horizontal rows (clay/mud): spacing is the perpendicular
 * row spacing, period the along-row slot spacing, dash the nominal segment length;
 * the renderer varies slot offsets and lengths with a small repeating stamp (see
 * AreaPatternRenderer) so the fill reads hand-drawn yet repeats evenly. "bedrock"
 * draws irregular-height courses with staggered, irregular-width joints: spacing is
 * the nominal course height and period the nominal block width. All metrics are ink
 * units, the same system as line weights: scene units = value *
 * TDSetting.inkUnit(), further scaled by the area's brush weight relative to the
 * standard weight. angle is degrees in the y-down scene sense. All fills anchor to
 * absolute scene coordinates. Unknown tokens are logged and skipped so future keys
 * degrade gracefully; an unsupported type or a malformed number voids the whole
 * pattern and the symbol falls back to its plain fill.
 */
class AreaLinePattern
{
  static final int TYPE_PARALLEL = 0;
  static final int TYPE_DASHES   = 1;
  static final int TYPE_BEDROCK  = 2;

  private static final String NAME_PARALLEL = "parallel";
  private static final String NAME_DASHES   = "dashes";
  private static final String NAME_BEDROCK  = "bedrock";

  static final float DEFAULT_ANGLE   = -35.0f;
  static final int   DEFAULT_COLOR   = 0x663366ff;
  static final float DEFAULT_WIDTH   = 5.0f;
  static final float DEFAULT_SPACING = 10.0f;
  static final float DEFAULT_FADE    = 0.0f;
  // dashes-only
  static final float DEFAULT_DASH    = 14.0f;
  static final float DEFAULT_PERIOD  = 24.0f;

  final int   mType;         // TYPE_PARALLEL, TYPE_DASHES, or TYPE_BEDROCK
  final float mAngle;        // line direction [degrees, y-down scene sense]
  final int   mColor;        // ink ARGB color
  final float mWidthScale;   // stroke width [ink units]
  final float mSpacingScale; // parallel: stripe period; dashes/bedrock: perpendicular row spacing [ink units]
  final float mFadeScale;    // boundary fade depth [ink units], 0 = no fade
  final float mDashScale;    // dashes: nominal segment length [ink units]
  final float mPeriodScale;  // dashes/bedrock: along-row slot or joint spacing [ink units]

  private AreaLinePattern( int type, float angle, int color, float width_scale, float spacing_scale,
                           float dash_scale, float period_scale, float fade_scale )
  {
    mType         = type;
    mAngle        = angle;
    mColor        = color;
    mWidthScale   = width_scale;
    mSpacingScale = spacing_scale;
    mDashScale    = dash_scale;
    mPeriodScale  = period_scale;
    mFadeScale    = fade_scale;
  }

  /** @return a parallel-stripe pattern, with non-positive/invalid metrics replaced by defaults
   */
  static AreaLinePattern parallel( float angle, int color, float width_scale, float spacing_scale, float fade_scale )
  {
    return new AreaLinePattern( TYPE_PARALLEL, angle, color,
        positiveOrDefault( width_scale,   DEFAULT_WIDTH ),
        positiveOrDefault( spacing_scale, DEFAULT_SPACING ),
        DEFAULT_DASH, DEFAULT_PERIOD,
        nonNegativeOrDefault( fade_scale, DEFAULT_FADE ) );
  }

  /** @return a broken-dash pattern, with non-positive/invalid metrics replaced by defaults
   */
  static AreaLinePattern dashes( float angle, int color, float width_scale, float spacing_scale,
                                 float dash_scale, float period_scale, float fade_scale )
  {
    return new AreaLinePattern( TYPE_DASHES, angle, color,
        positiveOrDefault( width_scale,   DEFAULT_WIDTH ),
        positiveOrDefault( spacing_scale, DEFAULT_SPACING ),
        positiveOrDefault( dash_scale,    DEFAULT_DASH ),
        positiveOrDefault( period_scale,  DEFAULT_PERIOD ),
        nonNegativeOrDefault( fade_scale, DEFAULT_FADE ) );
  }

  /** @return an irregular bedrock-course pattern, with invalid metrics replaced by defaults
   */
  static AreaLinePattern bedrock( float angle, int color, float width_scale, float spacing_scale,
                                  float period_scale, float fade_scale )
  {
    return new AreaLinePattern( TYPE_BEDROCK, angle, color,
        positiveOrDefault( width_scale,   DEFAULT_WIDTH ),
        positiveOrDefault( spacing_scale, DEFAULT_SPACING ),
        DEFAULT_DASH,
        positiveOrDefault( period_scale,  DEFAULT_PERIOD ),
        nonNegativeOrDefault( fade_scale, DEFAULT_FADE ) );
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
    int type;
    if ( NAME_PARALLEL.equals( vals[start] ) ) {
      type = TYPE_PARALLEL;
    } else if ( NAME_DASHES.equals( vals[start] ) ) {
      type = TYPE_DASHES;
    } else if ( NAME_BEDROCK.equals( vals[start] ) ) {
      type = TYPE_BEDROCK;
    } else {
      TDLog.e( "Unsupported area line_pattern type: " + vals[start] );
      return null;
    }
    float angle   = DEFAULT_ANGLE;
    int   color   = DEFAULT_COLOR;
    float width   = DEFAULT_WIDTH;
    float spacing = DEFAULT_SPACING;
    float dash    = DEFAULT_DASH;
    float period  = DEFAULT_PERIOD;
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
        } else if ( "dash".equals( key ) ) {
          k = nextToken( vals, k+1 );
          if ( k < vals.length ) dash = Float.parseFloat( vals[k] );
        } else if ( "period".equals( key ) ) {
          k = nextToken( vals, k+1 );
          if ( k < vals.length ) period = Float.parseFloat( vals[k] );
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
    if ( type == TYPE_DASHES ) {
      return dashes( angle, color, width, spacing, dash, period, fade );
    } else if ( type == TYPE_BEDROCK ) {
      return bedrock( angle, color, width, spacing, period, fade );
    }
    return parallel( angle, color, width, spacing, fade );
  }

  private static float positiveOrDefault( float value, float fallback )
  {
    return ( value > 0.0f && ! Float.isNaN( value ) && ! Float.isInfinite( value ) )? value : fallback;
  }

  private static float nonNegativeOrDefault( float value, float fallback )
  {
    return ( value >= 0.0f && ! Float.isNaN( value ) && ! Float.isInfinite( value ) )? value : fallback;
  }

  private static int nextToken( String[] vals, int start )
  {
    if ( start < 0 ) start = 0;
    while ( start < vals.length && ( vals[start] == null || vals[start].length() == 0 ) ) ++start;
    return start;
  }
}
