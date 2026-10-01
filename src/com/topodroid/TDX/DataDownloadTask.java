/* @file DataDownloadTask.java
 *
 * @author marco corvi
 * @date feb 2012
 *
 * @brief TopoDroid batch data-download task
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

// import com.topodroid.util.TDLog;
import com.topodroid.calib.CalibInfo;

import java.lang.ref.WeakReference;

// import java.util.ArrayList;

import android.os.AsyncTask;
// import android.os.Handler;

class DataDownloadTask extends AsyncTask< String, Integer, Integer >
{
  private final WeakReference<TopoDroidApp> mApp; // FIXME LEAK
  private final WeakReference<GMActivity> mGMactivity; // can be null
  private TopoDroidApp.DeviceOperation mOperation;
  private final ListerHandler mLister; // FIXME_LISTER
  private int mDataType;

  /** cstr
   * @param app       TopoDroid app
   * @param lister    data lister
   * @param data_type packet datatype
   */
  DataDownloadTask( TopoDroidApp app, ListerHandler lister, GMActivity gm_activity, int data_type ) // FIXME_LISTER
  {
    // TDLog.e( "Data Download Task cstr" );
    // TDLog.v( "data download task cstr");
    mApp        = new WeakReference<TopoDroidApp>( app );
    mGMactivity = new WeakReference<GMActivity>( gm_activity );
    mLister = lister;
    mDataType = data_type;
  }

  @Override protected void onPreExecute()
  {
    TopoDroidApp app = mApp.get();
    if ( app != null ) mOperation = app.beginDeviceOperation();
  }

// -------------------------------------------------------------------
  /** task background execution
   * @param statuses   (unused)
   * @return the number of downloaded packets - ( 0 if there is no app )
   */
  @Override
  protected Integer doInBackground( String... statuses )
  {
    if ( mOperation == null ) return null;
    try {
      GMActivity gm = mGMactivity.get();
      TopoDroidApp app = mApp.get();
      if ( gm != null && ! gm.isFinishing() && app != null ) {
        int algo = gm.getAlgo();
        if ( algo == CalibInfo.ALGO_AUTO ) {
          algo = app.getCalibAlgoFromDevice();
          if ( algo < CalibInfo.ALGO_AUTO ) { // could not get the algo from the device type
            algo = CalibInfo.ALGO_LINEAR;
          }
          app.updateCalibAlgo( algo );
          gm.setAlgo( algo );
        }
        // app.setGMdownload( true ); // GM_DOWNLOAD
      }
      return app.downloadDataBatch( mOperation, mLister, mDataType );
    } catch ( RuntimeException e ) {
      com.topodroid.util.TDLog.e( "Data download: " + e.getMessage() );
      return -1;
    }
    // app.setGMdownload( false ); // GM_DOWNLOAD
  }

  // @Override
  // protected void onProgressUpdate( Integer... values)
  // {
  //   super.onProgressUpdate( values );
  //   // TDLog.Log( TDLog.LOG_COMM, "onProgressUpdate " + values );
  // }

  /** post-execution: forward the result to the lister and some housekeeping
   * @param res   result of the background execution
   */
  @Override protected void onPostExecute( Integer result )
  {
    if ( mOperation == null ) return;
    try {
      TopoDroidApp app = mApp.get();
      if ( result != null && mLister != null ) mLister.refreshDisplay( result, true );
      if ( app != null ) app.stopDownloading( mLister );
    } finally { mOperation.close(); }
  }

  @Override protected void onCancelled( Integer result ) { onPostExecute( result ); }
}
