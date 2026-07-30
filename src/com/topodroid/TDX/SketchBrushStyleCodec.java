/* @file SketchBrushStyleCodec.java
 *
 * @author MuddyMohawk
 * @date jun 2026
 *
 * @brief TopoDroid Sketch brush-style persistence bridge
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

import java.util.Locale;

class SketchBrushStyleCodec
{
  // Alpha bridge: keep this token private to Sketch and strip it from structured exports.
  private SketchBrushStyleCodec() { }

  static SketchBrushStyle fromOptions( String options )
  {
    String value = SketchPrivateOptions.getOptionValue( options, SketchPrivateOptions.OPTION_BRUSH );
    if ( value == null || value.length() == 0 ) return null;

    boolean has_weight = false;
    float weight = SketchBrushStyle.DEFAULT_WEIGHT_STANDARD;
    boolean has_point_scale = false;
    float point_scale = SketchBrushStyle.DEFAULT_POINT_SCALE;
    boolean has_opacity = false;
    float opacity = SketchBrushStyle.DEFAULT_OPACITY;
    boolean has_color = false;
    int color = 0;

    String[] parts = value.split( "," );
    for ( String part : parts ) {
      if ( part == null ) continue;
      String[] pair = part.split( "=", 2 );
      if ( pair.length != 2 ) continue;
      String key = pair[0].trim();
      String val = pair[1].trim();
      try {
        if ( "w".equals( key ) ) {
          float parsed = Float.parseFloat( val );
          if ( parsed > 0.0f ) {
            has_weight = true;
            weight = parsed;
          }
        } else if ( "s".equals( key ) ) {
          float parsed = Float.parseFloat( val );
          if ( parsed > 0.0f ) {
            has_point_scale = true;
            point_scale = parsed;
          }
        } else if ( "o".equals( key ) ) {
          has_opacity = true;
          opacity = Float.parseFloat( val );
        } else if ( "c".equals( key ) ) {
          if ( val.startsWith( "0x" ) || val.startsWith( "0X" ) ) {
            color = Integer.parseInt( val.substring( 2 ), 16 );
          } else {
            color = Integer.parseInt( val, 16 );
          }
          has_color = true;
        }
      } catch ( NumberFormatException e ) {
        // Ignore malformed fields and keep any valid style fields.
      }
    }

    return SketchBrushStyle.fromFields( has_weight, weight, has_point_scale, point_scale,
                                        has_opacity, opacity, has_color, color );
  }

  static String storeInOptions( String options, SketchBrushStyle style )
  {
    String stripped = stripOptions( options );
    if ( style == null || style.isEmpty() ) return stripped;
    String value = toOptionValue( style );
    if ( value == null || value.length() == 0 ) return stripped;
    return SketchPrivateOptions.storeOption( stripped, SketchPrivateOptions.OPTION_BRUSH, value );
  }

  static String exportOptions( String options )
  {
    return SketchPrivateOptions.stripAll( options );
  }

  static String stripOptions( String options )
  {
    return SketchPrivateOptions.stripOption( options, SketchPrivateOptions.OPTION_BRUSH );
  }

  private static String toOptionValue( SketchBrushStyle style )
  {
    StringBuilder builder = new StringBuilder();
    if ( style.hasWeight() ) {
      appendField( builder, "w", formatFloat( style.weightOr( SketchBrushStyle.DEFAULT_WEIGHT_STANDARD ) ) );
    }
    if ( style.hasPointScale() ) {
      appendField( builder, "s", formatFloat( style.pointScaleOr( SketchBrushStyle.DEFAULT_POINT_SCALE ) ) );
    }
    if ( style.hasOpacity() ) {
      appendField( builder, "o", formatFloat( style.opacityOr( SketchBrushStyle.DEFAULT_OPACITY ) ) );
    }
    if ( style.hasColor() ) {
      appendField( builder, "c", String.format( Locale.US, "%06x", style.colorOr( 0 ) & 0x00ffffff ) );
    }
    return builder.toString();
  }

  private static void appendField( StringBuilder builder, String key, String value )
  {
    if ( builder.length() > 0 ) builder.append( ',' );
    builder.append( key ).append( '=' ).append( value );
  }

  private static String formatFloat( float value )
  {
    return String.format( Locale.US, "%.4f", value );
  }
}
