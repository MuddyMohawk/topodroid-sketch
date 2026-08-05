/* @file ProjectionBasis.java
 *
 * @brief Android-free mapping from magnetic ENU vectors to plot-page vectors
 */
package com.topodroid.geo;

public final class ProjectionBasis
{
  public static final class Trace
  {
    public final boolean valid;
    public final double geologicalApparentDipDegrees;
    public final double canvasTraceAngleDegrees;
    public final boolean fallsTowardPositiveX;

    Trace( boolean is_valid, double apparent, double canvas_angle, boolean positive_x )
    {
      valid = is_valid;
      geologicalApparentDipDegrees = apparent;
      canvasTraceAngleDegrees = canvas_angle;
      fallsTowardPositiveX = positive_x;
    }
  }

  private final GeoVector3 mPageX;
  private final GeoVector3 mPageY;
  private final GeoVector3 mViewHorizontal;

  private ProjectionBasis( GeoVector3 page_x, GeoVector3 page_y, GeoVector3 view_horizontal )
  {
    mPageX = page_x;
    mPageY = page_y;
    mViewHorizontal = view_horizontal;
  }

  public static ProjectionBasis plan()
  {
    return new ProjectionBasis( new GeoVector3( 1, 0, 0 ), new GeoVector3( 0, -1, 0 ), null );
  }

  public static ProjectionBasis projectedProfile( double azimuth_degrees, double clino_degrees )
  {
    double azimuth = Math.toRadians( azimuth_degrees );
    double combined = Math.toRadians( azimuth_degrees + clino_degrees );
    double sina = Math.sin( azimuth );
    double cosa = Math.cos( azimuth );
    double sinb = Math.sin( combined );
    double cosb = Math.cos( combined );
    double denominator = sinb * sina + cosb * cosa;
    if ( Math.abs( denominator ) < 1.0e-8 ) return null;
    double gamma = ( -sinb * cosa + cosb * sina ) / denominator;
    double cosp = cosa + gamma * sina;
    double sinp = sina - gamma * cosa;

    // DrawingWindow uses h = E*cosp + South*sinp, and South = -North.
    GeoVector3 page_x = new GeoVector3( cosp, -sinp, 0.0 );
    // ProjectionDialog defines the vertical section plane's horizontal axis as
    // x=(cos(azimuth),sin(azimuth)) in East-South. Oblique changes how points
    // outside that plane are projected onto it; points already in the section
    // plane retain unit horizontal scale.
    GeoVector3 horizontal = new GeoVector3( cosa, -sina, 0.0 );
    return new ProjectionBasis( page_x, new GeoVector3( 0, 0, -1 ), horizontal );
  }

  public static ProjectionBasis extendedProfile( double reference_bearing_degrees, double extend_sign )
  {
    double bearing = Math.toRadians( reference_bearing_degrees );
    double sign = extend_sign < 0.0 ? -1.0 : 1.0;
    GeoVector3 horizontal = new GeoVector3( sign * Math.sin( bearing ),
                                             sign * Math.cos( bearing ), 0.0 );
    return new ProjectionBasis( horizontal, new GeoVector3( 0, 0, -1 ), horizontal );
  }

  public double pageX( GeoVector3 vector ) { return mPageX.dot( vector ); }
  public double pageY( GeoVector3 vector ) { return mPageY.dot( vector ); }
  public double pageXEast() { return mPageX.east; }
  public double pageXNorth() { return mPageX.north; }

  public Trace trace( GeoVector3 plane_normal )
  {
    if ( plane_normal == null || mViewHorizontal == null ) return new Trace( false,
      Double.NaN, Double.NaN, false );
    GeoVector3 normal = plane_normal.normalized();
    if ( normal == null ) return new Trace( false, Double.NaN, Double.NaN, false );
    if ( normal.up < 0.0 ) normal = normal.times( -1.0 );
    double along = normal.dot( mViewHorizontal );
    if ( Math.abs( along ) < 1.0e-12 && Math.abs( normal.up ) < 1.0e-12 ) {
      return new Trace( false, Double.NaN, Double.NaN, false );
    }

    double apparent = Math.toDegrees( Math.atan2( Math.abs( along ), Math.max( 0.0, normal.up ) ) );
    // A tangent in the vertical viewing plane: nU*h - (n.h)*Up.
    GeoVector3 tangent = mViewHorizontal.times( normal.up )
      .minus( new GeoVector3( 0.0, 0.0, along ) );
    double x = pageX( tangent );
    double y = pageY( tangent );
    if ( Math.hypot( x, y ) < 1.0e-12 ) return new Trace( false, apparent, Double.NaN, false );
    double angle = Math.toDegrees( Math.atan2( y, x ) );
    boolean falls_positive = along > 0.0;
    return new Trace( true, apparent, angle, falls_positive );
  }
}
