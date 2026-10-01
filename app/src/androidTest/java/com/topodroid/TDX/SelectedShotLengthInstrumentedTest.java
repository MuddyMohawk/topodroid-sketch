package com.topodroid.TDX;

import static org.junit.Assert.*;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.topodroid.prefs.TDSetting;
import com.topodroid.types.LegType;
import com.topodroid.util.TDStatus;
import com.topodroid.util.TDUtil;

import java.lang.reflect.Field;
import java.io.File;
import java.util.ArrayList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Exercises selection through the shot rows, using only a disposable test survey. */
@RunWith( AndroidJUnit4.class )
public class SelectedShotLengthInstrumentedTest
{
  private ActivityScenario< ShotWindow > scenario;
  private TopoDroidApp app;
  private DataHelper data;
  private long surveyId = -1;
  private String surveyName;
  private final long[] ids = new long[3];
  private float previousLengthUnit;
  private String previousLengthName;
  private boolean previousDialogR;

  @Before public void setUp()
  {
    // Do not use the visual suite's all-survey cleanup on the field tablet.
    try ( ActivityScenario< MainWindow > main = ActivityScenario.launch( MainWindow.class ) ) {
      main.onActivity( activity -> {
        app = (TopoDroidApp)activity.getApplication();
        data = TopoDroidApp.mData;
        assertNotNull( data );
        previousDialogR = TopoDroidApp.sayDialogR();
        TopoDroidApp.setSayDialogR( false );
        previousLengthUnit = TDSetting.mUnitLength;
        previousLengthName = TDSetting.mUnitLengthStr;
        surveyName = "selected_length_qa_" + System.nanoTime();
        surveyId = app.setSurveyFromName( surveyName, SurveyInfo.DATAMODE_NORMAL, false, true );
        assertTrue( surveyId > 0 );
        ids[0] = insert( "A", "B", 1.2f );
        ids[1] = insert( "B", "C", 2.3f );
        ids[2] = insert( "C", "D", 4.5f );
      } );
    }
    scenario = ActivityScenario.launch( ShotWindow.class );
    scenario.onActivity( activity -> {
      TDSetting.mUnitLength = 1;
      TDSetting.mUnitLengthStr = "m";
      activity.setTheTitle();
    } );
  }

  @After public void tearDown()
  {
    if ( scenario != null ) scenario.close();
    InstrumentationRegistry.getInstrumentation().runOnMainSync( () -> {
      TDSetting.mUnitLength = previousLengthUnit;
      TDSetting.mUnitLengthStr = previousLengthName;
      if ( data != null && surveyId > 0 && data.getSurveyId( surveyName ) == surveyId ) {
        data.doDeleteSurvey( surveyId );
      }
      if ( app != null ) {
        app.setSurveyFromName( null, 0, false, false );
        TopoDroidApp.setSayDialogR( previousDialogR );
      }
    } );
  }

  @Test public void updatesForIndividualSelectionsUnitsAndEmptySelection()
  {
    scenario.onActivity( activity -> {
      assertHidden( activity );
      assertTrue( measurement( activity, ids[0] ).performLongClick() );
      assertTotal( activity, "1.20 m" );
      assertTrue( measurement( activity, ids[2] ).performClick() );
      assertTotal( activity, "5.70 m" ); // The unselected middle shot contributes nothing.
      assertTrue( measurement( activity, ids[0] ).performClick() );
      assertTotal( activity, "4.50 m" );
      TDSetting.mUnitLength = TDUtil.M2FT;
      TDSetting.mUnitLengthStr = "ft";
      activity.updateDisplay(); // Returning from settings also refreshes the title.
      assertTotal( activity, "14.76 ft" );
      assertTrue( measurement( activity, ids[2] ).performClick() );
      assertHidden( activity );
      DBlockAdapter adapter = adapter( activity );
      adapter.getItem( position( adapter, ids[0] ) ).mLength = 0;
      assertTrue( measurement( activity, ids[0] ).performLongClick() );
      assertTotal( activity, "0.00 ft" ); // A zero-length selection must still be visible.
      activity.clearMultiSelect();
      assertHidden( activity );
    } );
  }

