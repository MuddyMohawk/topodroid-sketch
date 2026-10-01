/* @file DeviceX310TakeShot.java
 *
 * @author marco corvi
 * @date nov 2011
 *
 * @brief TopoDroid DistoX310 shooting class
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.dev.distox2;

import com.topodroid.TDX.ILister;
import com.topodroid.TDX.ListerHandler;
import com.topodroid.TDX.TopoDroidApp;
import com.topodroid.dev.RemoteShotTask;

public class DeviceX310TakeShot extends RemoteShotTask
{
  public DeviceX310TakeShot( ILister ilister, ListerHandler lister, TopoDroidApp app, int nr, int dataType )
  {
    super( ilister, lister, app, nr, dataType, DistoX310Comm.class );
  }
}
