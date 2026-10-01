package com.topodroid.TDX;

import static org.junit.Assert.*;

import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.topodroid.prefs.TDSetting;
import com.topodroid.types.LegType;
import com.topodroid.util.TDColor;
import com.topodroid.util.TDStatus;

import java.lang.reflect.Field;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith( AndroidJUnit4.class )
public class BacksightWarningInstrumentedTest
{
  private static final String COMMENT = "Error. M: 1.0000 2.0000; surveyor note";
  private ActivityScenario< ShotWindow > scenario;
  private TopoDroidApp app;
  private DataHelper data;
  private long surveyId = -1;
  private long foreId;
  private long backId;
  private long nextId;
  private String surveyName;
  private float previousLengthUnit;
  private float previousAngleUnit;
  private String previousLengthName;
  private String previousAngleName;
  private boolean previousEditable;
  private boolean previousKeyboard;
  private boolean previousDialogR;
  private ShotEditDialog dialog;

  @Before public void setUp()
  {
    // Bootstrap normally; never use the visual suite's all-survey cleanup on a field tablet.
    try ( ActivityScenario< MainWindow > main = ActivityScenario.launch( MainWindow.class ) ) {
      main.onActivity( activity -> {
        app = (TopoDroidApp) activity.getApplication();
        data = TopoDroidApp.mData;
        assertNotNull( data );
        previousDialogR = TopoDroidApp.sayDialogR();
        TopoDroidApp.setSayDialogR( false ); // MainWindow normally exits the process on destruction.
        previousLengthUnit = TDSetting.mUnitLength;
        previousAngleUnit = TDSetting.mUnitAngle;
        previousLengthName = TDSetting.mUnitLengthStr;
        previousAngleName = TDSetting.mUnitAngleStr;
        previousEditable = TDSetting.mEditableShots;
        previousKeyboard = TDSetting.mKeyboard;
        TDSetting.mUnitLength = 1;
        TDSetting.mUnitLengthStr = "m";
        TDSetting.mUnitAngle = 1;
        TDSetting.mUnitAngleStr = "deg";
        TDSetting.mEditableShots = true;
        TDSetting.mKeyboard = false;
        surveyName = "backsight_warning_qa_" + System.nanoTime();
        surveyId = app.setSurveyFromName( surveyName, SurveyInfo.DATAMODE_NORMAL, false, true );
        assertTrue( surveyId > 0 );
        foreId = insert( "A", "B", 10, 90, 5, "foresight note" );
        backId = insert( "B", "A", 12, 270, -5, COMMENT );
        nextId = insert( "B", "C", 8, 0, 0, "next leg" );
      } );
    }
    scenario = ActivityScenario.launch( ShotWindow.class );
  }

  @After public void tearDown()
  {
    if ( scenario != null ) {
      scenario.onActivity( activity -> { if ( dialog != null ) dialog.dismiss(); } );
      scenario.close();
    }
    InstrumentationRegistry.getInstrumentation().runOnMainSync( () -> {
      TDSetting.mUnitLength = previousLengthUnit;
      TDSetting.mUnitAngle = previousAngleUnit;
      TDSetting.mUnitLengthStr = previousLengthName;
      TDSetting.mUnitAngleStr = previousAngleName;
      TDSetting.mEditableShots = previousEditable;
      TDSetting.mKeyboard = previousKeyboard;
      if ( data != null && surveyId > 0 && data.getSurveyId( surveyName ) == surveyId ) {
        data.doDeleteSurvey( surveyId ); // Only this test's newly created database rows.
      }
      if ( app != null ) app.setSurveyFromName( null, 0, false, false );
      if ( app != null ) TopoDroidApp.setSayDialogR( previousDialogR );
    } );
  }

  @Test public void editorKeepsMetadataAndCommentsAndResetsOnNavigation()
  {
    scenario.onActivity( activity -> {
      DBlockAdapter adapter = adapter( activity );
      DBlock bad = block( adapter, backId );
      View row = adapter.getView( position( adapter, backId ), null, (ListView) activity.findViewById( R.id.list ) );
      TextView measurements = row.findViewById( R.id.length );
      assertEquals( TDColor.DARK_ORANGE, ( (ColorDrawable) measurements.getBackground() ).getColor() );
      assertEquals( TDColor.BLACK, measurements.getCurrentTextColor() );
      assertEquals( 1, measurements.getMaxLines() );
      View reused = adapter.getView( position( adapter, foreId ), row, (ListView) activity.findViewById( R.id.list ) );
      assertEquals( TDColor.BLACK, ( (ColorDrawable) measurements.getBackground() ).getColor() );
      assertEquals( block( adapter, foreId ).getColorByType(), measurements.getCurrentTextColor() );
      adapter.getView( position( adapter, backId ), reused, (ListView) activity.findViewById( R.id.list ) );
      assertEquals( TDColor.DARK_ORANGE, ( (ColorDrawable) measurements.getBackground() ).getColor() );

      dialog = new ShotEditDialog( activity, activity, position( adapter, backId ), bad,
        block( adapter, foreId ), block( adapter, nextId ) );
      dialog.show();
      TextView warning = dialog.findViewById( R.id.shot_backsight_warning );
      TextView sensors = dialog.findViewById( R.id.shot_extra );
      TextView time = dialog.findViewById( R.id.shot_time );
      assertEquals( View.VISIBLE, warning.getVisibility() );
      assertEquals( "Backsight should be 10.00 m, 270.0\u00b0, -5.0\u00b0", warning.getText().toString() );
      assertEquals( TDColor.FIXED_ORANGE, warning.getCurrentTextColor() );
      assertTrue( sensors.getText().toString().startsWith( "A " ) );
      assertTrue( time.getText().length() > 0 );
      assertEquals( sensors.getParent(), warning.getParent() );
      android.view.ViewGroup metadata = (android.view.ViewGroup) warning.getParent();
      assertTrue( metadata.indexOfChild( sensors ) < metadata.indexOfChild( warning ) );
      assertTrue( metadata.indexOfChild( warning ) < metadata.indexOfChild( time ) );
      assertEquals( COMMENT, ( (EditText) dialog.findViewById( R.id.shot_comment ) ).getText().toString() );
      assertEquals( COMMENT, data.selectTheShot( backId, surveyId ).mComment );

      dialog.findViewById( R.id.btn_next ).performClick();
      assertEquals( View.GONE, warning.getVisibility() );
      assertEquals( "", warning.getText().toString() );
      assertEquals( "next leg", ( (EditText) dialog.findViewById( R.id.shot_comment ) ).getText().toString() );
      assertEquals( COMMENT, data.selectTheShot( backId, surveyId ).mComment );
    } );
  }

