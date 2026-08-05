package com.topodroid.geo;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.Test;

/** Fixed-seed calibration gate for the numerical regions shown by the editor.
 *  This uses the production estimator and declared random-error model; it does
 *  not claim coverage for compass bias, misidentified beds, or correlated geology.
 */
public class BeddingCoverageCalibrationTest
{
  private static final int REPLICATES = 24;

  @Test public void declaredJointRegions_haveRepresentativeModelCoverage()
  {
    Configuration[] configurations = {
      new Configuration( 10.0, 5.0, 6, 2.0 ),
      new Configuration( 95.0, 30.0, 3, 2.0 ),
      new Configuration( 185.0, 45.0, 4, 2.0 ),
      new Configuration( 275.0, 65.0, 6, 0.65 ),
      new Configuration( 330.0, 85.0, 12, 2.0 )
    };
    BeddingMeasurementModel[] models = {
      BeddingMeasurementModel.distoxConservativeV1(),
      BeddingMeasurementModel.manualConservativeV1()
    };
    Random random = new Random( 0x5eedbeddL );
    for ( BeddingMeasurementModel model : models ) verifyCoverage( configurations, model, random );
  }

  private static void verifyCoverage( Configuration[] configurations,
                                      BeddingMeasurementModel model, Random random )
  {
    int valid = 0;
    int covered68 = 0;
    int covered95 = 0;
    for ( Configuration configuration : configurations ) {
      for ( int repetition = 0; repetition < REPLICATES; ++repetition ) {
        BeddingAttitude truth = BeddingAttitude.fromDipDirection(
          configuration.direction, configuration.dip );
        List< BeddingObservation > observations = simulate( configuration, truth, random, model );
        BeddingFitResult result = BeddingPlaneFitter.fitForCoverage( observations, model );
        if ( result.status != BeddingFitResult.Status.VALID ) continue;
        ++valid;
        if ( contains( result.region68, truth ) ) ++covered68;
        if ( contains( result.region95, truth ) ) ++covered95;
      }
    }

    int total = configurations.length * REPLICATES;
    assertTrue( model.id + " valid fits " + valid + "/" + total, valid >= total - 2 );
    double rate68 = covered68 / (double)valid;
    double rate95 = covered95 / (double)valid;
    // With 120 deterministic trials these bounds are intentionally wider than
    // sampling error, but narrow enough to reject materially dishonest labels.
    assertTrue( model.id + " 68% coverage was " + rate68, rate68 >= 0.53 && rate68 <= 0.82 );
    assertTrue( model.id + " 95% coverage was " + rate95, rate95 >= 0.88 && rate95 <= 1.0 );
  }

  private static List< BeddingObservation > simulate( Configuration configuration,
                                                       BeddingAttitude truth, Random random,
                                                       BeddingMeasurementModel model )
  {
    GeoVector3 normal = truth.unitNormal;
    double strike_angle = Math.toRadians( truth.strikeRhrDegrees );
    GeoVector3 strike = new GeoVector3( Math.sin( strike_angle ), Math.cos( strike_angle ), 0.0 );
    double direction = Math.toRadians( configuration.direction );
    double dip = Math.toRadians( configuration.dip );
    GeoVector3 down_dip = new GeoVector3( Math.sin( direction ) * Math.cos( dip ),
      Math.cos( direction ) * Math.cos( dip ), -Math.sin( dip ) );
    GeoVector3 center = normal.times( 5.0 );
    ArrayList< BeddingObservation > observations = new ArrayList<>();
    for ( int i = 0; i < configuration.count; ++i ) {
      double around = 2.0 * Math.PI * i / configuration.count + 0.19 * ( i % 3 );
      double radius = configuration.patchRadius * ( 0.65 + 0.12 * ( i % 4 ) );
      GeoVector3 true_endpoint = center
        .plus( strike.times( radius * Math.cos( around ) ) )
        .plus( down_dip.times( radius * Math.sin( around ) ) )
        .plus( normal.times( model.surfaceScatterMeters * random.nextGaussian() ) );
      double length = true_endpoint.norm();
      double bearing = BeddingAttitude.wrap360( Math.toDegrees(
        Math.atan2( true_endpoint.east, true_endpoint.north ) ) );
      double clino = Math.toDegrees( Math.atan2( true_endpoint.up,
        Math.hypot( true_endpoint.east, true_endpoint.north ) ) );
      double measured_length = length + model.sigmaDistanceMeters * random.nextGaussian();
      double measured_bearing = bearing + Math.toDegrees(
        model.sigmaBearingRadians( Math.toRadians( clino ) ) ) * random.nextGaussian();
      double measured_clino = clino + Math.toDegrees( model.sigmaClinoRadians ) * random.nextGaussian();
      BeddingObservation observation = BeddingObservation.fromSplay( i + 1, "sim" + i, "splay",
        measured_length, measured_bearing, measured_clino, model );
      if ( observation != null ) observations.add( observation );
    }
    return observations;
  }

  private static boolean contains( BeddingFitResult.AttitudeRegion region, BeddingAttitude truth )
  {
    if ( region == null || ! Double.isFinite( region.dipMinimum )
        || truth.dipDegrees < region.dipMinimum - 1.0e-9
        || truth.dipDegrees > region.dipMaximum + 1.0e-9 ) return false;
    if ( region.status == BeddingFitResult.RegionStatus.DIRECTION_UNRESOLVED
        || region.status == BeddingFitResult.RegionStatus.AXIAL_VERTICAL ) return true;
    if ( ! Double.isFinite( region.directionStart ) || ! Double.isFinite( region.directionEnd ) ) return false;
    double direction = BeddingAttitude.wrap360( truth.dipDirectionDegrees );
    return region.directionWrapsNorth
      ? direction >= region.directionStart || direction <= region.directionEnd
      : direction >= region.directionStart && direction <= region.directionEnd;
  }

  private static final class Configuration
  {
    final double direction;
    final double dip;
    final int count;
    final double patchRadius;

    Configuration( double dip_direction, double dip_degrees, int point_count, double patch_radius )
    {
      direction = dip_direction;
      dip = dip_degrees;
      count = point_count;
      patchRadius = patch_radius;
    }
  }
}
