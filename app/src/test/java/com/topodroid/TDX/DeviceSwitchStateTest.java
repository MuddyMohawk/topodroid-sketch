package com.topodroid.TDX;

import org.junit.Test;
import static org.junit.Assert.*;

public class DeviceSwitchStateTest
{
  @Test public void downloadFinishesBeforeRequestedSwitchCanStart()
  {
    DeviceSwitchState state = new DeviceSwitchState();
    assertTrue( state.beginOperation() );
    assertTrue( state.request( "Cavway", "Disto" ) );
    assertNull( state.takePending() );
    assertFalse( state.beginOperation() );
    state.endOperation();
    assertEquals( "Disto", state.takePending() );
    assertFalse( state.beginOperation() );
    state.finishSwitch();
    assertTrue( state.beginOperation() );
  }

  @Test public void repeatedSwapsWhileBusyProduceOneSwitch()
  {
    DeviceSwitchState state = new DeviceSwitchState();
    state.beginOperation();
    state.request( "Cavway", "Disto" );
    state.request( "Cavway", "Disto" );
    state.endOperation();
    assertEquals( "Disto", state.takePending() );
    assertFalse( state.request( "Cavway", "Disto" ) );
    state.finishSwitch();
    assertNull( state.takePending() );
  }

  @Test public void explicitChoiceReplacesPendingTarget()
  {
    DeviceSwitchState state = new DeviceSwitchState();
    state.beginOperation();
    state.request( "Cavway", "Disto" );
    state.request( "Cavway", "BRIC" );
    state.endOperation();
    assertEquals( "BRIC", state.takePending() );
  }

  @Test public void leavingSurveyCancelsPendingSwitch()
  {
    DeviceSwitchState state = new DeviceSwitchState();
    state.beginOperation();
    state.request( "Cavway", "Disto" );
    state.cancelPending();
    state.endOperation();
    assertNull( state.takePending() );
    assertFalse( state.isBusy() );
  }

  @Test public void selectingActiveInstrumentDoesNotRestartIt()
  {
    DeviceSwitchState state = new DeviceSwitchState();
    assertFalse( state.request( "Cavway", "Cavway" ) );
    assertFalse( state.request( "Cavway", null ) );
    assertFalse( state.isBusy() );
  }
}
