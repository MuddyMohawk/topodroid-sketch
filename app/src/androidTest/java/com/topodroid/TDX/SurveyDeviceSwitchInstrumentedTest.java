package com.topodroid.TDX;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.widget.ListView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.topodroid.dev.*;
import com.topodroid.dev.cavway.CavwayComm;
import com.topodroid.dev.distox_ble.DistoXBLEComm;
import com.topodroid.prefs.TDSetting;
import com.topodroid.prefs.TDPrefHelper;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SurveyDeviceSwitchInstrumentedTest
{
  private static final String CAVWAY = "00:00:00:00:00:11";
  private static final String DISTO = "00:00:00:00:00:22";
  private TestApp app;
  private Context previousContext;
  private Device previousA, previousB;
  private int previousMode;
  private boolean previousMutable, previousSecondDevice;
  private long previousSurvey;
  private VisualTestSupport support;
  private Listener listener;

  @Before public void setUp() throws Exception
  {
    support = new VisualTestSupport("multi_device_contract");
    support.prepareForCase(VisualTestSupport.allSurveyNames("multi_device_contract", "multi_device_source", "multi_device_other"));
    support.launchMainWindowOnAnyDevice();
    previousContext = TDInstance.context;
    previousA = TDInstance.getDeviceA(); previousB = TDInstance.getDeviceB();
    previousMode = modeField().getInt(null);
    previousMutable = TDInstance.isSurveyMutable;
    previousSecondDevice = TDSetting.mSecondDistoX;
    previousSurvey = TDInstance.sid;
    Context base = InstrumentationRegistry.getInstrumentation().getTargetContext();
    Context isolated = new ContextWrapper(base) {
      @Override public SharedPreferences getSharedPreferences(String name, int mode) {
        return super.getSharedPreferences("multi_device_test_" + name, mode);
      }
    };
    onMain(() -> {
      TDInstance.context = isolated;
      TDInstance.isSurveyMutable = true;
      app = new TestApp(isolated);
      listener = new Listener();
      app.registerLister(listener);
      app.setDevicePrimary(CAVWAY, "CavwayX1-001", "001", null);
      app.setDeviceB(DISTO);
    });
  }

  @After public void tearDown() throws Exception
  {
    if (app != null) {
      awaitIdle();
      onMain(() -> { app.mDataDownloader.onStop(); app.setDevicePrimary(null, null, null, null); });
    }
    onMain(() -> {
      TDInstance.context = previousContext;
      TDInstance.setDeviceA(previousA); TDInstance.setDeviceB(previousB);
      TDInstance.sid = previousSurvey; TDInstance.isSurveyMutable = previousMutable;
      TDSetting.mSecondDistoX = previousSecondDevice;
    });
    modeField().setInt(null, previousMode);
    if (support != null) support.finish();
  }

  @Test public void switchesBothDirectionsAndObeysEveryConnectionMode() throws Exception
  {
    for (int mode : new int[]{0, 1, 2}) {
      modeField().setInt(null, mode);
      onMain(() -> app.requestSurveyDeviceSwitch(DISTO));
      awaitIdle();
      assertTrue(app.latest instanceof FakeDisto);
      assertEquals(DISTO, TDInstance.deviceAddress());
      assertEquals(CAVWAY, TDInstance.secondDeviceAddress());
      TDPrefHelper saved = new TDPrefHelper(TDInstance.context);
      assertEquals(DISTO, saved.getString(TDSetting.keyDeviceName(), ""));
      assertEquals(CAVWAY, saved.getString(TDSetting.keySecondDeviceName(), ""));
      assertEquals(mode == 1, app.latest.isConnected());
      assertEquals(mode == 1 ? 0 : 1, app.downloads);
      onMain(() -> app.requestSurveyDeviceSwitch(CAVWAY));
      awaitIdle();
      assertTrue(app.latest instanceof FakeCavway);
      assertEquals(CAVWAY, TDInstance.deviceAddress());
      assertEquals(DISTO, TDInstance.secondDeviceAddress());
      assertEquals(mode == 1, app.latest.isConnected());
      app.downloads = 0;
    }
  }

  @Test public void busyOperationRetainsSourceAndCoalescesSwitchRequests() throws Exception
  {
    modeField().setInt(null, 1);
    TopoDroidApp.DeviceOperation operation = app.beginDeviceOperation();
    TopoDroidComm original = operation.comm;
    onMain(() -> { app.requestSurveyDeviceSwitch(DISTO); app.requestSurveyDeviceSwitch(DISTO); });
    assertEquals(CAVWAY, operation.address);
    assertEquals(CAVWAY, TDInstance.deviceAddress());
    assertSame(original, app.latest);
    assertTrue(app.isDeviceOperationBusy());
    operation.close();
    awaitIdle();
    assertNotSame(original, app.latest);
    assertEquals(DISTO, TDInstance.deviceAddress());
    assertEquals(2, app.created);
  }

  @Test public void lateStatusFromOldDriverCannotDisconnectNewDevice() throws Exception
  {
    modeField().setInt(null, 1);
    TopoDroidComm original = app.latest;
    onMain(() -> app.requestSurveyDeviceSwitch(DISTO));
    awaitIdle();
    app.notifyCommStatus(original, app.mListerSet, ConnectionState.CONN_DISCONNECTED);
    onMain(() -> {});
    assertEquals(ConnectionState.CONN_CONNECTED, app.mDataDownloader.getStatus());
    assertEquals(ConnectionState.CONN_CONNECTED, listener.status);
  }

  @Test public void rememberedPairUsesAddressesDespiteDuplicateNicknames() throws Exception
  {
    onMain(() -> {
      TDInstance.setDeviceA(null); TDInstance.setDeviceB(null);
      app.restoreDevicePair(new TDPrefHelper(TDInstance.context));
      assertEquals(CAVWAY, TDInstance.deviceAddress());
      assertEquals(DISTO, TDInstance.secondDeviceAddress());
      app.setDeviceB(CAVWAY);
      assertEquals(DISTO, TDInstance.secondDeviceAddress());
      app.devices.remove(1);
      app.restoreDevicePair(new TDPrefHelper(TDInstance.context));
      assertNull(TDInstance.getDeviceB());
    });
  }

  @Test public void pickerIncludesBleAndChoosesObjectBehindDisplayedName() throws Exception
  {
    modeField().setInt(null, 0);
    onMain(() -> {
      DeviceSelectDialog dialog = new DeviceSelectDialog(TopoDroidApp.mMainActivity, app);
      dialog.show();
      ListView list = dialog.findViewById(R.id.dev_list);
      assertEquals(2, list.getAdapter().getCount());
      dialog.onItemClick(list, null, 1, 0);
    });
    awaitIdle();
    assertEquals(DISTO, TDInstance.deviceAddress());
  }

  @Test public void leavingSurveyCancelsDeferredSwitch() throws Exception
  {
    TopoDroidApp.DeviceOperation operation = app.beginDeviceOperation();
    onMain(() -> { app.requestSurveyDeviceSwitch(DISTO); app.mDataDownloader.onStop(); });
    operation.close();
    awaitIdle();
    assertEquals(CAVWAY, TDInstance.deviceAddress());
  }

  @Test public void remoteSequenceKeepsButtonsDisabledUntilFinalCommandFinishes() throws Exception
  {
    modeField().setInt(null, 1);
    app.mDataDownloader.setDownloading(true);
    app.latest.mBTConnected = true;
    FakeCavway cavway = (FakeCavway)app.latest;
    cavway.block = new CountDownLatch(1);
    onMain(() -> assertTrue(app.setCavwayLaser(Device.MEASURE, 1, app.mListerSet, DataType.DATA_SHOT, true, true)));
    assertTrue(cavway.entered.await(5, TimeUnit.SECONDS));
    onMain(() -> app.requestSurveyDeviceSwitch(DISTO));
    assertFalse(listener.enabled);
    assertEquals(CAVWAY, TDInstance.deviceAddress());
    cavway.block.countDown();
    awaitIdle();
    assertFalse(cavway.closeRequested);
    assertEquals(DISTO, TDInstance.deviceAddress());
    assertTrue(listener.enabled);
  }

  @Test public void packetContextDoesNotFollowGlobalSurveyOrInstrument() throws Exception
  {
    SnapshotComm comm = new SnapshotComm(app);
    comm.bindDataContext(101, 202, CAVWAY, Device.DISTO_CAVWAYX1);
    comm.mProtocol = new TopoDroidProtocol(app.devices.get(0), TDInstance.context);
    TDInstance.sid = 303;
    TDInstance.setDeviceA(app.devices.get(1));
    assertEquals(101, comm.survey());
    assertEquals(202, comm.calibration());
    assertEquals(CAVWAY, comm.address());
    assertEquals(Device.DISTO_CAVWAYX1, comm.type());
  }

  @Test public void packetsStayInTheirOriginalSurveyWithTheirActualInstrumentAddress() throws Exception
  {
    long source = TopoDroidApp.mData.setSurvey("multi_device_source", 0);
    long other = TopoDroidApp.mData.setSurvey("multi_device_other", 0);
    SnapshotComm cavway = new SnapshotComm(app);
    SnapshotComm disto = new SnapshotComm(app);
    cavway.bindDataContext(source, 0, CAVWAY, Device.DISTO_CAVWAYX1);
    disto.bindDataContext(source, 0, DISTO, Device.DISTO_XBLE);
    cavway.mProtocol = new TopoDroidProtocol(app.devices.get(0), TDInstance.context);
    disto.mProtocol = new TopoDroidProtocol(app.devices.get(1), TDInstance.context);
    cavway.mProtocol.mDistance = 10; disto.mProtocol.mDistance = 20;
    TDInstance.sid = other;
    TDInstance.setDeviceA(app.devices.get(1));
    cavway.handleCavwayPacket(DataType.PACKET_DATA, null, DataType.DATA_SHOT, null, 0);
    TDInstance.setDeviceA(app.devices.get(0));
    disto.handleRegularPacket(DataType.PACKET_DATA, null, DataType.DATA_SHOT);
    java.util.List<DBlock> shots = TopoDroidApp.mData.selectAllShots(source, com.topodroid.util.TDStatus.NORMAL);
    assertEquals(2, shots.size());
    assertEquals(CAVWAY, shots.get(0).getAddress());
    assertEquals(DISTO, shots.get(1).getAddress());
    assertTrue(TopoDroidApp.mData.selectAllShots(other, com.topodroid.util.TDStatus.NORMAL).isEmpty());
  }

  @Test public void longPressDownloadSwitchesBleFromBothSurveyScreens() throws Exception
  {
    modeField().setInt(null, 1);
    TDSetting.mSecondDistoX = true;
    support.createSurveyAndOpenShots("multi_device_contract", "Test", "1", "Device switch test");
    ShotWindow shots = TopoDroidApp.mShotWindow;
    switchFromWindow(shots, ShotWindow.class);
    assertEquals(DISTO, TDInstance.deviceAddress());
    support.addManualShot("1", "2", "10", "0", "0", true);
    support.openNewPlotFromShotWindow("1", "1");
    switchFromWindow(TopoDroidApp.mDrawingWindow, DrawingWindow.class);
    assertEquals(CAVWAY, TDInstance.deviceAddress());
  }

  private void switchFromWindow(ILister window, Class<?> type) throws Exception
  {
    Field appField = field(type, "mApp"), downloaderField = field(type, "mDataDownloader");
    Object originalApp = appField.get(window), originalDownloader = downloaderField.get(window);
    android.widget.Button[] buttons = (android.widget.Button[])field(type, "mButton1").get(window);
    int index = field(type, "BTN_DOWNLOAD").getInt(null);
    try {
      onMain(() -> {
        try { appField.set(window, app); downloaderField.set(window, app.mDataDownloader); }
        catch (IllegalAccessException e) { throw new AssertionError(e); }
        app.registerLister(window);
        assertTrue(buttons[index].isLongClickable());
        assertTrue(buttons[index].performLongClick());
      });
      awaitIdle();
    } finally {
      onMain(() -> {
        app.unregisterLister(window);
        try { appField.set(window, originalApp); downloaderField.set(window, originalDownloader); }
        catch (IllegalAccessException e) { throw new AssertionError(e); }
      });
    }
  }

  private static Field field(Class<?> type, String name) throws Exception
  {
    Field field = type.getDeclaredField(name); field.setAccessible(true); return field;
  }

  @Test public void fullRemoteShotSequenceDefersSwitchUntilLastMeasurement() throws Exception
  {
    modeField().setInt(null, 1);
    app.mDataDownloader.setDownloading(true);
    FakeCavway cavway = (FakeCavway)app.latest;
    cavway.mBTConnected = true;
    cavway.block = new CountDownLatch(1);
    int waitLaser = TDSetting.mWaitLaser, waitShot = TDSetting.mWaitShot;
    try {
      TDSetting.mWaitLaser = 0; TDSetting.mWaitShot = 0;
      onMain(() -> new com.topodroid.dev.cavway.CavwayTakeShot(listener, app.mListerSet, app, 3, DataType.DATA_SHOT).execute());
      assertTrue(cavway.entered.await(5, TimeUnit.SECONDS));
      onMain(() -> app.requestSurveyDeviceSwitch(DISTO));
      assertFalse(listener.enabled);
      assertEquals(CAVWAY, TDInstance.deviceAddress());
      assertEquals(java.util.Arrays.asList(Device.LASER_ON, Device.MEASURE, Device.LASER_ON, Device.MEASURE, Device.LASER_ON, Device.MEASURE), cavway.commands);
      cavway.block.countDown();
      awaitIdle();
      assertEquals(DISTO, TDInstance.deviceAddress());
      assertTrue(listener.enabled);
    } finally {
      cavway.block.countDown(); TDSetting.mWaitLaser = waitLaser; TDSetting.mWaitShot = waitShot;
    }
  }

  @Test public void failedConnectionLeavesChosenDeviceVisibleAndStopsWaiting() throws Exception
  {
    modeField().setInt(null, 1);
    app.failConnect = true;
    onMain(() -> app.requestSurveyDeviceSwitch(DISTO));
    awaitIdle();
    assertEquals(DISTO, TDInstance.deviceAddress());
    assertEquals(CAVWAY, TDInstance.secondDeviceAddress());
    assertEquals(ConnectionState.CONN_DISCONNECTED, listener.status);
    assertFalse(app.mDataDownloader.isDownloading());
    assertTrue(listener.enabled);
  }

  @Test public void leavingSurveyClosesConnectionThatCompletesAfterCancellation() throws Exception
  {
    modeField().setInt(null, 1);
    app.connectBlock = new CountDownLatch(1);
    try {
      onMain(() -> app.requestSurveyDeviceSwitch(DISTO));
      assertTrue(app.connectEntered.await(5, TimeUnit.SECONDS));
      onMain(() -> app.mDataDownloader.onStop());
      app.connectBlock.countDown();
      awaitIdle();
      assertFalse(app.latest.isConnected());
      assertEquals(ConnectionState.CONN_DISCONNECTED, listener.status);
      assertFalse(app.mDataDownloader.isDownloading());
    } finally { app.connectBlock.countDown(); }
  }

  private void awaitIdle() throws Exception
  {
    long deadline = System.currentTimeMillis() + 15000;
    do {
      onMain(() -> {});
      if (!app.isDeviceOperationBusy()) { Thread.sleep(150); onMain(() -> {}); if (!app.isDeviceOperationBusy()) return; }
      Thread.sleep(50);
    } while (System.currentTimeMillis() < deadline);
    fail("Device operation did not complete");
  }

  private static void onMain(Runnable action) { InstrumentationRegistry.getInstrumentation().runOnMainSync(action); }
  private static Field modeField() throws Exception { Field field = TDSetting.class.getDeclaredField("mConnectionMode"); field.setAccessible(true); return field; }

  private static final class Listener implements ILister {
    volatile int status; volatile boolean enabled;
    public String name() { return "device-test"; }
    public void refreshDisplay(int nr, boolean toast) {}
    public void updateBlockList(long id) {}
    public void updateBlockAMDList(long id) {}
    public void setConnectionStatus(int value) { status = value; }
    public void setRefAzimuth(float azimuth, long extend) {}
    public void setTheTitle() {}
    public void enableBluetoothButton(boolean value) { enabled = value; }
  }

  private static final class TestApp extends TopoDroidApp {
    final ArrayList<Device> devices = new ArrayList<>();
    volatile TopoDroidComm latest;
    volatile int downloads, created;
    volatile boolean failConnect;
    volatile CountDownLatch connectBlock;
    final CountDownLatch connectEntered = new CountDownLatch(1);
    TestApp(Context context) {
      attachBaseContext(context);
      devices.add(new Device(CAVWAY, "CavwayX1-001", 0, 0, "001", "Team instrument"));
      devices.add(new Device(DISTO, "DistoXBLE-002", 0, 0, "002", "Team instrument"));
      mListerSet = new ListerSetHandler();
      mDataDownloader = new DataDownloader(context, this);
    }
    @Override protected Device findDevice(String address) {
      for (Device device : devices) if (device.hasAddress(address)) return device;
      return null;
    }
    @Override protected ArrayList<Device> knownDevices() { return devices; }
    @Override protected TopoDroidComm makeDeviceComm(Device device) {
      ++created;
      latest = device.isCavway() ? new FakeCavway(this) : new FakeDisto(this);
      return latest;
    }
  }

  private static class FakeCavway extends CavwayComm {
    final TestApp app;
    final CountDownLatch entered = new CountDownLatch(1);
    volatile CountDownLatch block;
    volatile boolean closeRequested;
    final java.util.List<Integer> commands = java.util.Collections.synchronizedList(new ArrayList<Integer>());
    FakeCavway(TestApp app) { super(app, app, CAVWAY, null); this.app = app; }
    @Override public boolean connectDevice(String address, ListerHandler lister, int type, int timeout) {
      assertEquals(CAVWAY, address); mBTConnected = true; app.notifyCommStatus(this, lister, ConnectionState.CONN_CONNECTED); return true;
    }
    @Override public int downloadData(String address, ListerHandler lister, int type, int timeout) {
      assertEquals(CAVWAY, address); ++app.downloads; return 3;
    }
    @Override public boolean setCavwayLaser(String address, int what, int nr, ListerHandler lister, int type, boolean close) {
      closeRequested = close; commands.add(what);
      if (what == Device.MEASURE && nr > 0) entered.countDown();
      if (block != null && what == Device.MEASURE && nr > 0) try { if (!block.await(5, TimeUnit.SECONDS)) return false; } catch (InterruptedException e) { return false; }
      return true;
    }
  }

  private static final class FakeDisto extends DistoXBLEComm {
    final TestApp app;
    FakeDisto(TestApp app) { super(app, app, DISTO, null); this.app = app; }
    @Override public boolean connectDevice(String address, ListerHandler lister, int type, int timeout) {
      assertEquals(DISTO, address);
      if (app.failConnect) return false;
      app.connectEntered.countDown();
      while (app.connectBlock != null && app.connectBlock.getCount() > 0) {
        try { app.connectBlock.await(); } catch (InterruptedException ignored) { /* simulate a late platform connect result */ }
      }
      mBTConnected = true; app.notifyCommStatus(this, lister, ConnectionState.CONN_CONNECTED); return true;
    }
    @Override public int downloadData(String address, ListerHandler lister, int type, int timeout) {
      assertEquals(DISTO, address); ++app.downloads; return 3;
    }
  }

  private static final class SnapshotComm extends TopoDroidComm {
    SnapshotComm(TopoDroidApp app) { super(app); }
    long survey() { return surveyId(); }
    long calibration() { return calibrationId(); }
    String address() { return sourceAddress(); }
    int type() { return sourceType(); }
  }
}
