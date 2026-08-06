package com.topodroid.TDX;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import org.junit.Test;

public class TitleLegendModelTest
{
  @Test public void capacity_expandsWithoutMutatingRequest()
  {
    TitleLegendPointState.Capacity capacity = new TitleLegendPointState.Capacity( 7, 2, 3 );
    assertEquals( 2, capacity.requestedColumns );
    assertEquals( 3, capacity.rowsPerColumn );
    assertEquals( 3, capacity.requiredColumns );
    assertEquals( 3, capacity.renderedColumns );
    assertEquals( 1, capacity.shortfall );
    assertTrue( capacity.expanded );
    assertEquals( 3, capacity.rowsInColumn( 0 ) );
    assertEquals( 2, capacity.rowsInColumn( 1 ) );
    assertEquals( 2, capacity.rowsInColumn( 2 ) );
  }

  @Test public void capacity_matchesSettledExamples()
  {
    assertColumns( 6, 2, 3, 2, 3, 3 );
    assertColumns( 4, 3, 6, 3, 2, 1, 1 );
    assertColumns( 2, 5, 6, 2, 1, 1 );
    TitleLegendPointState.Capacity empty = new TitleLegendPointState.Capacity( 0, 2, 3 );
    assertEquals( 0, empty.renderedColumns );
    assertFalse( empty.expanded );
  }

  @Test public void shapeCopy_preservesRequestedValuesEvenWhenExpanded()
  {
    ArrayList< TitleLegendPointState.Row > rows = new ArrayList<>();
    for ( int i = 0; i < 7; ++i ) rows.add( TitleLegendPointState.Row.custom() );
    TitleLegendPointState state = new TitleLegendPointState( "object", false, true, 2, 3,
      SketchTextStyle.defaultStyle(), rows, Collections.< String >emptySet() );
    assertEquals( 2, state.requestedColumns );
    assertEquals( 3, state.rowsPerColumn );
    assertEquals( 3, state.capacity().renderedColumns );
    TitleLegendPointState reopened = state.withShape( state.requestedColumns, state.rowsPerColumn );
    assertEquals( 2, reopened.requestedColumns );
    assertEquals( 3, reopened.rowsPerColumn );
  }

  @Test public void rescan_appendsNewSymbolsAndHonorsTombstones()
  {
    PlotSymbolUsageSnapshot.Entry stalactite = new PlotSymbolUsageSnapshot.Entry(
      PlotSymbolUsageSnapshot.Kind.POINT, "stalactite", "Stalactite", 5 );
    PlotSymbolUsageSnapshot.Entry folia = new PlotSymbolUsageSnapshot.Entry(
      PlotSymbolUsageSnapshot.Kind.POINT, "u:folia", "Folia", 8 );
    TitleLegendPointState.Row existing = TitleLegendPointState.Row.fromEntry( stalactite );
    HashSet< String > dismissed = new HashSet<>();
    dismissed.add( "POINT:u:folia" );
    TitleLegendPointState state = new TitleLegendPointState( "object", false, true, 2, 1,
      SketchTextStyle.defaultStyle(), Arrays.asList( existing ), dismissed );
    TitleLegendPointState merged = state.rescan( PlotSymbolUsageSnapshot.fromEntries(
      Arrays.asList( stalactite, folia ) ) );
    assertEquals( 1, merged.rows.size() );

    TitleLegendPointState without_tombstone = new TitleLegendPointState( "object", false, true, 2, 1,
      SketchTextStyle.defaultStyle(), Arrays.asList( existing ), Collections.< String >emptySet() );
    merged = without_tombstone.rescan( PlotSymbolUsageSnapshot.fromEntries(
      Arrays.asList( stalactite, folia ) ) );
    assertEquals( 2, merged.rows.size() );
    assertEquals( "u:folia", merged.rows.get( 1 ).thName );
  }

  @Test public void labelStyle_isAlwaysWorldStableAndTableAligned()
  {
    SketchTextStyle screen_right = SketchTextStyle.of( SketchFontRegistry.FONT_DEFAULT,
      SketchTextStyle.SizeMode.SCREEN, 48.0f, false, false, false,
      SketchTextStyle.Alignment.RIGHT, 0xffffffff );
    TitleLegendPointState state = new TitleLegendPointState( "object", false, true, 2, 1,
      screen_right, Collections.< TitleLegendPointState.Row >emptyList(),
      Collections.< String >emptySet() );
    assertEquals( SketchTextStyle.SizeMode.AUTO_GRID, state.textStyle.sizeMode() );
    assertEquals( SketchTextStyle.Alignment.LEFT, state.textStyle.alignment() );
  }

  @Test public void legendScale_clampsAndSurvivesPresentationCopies()
  {
    TitleLegendPointState state = new TitleLegendPointState( "scaled", false, true, 2, 3,
      0.72f, SketchTextStyle.defaultStyle(),
      Collections.< TitleLegendPointState.Row >emptyList(), Collections.< String >emptySet() );
    assertEquals( 0.72f, state.withShape( 1, 1 ).legendScale, 0.001f );
    assertEquals( TitleLegendPointState.MIN_LEGEND_SCALE,
      state.withLegendScale( 0.01f ).legendScale, 0.001f );
    assertEquals( TitleLegendPointState.MAX_LEGEND_SCALE,
      state.withLegendScale( 9.0f ).legendScale, 0.001f );

    TitleLegendPointState older_default = new TitleLegendPointState( "default", false, true, 2, 3,
      SketchTextStyle.defaultStyle(), Collections.< TitleLegendPointState.Row >emptyList(),
      Collections.< String >emptySet() );
    assertEquals( 1.0f, older_default.legendScale, 0.001f );
  }

  private static void assertColumns( int count, int requested, int rows_per_column,
                                     int rendered, int... row_counts )
  {
    TitleLegendPointState.Capacity capacity =
      new TitleLegendPointState.Capacity( count, requested, rows_per_column );
    assertEquals( rendered, capacity.renderedColumns );
    assertEquals( rendered, row_counts.length );
    for ( int i = 0; i < rendered; ++i ) assertEquals( row_counts[i], capacity.rowsInColumn( i ) );
  }
}
