/* @file DeviceSelectDialog.java
 *
 * @author marco corvi
 * @date nov 2016
 *
 * @brief TopoDroid DistoX device selection dialog (for multi-DistoX)
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

import com.topodroid.ui.MyDialog;
import com.topodroid.ui.ListItemAdapter;
import com.topodroid.dev.Device;

// import java.util.Set;
// import java.util.List;
import java.util.ArrayList;

// import android.app.Dialog;
import android.os.Bundle;

import android.content.Context;
// import android.content.DialogInterface;

import android.widget.ListView;
import android.widget.Button;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;

import android.view.View;
import android.view.View.OnClickListener;

class DeviceSelectDialog extends MyDialog
                         implements OnItemClickListener
                         , OnClickListener
{
  private final Context mContext;

  private ListView mList;
  private final ArrayList<Device> mDevices = new ArrayList<>();
  // private Button  mBtnCancel;

  // ---------------------------------------------------------------
  /** cstr
   * @param context    context
   * @param app        application
   */
  DeviceSelectDialog( Context context, TopoDroidApp app )
  {
    super( context, app, R.string.DeviceSelectDialog );
    mContext = context;
    // TDLog.v( "device select dialog created");
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) 
  {
    super.onCreate(savedInstanceState);
 
    // TDLog.v( "device select dialog init layout");
    initLayout( R.layout.device_select_dialog, R.string.title_device_select );

    // mBtnCancel = (Button) findViewById( R.id.button_cancel );
    // mBtnCancel.setOnClickListener( this );
    ( (Button) findViewById( R.id.button_cancel ) ).setOnClickListener( this );

    mList = (ListView) findViewById(R.id.dev_list);
    mList.setOnItemClickListener( this );
    // mList.setLongClickable( true );
    mList.setDividerHeight( 2 );
    updateList();
  }

  /** update the list of known devices
   */
  private void updateList( )
  {
    ListItemAdapter array_adapter = new ListItemAdapter( mContext, R.layout.message );
    // mArrayAdapter.clear();
    // if ( TDLevel.overTester ) { // FIXME VirtualDistoX
    //   array_adapter.add( "X000" );
    // }
    mDevices.clear();
    for ( Device device : mApp.knownDevices() ) {
      if ( device.isBT() || device.isBLE() ) {
        mDevices.add( device );
        String label = device.toString() + " [" + device.getAddress() + "]";
        if ( device.hasAddress( TDInstance.deviceAddress() ) ) label += " (active)";
        array_adapter.add( label );
      }
    }
    mList.setAdapter( array_adapter );
  }

  /** respond to a user tap on a device of the list
   * @param parent ...
   * @param view   tapped view
   * @param pos    device position in the list
   * @param id     ...
   */
  @Override 
  public void onItemClick(AdapterView<?> parent, View view, int pos, long id)
  {
    dismiss();
    if ( pos >= 0 && pos < mDevices.size() ) mApp.requestSurveyDeviceSwitch( mDevices.get( pos ).getAddress() );
  }

  /** respond to a user tap - dismiss the dialog
   * @param v  tapped view
   */
  @Override
  public void onClick(View v) 
  {
    // Button b = (Button) v;
    // if ( b == mBtnCancel ) {
    //   /* nothing */
    // }
    dismiss();
  }

}

