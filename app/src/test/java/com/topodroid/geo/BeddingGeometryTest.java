package com.topodroid.geo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class BeddingGeometryTest
{
  private static final BeddingMeasurementModel MODEL = BeddingMeasurementModel.distoxConservativeV1();

  @Test public void attitudeCardinals_useEastFirstGeographicAtan2()
  {
    assertAttitude( 0.0, 30.0, 270.0 );
    assertAttitude( 90.0, 30.0, 0.0 );
    assertAttitude( 180.0, 30.0, 90.0 );
    assertAttitude( 270.0, 30.0, 180.0 );
  }

  @Test public void attitudeRoundTrip_denseGrid()
  {
    for ( int dip = 1; dip < 90; dip += 2 ) {
      for ( int direction = 0; direction < 360; direction += 3 ) {
        BeddingAttitude first = BeddingAttitude.fromDipDirection( direction, dip );
        BeddingAttitude second = BeddingAttitude.fromNormal( first.unitNormal );
        assertEquals( dip, second.dipDegrees, 1.0e-9 );
        assertAngle( direction, second.dipDirectionDegrees, 1.0e-9 );
      }
    }
    assertEquals( BeddingAttitude.Kind.HORIZONTAL,
      BeddingAttitude.fromDipDirection( 123.0, 0.0 ).kind );
    assertEquals( BeddingAttitude.Kind.VERTICAL,
      BeddingAttitude.fromDipDirection( 123.0, 90.0 ).kind );
  }

  @Test public void splayAdapter_usesMagneticEastNorthUpCardinals()
  {
    assertVector( new GeoVector3( 0, 2, 0 ),
      BeddingObservation.fromSplay( 1, "north", "splay", 2, 0, 0, MODEL ).endpoint );
    assertVector( new GeoVector3( 2, 0, 0 ),
      BeddingObservation.fromSplay( 2, "east", "splay", 2, 90, 0, MODEL ).endpoint );
    assertVector( new GeoVector3( 0, -2, 0 ),
      BeddingObservation.fromSplay( 3, "south", "splay", 2, 180, 0, MODEL ).endpoint );
    assertVector( new GeoVector3( -2, 0, 0 ),
      BeddingObservation.fromSplay( 4, "west", "splay", 2, 270, 0, MODEL ).endpoint );
    assertVector( new GeoVector3( 0, 0, 2 ),
      BeddingObservation.fromSplay( 5, "up", "splay", 2, 0, 90, MODEL ).endpoint );
  }

  @Test public void jacobiSolver_returnsSortedOrthonormalEigenpairs()
  {
    double[][] matrix = new double[][] {
      { 4.0, 1.0, 0.5 },
      { 1.0, 2.0, -0.25 },
      { 0.5, -0.25, 1.0 }
    };
    SymmetricEigen3.Result result = SymmetricEigen3.solve( matrix );
    assertTrue( result.converged );
    assertTrue( result.values[0] <= result.values[1] );
    assertTrue( result.values[1] <= result.values[2] );
    for ( int i = 0; i < 3; ++i ) {
      assertEquals( 1.0, result.vectors[i].norm(), 1.0e-10 );
      GeoVector3 product = multiply( matrix, result.vectors[i] );
      GeoVector3 residual = product.minus( result.vectors[i].times( result.values[i] ) );
      assertTrue( residual.norm() < 1.0e-9 );
      for ( int j = i + 1; j < 3; ++j ) {
        assertEquals( 0.0, result.vectors[i].dot( result.vectors[j] ), 1.0e-10 );
      }
    }
  }

  @Test public void fitter_recoversKnownEastDippingPlane()
  {
    List< BeddingObservation > observations = observationsOnPlane( 90.0, 30.0, 4.0,
      new double[][] { { -2, -1 }, { -1, 2 }, { 0, -2 }, { 1, 1.5 }, { 2, -0.5 }, { 1.5, 2.5 } } );
    BeddingFitResult result = BeddingPlaneFitter.fit( observations, MODEL );
    assertEquals( BeddingFitResult.Status.VALID, result.status );
    assertNotNull( result.attitude );
    assertEquals( 30.0, result.attitude.dipDegrees, 0.01 );
    assertAngle( 90.0, result.attitude.dipDirectionDegrees, 0.01 );
    assertAngle( 0.0, result.attitude.strikeRhrDegrees, 0.01 );
    assertTrue( result.region95.dipMinimum <= 30.0 );
    assertTrue( result.region95.dipMaximum >= 30.0 );
  }

  @Test public void fitter_threePoints_hasNonzeroModelRegionAndCaution()
  {
    List< BeddingObservation > observations = observationsOnPlane( 225.0, 45.0, 3.0,
      new double[][] { { -1, -1 }, { 1.5, -0.5 }, { 0, 2 } } );
    BeddingFitResult result = BeddingPlaneFitter.fit( observations, MODEL );
    assertEquals( BeddingFitResult.Status.VALID, result.status );
    assertTrue( result.issues.contains( BeddingFitResult.Issue.NO_REDUNDANCY ) );
    assertTrue( result.region95.dipMaximum > result.region95.dipMinimum );
  }

  @Test public void fitter_rejectsCollinearEndpoints()
  {
    ArrayList< BeddingObservation > observations = new ArrayList<>();
    observations.add( observationForPoint( 1, new GeoVector3( 1, 0, 0 ) ) );
    observations.add( observationForPoint( 2, new GeoVector3( 2, 0, 0 ) ) );
    observations.add( observationForPoint( 3, new GeoVector3( 3, 0, 0 ) ) );
    BeddingFitResult result = BeddingPlaneFitter.fit( observations, MODEL );
    assertEquals( BeddingFitResult.Status.INVALID, result.status );
    assertTrue( result.issues.contains( BeddingFitResult.Issue.COLLINEAR_POINTS ) );
  }

  @Test public void fitter_isInvariantToOrderTranslationAndExactUniformScale()
  {
    double[][] coordinates = { { -2, -1 }, { -1, 2 }, { 0, -2 }, { 1, 1.5 }, { 2, -0.5 }, { 1.5, 2.5 } };
    List< BeddingObservation > baseline = observationsOnPlane( 310.0, 37.0, 4.0, coordinates );
    BeddingFitResult first = BeddingPlaneFitter.fit( baseline, MODEL );
    ArrayList< BeddingObservation > reversed = new ArrayList<>( baseline );
    Collections.reverse( reversed );
    BeddingFitResult second = BeddingPlaneFitter.fit( reversed, MODEL );
    assertAttitudesEqual( first.attitude, second.attitude, 0.003 );

    GeoVector3 translation = new GeoVector3( 12.0, -8.0, 3.5 );
    ArrayList< BeddingObservation > translated = new ArrayList<>();
    ArrayList< BeddingObservation > scaled = new ArrayList<>();
    long id = 100;
    for ( BeddingObservation observation : baseline ) {
      translated.add( observationForPoint( id, observation.endpoint.plus( translation ) ) );
      scaled.add( observationForPoint( id + 100, observation.endpoint.times( 3.0 ) ) );
      ++id;
    }
    assertAttitudesEqual( first.attitude, BeddingPlaneFitter.fit( translated, MODEL ).attitude, 0.003 );
    assertAttitudesEqual( first.attitude, BeddingPlaneFitter.fit( scaled, MODEL ).attitude, 0.003 );
  }

  @Test public void fitter_rejectsDuplicateIdsAndNonFiniteInputs()
  {
    ArrayList< BeddingObservation > observations = new ArrayList<>(
      observationsOnPlane( 45.0, 25.0, 3.0,
        new double[][] { { -1, -1 }, { 1, -1 }, { 0, 1 } } ) );
    BeddingObservation first = observations.get( 0 );
    observations.set( 1, BeddingObservation.fromSplay( first.sourceId, "duplicate", "splay",
      observations.get( 1 ).lengthMeters, observations.get( 1 ).bearingDegrees,
      observations.get( 1 ).clinoDegrees, MODEL ) );
    BeddingFitResult duplicate = BeddingPlaneFitter.fit( observations, MODEL );
    assertEquals( BeddingFitResult.Status.INVALID, duplicate.status );
    assertTrue( duplicate.issues.contains( BeddingFitResult.Issue.DUPLICATE_SOURCE ) );

    assertEquals( null, BeddingObservation.fromSplay( 99, "bad", "splay",
      Double.NaN, 0.0, 0.0, MODEL ) );
  }

  @Test public void fitter_leaveOneOutIdentifiesDisproportionateShot()
  {
    List< BeddingObservation > clean = observationsOnPlane( 70.0, 25.0, 4.0,
      new double[][] { { -3, 0 }, { -2.5, -1.5 }, { -1.5, -2.5 }, { 0, -3 },
        { 1.5, -2.5 }, { 2.5, -1.5 }, { 3, 0 }, { 2.5, 1.5 }, { 1.5, 2.5 },
        { 0, 3 }, { -1.5, 2.5 }, { -2.5, 1.5 } } );
    ArrayList< BeddingObservation > contaminated = new ArrayList<>( clean );
    BeddingAttitude source_attitude = BeddingAttitude.fromDipDirection( 70.0, 25.0 );
    GeoVector3 displaced = clean.get( 11 ).endpoint
      .plus( source_attitude.unitNormal.times( 5.0 ) );
    contaminated.set( 11, observationForPoint( 12, displaced ) );
    BeddingFitResult result = BeddingPlaneFitter.fit( contaminated, MODEL );
    assertEquals( BeddingFitResult.Status.VALID, result.status );
    assertTrue( "max=" + result.leaveOneOutMaximumAngleDegrees + " id="
      + result.influentialSourceId + " issues=" + result.issues,
      result.issues.contains( BeddingFitResult.Issue.INFLUENTIAL_POINT ) );
    assertEquals( 12L, result.influentialSourceId );
    assertTrue( result.leaveOneOutMaximumAngleDegrees > 5.0 );
  }

  @Test public void observationCovariance_matchesFiniteDifferenceJacobian()
  {
    double length = 7.3;
    double bearing = 127.0;
    double clino = 42.0;
    BeddingObservation observation = BeddingObservation.fromSplay(
      1, "fixture", "splay", length, bearing, clino, MODEL );
    double eps_length = 1.0e-5;
    double eps_angle = 1.0e-6;
    GeoVector3 d_length = differenceEndpoint( length + eps_length, bearing, clino,
      length - eps_length, bearing, clino ).times( 0.5 / eps_length );
    double degree_step = Math.toDegrees( eps_angle );
    GeoVector3 d_bearing = differenceEndpoint( length, bearing + degree_step, clino,
      length, bearing - degree_step, clino ).times( 0.5 / eps_angle );
    GeoVector3 d_clino = differenceEndpoint( length, bearing, clino + degree_step,
      length, bearing, clino - degree_step ).times( 0.5 / eps_angle );
    double sigma_l2 = MODEL.sigmaDistanceMeters * MODEL.sigmaDistanceMeters;
    double sigma_b2 = MODEL.sigmaBearingRadians( Math.toRadians( clino ) );
    sigma_b2 *= sigma_b2;
    double sigma_c2 = MODEL.sigmaClinoRadians * MODEL.sigmaClinoRadians;
    double[][] covariance = covariance( d_length, d_bearing, d_clino,
      sigma_l2, sigma_b2, sigma_c2 );
    assertEquals( covariance[0][0], observation.cEE, 1.0e-9 );
    assertEquals( covariance[1][1], observation.cNN, 1.0e-9 );
    assertEquals( covariance[2][2], observation.cUU, 1.0e-9 );
    assertEquals( covariance[0][1], observation.cEN, 1.0e-9 );
    assertEquals( covariance[0][2], observation.cEU, 1.0e-9 );
    assertEquals( covariance[1][2], observation.cNU, 1.0e-9 );
  }

  @Test public void projectedProfile_usesSectionPlaneAndProductionProjection()
  {
    BeddingAttitude attitude = BeddingAttitude.fromDipDirection( 180.0, 45.0 );
    ProjectionBasis basis = ProjectionBasis.projectedProfile( 90.0, 30.0 );
    assertNotNull( basis );
    ProjectionBasis.Trace trace = basis.trace( attitude.unitNormal );
    assertTrue( trace.valid );
    assertEquals( 45.0, trace.geologicalApparentDipDegrees, 1.0e-8 );
    double canvasAcute = acuteLineAngle( trace.canvasTraceAngleDegrees );
    assertEquals( 45.0, canvasAcute, 1.0e-8 );

    // The selected section-plane horizontal maps to one page unit even though
    // an arbitrary off-plane East vector is affected by the oblique projection.
    GeoVector3 section_horizontal = new GeoVector3( 0.0, -1.0, 0.0 );
    assertEquals( 1.0, basis.pageX( section_horizontal ), 1.0e-8 );
    assertFalse( Math.abs( basis.pageX( new GeoVector3( 1.0, 0.0, 0.0 ) ) - 1.0 ) < 1.0e-6 );
  }

  @Test public void extendedProfile_reversalMirrorsTrace()
  {
    BeddingAttitude attitude = BeddingAttitude.fromDipDirection( 135.0, 40.0 );
    ProjectionBasis.Trace right = ProjectionBasis.extendedProfile( 135.0, 1.0 )
      .trace( attitude.unitNormal );
    ProjectionBasis.Trace left = ProjectionBasis.extendedProfile( 135.0, -1.0 )
      .trace( attitude.unitNormal );
    assertTrue( right.valid );
    assertTrue( left.valid );
    assertEquals( right.geologicalApparentDipDegrees, left.geologicalApparentDipDegrees, 1.0e-8 );
    assertEquals( -acuteSigned( right.canvasTraceAngleDegrees ),
      acuteSigned( left.canvasTraceAngleDegrees ), 1.0e-8 );
  }

  @Test public void profileTrace_matchesIndependentApparentDipAcrossQuadrants()
  {
    double[] directions = { 0.0, 45.0, 130.0, 225.0, 315.0 };
    double[] dips = { 5.0, 30.0, 60.0, 85.0 };
    double[] views = { 0.0, 35.0, 90.0, 170.0, 275.0 };
    for ( double direction : directions ) for ( double dip : dips ) for ( double view : views ) {
      BeddingAttitude attitude = BeddingAttitude.fromDipDirection( direction, dip );
      double expected_extended = Math.toDegrees( Math.atan( Math.abs( Math.tan( Math.toRadians( dip ) )
        * Math.cos( Math.toRadians( direction - view ) ) ) ) );
      // Production projected-profile azimuth labels the projection direction;
      // the section page's horizontal bearing is azimuth + 90 degrees.
      double expected_projected = Math.toDegrees( Math.atan( Math.abs( Math.tan( Math.toRadians( dip ) )
        * Math.cos( Math.toRadians( direction - ( view + 90.0 ) ) ) ) ) );
      ProjectionBasis.Trace projected = ProjectionBasis.projectedProfile( view, 23.0 )
        .trace( attitude.unitNormal );
      ProjectionBasis.Trace extended = ProjectionBasis.extendedProfile( view, 1.0 )
        .trace( attitude.unitNormal );
      assertTrue( projected.valid );
      assertTrue( extended.valid );
      assertEquals( expected_projected, projected.geologicalApparentDipDegrees, 1.0e-8 );
      assertEquals( expected_projected, acuteLineAngle( projected.canvasTraceAngleDegrees ), 1.0e-8 );
      assertEquals( expected_extended, extended.geologicalApparentDipDegrees, 1.0e-8 );
      assertEquals( expected_extended, acuteLineAngle( extended.canvasTraceAngleDegrees ), 1.0e-8 );
    }
  }

  private static void assertAttitude( double direction, double dip, double strike )
  {
    BeddingAttitude attitude = BeddingAttitude.fromDipDirection( direction, dip );
    assertEquals( dip, attitude.dipDegrees, 1.0e-10 );
    assertAngle( direction, attitude.dipDirectionDegrees, 1.0e-10 );
    assertAngle( strike, attitude.strikeRhrDegrees, 1.0e-10 );
  }

  private static List< BeddingObservation > observationsOnPlane( double direction, double dip,
                                                                  double normal_offset,
                                                                  double[][] coordinates )
  {
    BeddingAttitude attitude = BeddingAttitude.fromDipDirection( direction, dip );
    GeoVector3 n = attitude.unitNormal;
    double strike_radians = Math.toRadians( attitude.strikeRhrDegrees );
    GeoVector3 strike = new GeoVector3( Math.sin( strike_radians ), Math.cos( strike_radians ), 0.0 );
    double direction_radians = Math.toRadians( direction );
    double dip_radians = Math.toRadians( dip );
    GeoVector3 down_dip = new GeoVector3( Math.sin( direction_radians ) * Math.cos( dip_radians ),
      Math.cos( direction_radians ) * Math.cos( dip_radians ), -Math.sin( dip_radians ) );
    GeoVector3 base = n.times( normal_offset );
    ArrayList< BeddingObservation > result = new ArrayList<>();
    long id = 1;
    for ( double[] coordinate : coordinates ) {
      GeoVector3 point = base.plus( strike.times( coordinate[0] ) )
        .plus( down_dip.times( coordinate[1] ) );
      result.add( observationForPoint( id++, point ) );
    }
    return result;
  }

  private static BeddingObservation observationForPoint( long id, GeoVector3 point )
  {
    double length = point.norm();
    double bearing = BeddingAttitude.wrap360( Math.toDegrees( Math.atan2( point.east, point.north ) ) );
    double clino = Math.toDegrees( Math.atan2( point.up, Math.hypot( point.east, point.north ) ) );
    return BeddingObservation.fromSplay( id, "s" + id, "splay", length, bearing, clino, MODEL );
  }

  private static GeoVector3 multiply( double[][] matrix, GeoVector3 vector )
  {
    return new GeoVector3(
      matrix[0][0] * vector.east + matrix[0][1] * vector.north + matrix[0][2] * vector.up,
      matrix[1][0] * vector.east + matrix[1][1] * vector.north + matrix[1][2] * vector.up,
      matrix[2][0] * vector.east + matrix[2][1] * vector.north + matrix[2][2] * vector.up );
  }

  private static GeoVector3 differenceEndpoint( double l1, double b1, double c1,
                                                double l2, double b2, double c2 )
  {
    BeddingObservation first = BeddingObservation.fromSplay( 1, "", "", l1, b1, c1, MODEL );
    BeddingObservation second = BeddingObservation.fromSplay( 2, "", "", l2, b2, c2, MODEL );
    return first.endpoint.minus( second.endpoint );
  }

  private static double[][] covariance( GeoVector3 range, GeoVector3 bearing, GeoVector3 clino,
                                        double range2, double bearing2, double clino2 )
  {
    GeoVector3[] derivatives = { range, bearing, clino };
    double[] variances = { range2, bearing2, clino2 };
    double[][] result = new double[3][3];
    for ( int k = 0; k < derivatives.length; ++k ) {
      double[] v = { derivatives[k].east, derivatives[k].north, derivatives[k].up };
      for ( int i = 0; i < 3; ++i ) for ( int j = 0; j < 3; ++j ) {
        result[i][j] += variances[k] * v[i] * v[j];
      }
    }
    return result;
  }

  private static void assertAttitudesEqual( BeddingAttitude expected, BeddingAttitude actual,
                                            double tolerance )
  {
    assertNotNull( expected );
    assertNotNull( actual );
    assertEquals( expected.dipDegrees, actual.dipDegrees, tolerance );
    assertAngle( expected.dipDirectionDegrees, actual.dipDirectionDegrees, tolerance );
  }

  private static void assertVector( GeoVector3 expected, GeoVector3 actual )
  {
    assertEquals( expected.east, actual.east, 1.0e-9 );
    assertEquals( expected.north, actual.north, 1.0e-9 );
    assertEquals( expected.up, actual.up, 1.0e-9 );
  }

  private static void assertAngle( double expected, double actual, double tolerance )
  {
    double delta = Math.abs( BeddingAttitude.wrap360( actual - expected ) );
    delta = Math.min( delta, 360.0 - delta );
    assertTrue( "angle delta " + delta, delta <= tolerance );
  }

  private static double acuteLineAngle( double angle )
  {
    return Math.abs( acuteSigned( angle ) );
  }

  private static double acuteSigned( double angle )
  {
    double value = angle % 180.0;
    if ( value > 90.0 ) value -= 180.0;
    if ( value < -90.0 ) value += 180.0;
    return value;
  }
}
