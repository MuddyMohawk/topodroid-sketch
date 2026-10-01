package com.topodroid.TDX;

import com.topodroid.math.TDVector;
import com.topodroid.num.TDNum;
import com.topodroid.num.NumStation;
import com.topodroid.num.NumShot;
import com.topodroid.types.PlotType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/** Shared world-coordinate geometry for the editor, placed sections, and exports. */
final class SectionReferenceBuilder
{
  static final class Position
  {
    final float x, y, z;
    Position( float x, float y, float z ) { this.x = x; this.y = y; this.z = z; }
  }

  static final class Shot
  {
    final DBlock block;
    final Position from, to;
    final boolean splay, viewed;
    final float angle;
    Shot( DBlock block, Position from, Position to, boolean splay, boolean viewed, float angle )
    {
      this.block = block; this.from = from; this.to = to;
      this.splay = splay; this.viewed = viewed; this.angle = angle;
    }
  }

  final Map<String,Position> positions = new LinkedHashMap<>();
  final Map<String,Position> stations = new LinkedHashMap<>();
  final List<Shot> shots = new ArrayList<>();
  final List<Position> dots = new ArrayList<>();
  float northLength;

  private final PlotInfo plot;
  private final TDVector normal, horizontal, up;
  private final boolean rotateHorizontal;
  private float northX = 0, northY = -1;

  SectionReferenceBuilder( PlotInfo plot, List<DBlock> blocks, TDNum num,
                           SectionReferenceSelection selection, float threshold, float horizontalThreshold )
  {
    this.plot = plot;
    boolean multi = PlotType.isMultilegSection( plot.type, plot.view );
    rotateHorizontal = ! multi && plot.type == PlotType.PLOT_H_SECTION;
    double azimuth = Math.toRadians( plot.azimuth ), clino = Math.toRadians( plot.clino );
    normal = new TDVector( (float)( Math.cos( azimuth ) * Math.cos( clino ) ),
                           (float)( Math.sin( azimuth ) * Math.cos( clino ) ), (float)Math.sin( clino ) );
    horizontal = new TDVector( -(float)Math.sin( azimuth ), (float)Math.cos( azimuth ), 0 );
    up = normal.cross( horizontal );
    if ( rotateHorizontal && Math.abs( plot.clino ) > horizontalThreshold ) {
      northX = horizontal.x; northY = -up.x;
      float length = (float)Math.hypot( northX, northY );
      if ( length > 0 ) northLength = 2 / length;
    }

    DBlock anchor = null;
    if ( ! multi && PlotType.isLegSection( plot.type ) ) {
      for ( DBlock block : blocks ) if ( block.isLeg() && connects( block, plot.start, plot.view ) ) { anchor = block; break; }
    }
    if ( ! multi ) {
      if ( PlotType.isStationSection( plot.type ) ) positions.put( plot.start, new Position( 0, 0, 0 ) );
      else if ( anchor != null ) {
        // Retain the legacy local positions, even when reduction averages repeated legs.
        float distance = anchor.mLength * ( plot.start.equals( anchor.mFrom ) ? 1 : -1 );
        float fraction = plot.intercept >= 0 && plot.intercept <= 1 ? plot.intercept : 1;
        TDVector vector = anchor.getUnitVector();
        positions.put( plot.start, projectLegacy( vector, -distance * fraction ) );
        positions.put( plot.view, projectLegacy( vector, distance * ( 1 - fraction ) ) );
        if ( plot.intercept >= 0 && plot.intercept <= 1 && selection.includesLeg( plot.start, plot.view ) ) {
          dots.add( new Position( 0, 0, 0 ) );
        }
      }
    }

    if ( num != null ) {
      if ( multi && plot.center != null ) {
        for ( NumStation station : num.getStations() ) {
          positions.put( station.name, project( new TDVector( (float)( plot.center.y - station.s ),
            (float)( station.e - plot.center.x ), (float)( plot.center.z - station.v ) ) ) );
        }
      } else {
        NumStation origin = num.getStation( plot.start );
        Position base = positions.get( plot.start );
        if ( origin != null && base != null ) for ( NumStation station : num.getStations() ) {
          if ( positions.containsKey( station.name ) ) continue;
          Position offset = project( new TDVector( (float)( origin.s - station.s ),
            (float)( station.e - origin.e ), (float)( origin.v - station.v ) ) );
          positions.put( station.name, new Position( base.x + offset.x, base.y + offset.y, base.z + offset.z ) );
        }
      }
    }

    for ( String station : selection.stations ) {
      Position position = positions.get( station );
      if ( position != null ) stations.put( station, position );
    }

    Set<Long> added = new HashSet<>();
    if ( anchor != null ) addLeg( anchor, anchor.mFrom, anchor.mTo, selection, added, multi );
    if ( num != null ) for ( NumShot shot : num.getShots() ) {
      DBlock block = shot.getFirstBlock();
      // Reduction handles repeats and paired backsights; never redraw the defining pair.
      if ( anchor != null && connects( block, plot.start, plot.view ) ) continue;
      addLeg( block, shot.from.name, shot.to.name, selection, added, multi );
    }

    for ( DBlock block : blocks ) {
      if ( ! block.isSplay() ) continue;
      String station = block.isReverseSplay() ? block.mTo : block.mFrom;
      Position from = positions.get( station );
      if ( from == null || ! selection.includesSplays( station ) ) continue;
      float length = block.mLength * ( block.isReverseSplay() ? -1 : 1 );
      TDVector vector = block.getUnitVector();
      Position delta = project( vector.times( length ) );
      float angle;
      if ( multi ) {
        angle = (float)Math.toDegrees( Math.asin( clamp( vector.dot( normal ) * ( length < 0 ? -1 : 1 ) ) ) );
      } else {
        // Legacy sections classify the endpoint relative to the section origin.
        float z = -from.z + length * vector.dot( normal );
        // Classification precedes the legacy north-up rotation of the splay delta.
        float x = from.x + length * vector.dot( horizontal ), y = from.y + length * vector.dot( up );
        float distance = (float)Math.sqrt( x*x + y*y + z*z );
        angle = distance > 0 ? (float)Math.toDegrees( Math.asin( clamp( z / distance ) ) ) : 90;
      }
      if ( ! selection.showOutOfPlane && SectionReferenceSelection.isOutOfPlane( angle, threshold ) ) continue;
      shots.add( new Shot( block, from, new Position( from.x + delta.x, from.y + delta.y, from.z + delta.z ),
        true, plot.view != null && plot.view.equals( station ), angle ) );
    }
  }

