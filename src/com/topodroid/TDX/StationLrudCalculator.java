/* @file StationLrudCalculator.java
 *
 * @brief Reusable LRUD calculation using the application's export settings
 */
package com.topodroid.TDX;

import com.topodroid.prefs.TDSetting;
import com.topodroid.util.TDMath;
import com.topodroid.util.TDString;

import java.util.Collections;
import java.util.List;

final class StationLrudCalculator
{
  private StationLrudCalculator() { }

  /** Export-compatible calculation. Reverse splays remain excluded exactly as before. */
  static StationLrudResult computeForLeg( DBlock leg, List< DBlock > splays, boolean at_from )
  {
    if ( leg == null ) return new StationLrudResult();
    String station = at_from ? leg.mFrom : leg.mTo;
    return compute( leg, splays, station, false );
  }

  /** Station calculation used by smart points; accepts direct and reverse splays. */
  static StationLrudResult computeAtStation( DBlock reference_leg, List< DBlock > splays, String station )
  {
    if ( TDString.isNullOrEmpty( station ) ) return new StationLrudResult();
    if ( reference_leg == null && TDSetting.mOrthogonalLRUD ) return new StationLrudResult();
    return compute( reference_leg, null, null, splays, station, true );
  }

  /** Station calculation relative to an explicit cross-section facing. */
  static StationLrudResult computeAtStation( float bearing, float clino, List< DBlock > splays, String station )
  {
    if ( TDString.isNullOrEmpty( station ) ) return new StationLrudResult();
    return compute( null, Float.valueOf( bearing ), Float.valueOf( clino ), splays, station, true );
  }

  private static StationLrudResult compute( DBlock leg, List< DBlock > source, String station,
                                            boolean accept_reverse_splays )
  {
    return compute( leg, null, null, source, station, accept_reverse_splays );
  }

  private static StationLrudResult compute( DBlock leg, Float facing_bearing, Float facing_clino,
                                            List< DBlock > source, String station,
                                            boolean accept_reverse_splays )
  {
    StationLrudResult result = new StationLrudResult();
    if ( TDString.isNullOrEmpty( station ) ) return result;
    List< DBlock > splays = ( source == null ) ? Collections.< DBlock >emptyList() : source;

    float basis_bearing = ( facing_bearing != null ) ? facing_bearing.floatValue()
                                                     : ( leg == null ? 0.0f : leg.mBearing );
    float basis_clino = ( facing_clino != null ) ? facing_clino.floatValue()
                                                 : ( leg == null ? 0.0f : leg.mClino );
    float n0 = TDMath.cosd( basis_bearing );
    float e0 = TDMath.sind( basis_bearing );
    float cc0 = TDMath.cosd( basis_clino );
    float sc0 = TDMath.sind( basis_clino );

    boolean first_only = TDSetting.mLRUDcount;
    boolean take_left = true;
    boolean take_right = true;
    boolean take_up = true;
    boolean take_down = true;
    int remaining = 4;

    for ( DBlock item : splays ) {
      if ( item == null ) continue;
      boolean direct = station.equals( item.mFrom ) && TDString.isNullOrEmpty( item.mTo );
      boolean reverse = accept_reverse_splays && station.equals( item.mTo ) && TDString.isNullOrEmpty( item.mFrom );
      if ( ! direct && ! reverse ) continue;

      float bearing = reverse ? item.mBearing + 180.0f : item.mBearing;
      float clino = reverse ? -item.mClino : item.mClino;
      float cb = TDMath.cosd( bearing );
      float sb = TDMath.sind( bearing );
      float cc = TDMath.cosd( clino );
      float sc = TDMath.sind( clino );

      if ( TDSetting.mOrthogonalLRUD ) {
        float cosine = sc * sc0 * ( sb * e0 + cb * n0 ) + cc * cc0;
        if ( Math.abs( cosine ) > TDSetting.mOrthogonalLRUDCosine ) continue;
      }

      float z = item.mLength * sc;
      float north = item.mLength * cc * cb;
      float east = item.mLength * cc * sb;
      float abs_clino = Math.abs( clino );

      if ( abs_clino >= TDSetting.mLRUDvertical ) {
        if ( z > 0.0f ) {
          if ( ( ! first_only || take_up ) && z > result.up ) {
            result.up = z;
            result.hasUp = true;
            take_up = false;
          }
        } else if ( ( ! first_only || take_down ) && -z > result.down ) {
          result.down = -z;
          result.hasDown = true;
          take_down = false;
        }
        if ( first_only && --remaining == 0 ) break;
      } else if ( first_only && abs_clino <= TDSetting.mLRUDhorizontal ) {
        float right_left = east * n0 - north * e0;
        if ( right_left > 0.0f ) {
          if ( take_right && right_left > result.right ) {
            result.right = right_left;
            result.hasRight = true;
            take_right = false;
          }
        } else if ( take_left && -right_left > result.left ) {
          result.left = -right_left;
          result.hasLeft = true;
          take_left = false;
        }
        if ( --remaining == 0 ) break;
      }

      if ( ! first_only && abs_clino <= TDSetting.mLRUDhorizontal ) {
        float right_left = east * n0 - north * e0;
        if ( right_left > 0.0f ) {
          if ( right_left > result.right ) {
            result.right = right_left;
            result.hasRight = true;
          }
        } else if ( -right_left > result.left ) {
          result.left = -right_left;
          result.hasLeft = true;
        }
      }
    }
    return result;
  }
}
