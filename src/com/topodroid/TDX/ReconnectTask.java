/* @file ReconnectTask.java
 *
 * @author marco corvi
 * @date nov 2013
 *
 * @brief TopoDroid Data download reconnection task
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

import android.os.AsyncTask;

class ReconnectTask extends AsyncTask<String, Integer, Integer>
{
  private final DataDownloader mDownloader;
  private final ListerHandler mLister;
  private final int mDataType;
  private final int mDelay;
  private final long mGeneration;

  ReconnectTask( DataDownloader downloader, ListerHandler lister, int dataType, int delay )
  {
    mDownloader = downloader;
    mLister = lister;
    mDataType = dataType;
    mDelay = delay;
    mGeneration = downloader.generation();
  }

  @Override protected Integer doInBackground( String... ignored )
  {
    try {
      if ( mDelay > 0 ) Thread.sleep( mDelay );
      if ( ! isCancelled() && mDownloader.isCurrent( mGeneration ) ) mDownloader.tryConnect( mLister, mDataType, mGeneration );
    } catch ( InterruptedException e ) { Thread.currentThread().interrupt(); }
    finally { mDownloader.reconnectFinished( this ); }
    return 0;
  }
}