  @Test public void rangeSelectionStaysAtRightOfTitleAndClearsAfterSwap()
  {
    scenario.onActivity( activity -> {
      assertTrue( measurement( activity, ids[0] ).performLongClick() );
      assertTrue( measurement( activity, ids[2] ).performLongClick() );
      assertTotal( activity, "8.00 m" );
      assertEquals( 3, adapter( activity ).getMultiSelectSize() );
      // Long names give up title space while keeping the complete total visible.
      TDInstance.survey = surveyName + "_a_long_survey_name_that_should_be_truncated_in_the_title_bar";
      activity.setTheTitle();
    } );
    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    if ( Boolean.parseBoolean( InstrumentationRegistry.getArguments().getString( "selectedLengthScreenshot", "false" ) ) ) {
      File screenshot = new File( InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir( null ),
          "selected_shot_length.png" );
      assertTrue( UiDevice.getInstance( InstrumentationRegistry.getInstrumentation() ).takeScreenshot( screenshot ) );
    }
    scenario.onActivity( activity -> {
      TextView total = total( activity );
      TextView title = activity.getWindow().findViewById( android.R.id.title );
      ViewGroup row = (ViewGroup)total.getParent();
      assertEquals( row.getChildCount() - 1, row.indexOfChild( total ) );
      assertTrue( total.getWidth() > 0 );
      assertTrue( total.getLayout().getLineWidth( 0 ) <= total.getWidth() - total.getPaddingLeft() - total.getPaddingRight() );
      int[] titleLocation = new int[2];
      int[] totalLocation = new int[2];
      title.getLocationOnScreen( titleLocation );
      total.getLocationOnScreen( totalLocation );
      assertTrue( titleLocation[0] + title.getWidth() <= totalLocation[0] );
      assertEquals( row.getWidth() - row.getPaddingRight(), total.getRight() );
      assertEquals( title.getTextSize(), total.getTextSize(), 0 );
      assertEquals( title.getCurrentTextColor(), total.getCurrentTextColor() );
      TDInstance.survey = surveyName;
      // Range deselection removes the middle row too.
      assertTrue( measurement( activity, ids[2] ).performLongClick() );
      assertTotal( activity, "3.50 m" );
      assertTrue( measurement( activity, ids[0] ).performLongClick() );
      assertHidden( activity );
      assertTrue( measurement( activity, ids[0] ).performLongClick() );
      activity.doSwapBlocksName( new ArrayList<>( adapter( activity ).getMultiSelect() ) );
      assertHidden( activity );
      assertFalse( adapter( activity ).isMultiSelect() );
      assertFalse( activity.getTitle().toString().contains( String.format( activity.getString( R.string.select_size ), 1 ) ) );
    } );
  }

  private long insert( String from, String to, float length )
  {
    long id = data.insertCavwayShot( surveyId, -1, length, 90, 0, 0, 1000, 1000, 65,
        1, 0, LegType.NORMAL, TDStatus.NORMAL, "", "00:00:00:00:00:11", 0, 0, 0, 0, 0, 0, 0, 0 );
    assertTrue( id >= 0 );
    assertEquals( 0, data.updateShotNameAndData( id, surveyId, from, to, 1, 0, LegType.NORMAL, "" ) );
    return id;
  }

  private static DBlockAdapter adapter( ShotWindow activity )
  {
    try {
      Field field = ShotWindow.class.getDeclaredField( "mDataAdapter" );
      field.setAccessible( true );
      return (DBlockAdapter)field.get( activity );
    } catch ( ReflectiveOperationException e ) { throw new AssertionError( e ); }
  }

  private static int position( DBlockAdapter adapter, long id )
  {
    for ( int i = 0; i < adapter.getCount(); ++i ) if ( adapter.getItem( i ).mId == id ) return i;
    throw new AssertionError( "Missing test shot: " + id );
  }

  private static View measurement( ShotWindow activity, long id )
  {
    DBlockAdapter adapter = adapter( activity );
    return adapter.getView( position( adapter, id ), null, (ListView)activity.findViewById( R.id.list ) ).findViewById( R.id.length );
  }

  private static TextView total( ShotWindow activity )
  {
    TextView total = activity.getWindow().findViewById( R.id.selected_shot_length );
    assertNotNull( total );
    return total;
  }

  private static void assertTotal( ShotWindow activity, String expected )
  {
    assertEquals( View.VISIBLE, total( activity ).getVisibility() );
    assertEquals( expected, total( activity ).getText().toString() );
    assertEquals( "Selected shot length: " + expected, total( activity ).getContentDescription().toString() );
  }

  private static void assertHidden( ShotWindow activity )
  {
    assertEquals( View.GONE, total( activity ).getVisibility() );
    assertEquals( "", total( activity ).getText().toString() );
  }
}
