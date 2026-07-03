/* @file AreaLinePattern.java
 *
 * @author MuddyMohawk
 * @date jul 2026
 *
 * @brief TopoDroid Sketch area line-pattern metadata
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

import com.topodroid.util.TDLog;

class AreaLinePattern
{
  static final String TYPE_PARALLEL = "parallel";
  static final String ANCHOR_WORLD = "world";
  static final String OVERLAP_UNION = "union";

  final String mType;
  final float mAngle;
  final int mColor;
  final float mWidthScale;
  final float mSpacingScale;
  final String mAnchor;
  final String mOverlap;

  private AreaLinePattern( String type, float angle, int color, float width_scale,
                           float spacing_scale, String anchor, String overlap )
  {
    mType = type;
    mAngle = angle;
    mColor = color;
    mWidthScale = width_scale;
    mSpacingScale = spacing_scale;
    mAnchor = anchor;
    mOverlap = overlap;
  }

  static AreaLinePattern parallel( float angle, int color, float width_scale, float spacing_scale )
  {
    return new AreaLinePattern( TYPE_PARALLEL, angle, color,
        positiveOrDefault( width_scale, 1.0f ),
        positiveOrDefault( spacing_scale, 6.0f ),
        ANCHOR_WORLD, OVERLAP_UNION );
  }

  static AreaLinePattern parse( String[] vals, int start )
  {
    if ( vals == null || start >= vals.length ) return null;
    start = nextToken( vals, start );
    if ( start >= vals.length ) return null;
    if ( ! TYPE_PARALLEL.equals( vals[start] ) ) {
      TDLog.e( "Unsupported area line_pattern type: " + vals[start] );
      return null;
    }

    float angle = -35.0f;
    int color = 0x990099ff;
    float width_scale = 1.0f;
    float spacing_scale = 6.0f;
    String anchor = ANCHOR_WORLD;
    String overlap = OVERLAP_UNION;

    for ( int k = start + 1; k < vals.length; ++k ) {
      String key = vals[k];
      if ( key == null || key.length() == 0 ) continue;
      try {
        if ( "angle".equals( key ) && k + 1 < vals.length ) {
          k = nextToken( vals, k + 1 );
          if ( k < vals.length ) angle = Float.parseFloat( vals[k] );
        } else if ( "color".equals( key ) && k + 2 < vals.length ) {
          k = nextToken( vals, k + 1 );
          if ( k >= vals.length ) return null;
          int rgb = Integer.decode( vals[k] );
          k = nextToken( vals, k + 1 );
          if ( k >= vals.length ) return null;
          int alpha = Integer.decode( vals[k] );
          color = ( ( alpha & 0xff ) << 24 ) | ( rgb & 0x00ffffff );
        } else if ( "width".equals( key ) && k + 1 < vals.length ) {
          k = nextToken( vals, k + 1 );
          if ( k < vals.length ) width_scale = Float.parseFloat( vals[k] );
        } else if ( "spacing".equals( key ) && k + 1 < vals.length ) {
          k = nextToken( vals, k + 1 );
          if ( k < vals.length ) spacing_scale = Float.parseFloat( vals[k] );
        } else if ( "anchor".equals( key ) && k + 1 < vals.length ) {
          k = nextToken( vals, k + 1 );
          if ( k < vals.length ) anchor = vals[k];
        } else if ( "overlap".equals( key ) && k + 1 < vals.length ) {
          k = nextToken( vals, k + 1 );
          if ( k < vals.length ) overlap = vals[k];
        } else {
          TDLog.e( "Unknown area line_pattern token: " + key );
        }
      } catch ( NumberFormatException e ) {
        TDLog.e( "Malformed area line_pattern token " + key + ": " + e.getMessage() );
        return null;
      }
    }

    if ( ! ANCHOR_WORLD.equals( anchor ) ) {
      TDLog.e( "Unsupported area line_pattern anchor: " + anchor );
      return null;
    }
    if ( ! OVERLAP_UNION.equals( overlap ) ) {
      TDLog.e( "Unsupported area line_pattern overlap: " + overlap );
      return null;
    }
    return parallel( angle, color, width_scale, spacing_scale );
  }

  boolean isParallelWorldUnion()
  {
    return TYPE_PARALLEL.equals( mType )
        && ANCHOR_WORLD.equals( mAnchor )
        && OVERLAP_UNION.equals( mOverlap );
  }

  private static float positiveOrDefault( float value, float fallback )
  {
    return ( value > 0.0f && ! Float.isNaN( value ) && ! Float.isInfinite( value ) ) ? value : fallback;
  }

  private static int nextToken( String[] vals, int start )
  {
    while ( start < vals.length && ( vals[start] == null || vals[start].length() == 0 ) ) ++start;
    return start;
  }
}
