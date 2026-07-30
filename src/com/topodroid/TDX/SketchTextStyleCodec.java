/* @file SketchTextStyleCodec.java
 *
 * @author MuddyMohawk
 * @date jul 2026
 *
 * @brief TopoDroid Sketch text-style persistence bridge
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class SketchTextStyleCodec
{
  private SketchTextStyleCodec() { }

  static SketchTextStyle fromOptions( String options )
  {
    return decode( SketchPrivateOptions.getOptionValue( options, SketchPrivateOptions.OPTION_TEXT ) );
  }

  static String storeInOptions( String options, SketchTextStyle style )
  {
    if ( style == null ) return SketchPrivateOptions.stripOption( options, SketchPrivateOptions.OPTION_TEXT );
    return SketchPrivateOptions.storeOption( options, SketchPrivateOptions.OPTION_TEXT, encode( style ) );
  }

  static String stripOptions( String options )
  {
    return SketchPrivateOptions.stripOption( options, SketchPrivateOptions.OPTION_TEXT );
  }

  static String exportOptions( String options )
  {
    return SketchPrivateOptions.stripAll( options );
  }

  static String encode( SketchTextStyle style )
  {
    if ( style == null ) return null;
    StringBuilder builder = new StringBuilder();
    append( builder, "v", Integer.toString( style.codecVersion() ) );
    append( builder, "f", style.fontId() );
    append( builder, "m", encodeMode( style.sizeMode() ) );
    append( builder, "h", String.format( Locale.US, "%.4f", style.height() ) );
    append( builder, "w", String.format( Locale.US, "%.4f", style.lineWeight() ) );
    append( builder, "b", style.bold() ? "1" : "0" );
    append( builder, "i", style.italic() ? "1" : "0" );
    append( builder, "u", style.underline() ? "1" : "0" );
    append( builder, "a", encodeAlignment( style.alignment() ) );
    append( builder, "c", String.format( Locale.US, "%08x", style.color() ) );
    for ( Map.Entry< String, String > entry : style.unknownFields().entrySet() ) {
      String key = entry.getKey();
      String value = entry.getValue();
      if ( isSafeField( key ) && isSafeField( value ) ) append( builder, key, value );
    }
    return builder.toString();
  }

  static SketchTextStyle decode( String value )
  {
    if ( value == null || value.length() == 0 ) return null;

    int version = SketchTextStyle.CODEC_VERSION;
    String font = SketchFontRegistry.FONT_DEFAULT;
    SketchTextStyle.SizeMode mode = SketchTextStyle.SizeMode.AUTO_GRID;
    float height = SketchTextStyle.DEFAULT_AUTO_GRID_MULTIPLIER;
    float line_weight = SketchTextStyle.DEFAULT_LINE_WEIGHT;
    boolean bold = false;
    boolean italic = false;
    boolean underline = false;
    SketchTextStyle.Alignment alignment = SketchTextStyle.Alignment.LEFT;
    int color = SketchTextStyle.DEFAULT_COLOR;
    boolean recognized = false;
    LinkedHashMap< String, String > unknown = new LinkedHashMap<>();

    String[] parts = value.split( "," );
    for ( String part : parts ) {
      if ( part == null ) continue;
      String[] pair = part.split( "=", 2 );
      if ( pair.length != 2 ) continue;
      String key = pair[0].trim();
      String val = pair[1].trim();
      try {
        if ( "v".equals( key ) ) {
          version = Math.max( 1, Integer.parseInt( val ) );
          recognized = true;
        } else if ( "f".equals( key ) ) {
          font = val;
          recognized = true;
        } else if ( "m".equals( key ) ) {
          mode = decodeMode( val );
          recognized = true;
        } else if ( "h".equals( key ) ) {
          height = Float.parseFloat( val );
          recognized = true;
        } else if ( "w".equals( key ) ) {
          line_weight = Float.parseFloat( val );
          recognized = true;
        } else if ( "b".equals( key ) ) {
          bold = parseBoolean( val );
          recognized = true;
        } else if ( "i".equals( key ) ) {
          italic = parseBoolean( val );
          recognized = true;
        } else if ( "u".equals( key ) ) {
          underline = parseBoolean( val );
          recognized = true;
        } else if ( "a".equals( key ) ) {
          alignment = decodeAlignment( val );
          recognized = true;
        } else if ( "c".equals( key ) ) {
          color = (int)Long.parseLong( stripHexPrefix( val ), 16 );
          recognized = true;
        } else if ( isSafeField( key ) && isSafeField( val ) ) {
          unknown.put( key, val );
        }
      } catch ( NumberFormatException e ) {
        // Ignore the malformed field while retaining all valid fields.
      }
    }
    if ( ! recognized ) return null;
    return SketchTextStyle.decoded( version, font, mode, height, line_weight,
                                    bold, italic, underline,
                                    alignment, color, unknown );
  }

  private static String encodeMode( SketchTextStyle.SizeMode mode )
  {
    if ( mode == SketchTextStyle.SizeMode.WORLD ) return "w";
    if ( mode == SketchTextStyle.SizeMode.SCREEN ) return "s";
    return "g";
  }

  private static SketchTextStyle.SizeMode decodeMode( String value )
  {
    if ( "w".equals( value ) || "world".equals( value ) ) return SketchTextStyle.SizeMode.WORLD;
    if ( "s".equals( value ) || "screen".equals( value ) ) return SketchTextStyle.SizeMode.SCREEN;
    return SketchTextStyle.SizeMode.AUTO_GRID;
  }

  private static String encodeAlignment( SketchTextStyle.Alignment alignment )
  {
    if ( alignment == SketchTextStyle.Alignment.CENTER ) return "c";
    if ( alignment == SketchTextStyle.Alignment.RIGHT ) return "r";
    return "l";
  }

  private static SketchTextStyle.Alignment decodeAlignment( String value )
  {
    if ( "c".equals( value ) || "center".equals( value ) ) return SketchTextStyle.Alignment.CENTER;
    if ( "r".equals( value ) || "right".equals( value ) ) return SketchTextStyle.Alignment.RIGHT;
    return SketchTextStyle.Alignment.LEFT;
  }

  private static boolean parseBoolean( String value )
  {
    return "1".equals( value ) || "true".equalsIgnoreCase( value ) || "yes".equalsIgnoreCase( value );
  }

  private static String stripHexPrefix( String value )
  {
    return ( value.startsWith( "0x" ) || value.startsWith( "0X" ) ) ? value.substring( 2 ) : value;
  }

  private static void append( StringBuilder builder, String key, String value )
  {
    if ( builder.length() > 0 ) builder.append( ',' );
    builder.append( key ).append( '=' ).append( value );
  }

  private static boolean isSafeField( String value )
  {
    if ( value == null || value.length() == 0 ) return false;
    for ( int i = 0; i < value.length(); ++i ) {
      char ch = value.charAt( i );
      if ( ! Character.isLetterOrDigit( ch ) && ch != '.' && ch != '_' && ch != '-' ) return false;
    }
    return true;
  }
}
