package com.topodroid.TDX;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.topodroid.num.TDNum;
import com.topodroid.types.BlockType;
import com.topodroid.types.PlotType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

@RunWith( AndroidJUnit4.class )
public class SectionReferencesInstrumentedTest
{
  private DBlock block( long id, String from, String to, float length, float bearing, float clino )
  {
    DBlock block = new DBlock( from, to, length, bearing, clino, 0, 1,
      to.isEmpty() || from.isEmpty() ? BlockType.SPLAY : BlockType.MAIN_LEG, 0 );
    block.mId = id;
    return block;
  }

  private List<DBlock> survey()
  {
    return new ArrayList<>( Arrays.asList(
      block( 1, "A", "B", 10, 0, 0 ), block( 2, "A", "C", 2, 90, 0 ),
      block( 3, "C", "D", 10, 0, 0 ),
      block( 4, "A", "", 3, 90, 0 ), block( 5, "B", "", 3, 90, 0 ),
      block( 6, "C", "", 3, 90, 0 ), block( 7, "D", "", 3, 90, 0 ),
      block( 8, "X", "Y", 5, 90, 0 ) ) );
  }

  private PlotInfo plot( long type, String start, String view )
  {
    PlotInfo plot = new PlotInfo();
    plot.type = (int)type; plot.start = start; plot.view = view;
    plot.intercept = 0.5f; plot.azimuth = 45;
    plot.center = new Vector3D( 1, -5, 0 );
    return plot;
  }

  private SectionReferenceBuilder geometry( PlotInfo plot, List<DBlock> blocks, SectionReferenceSelection selection )
  {
    return new SectionReferenceBuilder( plot, blocks, new TDNum( blocks, "A", 0, null ), selection, 60, 70 );
  }

  private long legCount( SectionReferenceBuilder geometry )
  {
    long count = 0;
    for ( SectionReferenceBuilder.Shot shot : geometry.shots ) if ( ! shot.splay ) ++count;
    return count;
  }

  private long splayCount( SectionReferenceBuilder geometry ) { return geometry.shots.size() - legCount( geometry ); }

  @Test public void addedParallelStationsShowAllConnectedLegsAndIndependentSplays()
  {
    List<DBlock> blocks = survey();
    PlotInfo plot = plot( PlotType.PLOT_SECTION, "A", "B" );
    SectionReferenceSelection choices = SectionReferenceRepository.defaults( plot, blocks );
    assertEquals( new java.util.HashSet<>( Arrays.asList( "A", "B" ) ), choices.stations );
    SectionReferenceBuilder initial = geometry( plot, blocks, choices );
    assertEquals( 2, legCount( initial ) );
    assertEquals( 2, splayCount( initial ) );
    choices.setStation( "A", false ); choices.setStation( "C", true ); choices.setSplays( "C", false );
    SectionReferenceBuilder changed = geometry( plot, blocks, choices );
    assertEquals( 3, legCount( changed ) );
    assertEquals( 1, splayCount( changed ) );
    assertFalse( changed.stations.containsKey( "A" ) );
    assertFalse( changed.stations.containsKey( "D" ) );
    assertEquals( initial.positions.get( "A" ).x, changed.positions.get( "A" ).x, 0 );
    assertEquals( initial.positions.get( "B" ).x, changed.positions.get( "B" ).x, 0 );
    assertFalse( changed.positions.containsKey( "X" ) );
    assertEquals( initial.positions.get( "A" ).x + (float)Math.sqrt( 2 ), changed.positions.get( "C" ).x, 0.0001f );
  }

