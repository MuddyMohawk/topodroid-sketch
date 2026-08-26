/* @file CavwayBacksightFlagPolicy.java
 *
 * @brief Optional mapping from Cavway backsight labels to TopoDroid backshots
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

import com.topodroid.dev.cavway.CavwayConst;

final class CavwayBacksightFlagPolicy
{
  private CavwayBacksightFlagPolicy() {}

  static long apply( long flag, boolean enabled )
  {
    if ( enabled && DBlock.cavwayFlag( flag ) == CavwayConst.FLAG_BACKSIGHT ) {
      return flag | DBlock.FLAG_BACKSHOT;
    }
    return flag;
  }
}
