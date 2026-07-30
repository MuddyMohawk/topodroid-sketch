/* @file SketchTextInput.java
 *
 * @author MuddyMohawk
 * @date jul 2026
 *
 * @brief Validation helpers for Sketch text-object input
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

final class SketchTextInput
{
  private static final int MAX_MODIFIED_UTF_BYTES = 65535;

  private SketchTextInput() { }

  static String normalizeLineEndings( String text )
  {
    if ( text == null ) return "";
    return text.replace( "\r\n", "\n" ).replace( '\r', '\n' );
  }

  static boolean hasVisibleText( String text )
  {
    return text != null && text.trim().length() > 0;
  }

  static int modifiedUtfLength( String text )
  {
    if ( text == null ) return 0;
    int length = 0;
    for ( int i = 0; i < text.length(); ++i ) {
      int ch = text.charAt( i );
      if ( ch >= 0x0001 && ch <= 0x007f ) {
        ++length;
      } else if ( ch <= 0x07ff ) {
        length += 2;
      } else {
        length += 3;
      }
      if ( length > MAX_MODIFIED_UTF_BYTES ) return length;
    }
    return length;
  }

  static boolean fitsModifiedUtf( String text )
  {
    return modifiedUtfLength( text ) <= MAX_MODIFIED_UTF_BYTES;
  }
}
