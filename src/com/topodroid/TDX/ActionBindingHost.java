/* @file ActionBindingHost.java
 *
 * @author MuddyMohawk
 * @date apr 2026
 *
 * @brief Host interface for configurable action-key bindings
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

public interface ActionBindingHost
{
  boolean handleActionBindingAction( int action );
}
