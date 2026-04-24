/* @file SectionPointHelper.java
 *
 * @author MuddyMohawk
 * @date apr 2026
 *
 * @brief Helpers for section-point placement metadata
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

import com.topodroid.util.TDString;
import com.topodroid.util.TDUtil;

import android.graphics.RectF;

class SectionPointHelper
{
  static final String OPTION_PIP   = "-pip";
  static final String OPTION_PIP_W = "-pipw";
  static final String OPTION_PIP_H = "-piph";
  static final String OPTION_PIP_REFS = "-piprefs";

  static final float DEFAULT_BOX_WIDTH  = 240.0f;
  static final float DEFAULT_BOX_HEIGHT = 240.0f;
  static final float MIN_BOX_SIZE       = 80.0f;

  static boolean isPlaced( DrawingPointPath point )
  {
    if ( point == null ) return false;
    String value = point.getOption( OPTION_PIP );
    return value != null && ! "0".equals( value );
  }

  static float getBoxWidth( DrawingPointPath point )
  {
    return getOptionFloat( point, OPTION_PIP_W, DEFAULT_BOX_WIDTH );
  }

  static float getBoxHeight( DrawingPointPath point )
  {
    return getOptionFloat( point, OPTION_PIP_H, DEFAULT_BOX_HEIGHT );
  }

  static RectF getBox( DrawingPointPath point )
  {
    float half_w = getBoxWidth( point ) / 2.0f;
    float half_h = getBoxHeight( point ) / 2.0f;
    return new RectF( point.cx - half_w, point.cy - half_h,
                      point.cx + half_w, point.cy + half_h );
  }

  static boolean hasPlacedReferences( DrawingPointPath point )
  {
    if ( point == null ) return true;
    String value = point.getOption( OPTION_PIP_REFS );
    return value == null || ! "0".equals( value );
  }

  static void setPlaced( DrawingPointPath point, boolean placed )
  {
    if ( point == null ) return;
    if ( placed ) {
      point.setOption( OPTION_PIP, "1" );
      setBoxSize( point, getBoxWidth( point ), getBoxHeight( point ) );
    } else {
      point.removeOption( OPTION_PIP );
      point.removeOption( OPTION_PIP_W );
      point.removeOption( OPTION_PIP_H );
      point.removeOption( OPTION_PIP_REFS );
    }
  }

  static void setBoxSize( DrawingPointPath point, float width, float height )
  {
    if ( point == null ) return;
    point.setOption( OPTION_PIP_W, formatSize( width ) );
    point.setOption( OPTION_PIP_H, formatSize( height ) );
  }

  static void setPlacedReferences( DrawingPointPath point, boolean show_refs )
  {
    if ( point == null ) return;
    point.setOption( OPTION_PIP_REFS, show_refs ? "1" : "0" );
  }

  static String getSectionName( DrawingPointPath point )
  {
    if ( point == null ) return null;
    return TDUtil.replacePrefix( TDInstance.survey, point.getOption( TDString.OPTION_SCRAP ) );
  }

  static String getShortSectionName( DrawingPointPath point )
  {
    String name = getSectionName( point );
    if ( name == null ) return null;
    int pos = TDInstance.survey.length() + 1;
    return ( name.length() > pos ) ? name.substring( pos ) : name;
  }

  static String getFullSectionName( DrawingPointPath point )
  {
    return ( point == null ) ? null : point.getOption( TDString.OPTION_SCRAP );
  }

  static boolean isLegSection( DrawingPointPath point )
  {
    String name = getSectionName( point );
    return name != null && name.lastIndexOf( "-xx" ) >= 0;
  }

  static String stripPlacementOptions( String options )
  {
    if ( options == null ) return null;
    StringBuilder builder = new StringBuilder();
    String[] tokens = options.trim().split( "\\s+" );
    for ( int i = 0; i < tokens.length; ++i ) {
      String token = tokens[i];
      if ( token.length() == 0 ) continue;
      if ( OPTION_PIP.equals( token ) || OPTION_PIP_W.equals( token ) || OPTION_PIP_H.equals( token ) || OPTION_PIP_REFS.equals( token ) ) {
        if ( i + 1 < tokens.length && ! isOptionToken( tokens[i + 1] ) ) ++i;
        continue;
      }
      if ( builder.length() > 0 ) builder.append( ' ' );
      builder.append( token );
    }
    return ( builder.length() == 0 ) ? null : builder.toString();
  }

  private static float getOptionFloat( DrawingPointPath point, String key, float def_value )
  {
    if ( point == null ) return def_value;
    String value = point.getOption( key );
    if ( value == null ) return def_value;
    try {
      return Math.max( MIN_BOX_SIZE, Float.parseFloat( value ) );
    } catch ( NumberFormatException e ) {
      return def_value;
    }
  }

  private static String formatSize( float value )
  {
    float clamped = Math.max( MIN_BOX_SIZE, value );
    int rounded = Math.round( clamped );
    return Integer.toString( rounded );
  }

  private static boolean isOptionToken( String token )
  {
    return token != null && token.startsWith( "-" ) && ( token.length() < 2 || ! Character.isDigit( token.charAt( 1 ) ) );
  }
}
