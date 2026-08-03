/* @file SketchOcclusionCodec.java
 *
 * @author MuddyMohawk
 * @date aug 2026
 *
 * @brief Private option persistence for Sketch point occlusion membership
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

final class SketchOcclusionCodec
{
  private SketchOcclusionCodec() { }

  static String fromOptions( String options )
  {
    String value = SketchPrivateOptions.getOptionValue( options, SketchPrivateOptions.OPTION_OCCLUDE );
    if ( value == null || value.length() == 0 ) return null;
    boolean hasVersion = false;
    boolean hasGroup = false;
    int version = 0;
    String group = null;
    for ( String part : value.split( "," ) ) {
      if ( part == null ) continue;
      String[] pair = part.split( "=", 2 );
      if ( pair.length != 2 ) return null;
      String key = pair[0].trim();
      String val = pair[1].trim();
      try {
        if ( "v".equals( key ) ) {
          if ( hasVersion ) return null;
          hasVersion = true;
          version = Integer.parseInt( val );
        } else if ( "g".equals( key ) ) {
          if ( hasGroup ) return null;
          hasGroup = true;
          group = val;
        }
      } catch ( NumberFormatException e ) {
        return null;
      }
    }
    if ( ! hasVersion || version != 1 || ! hasGroup || ! isValidGroup( group ) ) return null;
    return group;
  }

  static String storeInOptions( String options, String group )
  {
    String stripped = stripOptions( options );
    if ( ! isValidGroup( group ) ) return stripped;
    return SketchPrivateOptions.storeOption( stripped, SketchPrivateOptions.OPTION_OCCLUDE, "v=1,g=" + group );
  }

  static String stripOptions( String options )
  {
    return SketchPrivateOptions.stripOption( options, SketchPrivateOptions.OPTION_OCCLUDE );
  }

  static boolean isValidGroup( String group )
  {
    if ( group == null || group.length() == 0 ) return false;
    for ( int i = 0; i < group.length(); ++i ) {
      char c = group.charAt( i );
      if ( ! Character.isLetterOrDigit( c ) && c != '-' && c != '_' && c != '.' ) return false;
    }
    return true;
  }
}
