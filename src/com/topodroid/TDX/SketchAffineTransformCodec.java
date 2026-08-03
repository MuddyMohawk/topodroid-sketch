/* @file SketchAffineTransformCodec.java
 *
 * @author MuddyMohawk
 * @date aug 2026
 *
 * @brief Private option persistence for Sketch affine point transforms
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

final class SketchAffineTransformCodec
{
  private SketchAffineTransformCodec() { }

  static SketchAffineTransform fromOptions( String options )
  {
    String value = SketchPrivateOptions.getOptionValue( options, SketchPrivateOptions.OPTION_AFFINE );
    if ( value == null || value.length() == 0 ) return null;

    boolean hasVersion = false;
    boolean has00 = false;
    boolean has01 = false;
    boolean has10 = false;
    boolean has11 = false;
    int version = 0;
    float m00 = 0.0f;
    float m01 = 0.0f;
    float m10 = 0.0f;
    float m11 = 0.0f;

    String[] parts = value.split( "," );
    for ( String part : parts ) {
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
        } else if ( "m00".equals( key ) ) {
          if ( has00 ) return null;
          has00 = true;
          m00 = Float.parseFloat( val );
        } else if ( "m01".equals( key ) ) {
          if ( has01 ) return null;
          has01 = true;
          m01 = Float.parseFloat( val );
        } else if ( "m10".equals( key ) ) {
          if ( has10 ) return null;
          has10 = true;
          m10 = Float.parseFloat( val );
        } else if ( "m11".equals( key ) ) {
          if ( has11 ) return null;
          has11 = true;
          m11 = Float.parseFloat( val );
        }
      } catch ( NumberFormatException e ) {
        return null;
      }
    }

    if ( ! hasVersion || version != 1 || ! has00 || ! has01 || ! has10 || ! has11 ) return null;
    return SketchAffineTransform.create( m00, m01, m10, m11 );
  }

  static String storeInOptions( String options, SketchAffineTransform transform )
  {
    String stripped = stripOptions( options );
    if ( transform == null ) return stripped;
    String value = "v=1,m00=" + Float.toString( transform.m00 )
                 + ",m01=" + Float.toString( transform.m01 )
                 + ",m10=" + Float.toString( transform.m10 )
                 + ",m11=" + Float.toString( transform.m11 );
    return SketchPrivateOptions.storeOption( stripped, SketchPrivateOptions.OPTION_AFFINE, value );
  }

  static String stripOptions( String options )
  {
    return SketchPrivateOptions.stripOption( options, SketchPrivateOptions.OPTION_AFFINE );
  }
}
