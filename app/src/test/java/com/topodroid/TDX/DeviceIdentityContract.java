package com.topodroid.TDX;

import com.topodroid.dev.Device;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

@com.topodroid.qa.TestFeatures({com.topodroid.qa.TestFeatures.Feature.DEVICES})
public class DeviceIdentityContract
{
  private static final String FIRST = "00:11:22:33:44:01";
  private static final String SECOND = "00:11:22:33:44:02";
  private static final String THIRD = "00:11:22:33:44:03";

  private static Device instrument(String address, String model)
  {
    return new Device(address, model, "instrument", "Shared nickname");
  }

  @Test public void pickerIncludesBtAndBleAndChoosesTheObjectBehindDuplicateLabels()
  {
    Device bt = instrument(FIRST, "X310");
    Device ble = instrument(SECOND, "XBLE");
    Device sameLabel = instrument(THIRD, "XBLE");
    Device other = instrument("00:11:22:33:44:04", "Unknown");
    assertEquals("Fixture must reproduce identical displayed names", ble.toString(), sameLabel.toString());
    List<Device> displayed = SurveyDeviceSelection.pickerDevices(Arrays.asList(bt, other, ble, sameLabel));
    assertEquals(Arrays.asList(bt, ble, sameLabel), displayed);
    assertEquals(FIRST, SurveyDeviceSelection.pickerAddress(displayed, 0));
    assertEquals(SECOND, SurveyDeviceSelection.pickerAddress(displayed, 1));
    assertEquals(THIRD, SurveyDeviceSelection.pickerAddress(displayed, 2));
    assertNull(SurveyDeviceSelection.pickerAddress(displayed, -1));
    assertNull(SurveyDeviceSelection.pickerAddress(displayed, displayed.size()));
  }

  @Test public void rememberedPairResolvesSavedAddressesDespiteDuplicateNicknames()
  {
    Device a = instrument(FIRST, "X310"), b = instrument(SECOND, "XBLE");
    Map<String,Device> known = new HashMap<>();
    known.put(FIRST, a); known.put(SECOND, b);
    List<String> events = new ArrayList<>();
    Device[] selected = new Device[2];
    SurveyDeviceSelection.restorePair(SECOND, FIRST,
      key -> {events.add("lookup:" + key); return known.get(key);},
      device -> {events.add("primary"); selected[0] = device;},
      device -> selected[1] = device);
    assertSame(b, selected[0]); assertSame(a, selected[1]);
    assertEquals(Arrays.asList("lookup:" + SECOND, "primary", "lookup:" + FIRST), events);
  }

  @Test public void duplicateAddressFromSeparateResolvedObjectsCannotOccupyBothSlots()
  {
    Device primary = instrument(FIRST, "X310"), alternate = instrument(FIRST, "X310");
    assertNotSame(primary, alternate);
    Device[] selected = {null, alternate};
    SurveyDeviceSelection.restorePair(FIRST, "legacy nickname alias",
      key -> FIRST.equals(key) ? primary : alternate,
      device -> selected[0] = device, device -> selected[1] = device);
    assertSame(primary, selected[0]); assertNull(selected[1]);
  }

  @Test public void missingPrimaryDoesNotDiscardAValidAlternate()
  {
    Device alternate = instrument(SECOND, "XBLE");
    Device[] selected = {instrument(FIRST, "X310"), null};
    SurveyDeviceSelection.restorePair("missing", SECOND,
      key -> SECOND.equals(key) ? alternate : null,
      device -> selected[0] = device, device -> selected[1] = device);
    assertNull(selected[0]); assertSame(alternate, selected[1]);
  }

  @Test public void missingAlternateDoesNotDiscardAValidPrimary()
  {
    Device primary = instrument(FIRST, "X310");
    Device[] selected = {null, instrument(SECOND, "XBLE")};
    SurveyDeviceSelection.restorePair(FIRST, "missing",
      key -> FIRST.equals(key) ? primary : null,
      device -> selected[0] = device, device -> selected[1] = device);
    assertSame(primary, selected[0]); assertNull(selected[1]);
  }
}
