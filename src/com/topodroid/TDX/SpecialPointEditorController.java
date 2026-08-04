/* @file SpecialPointEditorController.java
 *
 * @brief Point-dialog extension supplied by a special-point behavior
 */
package com.topodroid.TDX;

import android.widget.EditText;
import android.widget.LinearLayout;

interface SpecialPointEditorController
{
  void bind( LinearLayout container, EditText primary_text );

  void apply();
}
