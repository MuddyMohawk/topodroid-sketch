/* @file SketchPrivateOptions.java
 *
 * @author MuddyMohawk
 * @date jul 2026
 *
 * @brief Helpers for private TopoDroid Sketch options stored in TDR mOptions
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

final class SketchPrivateOptions
{
  static final String OPTION_BRUSH = "-tdx-brush";
  static final String OPTION_TEXT = "-tdx-text";
  static final String OPTION_AFFINE = "-tdx-affine";
  static final String OPTION_OCCLUDE = "-tdx-occlude";
  static final String OPTION_SPECIAL = "-tdx-special";

  private SketchPrivateOptions() { }

  static String getOptionValue( String options, String key )
  {
    if ( options == null || key == null ) return null;
    String value = null;
    String[] tokens = options.trim().split( "\\s+" );
    for ( int i = 0; i < tokens.length; ++i ) {
      if ( key.equals( tokens[i] ) ) {
        while ( ++i < tokens.length ) {
          if ( tokens[i].length() > 0 ) {
            if ( ! isOptionToken( tokens[i] ) ) value = tokens[i];
            break;
          }
        }
      }
    }
    return value;
  }

  static String storeOption( String options, String key, String value )
  {
    String stripped = stripOption( options, key );
    if ( value == null || value.length() == 0 ) return stripped;
    StringBuilder builder = new StringBuilder();
    if ( stripped != null && stripped.length() > 0 ) builder.append( stripped ).append( ' ' );
    builder.append( key ).append( ' ' ).append( value );
    return builder.toString();
  }

  static String stripAll( String options )
  {
    return stripOption(
      stripOption( stripOption( stripOption( stripOption( options, OPTION_BRUSH ), OPTION_TEXT ), OPTION_AFFINE ), OPTION_OCCLUDE ),
      OPTION_SPECIAL );
  }

  /** Replace public Therion options while retaining every private Sketch option. */
  static String mergePublicOptions( String existing_options, String public_options )
  {
    String merged = normalize( public_options );
    String[] private_keys = { OPTION_BRUSH, OPTION_TEXT, OPTION_AFFINE, OPTION_OCCLUDE, OPTION_SPECIAL };
    for ( String key : private_keys ) {
      String value = getOptionValue( existing_options, key );
      if ( value != null && value.length() > 0 ) merged = storeOption( merged, key, value );
    }
    return merged;
  }

  static String stripOption( String options, String key )
  {
    if ( options == null ) return null;
    StringBuilder builder = new StringBuilder();
    String[] tokens = options.trim().split( "\\s+" );
    for ( int i = 0; i < tokens.length; ++i ) {
      String token = tokens[i];
      if ( token == null || token.length() == 0 ) continue;
      if ( key.equals( token ) ) {
        if ( i + 1 < tokens.length && ! isOptionToken( tokens[i + 1] ) ) ++i;
        continue;
      }
      if ( builder.length() > 0 ) builder.append( ' ' );
      builder.append( token );
    }
    return ( builder.length() == 0 ) ? null : builder.toString();
  }

  private static boolean isOptionToken( String token )
  {
    return token != null && token.startsWith( "-" )
        && ( token.length() < 2 || ! Character.isDigit( token.charAt( 1 ) ) );
  }

  private static String normalize( String options )
  {
    if ( options == null ) return null;
    String value = options.trim();
    return value.length() == 0 ? null : value;
  }
}
