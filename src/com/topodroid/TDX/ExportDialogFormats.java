package com.topodroid.TDX;

import java.util.ArrayList;

/** Visible export choices, retaining the original registry positions and format IDs. */
final class ExportDialogFormats
{
  final String[] types;
  private final int[] mPositions;
  private final int[] mFormats;

  private ExportDialogFormats( String[] candidates, String[] registry, int[] formats )
  {
    ArrayList<String> visible = new ArrayList<>();
    ArrayList<Integer> positions = new ArrayList<>();
    ArrayList<Integer> ids = new ArrayList<>();
    for ( String candidate : candidates ) {
      for ( int k = 0; k < registry.length; ++k ) {
        if ( registry[k].equals( candidate ) && TDConst.isExportFormatVisible( formats[k] ) ) {
          visible.add( candidate );
          positions.add( k );
          ids.add( formats[k] );
          break;
        }
      }
    }
    types = visible.toArray( new String[0] );
    mPositions = new int[types.length];
    mFormats = new int[types.length];
    for ( int k = 0; k < types.length; ++k ) {
      mPositions[k] = positions.get( k );
      mFormats[k] = ids.get( k );
    }
  }

  static ExportDialogFormats survey( String[] candidates )
  {
    return new ExportDialogFormats( candidates, TDConst.mSurveyExportTypes, TDConst.mSurveyExportIndex );
  }

  static ExportDialogFormats plot( String[] candidates, boolean overview )
  {
    return overview
      ? new ExportDialogFormats( candidates, TDConst.mOverviewExportTypes, TDConst.mOverviewExportIndex )
      : new ExportDialogFormats( candidates, TDConst.mPlotExportTypes, TDConst.mPlotExportIndex );
  }

  int position( int visiblePosition ) { return mPositions[visiblePosition]; }
  int format( int visiblePosition ) { return mFormats[visiblePosition]; }

  /** Use a visible saved default, then the requested fallback, then the first choice. */
  int selection( int preferredFormat, int fallbackFormat )
  {
    int fallback = types.length > 0 ? 0 : -1;
    for ( int k = 0; k < types.length; ++k ) {
      if ( mFormats[k] == preferredFormat ) return k;
      if ( mFormats[k] == fallbackFormat ) fallback = k;
    }
    return fallback;
  }

  static boolean surveySupported( int format )
  {
    return format == TDConst.SURVEY_FORMAT_ZIP || format == TDConst.SURVEY_FORMAT_DAT
      || format == TDConst.SURVEY_FORMAT_CSV;
  }

  static boolean plotSupported( int format ) { return format == TDConst.SURVEY_FORMAT_PNG; }
}