  @Test public void anchorUsesRawDefiningLegEvenWhenReducedLegIsAveraged()
  {
    List<DBlock> blocks = survey();
    DBlock repeated = block( 20, "", "", 10.2f, 0, 0 );
    repeated.resetBlockType( BlockType.SEC_LEG );
    blocks.add( 1, repeated );
    PlotInfo plot = plot( PlotType.PLOT_SECTION, "A", "B" );
    SectionReferenceBuilder geometry = geometry( plot, blocks, SectionReferenceRepository.defaults( plot, blocks ) );
    assertEquals( (float)( 5 / Math.sqrt( 2 ) ), geometry.positions.get( "A" ).x, 0.0001f );
    assertEquals( -(float)( 5 / Math.sqrt( 2 ) ), geometry.positions.get( "B" ).x, 0.0001f );
    assertEquals( 2, legCount( geometry ) );
  }

  @Test public void reversedDefiningLegRetainsInterceptAndOrigin()
  {
    List<DBlock> blocks = survey();
    PlotInfo plot = plot( PlotType.PLOT_SECTION, "B", "A" ); plot.intercept = 0.25f;
    SectionReferenceBuilder geometry = geometry( plot, blocks, SectionReferenceRepository.defaults( plot, blocks ) );
    assertEquals( -(float)( 2.5 / Math.sqrt( 2 ) ), geometry.positions.get( "B" ).x, 0.0001f );
    assertEquals( (float)( 7.5 / Math.sqrt( 2 ) ), geometry.positions.get( "A" ).x, 0.0001f );
    assertEquals( 1, geometry.dots.size() );
  }

  @Test public void multilegDefaultsDeduplicateStationsAndRetainStoredCenter()
  {
    List<DBlock> blocks = survey();
    PlotInfo plot = plot( PlotType.PLOT_SECTION, "1 3", "" );
    SectionReferenceSelection choices = SectionReferenceRepository.defaults( plot, blocks );
    assertEquals( 4, choices.stations.size() );
    SectionReferenceBuilder geometry = geometry( plot, blocks, choices );
    assertEquals( 3, legCount( geometry ) );
    assertEquals( 4, splayCount( geometry ) );
    choices.setStation( "A", false ); choices.setStation( "B", false );
    SectionReferenceBuilder changed = geometry( plot, blocks, choices );
    assertEquals( geometry.positions.get( "C" ).x, changed.positions.get( "C" ).x, 0 );
    assertEquals( 1, plot.center.x, 0 ); assertEquals( -5, plot.center.y, 0 );
    assertEquals( 2, changed.stations.size() );
  }

  @Test public void stationSectionsAndEmptySelectionsHaveStableOrigin()
  {
    List<DBlock> blocks = survey();
    for ( long type : new long[] { PlotType.PLOT_X_SECTION, PlotType.PLOT_XH_SECTION } ) {
      PlotInfo plot = plot( type, "A", "" );
      SectionReferenceSelection choices = SectionReferenceRepository.defaults( plot, blocks );
      assertEquals( 1, choices.stations.size() );
      SectionReferenceBuilder initial = geometry( plot, blocks, choices );
      assertEquals( 0, initial.positions.get( "A" ).x, 0 );
      assertEquals( 2, legCount( initial ) );
      choices.setStation( "A", false );
      SectionReferenceBuilder empty = geometry( plot, blocks, choices );
      assertTrue( empty.stations.isEmpty() ); assertTrue( empty.shots.isEmpty() );
      assertTrue( empty.dots.isEmpty() );
    }
  }

  @Test public void horizontalSectionKeepsLegacyNorthUpRotation()
  {
    List<DBlock> blocks = survey();
    PlotInfo plot = plot( PlotType.PLOT_H_SECTION, "A", "B" );
    plot.azimuth = 0; plot.clino = 90;
    SectionReferenceSelection choices = SectionReferenceRepository.defaults( plot, blocks );
    SectionReferenceBuilder geometry = geometry( plot, blocks, choices );
    assertEquals( 0, geometry.positions.get( "A" ).x, 0.0001 );
    assertEquals( 5, geometry.positions.get( "A" ).y, 0.0001 );
    assertEquals( -5, geometry.positions.get( "B" ).y, 0.0001 );
    assertEquals( 2, geometry.northLength, 0.0001 );
    choices.setStation( "C", true );
    assertEquals( geometry.positions.get( "A" ).y, geometry( plot, blocks, choices ).positions.get( "A" ).y, 0 );
  }

