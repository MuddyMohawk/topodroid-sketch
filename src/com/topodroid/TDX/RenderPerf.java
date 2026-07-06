/* @file RenderPerf.java
 *
 * @author MuddyMohawk
 * @date jul 2026
 *
 * @brief TopoDroid sketch render frame-time instrumentation
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 * Debug-only frame timing for the sketch render loop.
 * Disabled (single static-final boolean branch per call) unless the log tag
 * is enabled before app start:
 *     adb shell setprop log.tag.TDRenderPerf DEBUG
 * Then watch with:
 *     adb logcat -s TDRenderPerf
 * One summary line is emitted every WINDOW frames with avg/p50/max frame ms
 * and the average per-section breakdown.
 *
 * Sections are attributed on the render thread only; concurrent export
 * renders calling executeAll would skew section stats while they run.
 */
package com.topodroid.TDX;

import android.util.Log;

class RenderPerf
{
  private static final String TAG = "TDRenderPerf";

  static final boolean ENABLED = Log.isLoggable( TAG, Log.DEBUG );

  // section ids - order matches executeAll drawing order
  static final int SECTION_REFS     = 0; // reference-image / xsection underlays
  static final int SECTION_GRID     = 1; // grid stacks + north + scalebar
  static final int SECTION_SHOTS    = 2; // legs + splays
  static final int SECTION_STATIONS = 3; // station names + user stations
  static final int SECTION_SCRAPS   = 4; // scrap items (lines/areas/points)
  static final int SECTION_DOTS     = 5; // edit-mode selection dots + selection
  static final int SECTION_OTHER    = 6; // remainder (outlines, eraser, ...)
  private static final int NR_SECTIONS = 7;
  private static final String[] SECTION_NAMES = { "refs", "grid", "shots", "stations", "scraps", "dots", "other" };

  private static final int WINDOW = 64; // frames per summary line

  private static final long[] mFrameNs   = new long[ WINDOW ];
  private static final long[] mSectionNs = new long[ NR_SECTIONS ];
  private static long mFrameStartNs   = 0;
  private static long mSectionStartNs = 0;
  private static int  mFrameCnt = 0;

  /** mark the start of a frame (render thread only) */
  static void frameStart()
  {
    if ( ! ENABLED ) return;
    mFrameStartNs   = System.nanoTime();
    mSectionStartNs = mFrameStartNs;
  }

  /** close the previous section and charge its elapsed time to it
   * @param id   section that has just ENDED
   * @note call at the end of each instrumented block, in drawing order
   */
  static void section( int id )
  {
    if ( ! ENABLED ) return;
    if ( mFrameStartNs == 0 ) return; // frameStart not seen (disabled mid-run or export thread)
    long now = System.nanoTime();
    mSectionNs[ id ] += now - mSectionStartNs;
    mSectionStartNs = now;
  }

  /** mark the end of a frame; emits one summary line every WINDOW frames */
  static void frameEnd()
  {
    if ( ! ENABLED ) return;
    if ( mFrameStartNs == 0 ) return;
    long now = System.nanoTime();
    mSectionNs[ SECTION_OTHER ] += now - mSectionStartNs;
    mFrameNs[ mFrameCnt ++ ] = now - mFrameStartNs;
    mFrameStartNs = 0;
    if ( mFrameCnt < WINDOW ) return;

    long sum = 0, max = 0;
    for ( int k = 0; k < WINDOW; ++ k ) {
      sum += mFrameNs[ k ];
      if ( mFrameNs[ k ] > max ) max = mFrameNs[ k ];
    }
    java.util.Arrays.sort( mFrameNs );
    long p50 = mFrameNs[ WINDOW / 2 ];

    StringBuilder sb = new StringBuilder( 160 );
    sb.append( "frames " ).append( WINDOW )
      .append( " avg " ).append( String.format( java.util.Locale.US, "%.2f", sum / (WINDOW * 1e6) ) )
      .append( " p50 " ).append( String.format( java.util.Locale.US, "%.2f", p50 / 1e6 ) )
      .append( " max " ).append( String.format( java.util.Locale.US, "%.2f", max / 1e6 ) )
      .append( " ms |" );
    for ( int s = 0; s < NR_SECTIONS; ++ s ) {
      sb.append( ' ' ).append( SECTION_NAMES[ s ] ).append( ' ' )
        .append( String.format( java.util.Locale.US, "%.2f", mSectionNs[ s ] / (WINDOW * 1e6) ) );
      mSectionNs[ s ] = 0;
    }
    mFrameCnt = 0;
    Log.d( TAG, sb.toString() );
  }
}
