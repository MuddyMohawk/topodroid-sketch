/* @file DeviceCavwayTakeShot.java
 *
 * @author siwei tian
 * @date july 2024
 *
 * @brief TopoDroid Cavway shooting class
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.dev.cavway;

import com.topodroid.TDX.ILister;
import com.topodroid.TDX.ListerHandler;
import com.topodroid.TDX.TopoDroidApp;
import com.topodroid.dev.RemoteShotTask;

public class CavwayTakeShot extends RemoteShotTask
{
  public CavwayTakeShot( ILister ilister, ListerHandler lister, TopoDroidApp app, int nr, int dataType )
  {
    super( ilister, lister, app, nr, dataType, CavwayComm.class );
  }
}
