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
import com.topodroid.util.TDLog;
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
  private final DrawingSurface mSurface;
  private final long mPlotType;
  private final SketchPngExportOptions mOptions;
  private final String mFilename;
  private final boolean mToast;
  private final String mFormat;
  private final Uri mUri;

  ExportPngToFile( TopoDroidApp app, Context context, Uri uri, DrawingSurface surface, long plot_type,
                   SketchPngExportOptions options, String filename, boolean toast )
  {
    mApp = app;
    mUri = uri;
    mSurface = surface;
    mPlotType = plot_type;
    mOptions = options;
    mFilename = filename;
    mToast = toast;
    mFormat = context.getResources().getString( R.string.saved_file_1 );
  }

  @Override
  protected Boolean doInBackground( Void... args )
  {
    if ( mSurface == null || mOptions == null ) return false;

    ParcelFileDescriptor pfd = null;
    FileOutputStream fos = null;
    String file_path = null; // set when writing a plain file (not SAF): deleted on failure
    boolean ok = false;
    Bitmap bitmap = null;
    try {
      // rendering a big sketch at export scale takes seconds: it runs here,
      // off the UI thread (same concurrency model as the other export renders)
      bitmap = mSurface.renderExportBitmap( mPlotType, mOptions );
      if ( bitmap == null ) return false;

      TDPath.checkOutdir();
      boolean use_saf = ( mUri != null && "content".equalsIgnoreCase( mUri.getScheme() ) );
      if ( use_saf ) {
        pfd = TDsafUri.docWriteFileDescriptor( mUri );
        if ( pfd != null ) fos = TDsafUri.docFileOutputStream( pfd );
      }
      if ( fos == null ) {
        file_path = TDPath.getOutFile( mFilename );
        fos = TDFile.getFileOutputStream( file_path );
      }
      if ( fos == null ) return false;
      ok = bitmap.compress( Bitmap.CompressFormat.PNG, 100, fos );
      fos.flush();
      return ok;
    } catch ( IOException e ) {
      return false;
    } catch ( OutOfMemoryError e ) {
      // compress streams rows top-down: a mid-write OOM leaves a truncated
      // file that image viewers decode as the top slice of the sketch -
      // fail loudly and remove the partial file instead
      TDLog.e( "png export oom " + e.getMessage() );
      return false;
    } catch ( RuntimeException e ) {
      TDLog.e( "png export failed " + e.getMessage() );
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
      if ( ! ok && file_path != null ) {
        TDFile.deleteFile( file_path ); // never leave a truncated export behind
      }
      if ( bitmap != null && ! bitmap.isRecycled() ) bitmap.recycle();
    }
  }

  @Override
  protected void onPostExecute( Boolean ok )
  {
    super.onPostExecute( ok );
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
  }
}
