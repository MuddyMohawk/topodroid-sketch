package com.topodroid.dev;

import android.os.AsyncTask;
import com.topodroid.TDX.ILister;
import com.topodroid.TDX.ListerHandler;
import com.topodroid.TDX.TopoDroidApp;
import com.topodroid.TDX.TDToast;
import com.topodroid.TDX.R;
import com.topodroid.prefs.TDSetting;
import com.topodroid.util.TDLog;
import com.topodroid.util.TDUtil;

/** Keeps the entire remote shot sequence on one captured instrument. */
public class RemoteShotTask extends AsyncTask<Integer, Integer, Boolean>
{
  private final ILister mILister;
  private final ListerHandler mLister;
  private final TopoDroidApp mApp;
  private final int mNr;
  private final int mDataType;
  private final Class<?> mDriverType;
  private TopoDroidApp.DeviceOperation mOperation;

  protected RemoteShotTask( ILister ilister, ListerHandler lister, TopoDroidApp app, int nr, int dataType, Class<?> driverType )
  {
    mILister = ilister;
    mLister = lister;
    mApp = app;
    mNr = nr;
    mDataType = dataType;
    mDriverType = driverType;
  }

  @Override protected void onPreExecute()
  {
    mOperation = mApp.beginDeviceOperation();
    mILister.enableBluetoothButton( false );
  }

  @Override protected Boolean doInBackground( Integer... ignored )
  {
    if ( mOperation == null || ! mDriverType.isInstance( mOperation.comm ) || mNr <= 0 ) return false;
    try {
      boolean classic = mOperation.comm instanceof com.topodroid.dev.distox2.DistoX310Comm;
      if ( classic || (mNr > 1 && mDataType == DataType.DATA_CALIB) ) TDUtil.slowDown( TDSetting.mWaitShot );
      for ( int i = 0; i < mNr; ++i ) {
        if ( isCancelled() || ! mOperation.laser( Device.LASER_ON, 0, mLister, mDataType, false ) ) return false;
        if ( ! TDUtil.slowDown( TDSetting.mWaitLaser ) ) return false;
        boolean last = i == mNr - 1;
        int toRead = last && mLister != null ? (classic ? mNr : 1) : 0;
        if ( ! mOperation.laser( Device.MEASURE, toRead, classic && ! last ? null : mLister, mDataType, last ) ) return false;
        if ( ! last && ! TDUtil.slowDown( TDSetting.mWaitShot ) ) return false;
      }
      return true;
    } catch ( RuntimeException e ) {
      TDLog.e( "Remote shot: " + e.getMessage() );
      return false;
    }
  }

  @Override protected void onPostExecute( Boolean success )
  {
    if ( mOperation != null ) mOperation.close();
    mILister.enableBluetoothButton( true );
    if ( ! Boolean.TRUE.equals( success ) ) TDToast.makeBad( R.string.device_command_failed );
  }

  @Override protected void onCancelled( Boolean success ) { onPostExecute( false ); }
}
