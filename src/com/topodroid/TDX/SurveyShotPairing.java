/* @file SurveyShotPairing.java
 *
 * @brief Direction normalization for a named foresight/backsight pair.
 */
package com.topodroid.TDX;

final class SurveyShotPairing
{
  private DBlock pairedBacksight;

  void start( AverageLeg leg, DBlock head )
  {
    pairedBacksight = null;
    leg.set( head );
  }

  boolean addBacksight( AverageLeg leg, DBlock head, DBlock shot )
  {
    if ( head == null || !head.isMainLeg() || pairedBacksight != null ||
         !isNamedBacksightPair( head.mFrom, head.mTo, shot.mFrom, shot.mTo, shot.isBackLeg() ) ) return false;
    pairedBacksight = shot;
    addReversed( leg, shot );
    return true;
  }

  void addRepeat( AverageLeg leg, DBlock head, DBlock shot )
  {
    if ( head == null ) return;
    DBlock owner = pairedBacksight == null ? head : pairedBacksight;
    if ( !shot.isSecLeg() && !shot.isRelativeDistance( owner ) ) return;
    if ( pairedBacksight == null ) leg.add( shot );
    else addReversed( leg, shot );
  }

  private static void addReversed( AverageLeg leg, DBlock shot )
  {
    leg.add( shot.mLength, forwardBearing( shot.mBearing ), forwardClino( shot.mClino ) );
  }

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