  @Test public void outOfPlaneFilteringUsesSameAngleAsPaintAndPreservesOtherSections()
  {
    List<DBlock> blocks = new ArrayList<>( Arrays.asList(
      block( 1, "A", "B", 10, 0, 0 ), block( 2, "A", "", 2, 90, 0 ),
      block( 3, "A", "", 2, 0, 0 ), block( 4, "", "A", 2, 180, 0 ) ) );
    PlotInfo plot = plot( PlotType.PLOT_X_SECTION, "A", "" ); plot.azimuth = 0;
    SectionReferenceSelection original = SectionReferenceRepository.defaults( plot, blocks );
    SectionReferenceSelection filtered = new SectionReferenceSelection( original ); filtered.showOutOfPlane = false;
    assertEquals( 3, splayCount( geometry( plot, blocks, original ) ) );
    SectionReferenceBuilder geometry = geometry( plot, blocks, filtered );
    assertEquals( 1, splayCount( geometry ) );
    for ( SectionReferenceBuilder.Shot shot : geometry.shots ) if ( shot.splay ) assertTrue( Math.abs( shot.angle ) <= 60 );
    assertEquals( 3, splayCount( geometry( plot, blocks, original ) ) );
  }

  @Test public void tiltedHorizontalSectionPreservesLegacySplayClassification()
  {
    List<DBlock> blocks = Arrays.asList( block( 1, "A", "B", 10, 0, 0 ), block( 2, "A", "", 3, 90, 0 ) );
    PlotInfo plot = plot( PlotType.PLOT_H_SECTION, "A", "B" ); plot.clino = 80;
    SectionReferenceBuilder geometry = geometry( plot, blocks, SectionReferenceRepository.defaults( plot, blocks ) );
    for ( SectionReferenceBuilder.Shot shot : geometry.shots ) if ( shot.splay ) assertEquals( 15.50356f, shot.angle, 0.001f );
  }

  @Test public void shotRefreshAddsNewConnectedGeometryWithoutSelectingNewLabels()
  {
    List<DBlock> blocks = survey();
    PlotInfo plot = plot( PlotType.PLOT_X_SECTION, "A", "" );
    SectionReferenceSelection choices = SectionReferenceRepository.defaults( plot, blocks );
    SectionReferenceBuilder initial = geometry( plot, blocks, choices );
    blocks.add( block( 30, "A", "E", 2, 270, 0 ) );
    blocks.add( block( 31, "A", "", 2, 270, 0 ) );
    SectionReferenceBuilder refreshed = geometry( plot, blocks, choices );
    assertEquals( legCount( initial ) + 1, legCount( refreshed ) );
    assertEquals( splayCount( initial ) + 1, splayCount( refreshed ) );
    assertFalse( refreshed.stations.containsKey( "E" ) );
  }

