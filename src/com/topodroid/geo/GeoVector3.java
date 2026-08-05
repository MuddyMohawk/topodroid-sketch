/* @file GeoVector3.java
 *
 * @brief Immutable double-precision East-North-Up vector
 */
package com.topodroid.geo;

/** Kept separate from the app's TDVector so the fitting package stays
 *  Android-free and uses double precision throughout its numerical work. */
public final class GeoVector3
{
  public final double east;
  public final double north;
  public final double up;

  public GeoVector3( double e, double n, double u )
  {
    east = e;
    north = n;
    up = u;
  }

  public boolean isFinite()
  {
    return Double.isFinite( east ) && Double.isFinite( north ) && Double.isFinite( up );
  }

  public GeoVector3 plus( GeoVector3 other )
  {
    return new GeoVector3( east + other.east, north + other.north, up + other.up );
  }

  public GeoVector3 minus( GeoVector3 other )
  {
    return new GeoVector3( east - other.east, north - other.north, up - other.up );
  }

  public GeoVector3 times( double factor )
  {
    return new GeoVector3( east * factor, north * factor, up * factor );
  }

  public double dot( GeoVector3 other )
  {
    return east * other.east + north * other.north + up * other.up;
  }

  public GeoVector3 cross( GeoVector3 other )
  {
    return new GeoVector3(
      north * other.up - up * other.north,
      up * other.east - east * other.up,
      east * other.north - north * other.east );
  }

  public double normSquared() { return dot( this ); }

  public double norm() { return Math.sqrt( normSquared() ); }

  public GeoVector3 normalized()
  {
    double length = norm();
    if ( ! Double.isFinite( length ) || length <= 0.0 ) return null;
    return times( 1.0 / length );
  }
}
