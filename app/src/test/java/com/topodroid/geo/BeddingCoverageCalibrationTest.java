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
  private static final int REPLICATES = 400;

  @Test public void declaredJointRegions_haveRepresentativeModelCoverage()
  {
    Configuration[] configurations = {
      new Configuration( "shallow-n6", 10.0, 5.0, 6, 2.0, 0x10L ),
      new Configuration( "ordinary-n3", 95.0, 30.0, 3, 2.0, 0x30L ),
      new Configuration( "ordinary-n4", 185.0, 45.0, 4, 2.0, 0x45L ),
      new Configuration( "narrow-n6", 275.0, 65.0, 6, 0.65, 0x65L ),
      new Configuration( "steep-n12", 330.0, 85.0, 12, 2.0, 0x85L )
    };
    BeddingMeasurementModel[] models = {
      BeddingMeasurementModel.distoxConservativeV1(),
      BeddingMeasurementModel.manualConservativeV1()
    };
    for ( BeddingMeasurementModel model : models ) {
      for ( Configuration configuration : configurations ) verifyCoverage( configuration, model );
    }
  }

  private static void verifyCoverage( Configuration configuration,
                                      BeddingMeasurementModel model )
  {
    long seed = 0x5eedbeddL ^ configuration.seed
      ^ ( (long)model.id.hashCode() * 0x9e3779b97f4a7c15L );
    Random random = new Random( seed );
    int valid = 0;
    int covered68 = 0;
    int covered95 = 0;
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

    String label = model.id + "/" + configuration.name;
    assertTrue( label + " valid fits " + valid + "/" + REPLICATES,
      valid >= REPLICATES - 2 );
    double rate68 = covered68 / (double)valid;
    double rate95 = covered95 / (double)valid;
    // This fixed-seed acceptance band accommodates the estimator's deliberate
    // small-sample conservatism while rejecting serious under-coverage or a
    // confidence region that has expanded until it is effectively meaningless.
    assertTrue( label + " 68% coverage was " + rate68, rate68 >= 0.63 && rate68 <= 0.79 );
    assertTrue( label + " 95% coverage was " + rate95, rate95 >= 0.93 && rate95 <= 0.995 );
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
    final String name;
    final double direction;
    final double dip;
    final int count;
    final double patchRadius;
    final long seed;

    Configuration( String configuration_name, double dip_direction, double dip_degrees,
                   int point_count, double patch_radius, long configuration_seed )
    {
      name = configuration_name;
      direction = dip_direction;
      dip = dip_degrees;
      count = point_count;
      patchRadius = patch_radius;
      seed = configuration_seed;
    }
  }
}
