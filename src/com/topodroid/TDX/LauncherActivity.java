/* @file LauncherActivity.java
 *
 * @brief Launcher entry point that restores the existing TopoDroid task
 * --------------------------------------------------------
 * Copyright This software is distributed under GPL-3.0 or later
 * See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/** Keep launcher taps from replacing the active survey or drawing activity. */
public class LauncherActivity extends Activity
{
  @Override
  protected void onCreate( Bundle saved_instance_state )
  {
    super.onCreate( saved_instance_state );

    ActivityManager manager = (ActivityManager)getSystemService( Context.ACTIVITY_SERVICE );
    if ( manager != null ) {
      for ( ActivityManager.AppTask task : manager.getAppTasks() ) {
        ActivityManager.RecentTaskInfo info = task.getTaskInfo();
        ComponentName top = ( info == null ) ? null : info.topActivity;
        if ( top != null
            && getPackageName().equals( top.getPackageName() )
            && ! LauncherActivity.class.getName().equals( top.getClassName() ) ) {
          task.moveToFront();
          finishAndRemoveTask();
          return;
        }
      }
    }

    Intent intent = new Intent( this, MainWindow.class );
    intent.setAction( Intent.ACTION_MAIN );
    intent.addCategory( Intent.CATEGORY_LAUNCHER );
    intent.addFlags( Intent.FLAG_ACTIVITY_NEW_TASK );
    startActivity( intent );
    finishAndRemoveTask();
  }
}
