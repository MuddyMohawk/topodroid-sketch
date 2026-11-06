/* @file ExportDialogTdm.java
 *
 * @author marco corvi
 * @date nov 2011
 *
 * @brief TopoDroid manager export dialog
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

import com.topodroid.ui.MyDialog;
import com.topodroid.util.TDColor;
// import com.topodroid.prefs.TDSetting;

// import android.app.Dialog;
// import android.app.Activity;
import android.os.Bundle;

// import android.content.Intent;

import android.content.Context;

import android.widget.Button;
import android.widget.Spinner;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import android.view.View;
// import android.view.View.OnKeyListener;
// import android.view.KeyEvent;
// import android.view.ViewGroup.LayoutParams;

public class ExportDialogTdm extends MyDialog
                   implements AdapterView.OnItemSelectedListener
                   , View.OnClickListener
{
  private Button   mBtnOk;
  // private Button   mBtnBack;

  private final IExporter mParent;
  private String[]  mTypes;
  private final ExportDialogFormats mFormats;
  private TextView mExportWarning;
  private String    mSelected;
  private final int mTitle;
  private String    mSurvey;
  private int       mSelectedPos;

  /** cstr
   * @param context     context
   * @param parent      parent window
   * @param types       export types, for the options
   * @param title       dialog title (resource)
   * @param survey      survey name
   */
  public ExportDialogTdm( Context context, IExporter parent, String[] types, int title, String survey )
  {
    super( context, null, R.string.ExportDialogTdm ); // null app
    mParent = parent;
    mFormats = ExportDialogFormats.survey( types );
    mTypes = mFormats.types;
    mSelected = null;
    mTitle    = title;
    mSurvey   = survey;
  }

// -------------------------------------------------------------------
  @Override
  protected void onCreate(Bundle savedInstanceState) 
  {
    super.onCreate(savedInstanceState);

    initLayout( R.layout.export_dialog_calib, mTitle );

    Spinner spin = (Spinner)findViewById( R.id.spin );
    ArrayAdapter adapter = new ArrayAdapter<>( mContext, R.layout.menu, mTypes );
    spin.setAdapter( adapter );

    mExportWarning = (TextView) findViewById( R.id.export_warning );
    mExportWarning.setTextColor( TDColor.YELLOW );

    mBtnOk   = (Button) findViewById(R.id.button_ok );
    mBtnOk.setOnClickListener( this );
    // mBtnBack = (Button) findViewById(R.id.button_back );
    // mBtnBack.setOnClickListener( this );
    ( (Button) findViewById(R.id.button_back ) ).setOnClickListener( this );

    // Bundle extras = getIntent().getExtras();
    // String title  = extras.getString( TopoDroidApp.TOPODROID_SURVEY );

    int pos = mFormats.selection( TDConst.SURVEY_FORMAT_NONE, TDConst.SURVEY_FORMAT_NONE );
    mSelected = pos >= 0 ? mTypes[pos] : null;
    mSelectedPos = pos >= 0 ? mFormats.position( pos ) : -1;
    mBtnOk.setEnabled( pos >= 0 );
    spin.setSelection( pos );
    updateWarning();
    spin.setOnItemSelectedListener( this );
  }

  /** react to an item selection
   * @param av    item adapter
   * @param v     item view
   * @param pos   item position
   * @param id    ?
   */
  @Override
  public void onItemSelected( AdapterView av, View v, int pos, long id ) 
  { 
    mSelected = mTypes[ pos ];
    mSelectedPos = mFormats.position( pos );
    updateWarning();
  }

  /** react to a deselection
   * @param av    item adapter
   */
  @Override
  public void onNothingSelected( AdapterView av )
  {
    mSelected = null;
    mSelectedPos = -1;
    updateWarning();
  }

  private void updateWarning()
  {
    mExportWarning.setVisibility( mSelected == null ? View.GONE : View.VISIBLE );
    if ( mSelected != null ) mExportWarning.setText( mContext.getString( R.string.export_unsupported_warning, mSelected ) );
  }

  /** react to a user tap
   * @param v  tapped view
   */
  @Override
  public void onClick(View v) 
  {
    // TDLog.v("TDM selected " + mSelected );
    Button b = (Button)v;
    if ( b == mBtnOk && mSelected != null ) {
      mParent.doExport( mSelected, getFilename(), null, -1L, false ); // null prefix, false second, -1=first
    // } else if ( b == mBtnBack ) {
    //   /* nothing */
    }
    dismiss();
  }

  /** @return the filename, ie, survey + extension
   */
  private String getFilename()
  {
    return TDConst.getSurveyFilename( mSelectedPos, mSurvey );
  } 

}


