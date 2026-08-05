/* @file BeddingSurveyContext.java
 *
 * @brief Station and splay candidates available to a bedding fit editor
 */
package com.topodroid.TDX;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

final class BeddingSurveyContext
{
  static final class Splay
  {
    final long id;
    final double lengthMeters;
    final double bearingDegrees;
    final double clinoDegrees;
    final String type;
    final boolean manualSource;
    final boolean eligible;
    final String exclusionReason;

    Splay( DBlock block )
    {
      id = block.mId;
      lengthMeters = block.mLength;
      boolean reverse = block.isReverseSplay();
      bearingDegrees = wrap360( block.mBearing + ( reverse ? 180.0 : 0.0 ) );
      clinoDegrees = reverse ? -block.mClino : block.mClino;
      type = splayType( block );
      manualSource = block.isManual();
      boolean finite = Double.isFinite( lengthMeters ) && lengthMeters > 0.0
        && Double.isFinite( bearingDegrees ) && Double.isFinite( clinoDegrees )
        && Math.abs( clinoDegrees ) <= 90.0;
      eligible = block.isSplay() && ! block.isScan() && finite;
      exclusionReason = block.isScan() ? "scan" : ( finite ? "" : "invalid measurement" );
    }

    String displayLabel()
    {
      return String.format( Locale.US, "#%d  %.2f m  %.1f\u00b0 / %.1f\u00b0  %s",
        id, lengthMeters, bearingDegrees, clinoDegrees,
        type + ( manualSource ? ", manual" : "" ) );
    }

    private static String splayType( DBlock block )
    {
      if ( block.isScan() ) return "scan";
      if ( block.isXSplay() ) return "cross";
      if ( block.isHSplay() ) return "horizontal";
      if ( block.isVSplay() ) return "vertical";
      return block.isReverseSplay() ? "reverse splay" : "splay";
    }

    private static double wrap360( double degrees )
    {
      double value = degrees % 360.0;
      return value < 0.0 ? value + 360.0 : value;
    }
  }

  final String stationName;
  final List< Splay > splays;

  BeddingSurveyContext( String station, List< DBlock > blocks )
  {
    stationName = station == null ? "" : station;
    ArrayList< Splay > values = new ArrayList<>();
    if ( blocks != null ) for ( DBlock block : blocks ) if ( block != null ) values.add( new Splay( block ) );
    splays = Collections.unmodifiableList( values );
  }

  static BeddingSurveyContext empty()
  {
    return new BeddingSurveyContext( "", null );
  }
}
