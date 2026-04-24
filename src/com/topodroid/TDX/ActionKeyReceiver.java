/* @file ActionKeyReceiver.java
 *
 * @author MuddyMohawk
 * @date apr 2026
 *
 * @brief Broadcast receiver for Samsung rugged-device Active Key reports
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ActionKeyReceiver extends BroadcastReceiver
{
  @Override
  public void onReceive( Context context, Intent intent )
  {
    ActionKeyBindingManager.onBroadcastIntent( intent );
  }
}
