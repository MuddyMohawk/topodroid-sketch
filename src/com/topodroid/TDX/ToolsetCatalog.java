/* @file ToolsetCatalog.java
 *
 * Searchable category-first view of all installed toolbar-eligible symbols.
 */
package com.topodroid.TDX;

import com.topodroid.types.SymbolType;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class ToolsetCatalog
{
  static final class Entry
  {
    final int mType;
    final int mIndex;
    final Symbol mSymbol;
    final String mCategory;
    final String mSection;
    final String mSearchText;

    Entry( int type, int index, Symbol symbol )
    {
      mType = type;
      mIndex = index;
      mSymbol = symbol;
      mCategory = symbol.getPickerCategory();
      mSection = symbol.getPickerSection();
      StringBuilder search = new StringBuilder();
      addSearch( search, symbol.getName() );
      addSearch( search, symbol.getFullThName() );
      addSearch( search, symbol.getGroup() );
      addSearch( search, mCategory );
      addSearch( search, ToolsetCategory.label( mCategory ) );
      addSearch( search, mSection );
      addSearch( search, ToolsetCategory.sectionLabel( mSection ) );
      for ( String term : symbol.getSearchTerms() ) addSearch( search, term );
      mSearchText = normalize( search.toString() );
    }

    ToolsetProfile.Slot ref() { return new ToolsetProfile.Slot( mType, mSymbol.getFullThName() ); }

    String typeMark()
    {
      if ( mType == SymbolType.POINT ) return "pt";
      if ( mType == SymbolType.LINE ) return "ln";
      return "ar";
    }

    boolean matches( String query )
    {
      String normalized = normalize( query );
      if ( normalized.length() == 0 ) return true;
      for ( String token : normalized.split( " " ) ) if ( ! mSearchText.contains( token ) ) return false;
      return true;
    }
  }

  private ToolsetCatalog() { }

  static ArrayList< Entry > all()
  {
    ArrayList< Entry > entries = new ArrayList<>();
    addLibrary( entries, SymbolType.POINT, BrushManager.getPointLib() );
    addLibrary( entries, SymbolType.LINE, BrushManager.getLineLib() );
    addLibrary( entries, SymbolType.AREA, BrushManager.getAreaLib() );
    Collections.sort( entries, new Comparator< Entry >() {
      @Override public int compare( Entry left, Entry right ) {
        int category = ToolsetCategory.orderedIds().indexOf( left.mCategory ) - ToolsetCategory.orderedIds().indexOf( right.mCategory );
        if ( category != 0 ) return category;
        int section = sectionOrder( left.mSection ) - sectionOrder( right.mSection );
        if ( section != 0 ) return section;
        int name = left.mSymbol.getName().compareToIgnoreCase( right.mSymbol.getName() );
        if ( name != 0 ) return name;
        return left.mType - right.mType;
      }
    } );
    return entries;
  }

  static ArrayList< Entry > filter( List< Entry > source, String category, int type, String query )
  {
    ArrayList< Entry > filtered = new ArrayList<>();
    boolean searching = normalize( query ).length() > 0;
    if ( source == null ) return filtered;
    for ( Entry entry : source ) {
      if ( type != SymbolType.UNDEF && entry.mType != type ) continue;
      if ( ! searching && ! ToolsetCategory.normalizeId( category ).equals( entry.mCategory ) ) continue;
      if ( ! entry.matches( query ) ) continue;
      filtered.add( entry );
    }
    return filtered;
  }

  static Symbol resolve( ToolsetProfile.Slot slot )
  {
    if ( slot == null || ! slot.isValid() ) return null;
    switch ( slot.mType ) {
      case SymbolType.POINT: return BrushManager.getPointByThName( slot.mFullThName );
      case SymbolType.LINE: return BrushManager.getLineByThName( slot.mFullThName );
      case SymbolType.AREA: return BrushManager.getAreaByThName( slot.mFullThName );
    }
    return null;
  }

  static int resolveIndex( ToolsetProfile.Slot slot )
  {
    if ( slot == null || ! slot.isValid() ) return -1;
    switch ( slot.mType ) {
      case SymbolType.POINT: return BrushManager.getPointIndexByThName( slot.mFullThName );
      case SymbolType.LINE: return BrushManager.getLineIndexByThName( slot.mFullThName );
      case SymbolType.AREA: return BrushManager.getAreaIndexByThName( slot.mFullThName );
    }
    return -1;
  }

  static Entry find( List< Entry > entries, ToolsetProfile.Slot slot )
  {
    if ( entries == null || slot == null ) return null;
    for ( Entry entry : entries ) if ( entry.mType == slot.mType && entry.mSymbol.getFullThName().equals( slot.mFullThName ) ) return entry;
    return null;
  }

  private static void addLibrary( ArrayList< Entry > entries, int type, SymbolLibrary library )
  {
    if ( library == null ) return;
    for ( int index = 0; index < library.size(); ++index ) {
      Symbol symbol = library.getSymbolByIndex( index );
      if ( symbol != null && eligible( type, symbol ) ) entries.add( new Entry( type, index, symbol ) );
    }
  }

  private static boolean eligible( int type, Symbol symbol )
  {
    String name = symbol.getThName();
    if ( name == null ) return false;
    if ( type == SymbolType.POINT ) {
      return ! Symbol.deprefix_u( SymbolLibrary.USER ).equals( name )
          && ! SymbolLibrary.SECTION.equals( name )
          && ! SymbolLibrary.PICTURE.equals( name )
          && ! SymbolLibrary.REFERENCE.equals( name );
    }
    if ( type == SymbolType.AREA ) return ! Symbol.deprefix_u( SymbolLibrary.USER ).equals( name );
    return type == SymbolType.LINE;
  }

  static String normalize( String value )
  {
    if ( value == null ) return "";
    String normalized = Normalizer.normalize( value, Normalizer.Form.NFD )
      .replaceAll( "\\p{M}+", "" )
      .toLowerCase( Locale.ROOT )
      .replaceAll( "[^a-z0-9]+", " " )
      .trim();
    return normalized.replaceAll( "\\s+", " " );
  }

  private static void addSearch( StringBuilder search, String value )
  {
    if ( value == null || value.length() == 0 ) return;
    if ( search.length() > 0 ) search.append( ' ' );
    search.append( value );
  }

  private static int sectionOrder( String section )
  {
    if ( ToolsetCategory.SECTION_FORMATIONS.equals( section ) ) return 0;
    if ( ToolsetCategory.SECTION_MINERAL_POOL.equals( section ) ) return 1;
    if ( ToolsetCategory.SECTION_GYPSUM.equals( section ) ) return 2;
    return 3;
  }
}
