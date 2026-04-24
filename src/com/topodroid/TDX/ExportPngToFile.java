/* @file ExportPngToFile.java
 *
 * @author MuddyMohawk
 * @date apr 2026
 *
 * @brief TopoDroid export sketch PNG to file
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

import com.topodroid.util.TDFile;
import com.topodroid.util.TDsafUri;
import com.topodroid.prefs.TDSetting;

import java.io.FileOutputStream;
import java.io.IOException;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.ParcelFileDescriptor;

class ExportPngToFile extends AsyncTask<Void,Void,Boolean>
{
  private final TopoDroidApp mApp;
  private final Bitmap mBitmap;
  private final String mFilename;
  private final boolean mToast;
  private final String mFormat;
  private final Uri mUri;

  ExportPngToFile( TopoDroidApp app, Context context, Uri uri, Bitmap bitmap, String filename, boolean toast )
  {
    mApp = app;
    mUri = uri;
    mBitmap = bitmap;
    mFilename = filename;
    mToast = toast;
    mFormat = context.getResources().getString( R.string.saved_file_1 );
  }

  @Override
  protected Boolean doInBackground( Void... args )
  {
    if ( mBitmap == null ) return false;

    ParcelFileDescriptor pfd = null;
    FileOutputStream fos = null;
    try {
      TDPath.checkOutdir();
      boolean use_saf = ( mUri != null && "content".equalsIgnoreCase( mUri.getScheme() ) );
      if ( use_saf ) {
        pfd = TDsafUri.docWriteFileDescriptor( mUri );
        if ( pfd != null ) fos = TDsafUri.docFileOutputStream( pfd );
      }
      if ( fos == null ) {
        String file_path = TDPath.getOutFile( mFilename );
        fos = TDFile.getFileOutputStream( file_path );
      }
      if ( fos == null ) return false;
      boolean ok = mBitmap.compress( Bitmap.CompressFormat.PNG, 100, fos );
      fos.flush();
      return ok;
    } catch ( IOException e ) {
      return false;
    } finally {
      if ( fos != null ) {
        try {
          fos.close();
        } catch ( IOException e ) {
          // ignore close failure
        }
      }
      if ( pfd != null ) {
        TDsafUri.closeFileDescriptor( pfd );
      }
    }
  }

  @Override
  protected void onPostExecute( Boolean ok )
  {
    super.onPostExecute( ok );
    try {
      if ( mToast ) {
        if ( ok ) {
          TDToast.make( String.format( mFormat, mFilename ) );
        } else {
          TDToast.makeBad( R.string.saving_file_failed );
        }
      }
      if ( ok && TDSetting.mExportPlotShare && mApp != null ) {
        String mimetype = TDConst.getMimeFromExtension( "png" );
        if ( mimetype != null ) {
          mApp.shareFile( mFilename, mimetype, 2 );
        }
      }
    } finally {
      if ( mBitmap != null && ! mBitmap.isRecycled() ) mBitmap.recycle();
    }
  }
}
