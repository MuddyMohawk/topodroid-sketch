package com.topodroid.TDX;

import com.topodroid.dev.Device;
import java.util.ArrayList;
import java.util.List;

/** Selection identity shared by the picker and remembered-pair orchestration. */
final class SurveyDeviceSelection
{
  // Local interfaces keep the helper usable on every supported Android API.
  interface Lookup { Device find(String identity); }
  interface Setter { void set(Device device); }

  static ArrayList<Device> pickerDevices(Iterable<Device> known)
  {
    ArrayList<Device> result = new ArrayList<>();
    for (Device device : known) if (device.isBT() || device.isBLE()) result.add(device);
    return result;
  }

  static String pickerAddress(List<Device> displayed, int position)
  {
    return position >= 0 && position < displayed.size() ? displayed.get(position).getAddress() : null;
  }

  static void restorePair(String primaryKey, String alternateKey, Lookup lookup,
                          Setter setPrimary, Setter setAlternate)
  {
    Device primary = lookup.find(primaryKey);
    setPrimary.set(primary);
    Device alternate = lookup.find(alternateKey);
    if (alternate != null && primary != null && alternate.hasAddress(primary.getAddress())) alternate = null;
    setAlternate.set(alternate);
  }
}
