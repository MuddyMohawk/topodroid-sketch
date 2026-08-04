/* @file FramedTextPointState.java
 *
 * @brief Shared presentation contract for framed-value special points
 */
package com.topodroid.TDX;

interface FramedTextPointState extends SpecialPointState
{
  enum Separator { NONE, WAVE }

  String[] displayRows( String primary_text );

  Separator separator();

  String fontId();

  boolean bold();

  boolean italic();

  boolean underline();

  int textScalePercent();
}
