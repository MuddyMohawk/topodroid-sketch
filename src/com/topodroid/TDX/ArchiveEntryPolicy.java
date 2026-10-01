/* @file ArchiveEntryPolicy.java
 *
 * @brief Validation shared by survey archive import and its contract tests.
 */
package com.topodroid.TDX;

final class ArchiveEntryPolicy
{
  private ArchiveEntryPolicy() { }

  /** Archive entries may be relative paths, but must never escape the survey root. */
  static boolean isSafeName( String name )
  {
    if ( name == null || name.length() == 0 ) return false;
    if ( name.startsWith( "/" ) || name.startsWith( "\\" ) || name.indexOf( '\\' ) >= 0 ) return false;
    if ( name.indexOf( ':' ) >= 0 || name.indexOf( '\0' ) >= 0 ) return false;

    String[] parts = name.split( "/", -1 );
    for ( int k = 0; k < parts.length; ++k ) {
      String part = parts[k];
      if ( part.length() == 0 ) {
        // ZIPs may contain explicit directory entries, which end in one slash.
        if ( k == parts.length - 1 && k > 0 ) continue;
        return false;
      }
      if ( ".".equals( part ) || "..".equals( part ) ) return false;
    }
    return true;
  }
}
