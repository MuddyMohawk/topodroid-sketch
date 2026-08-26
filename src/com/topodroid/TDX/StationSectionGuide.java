/* @file StationSectionGuide.java
 *
 * @brief Geometry and identification for editable at-station section guides
 */
package com.topodroid.TDX;

import com.topodroid.prefs.TDSetting;
import com.topodroid.util.TDMath;

final class StationSectionGuide
{
  static final float MIN_HALF_LENGTH_METRES = 0.10f;

  private StationSectionGuide() { }

  static boolean isGuide( DrawingLinePath line )
  {
    if ( line == null || line.mLineType != BrushManager.getLineSectionIndex() || line.size() != 3 ) return false;
    if ( ! "1".equals( SketchPrivateOptions.getOptionValue( line.mOptions,
                                                            SketchPrivateOptions.OPTION_STATION_GUIDE ) ) ) return false;
    String id = line.getOption( "-id" );
    return id != null && ( id.startsWith( "xs-" ) || id.startsWith( "xh-" ) );
  }

  static LinePoint anchor( DrawingLinePath line )
  {
    return isGuide( line ) ? line.mFirst.mNext : null;
  }

  static DrawingLinePath create( int scrap, String id, float cx, float cy,
                                 float tick_x, float tick_y,
                                 float first_metres, float last_metres )
  {
    float norm = (float)Math.hypot( tick_x, tick_y );
    if ( norm < 0.0001f ) { tick_x = 0.0f; tick_y = -1.0f; norm = 1.0f; }
    tick_x /= norm;
    tick_y /= norm;
    float tangent_x = -tick_y;
    float tangent_y = tick_x;
    float first = Math.max( MIN_HALF_LENGTH_METRES, first_metres ) * DrawingUtil.SCALE_FIX;
    float last  = Math.max( MIN_HALF_LENGTH_METRES, last_metres  ) * DrawingUtil.SCALE_FIX;

    DrawingLinePath line = new DrawingLinePath( BrushManager.getLineSectionIndex(), scrap );
    line.addOption( "-direction both" );
    line.addOption( "-id " + id );
    line.addOption( SketchPrivateOptions.OPTION_STATION_GUIDE + " 1" );
    line.addStartPoint( cx - first * tangent_x, cy - first * tangent_y );
    line.addPoint( cx, cy );
    line.addPoint( cx + last * tangent_x, cy + last * tangent_y );
    line.computeUnitNormal();
    return line;
  }

  static float paddingMetres()
  {
    String unit = TDSetting.mUnitLengthStr;
    return ( "ft".equalsIgnoreCase( unit ) || "feet".equalsIgnoreCase( unit ) ) ? 0.9144f : 1.0f;
  }

  /** First/last lengths are left/right, or visible upper/lower for a vertical profile cut. */
  static HalfLengths initialLengths( StationLrudResult lrud, boolean vertical_profile )
  {
    if ( lrud == null ) lrud = new StationLrudResult();
    float first_value;
    float last_value;
    boolean has_first;
    boolean has_last;
    if ( vertical_profile ) {
      first_value = lrud.up;
      last_value = lrud.down;
      has_first = lrud.hasUp;
      has_last = lrud.hasDown;
    } else {
      first_value = lrud.left;
      last_value = lrud.right;
      has_first = lrud.hasLeft;
      has_last = lrud.hasRight;
    }
    if ( ! has_first && has_last ) first_value = last_value;
    if ( ! has_last && has_first ) last_value = first_value;
    if ( ! has_first && ! has_last ) first_value = last_value = 0.0f;
    float padding = paddingMetres();
    return new HalfLengths( Math.max( MIN_HALF_LENGTH_METRES, first_value + padding ),
                            Math.max( MIN_HALF_LENGTH_METRES, last_value + padding ) );
  }

  static float tickX( float azimuth, float clino, long parent_type )
  {
    if ( parent_type == com.topodroid.types.PlotType.PLOT_PLAN ) return TDMath.sind( azimuth );
    if ( clino > 89.0f || clino < -89.0f ) return 0.0f;
    return 1.0f;
  }

  static float tickY( float azimuth, float clino, long parent_type )
  {
    if ( parent_type == com.topodroid.types.PlotType.PLOT_PLAN ) return -TDMath.cosd( azimuth );
    if ( clino > 89.0f ) return -1.0f;
    if ( clino < -89.0f ) return 1.0f;
    return 0.0f;
  }

  static final class HalfLengths
  {
    final float firstMetres;
    final float lastMetres;

    HalfLengths( float first_metres, float last_metres )
    {
      firstMetres = first_metres;
      lastMetres = last_metres;
    }
  }
}
