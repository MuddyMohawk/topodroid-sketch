/* @file NativeName.java
 *
 * @author marco corvi
 * @date may 2017
 *
 * @brief native name
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

import com.topodroid.util.TDLog;

//  import android.util.FloatMath;
// import java.util.List;
import java.util.Set;

class NativeName
{
  static NativeName mNativeName = null; // singleton
  private static boolean mLoadAttempted = false;
  private static boolean mLibraryLoaded = false;

  public native static String incrementName( String name, Set<String> stations );

  public native void initLog();

  // Lazy loading lets station naming fall back to Java when the native
  // library is not packaged in the APK.
  private static boolean ensureLibraryLoaded()
  {
    if ( ! mLoadAttempted ) {
      mLoadAttempted = true;
      try {
        System.loadLibrary( "nativename" );
        mLibraryLoaded = true;
      } catch ( java.lang.UnsatisfiedLinkError e ) {
        TDLog.e("Native link error " + e.getMessage() );
        mLibraryLoaded = false;
      }
    }
    return mLibraryLoaded;
  }

  private NativeName()
  {
    initLog();
  }

  /** factory method
   * @return the NativeName or null if failed to create
   */
  static NativeName get()
  {
    if ( ! ensureLibraryLoaded() ) return null;
    if ( mNativeName == null ) {
      try {
        mNativeName = new NativeName();
        // TDLog.v( "Using native name lib" );
      } catch ( java.lang.UnsatisfiedLinkError e ) {
        TDLog.e("Native link error " + e.getMessage() );
        mNativeName = null;
        mLibraryLoaded = false;
      }
    }
    return mNativeName;
  }


}
