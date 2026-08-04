/* @file FramedTextPointState.java
 *
 * @brief Shared presentation contract for framed-value special points
 */
package com.topodroid.TDX;

interface FramedTextPointState extends SpecialPointState
{
  int MIN_TEXT_SCALE = 50;
  int MAX_TEXT_SCALE = 200;
  int DEFAULT_TEXT_SCALE = 175;

  enum Separator { NONE, WAVE }

  String[] displayRows( String primary_text );

  Separator separator();

  String fontId();

  boolean bold();

  boolean italic();

  boolean underline();

  int textScalePercent();
}
