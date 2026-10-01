package com.topodroid.dev.ble;

import org.junit.Test;
import static org.junit.Assert.*;

public class BleQueueShutdownTest
{
  @Test public void shutdownDrainsAcceptedPacketsAndRejectsLateNotifications()
  {
    BleQueue queue = new BleQueue();
    queue.put( BleQueue.DATA_PRIM, new byte[]{1} );
    queue.put( BleQueue.DATA_PRIM, new byte[]{2} );
    queue.close();
    queue.put( BleQueue.DATA_PRIM, new byte[]{3} );
    queue.close();
    assertArrayEquals( new byte[]{1}, queue.get().data );
    assertArrayEquals( new byte[]{2}, queue.get().data );
    assertEquals( BleQueue.DATA_QUIT, queue.get().type );
    assertEquals( 0, queue.size );
  }
  @Test public void discardedOperationsCannotRunFromLateCallbacks()
  {
    BleOpsQueue queue = new BleOpsQueue();
    final int[] executed = {0};
    BleOperation command = new BleOperation(null, null) {
      @Override public void execute() { ++executed[0]; }
    };
    assertTrue(queue.isIdle());
    queue.enqueueOp(command);
    assertFalse(queue.isIdle());
    queue.enqueueOp(command);
    queue.discard();
    assertTrue(queue.isIdle());
    queue.clearPending();
    queue.enqueueOp(command);
    queue.doNextOp();
    assertEquals(1, executed[0]);
  }
}