  private static float clamp( float value ) { return Math.max( -1, Math.min( 1, value ) ); }

  private static boolean connects( DBlock block, String from, String to )
  {
    return from != null && to != null && ( from.equals( block.mFrom ) && to.equals( block.mTo )
      || from.equals( block.mTo ) && to.equals( block.mFrom ) );
  }

  private Position projectLegacy( TDVector vector, float length )
  {
    return project( vector.times( length ) );
  }

  private Position project( TDVector vector )
  {
    float x = vector.dot( horizontal ), y = -vector.dot( up );
    if ( rotateHorizontal ) {
      float xx = -northY * x + northX * y;
      y = -northX * x - northY * y; x = xx;
    }
    return new Position( x, y, vector.dot( normal ) );
  }

  private void addLeg( DBlock block, String fromName, String toName,
                       SectionReferenceSelection selection, Set<Long> added, boolean multi )
  {
    if ( ! selection.includesLeg( fromName, toName ) ) return;
    Position from = positions.get( fromName ), to = positions.get( toName );
    if ( from == null || to == null || ! added.add( block.mId ) ) return;
    shots.add( new Shot( block, from, to, false, false, 0 ) );
    if ( multi ) {
      float denominator = from.z - to.z;
      if ( Math.abs( denominator ) > 1e-6f ) {
        float fraction = from.z / denominator;
        if ( fraction >= 0 && fraction <= 1 ) dots.add( new Position(
          from.x + fraction * ( to.x - from.x ), from.y + fraction * ( to.y - from.y ), 0 ) );
      }
    }
  }
}
