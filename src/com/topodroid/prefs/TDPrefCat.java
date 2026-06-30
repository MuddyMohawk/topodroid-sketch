/* @file TDPrefCat.java
 *
 * @author marco corvi
 * @date aug 2018
 *
 * @brief TopoDroid preferences categories
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.prefs;

import com.topodroid.TDX.R;

public class TDPrefCat
{
  public static final String PREF_CATEGORY = "PrefCategory";

  // the order must be the same as TDPrefKey.mKeySet
  public static final int PREF_CATEGORY_ALL       =  0;
  public static final int PREF_CATEGORY_SURVEY    =  1;
  public static final int PREF_CATEGORY_PLOT      =  2;
  public static final int PREF_CATEGORY_CALIB     =  3;
  public static final int PREF_CATEGORY_DEVICE    =  4;
  public static final int PREF_CATEGORY_SKETCH    =  5;
  public static final int PREF_SHOT_DATA          =  6; 
  public static final int PREF_SHOT_UNITS         =  7; 
  public static final int PREF_ACCURACY           =  8; 
  public static final int PREF_LOCATION           =  9; 
  public static final int PREF_PLOT_SCREEN        = 10; 
  public static final int PREF_TOOL_LINE          = 11; 
  public static final int PREF_TOOL_POINT         = 12; 
  // public static final int PREF_PLOT_WALLS         = 29;  // AUTOWALLS UNUSED
  public static final int PREF_PLOT_DRAW          = 13; 
  public static final int PREF_PLOT_ERASE         = 14; 
  public static final int PREF_PLOT_EDIT          = 15;
  public static final int PREF_CATEGORY_CAVE3D    = 16;
  public static final int PREF_DEM3D              = 17;
  public static final int PREF_WALLS3D            = 18;
  public static final int PREF_CATEGORY_GEEK      = 19; 
  public static final int PREF_GEEK_SHOT          = 20; 
  public static final int PREF_GEEK_SPLAY         = 21; 
  public static final int PREF_GEEK_PLOT          = 22; 
  public static final int PREF_GEEK_LINE          = 23; 
  public static final int PREF_GEEK_DEVICE        = 24; 
  public static final int PREF_GEEK_IMPORT        = 25; 
  public static final int PREF_GEEK_SKETCH        = 26; 
  public static final int PREF_CATEGORY_EXPORT    = 27;
  public static final int PREF_CATEGORY_IMPORT    = 28;
  public static final int PREF_CATEGORY_EXPORT_ENABLE =29;
  public static final int PREF_CATEGORY_SVX       = 30;
  public static final int PREF_CATEGORY_TH        = 31;
  public static final int PREF_CATEGORY_DAT       = 32;
  public static final int PREF_CATEGORY_CSX       = 33;
  public static final int PREF_CATEGORY_TRO       = 34;
  public static final int PREF_CATEGORY_SVG       = 35;
  public static final int PREF_CATEGORY_SHP       = 36;
  public static final int PREF_CATEGORY_DXF       = 37;
  // public static final int PREF_CATEGORY_PNG       = 17; // NO_PNG
  public static final int PREF_CATEGORY_GPX       = 38;
  public static final int PREF_CATEGORY_KML       = 39;
  public static final int PREF_CATEGORY_CSV       = 40;
  public static final int PREF_CATEGORY_SRV       = 41;
  public static final int PREF_CATEGORY_PLY       = 42;
  public static final int PREF_TOOL_PRESET       = 43;
  public static final int PREF_PRESET_FIRST      = 44;
  public static final int PREF_PRESET_1          = 44;
  public static final int PREF_PRESET_2          = 45;
  public static final int PREF_PRESET_3          = 46;
  public static final int PREF_PRESET_4          = 47;
  public static final int PREF_PRESET_5          = 48;
  public static final int PREF_PRESET_6          = 49;
  public static final int PREF_PRESET_7          = 50;
  public static final int PREF_PRESET_8          = 51;
  public static final int PREF_PRESET_LAST       = 51;
  public static final int PREF_TOOL_STYLE        = 52;
  public static final int PREF_STYLE_FIRST       = 53;
  public static final int PREF_STYLE_1           = 53;
  public static final int PREF_STYLE_2           = 54;
  public static final int PREF_STYLE_3           = 55;
  public static final int PREF_STYLE_4           = 56;
  public static final int PREF_STYLE_5           = 57;
  public static final int PREF_STYLE_6           = 58;
  public static final int PREF_STYLE_7           = 59;
  public static final int PREF_STYLE_8           = 60;
  public static final int PREF_STYLE_LAST        = 60;
  public static final int PREF_CATEGORY_SPEN      = 61;
  // public static final int PREF_CATEGORY_LOG       = 42; // this must be the last NO_LOGS
  public static final int PREF_CATEGORY_MAX = 61; // last category

  public static int presetCategory( int preset )
  {
    return PREF_PRESET_FIRST + preset - 1;
  }

  public static int presetFromCategory( int cat )
  {
    if ( cat < PREF_PRESET_FIRST || cat > PREF_PRESET_LAST ) return 0;
    return cat - PREF_PRESET_FIRST + 1;
  }

  public static int styleCategory( int style )
  {
    return PREF_STYLE_FIRST + style - 1;
  }

  public static int styleFromCategory( int cat )
  {
    if ( cat < PREF_STYLE_FIRST || cat > PREF_STYLE_LAST ) return 0;
    return cat - PREF_STYLE_FIRST + 1;
  }

  // the order must be the same as TDPrefKey.mKeySet as above
  static int[] mTitleRes = {
    R.string.title_settings_main,     // 0
    R.string.title_settings_survey,
    R.string.title_settings_plot,
    R.string.title_settings_calib,
    R.string.title_settings_device,
    R.string.title_settings_sketch,   // 5
    R.string.title_settings_shot,     
    R.string.title_settings_units,    // 7
    R.string.title_settings_accuracy,
    R.string.title_settings_location,
    R.string.title_settings_screen,   // 10
    R.string.title_settings_line,
    R.string.title_settings_point,    // 12
    // R.string.title_settings_walls, // 27 AUTOWALLS
    R.string.title_settings_draw,
    R.string.title_settings_erase,    // 14
    R.string.title_settings_edit,
    R.string.title_settings_3d,
    R.string.title_settings_dem,
    R.string.title_settings_walls3d,  // 18
    R.string.title_settings_geek,
    R.string.title_settings_geek_survey,   // 20
    R.string.title_settings_geek_splay,    // 21
    R.string.title_settings_geek_plot,
    R.string.title_settings_geek_line,     // 23
    R.string.title_settings_geek_device,   //
    R.string.title_settings_geek_import,   // 25
    R.string.title_settings_sketch,   // 5 geek_sketch
    R.string.title_settings_export,
    R.string.title_settings_import,
    R.string.title_settings_export_enable,
    R.string.title_settings_svx,     // 30
    R.string.title_settings_th,
    R.string.title_settings_dat,
    R.string.title_settings_csx,
    R.string.title_settings_tro,
    R.string.title_settings_svg,      // 35
    R.string.title_settings_shp,
    R.string.title_settings_dxf,
    // R.string.title_settings_png, // 17
    R.string.title_settings_gpx,      // 38
    R.string.title_settings_kml,      // 39
    R.string.title_settings_csv,
    R.string.title_settings_srv,      // 41
    R.string.title_settings_ply,
    R.string.title_settings_presets,
    R.string.title_settings_preset,
    R.string.title_settings_preset,
    R.string.title_settings_preset,
    R.string.title_settings_preset,
    R.string.title_settings_preset,
    R.string.title_settings_preset,
    R.string.title_settings_preset,
    R.string.title_settings_preset,
    R.string.title_settings_styles,
    R.string.title_settings_style,
    R.string.title_settings_style,
    R.string.title_settings_style,
    R.string.title_settings_style,
    R.string.title_settings_style,
    R.string.title_settings_style,
    R.string.title_settings_style,
    R.string.title_settings_style,
    R.string.title_settings_spen,
    // R.string.title_settings_log       // 43
  };
}
