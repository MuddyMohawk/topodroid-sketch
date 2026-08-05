/* @file BeddingMeasurementModel.java
 *
 * @brief Named random-error assumptions for a station-relative splay endpoint
 */
package com.topodroid.geo;

public final class BeddingMeasurementModel
{
  public static final String DISTOX_CONSERVATIVE_V1 = "distox-conservative-v1";
  public static final String MANUAL_CONSERVATIVE_V1 = "manual-conservative-v1";

  public final String id;
  public final double sigmaDistanceMeters;
  public final double sigmaBearingBaseRadians;
  public final double sigmaClinoRadians;
  public final double surfaceScatterMeters;
  public final double bearingCosineFloor;

  public BeddingMeasurementModel( String model_id, double sigma_distance,
                                  double sigma_bearing_degrees, double sigma_clino_degrees,
                                  double surface_scatter, double bearing_cosine_floor )
  {
    id = ( model_id == null || model_id.length() == 0 ) ? "custom" : model_id;
    sigmaDistanceMeters = sigma_distance;
    sigmaBearingBaseRadians = Math.toRadians( sigma_bearing_degrees );
    sigmaClinoRadians = Math.toRadians( sigma_clino_degrees );
    surfaceScatterMeters = surface_scatter;
    bearingCosineFloor = bearing_cosine_floor;
  }

  public static BeddingMeasurementModel distoxConservativeV1()
  {
    return new BeddingMeasurementModel( DISTOX_CONSERVATIVE_V1,
      0.002, 0.5, 0.5, 0.015, 0.25 );
  }

  public static BeddingMeasurementModel manualConservativeV1()
  {
    return new BeddingMeasurementModel( MANUAL_CONSERVATIVE_V1,
      0.01, 1.0, 1.0, 0.02, 0.25 );
  }

  public boolean isValid()
  {
    return Double.isFinite( sigmaDistanceMeters ) && sigmaDistanceMeters > 0.0
      && Double.isFinite( sigmaBearingBaseRadians ) && sigmaBearingBaseRadians > 0.0
      && Double.isFinite( sigmaClinoRadians ) && sigmaClinoRadians > 0.0
      && Double.isFinite( surfaceScatterMeters ) && surfaceScatterMeters > 0.0
      && Double.isFinite( bearingCosineFloor ) && bearingCosineFloor > 0.0
      && bearingCosineFloor <= 1.0;
  }

  public double sigmaBearingRadians( double clino_radians )
  {
    double horizontal = Math.abs( Math.cos( clino_radians ) );
    return sigmaBearingBaseRadians / Math.max( bearingCosineFloor, horizontal );
  }
}
