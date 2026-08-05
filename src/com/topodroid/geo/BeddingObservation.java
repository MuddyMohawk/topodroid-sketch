/* @file BeddingObservation.java
 *
 * @brief One station-relative splay endpoint and its ENU covariance
 */
package com.topodroid.geo;

public final class BeddingObservation
{
  public final long sourceId;
  public final String label;
  public final String splayType;
  public final double lengthMeters;
  public final double bearingDegrees;
  public final double clinoDegrees;
  public final GeoVector3 endpoint;

  // Symmetric covariance: EE, NN, UU, EN, EU, NU.
  final double cEE;
  final double cNN;
  final double cUU;
  final double cEN;
  final double cEU;
  final double cNU;

  private BeddingObservation( long source_id, String source_label, String type,
                              double length, double bearing, double clino,
                              GeoVector3 point, double ee, double nn, double uu,
                              double en, double eu, double nu )
  {
    sourceId = source_id;
    label = ( source_label == null ) ? "" : source_label;
    splayType = ( type == null ) ? "splay" : type;
    lengthMeters = length;
    bearingDegrees = BeddingAttitude.wrap360( bearing );
    clinoDegrees = clino;
    endpoint = point;
    cEE = ee;
    cNN = nn;
    cUU = uu;
    cEN = en;
    cEU = eu;
    cNU = nu;
  }

  public static BeddingObservation fromSplay( long source_id, String label, String type,
                                              double length_meters, double bearing_degrees,
                                              double clino_degrees, BeddingMeasurementModel model )
  {
    if ( model == null || ! model.isValid() || ! Double.isFinite( length_meters )
        || length_meters <= 0.0 || ! Double.isFinite( bearing_degrees )
        || ! Double.isFinite( clino_degrees ) || Math.abs( clino_degrees ) > 90.0 ) return null;

    double b = Math.toRadians( BeddingAttitude.wrap360( bearing_degrees ) );
    double c = Math.toRadians( clino_degrees );
    double sinb = Math.sin( b );
    double cosb = Math.cos( b );
    double sinc = Math.sin( c );
    double cosc = Math.cos( c );

    GeoVector3 r = new GeoVector3( cosc * sinb, cosc * cosb, sinc );
    GeoVector3 rb = new GeoVector3( cosc * cosb, -cosc * sinb, 0.0 );
    GeoVector3 rc = new GeoVector3( -sinc * sinb, -sinc * cosb, cosc );
    GeoVector3 endpoint = r.times( length_meters );

    double range2 = model.sigmaDistanceMeters * model.sigmaDistanceMeters;
    double bearing_sigma = model.sigmaBearingRadians( c );
    double bearing2 = length_meters * length_meters * bearing_sigma * bearing_sigma;
    double clino2 = length_meters * length_meters
      * model.sigmaClinoRadians * model.sigmaClinoRadians;

    double ee = range2 * r.east * r.east + bearing2 * rb.east * rb.east
      + clino2 * rc.east * rc.east;
    double nn = range2 * r.north * r.north + bearing2 * rb.north * rb.north
      + clino2 * rc.north * rc.north;
    double uu = range2 * r.up * r.up + bearing2 * rb.up * rb.up
      + clino2 * rc.up * rc.up;
    double en = range2 * r.east * r.north + bearing2 * rb.east * rb.north
      + clino2 * rc.east * rc.north;
    double eu = range2 * r.east * r.up + bearing2 * rb.east * rb.up
      + clino2 * rc.east * rc.up;
    double nu = range2 * r.north * r.up + bearing2 * rb.north * rb.up
      + clino2 * rc.north * rc.up;

    return new BeddingObservation( source_id, label, type, length_meters, bearing_degrees,
      clino_degrees, endpoint, ee, nn, uu, en, eu, nu );
  }

  double normalVariance( GeoVector3 normal, double extra_scatter )
  {
    double e = normal.east;
    double n = normal.north;
    double u = normal.up;
    return cEE * e * e + cNN * n * n + cUU * u * u
      + 2.0 * cEN * e * n + 2.0 * cEU * e * u + 2.0 * cNU * n * u
      + extra_scatter * extra_scatter;
  }
}
