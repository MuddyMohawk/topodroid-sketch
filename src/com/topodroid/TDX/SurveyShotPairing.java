/* @file SurveyShotPairing.java
 *
 * @brief Direction normalization for a named foresight/backsight pair.
 */
package com.topodroid.TDX;

final class SurveyShotPairing
{
  private SurveyShotPairing() { }

  static boolean isNamedBacksightPair( String forwardFrom, String forwardTo,
                                       String candidateFrom, String candidateTo,
                                       boolean candidateIsBackLeg )
  {
    return candidateIsBackLeg
        && forwardFrom != null && forwardTo != null
        && forwardFrom.equals( candidateTo )
        && forwardTo.equals( candidateFrom );
  }

  static float forwardBearing( float reverseBearing )
  {
    float bearing = reverseBearing + 180.0f;
    while ( bearing >= 360.0f ) bearing -= 360.0f;
    while ( bearing < 0.0f ) bearing += 360.0f;
    return bearing;
  }

  static float forwardClino( float reverseClino )
  {
    return - reverseClino;
  }
}