  @Test public void persistenceArchiveRenameAndDeletionRespectSectionIdentity() throws Exception
  {
    DataHelper data = TopoDroidApp.mData;
    if ( data == null ) data = new DataHelper( InstrumentationRegistry.getInstrumentation().getTargetContext() );
    long sid = data.setSurvey( "section_choices_" + System.nanoTime(), 0 );
    long other = data.setSurvey( "section_choices_other_" + System.nanoTime(), 0 );
    try {
      long pid = data.insertPlot( sid, -1, "xx0", PlotType.PLOT_SECTION, 0, "A", "B", 0, 0, 1, 45, 0, "", "", 0 );
      long second = data.insertPlot( sid, -1, "xx1", PlotType.PLOT_X_SECTION, 0, "C", "", 0, 0, 1, 45, 0, "", "", 0 );
      data.insertPlot( sid, -1, "multi", PlotType.PLOT_SECTION, 0, "1 3", "", 0, 0, 1, 45, 0, "", "", 0 );
      long imported = data.insertPlot( other, -1, "xx0", PlotType.PLOT_SECTION, 0, "A", "B", 0, 0, 1, 45, 0, "", "", 0 );
      SectionReferenceSelection choices = new SectionReferenceSelection();
      choices.setStation( "C", true ); choices.setSplays( "A", false ); choices.showOutOfPlane = false;
      assertTrue( SectionReferenceRepository.save( data, sid, pid, choices ) );
      assertNull( data.getValue( SectionReferenceRepository.key( sid, second ) ) );
      assertFalse( SectionReferenceRepository.load( data, sid, data.getPlotInfo( sid, "xx0" ), survey() ).showOutOfPlane );
      String archive = SectionReferenceRepository.exportArchive( data, sid );
      assertTrue( SectionReferenceRepository.importArchive( data, other, archive ) );
      assertTrue( SectionReferenceRepository.load( data, other, data.getPlotInfo( other, "xx0" ), survey() ).includes( "C" ) );
      assertFalse( SectionReferenceRepository.importArchive( data, other, "{\"version\":99}" ) );
      assertTrue( SectionReferenceRepository.importArchive( data, other, null ) );
      assertFalse( SectionReferenceRepository.save( data, sid, 999999, choices ) );
      data.remapStationNames( sid, Arrays.asList( new StationMap( "C", "Z" ), new StationMap( "A", "Q" ) ) );
      SectionReferenceSelection renamed = SectionReferenceRepository.load( data, sid, data.getPlotInfo( sid, "xx0" ), survey() );
      assertTrue( renamed.includes( "Z" ) ); assertTrue( renamed.hiddenSplays.contains( "Q" ) );
      data.updatePlotName( sid, pid, "renamed" );
      data.deletePlot( pid, sid );
      assertNotNull( data.getValue( SectionReferenceRepository.key( sid, pid ) ) );
      data.undeletePlot( pid, sid );
      data.prefixSurveyStations( sid, "P-" );
      assertTrue( SectionReferenceRepository.load( data, sid, data.getPlotInfo( sid, "renamed" ), survey() ).includes( "P-Z" ) );
      assertEquals( "1 3", data.getPlotInfo( sid, "multi" ).start );
      data.deletePlotByName( "renamed", sid );
      assertNull( data.getValue( SectionReferenceRepository.key( sid, pid ) ) );
      data.dropPlot( imported, other );
      assertNull( data.getValue( SectionReferenceRepository.key( other, imported ) ) );
      assertTrue( SectionReferenceRepository.save( data, sid, second, choices ) );
      data.doDeleteSurvey( sid );
      assertNull( data.getValue( SectionReferenceRepository.key( sid, second ) ) );
    } finally { data.doDeleteSurvey( sid ); data.doDeleteSurvey( other ); }
  }

  @Test public void codecPreservesUnavailableStationsAndRejectsBadMetadata() throws Exception
  {
    SectionReferenceSelection choices = new SectionReferenceSelection();
    choices.setStation( "unknown station", true ); choices.setSplays( "A", false ); choices.showOutOfPlane = false;
    SectionReferenceSelection reopened = SectionReferenceRepository.decode( new JSONObject( SectionReferenceRepository.encode( choices ).toString() ) );
    assertEquals( choices.stations, reopened.stations ); assertEquals( choices.hiddenSplays, reopened.hiddenSplays );
    assertFalse( reopened.showOutOfPlane );
    SectionReferenceBuilder geometry = geometry( plot( PlotType.PLOT_X_SECTION, "A", "" ), survey(), reopened );
    assertTrue( geometry.stations.isEmpty() ); assertTrue( reopened.includes( "unknown station" ) );
    try { SectionReferenceRepository.decode( new JSONObject( "{\"version\":1,\"stations\":[3],\"hiddenSplays\":[],\"showOutOfPlane\":true}" ) ); fail(); }
    catch ( org.json.JSONException expected ) { }
  }
}
