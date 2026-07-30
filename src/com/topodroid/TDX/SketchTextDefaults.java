/* @file SketchTextDefaults.java
 *
 * @author MuddyMohawk
 * @date jul 2026
 *
 * @brief Local per-survey default style for the next Sketch text object
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

final class SketchTextDefaults
{
  private static final String KEY_PREFIX = "sketch_text_style:";

  private SketchTextDefaults() { }

  static SketchTextStyle load( DataHelper data, long survey_id )
  {
    if ( data == null || survey_id < 0 ) return SketchTextStyle.defaultStyle();
    SketchTextStyle style = SketchTextStyleCodec.decode( data.getValue( key( survey_id ) ) );
    return ( style == null ) ? SketchTextStyle.defaultStyle() : style;
  }

  static void save( DataHelper data, long survey_id, SketchTextStyle style )
  {
    if ( data == null || survey_id < 0 || style == null ) return;
    data.setValue( key( survey_id ), SketchTextStyleCodec.encode( style ) );
  }

  static void delete( DataHelper data, long survey_id )
  {
    if ( data == null || survey_id < 0 ) return;
    data.deleteValue( key( survey_id ) );
  }

  static String key( long survey_id )
  {
    return KEY_PREFIX + survey_id;
  }
}
