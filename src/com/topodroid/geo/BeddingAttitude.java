/* @file BeddingAttitude.java
 *
 * @brief Geological bedding attitude in the magnetic East-North-Up frame
 */
package com.topodroid.geo;

public final class BeddingAttitude
{
  public enum Kind { HORIZONTAL, INCLINED, VERTICAL }

  private static final double POLE_EPSILON = 1.0e-8;

  public final Kind kind;
  public final GeoVector3 unitNormal;
  public final double strikeRhrDegrees;
  public final double dipDegrees;
  public final double dipDirectionDegrees;

  private BeddingAttitude( Kind attitude_kind, GeoVector3 normal, double strike,
                           double dip, double dip_direction )
  {
    kind = attitude_kind;
    unitNormal = normal;
    strikeRhrDegrees = strike;
    dipDegrees = dip;
    dipDirectionDegrees = dip_direction;
  }

  public static BeddingAttitude fromNormal( GeoVector3 input )
  {
    if ( input == null ) return null;
    GeoVector3 normal = input.normalized();
    if ( normal == null || ! normal.isFinite() ) return null;
    if ( normal.up < 0.0 ) normal = normal.times( -1.0 );

    double horizontal = Math.hypot( normal.east, normal.north );
    double dip = Math.toDegrees( Math.atan2( horizontal, Math.max( 0.0, normal.up ) ) );
    if ( horizontal <= POLE_EPSILON ) {
      return new BeddingAttitude( Kind.HORIZONTAL, new GeoVector3( 0.0, 0.0, 1.0 ),
                                  Double.NaN, 0.0, Double.NaN );
    }

    // Java atan2 is atan2(y,x): East deliberately occupies the y argument so
    // geographic azimuth is clockwise from North.
    double dip_direction = wrap360( Math.toDegrees( Math.atan2( normal.east, normal.north ) ) );
    double strike = wrap360( dip_direction - 90.0 );
    if ( normal.up <= POLE_EPSILON ) {
      strike = wrap180( strike );
      return new BeddingAttitude( Kind.VERTICAL, normal, strike, 90.0, Double.NaN );
    }
    return new BeddingAttitude( Kind.INCLINED, normal, strike, dip, dip_direction );
  }

  public static BeddingAttitude fromDipDirection( double dip_direction, double dip )
  {
    if ( ! Double.isFinite( dip_direction ) || ! Double.isFinite( dip )
        || dip < 0.0 || dip > 90.0 ) return null;
    double a = Math.toRadians( wrap360( dip_direction ) );
    double d = Math.toRadians( dip );
    return fromNormal( new GeoVector3( Math.sin( a ) * Math.sin( d ),
                                       Math.cos( a ) * Math.sin( d ),
                                       Math.cos( d ) ) );
  }

  public static double wrap360( double degrees )
  {
    double value = degrees % 360.0;
    if ( value < 0.0 ) value += 360.0;
    return value;
  }

  public static double wrap180( double degrees )
  {
    double value = wrap360( degrees );
    return ( value >= 180.0 ) ? value - 180.0 : value;
  }
}
