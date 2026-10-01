/* @file ShotSiblingCheck.java
 *
 * @brief Shared sketch and shot-list sibling agreement checks.
 */
package com.topodroid.TDX;

import com.topodroid.math.TDVector;
import com.topodroid.util.TDMath;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ShotSiblingCheck
{
  private ShotSiblingCheck() { }

  /** The sketch's combined relative vector discrepancy, including direction. */
  public static float relativeDifference( DBlock reference, DBlock sibling, boolean reversed, float declination )
  {
    return relativeDifference( reference.mLength, reference.mBearing, reference.mClino,
                               sibling.mLength, sibling.mBearing, sibling.mClino, reversed, declination );
  }

  private static float relativeDifference( float length0, float bearing0, float clino0,
                                          float length1, float bearing1, float clino1,
                                          boolean reversed, float declination )
  {
    TDVector v0 = vector( length0, bearing0 + declination, clino0 );
    TDVector v1 = vector( length1, bearing1 + declination, clino1 );
    float distance = ( reversed ? v0.plus( v1 ) : v0.minus( v1 ) ).length();
    return distance / length0 + distance / length1;
  }

  private static TDVector vector( float length, float bearing, float clino )
  {
    float cc = TDMath.cosd( clino );
    float sc = TDMath.sind( clino );
    float cb = TDMath.cosd( bearing );
    float sb = TDMath.sind( bearing );
    return new TDVector( length * cc * sb, length * cc * cb, length * sc );
  }

  /** Depth preparation used by the sketch, without changing any shot data. */
  public static HashMap< String, Float > stationDepths( List< DBlock > blocks )
  {
    HashMap< String, Float > depths = new HashMap<>();
    for ( DBlock block : blocks ) {
      if ( ! hasStations( block ) ) continue;
      if ( depths.isEmpty() ) {
        depths.put( block.mFrom, 0.0f );
        depths.put( block.mTo, block.mDepth );
      } else if ( ! depths.containsKey( block.mTo ) ) {
        depths.put( block.mTo, block.mDepth );
      }
    }
    return depths;
  }

  /** Check first leg blocks, just as TDNum does; secondary measurements are not references. */
  static Map< Long, ExpectedBacksight > badBacksights( List< DBlock > blocks, float declination,
                                                     float threshold, boolean magneticAnomaly, boolean diving )
  {
    if ( magneticAnomaly || blocks == null || blocks.isEmpty() ) return Collections.emptyMap();
    Map< StationPair, DBlock > references = new HashMap<>();
    Map< Long, ExpectedBacksight > warnings = new HashMap<>();
    Map< String, Float > depths = diving ? stationDepths( blocks ) : Collections.emptyMap();
    for ( DBlock block : blocks ) {
      if ( ! hasStations( block ) || ! block.isLeg() ) continue;
      StationPair pair = new StationPair( block.mFrom, block.mTo );
      DBlock reference = references.get( pair );
      if ( reference == null ) {
        references.put( pair, block );
      } else if ( reference.mFrom.equals( block.mTo ) && reference.mTo.equals( block.mFrom )
                  && ! reference.mFrom.equals( block.mFrom ) ) {
        float clino0 = clino( reference, depths );
        float clino1 = clino( block, depths );
        float difference = relativeDifference( reference.mLength, reference.mBearing, clino0,
                                               block.mLength, block.mBearing, clino1, true, declination );
        if ( difference > threshold ) {
          warnings.put( block.mId, new ExpectedBacksight( reference.mLength,
            TDMath.in360( reference.mBearing + 180 ), clino0 == 0.0f ? 0.0f : -clino0 ) );
        }
      }
    }
    return warnings;
  }

  private static boolean hasStations( DBlock block )
  {
    return block != null && block.mFrom != null && ! block.mFrom.isEmpty()
                        && block.mTo != null && ! block.mTo.isEmpty();
  }

  private static float clino( DBlock block, Map< String, Float > depths )
  {
    if ( depths.containsKey( block.mFrom ) && depths.containsKey( block.mTo ) ) {
      return TDMath.asind( ( depths.get( block.mFrom ) - depths.get( block.mTo ) ) / block.mLength );
    }
    return block.mClino;
  }

  static final class ExpectedBacksight
  {
    final float length;
    final float bearing;
    final float clino;

    ExpectedBacksight( float l, float b, float c ) { length = l; bearing = b; clino = c; }

    String format( float unitLength, String lengthUnit, float unitAngle, String angleUnit )
    {
      String suffix = "deg".equals( angleUnit ) ? "\u00b0" : " " + angleUnit;
      return String.format( Locale.US, "%.2f %s, %.1f%s, %.1f%s",
        length * unitLength, lengthUnit, bearing * unitAngle, suffix, clino * unitAngle, suffix );
    }
  }

  private static final class StationPair
  {
    final String first;
    final String second;

    StationPair( String from, String to )
    {
      boolean ordered = from.compareTo( to ) <= 0;
      first = ordered ? from : to;
      second = ordered ? to : from;
    }

    @Override public boolean equals( Object other )
    {
      if ( ! ( other instanceof StationPair ) ) return false;
      StationPair pair = (StationPair) other;
      return first.equals( pair.first ) && second.equals( pair.second );
    }

    @Override public int hashCode() { return 31 * first.hashCode() + second.hashCode(); }
  }
}
