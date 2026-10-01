/* @file DelimitedText.java
 *
 * @brief Standards-compliant field escaping for delimited survey exports.
 */
package com.topodroid.TDX;

final class DelimitedText
{
  private DelimitedText() { }

  static String field( String value, char separator )
  {
    if ( value == null ) return "";
    boolean quote = false;
    for ( int i = 0; i < value.length(); ++i ) {
      char ch = value.charAt( i );
      if ( ch == separator || ch == '"' || ch == '\r' || ch == '\n' ) {
        quote = true;
        break;
      }
    }
    if ( ! quote ) return value;
    return '"' + value.replace( "\"", "\"\"" ) + '"';
  }
}
