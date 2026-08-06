/* @file PlotSymbolUsageSnapshot.java
 *
 * @brief Immutable inventory of symbols used by one plot
 */
package com.topodroid.TDX;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

final class PlotSymbolUsageSnapshot
{
  enum Kind { POINT, LINE, AREA }

  static final class Occurrence
  {
    final Kind kind;
    final String thName;
    final float x;
    final float y;
    final int scrap;

    Occurrence( Kind kind, String th_name, float x, float y, int scrap )
    {
      this.kind = kind;
      thName = th_name;
      this.x = x;
      this.y = y;
      this.scrap = scrap;
    }
  }

  static final class Entry
  {
    final Kind kind;
    final String thName;
    final String displayName;
    final int libraryIndex;

    Entry( Kind kind, String th_name, String display_name, int library_index )
    {
      this.kind = kind;
      thName = th_name;
      displayName = display_name;
      libraryIndex = library_index;
    }

    String identity() { return PlotSymbolUsageSnapshot.identity( kind, thName ); }
  }

  /** A tiny command-lock snapshot. Names are resolved after the drawing lock is released. */
  static final class RawOccurrence
  {
    final Kind kind;
    final int libraryIndex;
    final float x;
    final float y;
    final int scrap;

    RawOccurrence( Kind kind, int library_index, float x, float y, int scrap )
    {
      this.kind = kind;
      libraryIndex = library_index;
      this.x = x;
      this.y = y;
      this.scrap = scrap;
    }
  }

  private static final PlotSymbolUsageSnapshot EMPTY =
    new PlotSymbolUsageSnapshot( Collections.< Occurrence >emptyList(),
                                 Collections.< Entry >emptyList() );

  private final List< Occurrence > mOccurrences;
  private final List< Entry > mEntries;
  private final Set< String > mIdentities;

  private PlotSymbolUsageSnapshot( List< Occurrence > occurrences, List< Entry > entries )
  {
    mOccurrences = Collections.unmodifiableList( new ArrayList<>( occurrences ) );
    mEntries = Collections.unmodifiableList( new ArrayList<>( entries ) );
    HashSet< String > identities = new HashSet<>();
    for ( Entry entry : entries ) identities.add( entry.identity() );
    mIdentities = Collections.unmodifiableSet( identities );
  }

  static PlotSymbolUsageSnapshot empty() { return EMPTY; }

  static PlotSymbolUsageSnapshot fromEntries( List< Entry > entries )
  {
    if ( entries == null || entries.isEmpty() ) return EMPTY;
    ArrayList< Entry > copy = new ArrayList<>( entries );
    Collections.sort( copy, new Comparator< Entry >() {
      @Override public int compare( Entry left, Entry right )
      {
        int kind = left.kind.ordinal() - right.kind.ordinal();
        return kind != 0 ? kind : left.libraryIndex - right.libraryIndex;
      }
    } );
    return new PlotSymbolUsageSnapshot( Collections.< Occurrence >emptyList(), copy );
  }

  List< Occurrence > occurrences() { return mOccurrences; }

  List< Entry > entries() { return mEntries; }

  boolean contains( Kind kind, String th_name )
  {
    return mIdentities.contains( identity( kind, th_name ) );
  }

  static String identity( Kind kind, String th_name )
  {
    return ( kind == null ? "" : kind.name() ) + ":" + ( th_name == null ? "" : th_name );
  }

  /** Resolve a lock-safe list of indices into stable names and deterministic library order. */
  static PlotSymbolUsageSnapshot resolve( List< RawOccurrence > raw )
  {
    if ( raw == null || raw.isEmpty() ) return EMPTY;
    ArrayList< Occurrence > occurrences = new ArrayList<>();
    LinkedHashMap< String, Entry > distinct = new LinkedHashMap<>();
    for ( RawOccurrence item : raw ) {
      if ( item == null ) continue;
      String th_name = fullThName( item.kind, item.libraryIndex );
      if ( th_name == null || th_name.length() == 0 ) continue;
      if ( item.kind == Kind.POINT && TitleLegendPointBehavior.isTitleLegendName( th_name ) ) continue;
      String label = displayName( item.kind, item.libraryIndex );
      occurrences.add( new Occurrence( item.kind, th_name, item.x, item.y, item.scrap ) );
      String identity = identity( item.kind, th_name );
      if ( ! distinct.containsKey( identity ) ) {
        distinct.put( identity, new Entry( item.kind, th_name, label, item.libraryIndex ) );
      }
    }
    ArrayList< Entry > entries = new ArrayList<>( distinct.values() );
    Collections.sort( entries, new Comparator< Entry >() {
      @Override public int compare( Entry left, Entry right )
      {
        int kind = left.kind.ordinal() - right.kind.ordinal();
        if ( kind != 0 ) return kind;
        return left.libraryIndex - right.libraryIndex;
      }
    } );
    return new PlotSymbolUsageSnapshot( occurrences, entries );
  }

  private static String fullThName( Kind kind, int index )
  {
    if ( kind == Kind.POINT ) return BrushManager.getPointFullThName( index );
    if ( kind == Kind.LINE ) return BrushManager.getLineFullThName( index );
    return BrushManager.getAreaFullThName( index );
  }

  private static String displayName( Kind kind, int index )
  {
    if ( kind == Kind.POINT ) return BrushManager.getPointName( index );
    if ( kind == Kind.LINE ) return BrushManager.getLineName( index );
    return BrushManager.getAreaName( index );
  }
}
