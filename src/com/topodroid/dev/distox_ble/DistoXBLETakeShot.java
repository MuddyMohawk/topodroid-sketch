/* @file DeviceXBLETakeShot.java
 *
 * @author siwei tian
 * @date aug 2022
 *
 * @brief TopoDroid DistoXBLE shooting class
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.dev.distox_ble;

import com.topodroid.TDX.ILister;
import com.topodroid.TDX.ListerHandler;
import com.topodroid.TDX.TopoDroidApp;
import com.topodroid.dev.RemoteShotTask;

public class DistoXBLETakeShot extends RemoteShotTask
{
  public DistoXBLETakeShot( ILister ilister, ListerHandler lister, TopoDroidApp app, int nr, int dataType )
  {
    super( ilister, lister, app, nr, dataType, DistoXBLEComm.class );
  }
}