  @Test public void savingCorrectionClearsWarningAndRestoresListColorWithoutChangingComment()
  {
    scenario.onActivity( activity -> {
      DBlockAdapter adapter = adapter( activity );
      DBlock bad = block( adapter, backId );
      dialog = new ShotEditDialog( activity, activity, position( adapter, backId ), bad,
        block( adapter, foreId ), block( adapter, nextId ) );
      dialog.show();
      ( (EditText) dialog.findViewById( R.id.shot_distance ) ).setText( "10.00" );
      dialog.findViewById( R.id.btn_save ).performClick();
      assertTrue( dialog.isShowing() );
      assertEquals( View.GONE, dialog.findViewById( R.id.shot_backsight_warning ).getVisibility() );
      assertFalse( activity.isBlockBacksightBad( bad ) );
      assertEquals( COMMENT, data.selectTheShot( backId, surveyId ).mComment );
      assertEquals( 10, data.selectTheShot( backId, surveyId ).mLength, 0 );
      View row = adapter.getView( position( adapter, backId ), null, (ListView) activity.findViewById( R.id.list ) );
      TextView measurements = row.findViewById( R.id.length );
      assertEquals( TDColor.BLACK, ( (ColorDrawable) measurements.getBackground() ).getColor() );
      assertEquals( bad.getColorByType(), measurements.getCurrentTextColor() );
      dialog.dismiss();
      activity.updateDisplay();
      assertEquals( "", activity.getBlockBacksightWarning( data.selectTheShot( backId, surveyId ) ) );
      assertEquals( COMMENT, data.selectTheShot( backId, surveyId ).mComment );
    } );
  }

  @Test public void changingForesightAndDeletingPairRefreshWarningsFromCompleteDataset()
  {
    scenario.onActivity( activity -> {
      DBlock fore = data.selectTheShot( foreId, surveyId );
      DBlock back = data.selectTheShot( backId, surveyId );
      assertTrue( activity.isBlockBacksightBad( back ) );
      activity.updateDBlockName( back, "B", "D" );
      assertFalse( activity.isBlockBacksightBad( back ) );
      activity.updateDBlockName( back, "B", "A" );
      assertTrue( activity.isBlockBacksightBad( back ) );
      activity.updateShotDistanceBearingClino( 12, 90, 5, fore );
      assertFalse( activity.isBlockBacksightBad( back ) );
      activity.updateShotDistanceBearingClino( 10, 90, 5, fore );
      assertTrue( activity.isBlockBacksightBad( back ) );
      activity.doDeleteShot( foreId, fore, TDStatus.DELETED, false );
      assertFalse( activity.isBlockBacksightBad( back ) );
      assertEquals( "", activity.getBlockBacksightWarning( back ) );
      assertEquals( COMMENT, data.selectTheShot( backId, surveyId ).mComment );
    } );
  }

  private long insert( String from, String to, float length, float bearing, float clino, String comment )
  {
    long id = data.insertCavwayShot( surveyId, -1, length, bearing, clino, 0, 1000, 1000, 65,
      1, 0, LegType.NORMAL, TDStatus.NORMAL, comment, "00:00:00:00:00:11", 0, 0, 0, 0, 0, 0, 0, 0 );
    assertTrue( id >= 0 );
    assertEquals( 0, data.updateShotNameAndData( id, surveyId, from, to, 1, 0, LegType.NORMAL, comment ) );
    return id;
  }

  private static DBlockAdapter adapter( ShotWindow activity )
  {
    try {
      Field field = ShotWindow.class.getDeclaredField( "mDataAdapter" );
      field.setAccessible( true );
      return (DBlockAdapter) field.get( activity );
    } catch ( ReflectiveOperationException e ) { throw new AssertionError( e ); }
  }

  private static int position( DBlockAdapter adapter, long id )
  {
    for ( int i = 0; i < adapter.getCount(); ++i ) if ( adapter.getItem( i ).mId == id ) return i;
    throw new AssertionError( "Shot missing from adapter: " + id );
  }

  private static DBlock block( DBlockAdapter adapter, long id ) { return adapter.getItem( position( adapter, id ) ); }
}
