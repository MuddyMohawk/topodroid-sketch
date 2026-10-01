/* @file RequiredLineSymbols.java
 *
 * @brief Stable semantic contracts for line symbols required by drawing workflows
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

/**
 * The visual definitions for these lines live in the packaged symbol files.
 * Only their stable identities and workflow semantics are defined here.
 */
final class RequiredLineSymbols
{
  private static final String[] NAMES = {
    Symbol.deprefix_u( SymbolLibrary.USER ),
    SymbolLibrary.WALL,
    SymbolLibrary.SECTION
  };

  private RequiredLineSymbols() { }

  static String[] names()
  {
    return NAMES.clone();
  }

  static boolean isRequired( String thName )
  {
    String name = Symbol.deprefix_u( thName );
    if ( name == null ) return false;
    for ( String required : NAMES ) if ( required.equals( name ) ) return true;
    return false;
  }

  /** @return an explanation when role-defining fields are invalid, or null */
  static String semanticError( SymbolLine symbol )
  {
    if ( symbol == null || ! isRequired( symbol.getThName() ) ) return "Not a required line symbol";
    String name = Symbol.deprefix_u( symbol.getThName() );
    String expectedGroup = SymbolLibrary.WALL.equals( name ) ? SymbolLibrary.WALL : null;
    if ( ! same( expectedGroup, symbol.getGroup() ) ) return "Required line group cannot be changed";

    int expectedLevel = SymbolLibrary.WALL.equals( name ) ? DrawingLevel.LEVEL_WALL : DrawingLevel.LEVEL_USER;
    if ( symbol.mLevel != expectedLevel ) return "Required line level cannot be changed";

    int expectedRoundTrip = SymbolLibrary.WALL.equals( name ) ? Symbol.W2D_WALLS_SHP : Symbol.W2D_DETAIL_SHP;
    if ( symbol.mRoundTrip != expectedRoundTrip ) return "Required line roundtrip role cannot be changed";
    if ( symbol.mClosed ) return "Required line cannot be closed";
    if ( symbol.mStyleStraight || symbol.mStyleX != 1 ) return "Required line geometry style cannot be changed";
    if ( symbol.getDefaultOptions() != null && symbol.getDefaultOptions().trim().length() > 0 ) {
      return "Required line default options cannot be changed";
    }
    return null;
  }

  static boolean isAppearanceField( String key )
  {
    return "sketch_picker_category".equals( key )
        || "sketch_picker_section".equals( key )
        || "sketch_search_terms".equals( key )
        || "color".equals( key )
        || "alpha".equals( key )
        || "width".equals( key )
        || "dash".equals( key );
  }

  private static boolean same( String left, String right )
  {
    return left == null ? right == null : left.equals( right );
  }
}
