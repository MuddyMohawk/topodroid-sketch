package com.topodroid.TDX;

import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.After;
import org.junit.runner.RunWith;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith( AndroidJUnit4.class )
public class SectionShotsDialogInstrumentedTest
{
  private boolean previousDialogR;
  @After public void restoreExitPolicy() { TopoDroidApp.setSayDialogR( previousDialogR ); }

  private void keepTestProcessAlive()
  {
    previousDialogR = TopoDroidApp.sayDialogR();
    TopoDroidApp.setSayDialogR( false );
  }
  private Map<String,Boolean> stations()
  {
    Map<String,Boolean> stations = new LinkedHashMap<>();
    stations.put( "10", true ); stations.put( "2", true ); stations.put( "A", true ); stations.put( "X", false );
    return stations;
  }

  private View row( DrawingSectionShotsDialog dialog, int position )
  {
    ListView list = dialog.findViewById( R.id.section_station_list );
    return list.getAdapter().getView( position, null, list );
  }

  @Test public void searchSortingSplayMemoryAndCancelLeaveOriginalUntouched()
  {
    SectionReferenceSelection original = new SectionReferenceSelection(); original.setStation( "A", true );
    AtomicReference<SectionReferenceSelection> saved = new AtomicReference<>();
    try ( ActivityScenario<MainWindow> scenario = ActivityScenario.launch( MainWindow.class ) ) {
      scenario.onActivity( activity -> {
        keepTestProcessAlive();
        DrawingSectionShotsDialog dialog = new DrawingSectionShotsDialog( activity, original, stations(), choices -> { saved.set( choices ); return true; } );
        dialog.show();
        ListView list = dialog.findViewById( R.id.section_station_list );
        assertEquals( "A", list.getAdapter().getItem( 0 ) );
        assertEquals( "2", list.getAdapter().getItem( 1 ) );
        assertEquals( "10", list.getAdapter().getItem( 2 ) );
        View first = row( dialog, 0 );
        CheckBox include = first.findViewById( R.id.section_station_checked );
        CheckBox splays = first.findViewById( R.id.section_station_splays );
        splays.performClick(); include.performClick();
        assertFalse( splays.isEnabled() );
        include.performClick(); assertTrue( splays.isEnabled() ); assertFalse( splays.isChecked() );
        EditText search = dialog.findViewById( R.id.section_station_search ); search.setText( "x" );
        assertEquals( 1, list.getAdapter().getCount() );
        assertFalse( row( dialog, 0 ).findViewById( R.id.section_station_checked ).isEnabled() );
        search.setText( "A" );
        assertFalse( ((CheckBox)row( dialog, 0 ).findViewById( R.id.section_station_splays )).isChecked() );
        dialog.findViewById( R.id.button_cancel ).performClick();
        assertNull( saved.get() ); assertTrue( original.includesSplays( "A" ) );
      } );
    }
  }

  @Test public void saveFailureKeepsDraftOpenAndSuccessfulSaveIncludesFilter()
  {
    SectionReferenceSelection original = new SectionReferenceSelection(); original.setStation( "A", true );
    AtomicReference<SectionReferenceSelection> saved = new AtomicReference<>();
    try ( ActivityScenario<MainWindow> scenario = ActivityScenario.launch( MainWindow.class ) ) {
      scenario.onActivity( activity -> {
        keepTestProcessAlive();
        DrawingSectionShotsDialog dialog = new DrawingSectionShotsDialog( activity, original, stations(), choices -> { saved.set( choices ); return false; } );
        dialog.show();
        ((CheckBox)dialog.findViewById( R.id.section_out_of_plane )).performClick();
        dialog.findViewById( R.id.button_save ).performClick();
        assertTrue( dialog.isShowing() ); assertFalse( saved.get().showOutOfPlane );
        assertTrue( original.showOutOfPlane ); dialog.onBackPressed(); assertFalse( dialog.isShowing() );
        DrawingSectionShotsDialog successful = new DrawingSectionShotsDialog( activity, saved.get(), stations(), choices -> { saved.set( choices ); return true; } );
        successful.show(); successful.findViewById( R.id.button_save ).performClick();
        assertFalse( successful.isShowing() ); assertFalse( saved.get().showOutOfPlane );
      } );
    }
  }

  @Test public void dialogRendersWithAppStylesOnTablet() throws Exception
  {
    AtomicReference<DrawingSectionShotsDialog> shown = new AtomicReference<>();
    try ( ActivityScenario<MainWindow> scenario = ActivityScenario.launch( MainWindow.class ) ) {
      scenario.onActivity( activity -> {
        keepTestProcessAlive();
        SectionReferenceSelection choices = new SectionReferenceSelection(); choices.setStation( "A", true );
        DrawingSectionShotsDialog dialog = new DrawingSectionShotsDialog( activity, choices, stations(), selection -> true );
        shown.set( dialog ); dialog.show();
      } );
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      androidx.test.uiautomator.UiDevice device = androidx.test.uiautomator.UiDevice.getInstance( InstrumentationRegistry.getInstrumentation() );
      assertTrue( device.wait( androidx.test.uiautomator.Until.hasObject( androidx.test.uiautomator.By.text( "Cross-section shots" ) ), 5000 ) );
      device.waitForIdle();
      java.io.File screenshot = new java.io.File( InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir( null ), "section-shots-dialog.png" );
      assertTrue( device.takeScreenshot( screenshot ) );
      scenario.onActivity( activity -> shown.get().dismiss() );
    }
  }
}
