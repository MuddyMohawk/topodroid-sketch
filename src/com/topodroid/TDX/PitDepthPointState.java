/* @file PitDepthPointState.java
 *
 * @brief Immutable typography state for the pit-depth special point
 */
package com.topodroid.TDX;

final class PitDepthPointState implements FramedTextPointState
{
  static final int MIN_TEXT_SCALE = FramedTextPointState.MIN_TEXT_SCALE;
  static final int MAX_TEXT_SCALE = FramedTextPointState.MAX_TEXT_SCALE;
  static final int DEFAULT_TEXT_SCALE = FramedTextPointState.DEFAULT_TEXT_SCALE;

  private final String mFontId;
  private final boolean mBold;
  private final boolean mItalic;
  private final boolean mUnderline;
  private final int mTextScalePercent;

  PitDepthPointState( String font_id, boolean bold, boolean italic, boolean underline,
                      int text_scale_percent )
  {
    mFontId = SketchFontRegistry.normalizeFontId( font_id );
    mBold = bold;
    mItalic = italic;
    mUnderline = underline;
    mTextScalePercent = Math.max( MIN_TEXT_SCALE, Math.min( MAX_TEXT_SCALE, text_scale_percent ) );
  }

  static PitDepthPointState defaultState()
  {
    return new PitDepthPointState( SketchFontRegistry.FONT_DEFAULT,
                                   false, false, false, DEFAULT_TEXT_SCALE );
  }

  PitDepthPointState withTypography( SketchTextStyle style )
  {
    if ( style == null ) return this;
    return new PitDepthPointState( style.fontId(), style.bold(), style.italic(),
                                   style.underline(), mTextScalePercent );
  }

  @Override public String[] displayRows( String primary_text )
  {
    return new String[] { ( primary_text == null ) ? "" : primary_text };
  }

  @Override public Separator separator() { return Separator.NONE; }
  @Override public String fontId() { return mFontId; }
  @Override public boolean bold() { return mBold; }
  @Override public boolean italic() { return mItalic; }
  @Override public boolean underline() { return mUnderline; }
  @Override public int textScalePercent() { return mTextScalePercent; }
}
