/* @file StationLrudResult.java
 *
 * @brief LRUD values with explicit measurement-presence flags
 */
package com.topodroid.TDX;

final class StationLrudResult
{
  float left;
  float right;
  float up;
  float down;
  boolean hasLeft;
  boolean hasRight;
  boolean hasUp;
  boolean hasDown;

  LRUD toLrud()
  {
    LRUD lrud = new LRUD();
    lrud.l = left;
    lrud.r = right;
    lrud.u = up;
    lrud.d = down;
    return lrud;
  }
}
