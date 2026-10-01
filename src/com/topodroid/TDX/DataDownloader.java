/* @file DataDownloader.java
 *
 * @author marco corvi
 * @date sept 2014
 *
 * @brief TopoDroid survey shots data downloader (continuous mode)
 *
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

import android.content.Context;
import android.os.AsyncTask;
import android.os.SystemClock;
import com.topodroid.dev.ConnectionState;
import com.topodroid.dev.DeviceUtil;
import com.topodroid.prefs.TDSetting;

public class DataDownloader
{
  private final TopoDroidApp mApp;
  private ListerHandler mLister;
  private volatile int mConnected = ConnectionState.CONN_DISCONNECTED;
  private volatile boolean mDownloading;
  private volatile long mGeneration;
  private ReconnectTask mReconnectTask;

  DataDownloader( Context context, TopoDroidApp app ) { mApp = app; }
  boolean isDownloading() { return mDownloading; }
  boolean needReconnect() { return mDownloading && mConnected != ConnectionState.CONN_CONNECTED; }
  public void setConnected( int connected ) { mConnected = connected; }
  public void setDownloading( boolean downloading )
  {
    mDownloading = downloading;
    if ( ! downloading ) mConnected = ConnectionState.CONN_DISCONNECTED;
  }
  long generation() { return mGeneration; }
  boolean isCurrent( long generation ) { return generation == mGeneration && mDownloading; }

  public void updateConnected( boolean connected )
  {
    mConnected = connected ? ConnectionState.CONN_CONNECTED
      : (mDownloading ? ConnectionState.CONN_WAITING : ConnectionState.CONN_DISCONNECTED);
  }

  int getStatus() { return mDownloading ? mConnected : ConnectionState.CONN_DISCONNECTED; }

  public boolean toggleDownloading()
  {
    if ( mApp.isDeviceOperationBusy() ) return mDownloading;
    mDownloading = ! mDownloading;
    mConnected = mDownloading ? ConnectionState.CONN_WAITING : ConnectionState.CONN_DISCONNECTED;
    if ( ! mDownloading ) cancelReconnect();
    return mDownloading;
  }

  public void doDataDownload( ListerHandler lister, int dataType )
  {
    if ( mApp.isDeviceOperationBusy() ) return;
    mLister = lister;
    if ( ! mDownloading ) { stopDownloadData( lister ); return; }
    if ( TDInstance.isContinuousMode() ) {
      TDInstance.secondLastShotId = TopoDroidApp.lastShotId();
      requestReconnect( lister, dataType, 0 );
    } else {
      if ( TDInstance.getDeviceA() != null && DeviceUtil.isAdapterEnabled() ) {
        notifyConnectionStatus( lister, ConnectionState.CONN_WAITING );
        new DataDownloadTask( mApp, lister, null, dataType ).executeOnExecutor( AsyncTask.THREAD_POOL_EXECUTOR );
      } else connectionFailed( lister, mGeneration );
    }
  }

  synchronized void requestReconnect( ListerHandler lister, int dataType, int delay )
  {
    if ( ! needReconnect() || mApp.isDeviceOperationBusy() || mReconnectTask != null ) return;
    mReconnectTask = new ReconnectTask( this, lister, dataType, delay );
    mReconnectTask.executeOnExecutor( AsyncTask.THREAD_POOL_EXECUTOR );
  }

  synchronized void reconnectFinished( ReconnectTask task )
  {
    if ( mReconnectTask == task ) mReconnectTask = null;
  }

  private synchronized void cancelReconnect()
  {
    ++mGeneration;
    if ( mReconnectTask != null ) mReconnectTask.cancel( true );
    mReconnectTask = null;
  }

  /** Invalidate retries, but let accepted packets drain under the original context. */
  void prepareForDeviceSwitch() { cancelReconnect(); }

  void tryConnect( ListerHandler lister, int dataType, long generation )
  {
    if ( ! isCurrent( generation ) ) return;
    TopoDroidApp.DeviceOperation operation = mApp.beginDeviceOperation();
    if ( operation == null ) {
      if ( ! mApp.isDeviceOperationBusy() ) connectionFailed( lister, generation );
      return;
    }
    try {
      if ( ! isCurrent( generation ) ) return;
      if ( ! DeviceUtil.isAdapterEnabled() ) { connectionFailed( lister, generation ); return; }
      if ( operation.comm.isConnected() ) {
        mApp.notifyCommStatus( operation.comm, lister, ConnectionState.CONN_CONNECTED );
        return;
      }
      notifyConnectionStatus( lister, ConnectionState.CONN_WAITING );
      boolean accepted = operation.comm.connectDevice( operation.address, lister, dataType, 10 );
      if ( accepted && TDInstance.isDeviceBLE() ) {
        long deadline = SystemClock.elapsedRealtime() + 10000;
        while ( isCurrent( generation ) && ! operation.comm.isConnected() && SystemClock.elapsedRealtime() < deadline ) {
          try { Thread.sleep( 100 ); }
          catch ( InterruptedException e ) { Thread.currentThread().interrupt(); return; }
        }
        accepted = operation.comm.isConnected();
      }
      if ( ! isCurrent( generation ) ) return;
      if ( accepted ) mApp.notifyCommStatus( operation.comm, lister, ConnectionState.CONN_CONNECTED );
      else {
        operation.comm.disconnectDevice();
        connectionFailed( lister, generation );
      }
    } catch ( RuntimeException e ) {
      com.topodroid.util.TDLog.e( "Device connection: " + e.getMessage() );
      operation.comm.disconnectDevice();
      connectionFailed( lister, generation );
    } finally {
      try { if ( ! isCurrent( generation ) ) operation.comm.disconnectDevice(); }
      finally { operation.close(); }
    }
  }

  private void connectionFailed( ListerHandler lister, long generation )
  {
    if ( generation != mGeneration ) return;
    mDownloading = false;
    notifyConnectionStatus( lister, ConnectionState.CONN_DISCONNECTED );
    mApp.runOnMainThread( () -> { if ( generation == mGeneration ) TDToast.makeBad( R.string.device_connection_failed ); } );
  }

  void notifyConnectionStatus( ListerHandler lister, int connected )
  {
    mConnected = connected;
    final long generation = mGeneration;
    mApp.runOnMainThread( () -> { if ( generation == mGeneration && lister != null ) lister.setConnectionStatus( getStatus() ); } );
  }

  void stopDownloadData( ListerHandler lister )
  {
    cancelReconnect();
    if ( mApp.disconnectComm() ) notifyConnectionStatus( lister, ConnectionState.CONN_DISCONNECTED );
  }

  void onStop()
  {
    mApp.cancelPendingDeviceSwitch();
    mDownloading = false;
    stopDownloadData( mLister );
  }

  void onResume() { }
}
