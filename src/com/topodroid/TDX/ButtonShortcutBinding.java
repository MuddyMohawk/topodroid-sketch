package com.topodroid.TDX;

import com.topodroid.prefs.TDSetting;

/** Explicit visual order: Press, Double-press, Hold (different from the preference key table). */
final class ButtonShortcutBinding
{
  static final int[] BUTTON_NAMES = { R.string.button_shortcuts_row_pen, R.string.button_shortcuts_row_active,
    R.string.button_shortcuts_row_volup, R.string.button_shortcuts_row_voldown };
  static final int[] GESTURE_NAMES = { R.string.button_shortcuts_col_press,
    R.string.button_shortcuts_col_double, R.string.button_shortcuts_col_hold };
  static final String[][] KEYS = {
    { "DISTOX_SPEN_SINGLE_CLICK", "DISTOX_SPEN_DOUBLE_CLICK", "DISTOX_SPEN_LONG_CLICK" },
    { "DISTOX_ACTIVE_KEY_SINGLE_PRESS", "DISTOX_ACTIVE_KEY_DOUBLE_PRESS", "DISTOX_ACTIVE_KEY_LONG_PRESS" },
    { "DISTOX_VOLUME_UP_SINGLE_PRESS", "DISTOX_VOLUME_UP_DOUBLE_PRESS", "DISTOX_VOLUME_UP_LONG_PRESS" },
    { "DISTOX_VOLUME_DOWN_SINGLE_PRESS", "DISTOX_VOLUME_DOWN_DOUBLE_PRESS", "DISTOX_VOLUME_DOWN_LONG_PRESS" }
  };

  final int row;
  final int gesture;
  final String key;

  ButtonShortcutBinding( int row, int gesture )
  {
    this.row = row; this.gesture = gesture; key = KEYS[row][gesture];
  }

  int value()
  {
    switch ( row * 3 + gesture ) {
      case 0: return TDSetting.mSPenSingleClickAction;
      case 1: return TDSetting.mSPenDoubleClickAction;
      case 2: return TDSetting.mSPenLongClickAction;
      case 3: return TDSetting.mActiveKeySinglePressAction;
      case 4: return TDSetting.mActiveKeyDoublePressAction;
      case 5: return TDSetting.mActiveKeyLongPressAction;
      case 6: return TDSetting.mVolumeUpSinglePressAction;
      case 7: return TDSetting.mVolumeUpDoublePressAction;
      case 8: return TDSetting.mVolumeUpLongPressAction;
      case 9: return TDSetting.mVolumeDownSinglePressAction;
      case 10: return TDSetting.mVolumeDownDoublePressAction;
      case 11: return TDSetting.mVolumeDownLongPressAction;
      default: throw new IllegalStateException( "Unknown button gesture" );
    }
  }

  static final class Action
  {
    final int value;
    final int name;
    final int shortName;
    final int description;

    Action( int value, int name, int shortName, int description )
    {
      this.value = value; this.name = name; this.shortName = shortName; this.description = description;
    }

    boolean sketchOnly() { return value != TDSetting.SPEN_ACTION_NONE && value != TDSetting.SPEN_ACTION_BACK; }
    boolean availableFor( int gesture ) { return gesture != 1 || ! TDSetting.isHeldAction( value ); }
  }

  static final Action[] ACTIONS = {
    new Action( TDSetting.SPEN_ACTION_UNDO, R.string.button_shortcuts_action_undo, R.string.button_shortcuts_action_undo, R.string.button_shortcuts_desc_undo ),
    new Action( TDSetting.SPEN_ACTION_REDO, R.string.button_shortcuts_action_redo, R.string.button_shortcuts_action_redo, R.string.button_shortcuts_desc_redo ),
    new Action( TDSetting.SPEN_ACTION_TOGGLE_ERASE, R.string.button_shortcuts_action_eraser, R.string.button_shortcuts_short_eraser, R.string.button_shortcuts_desc_eraser ),
    new Action( TDSetting.SPEN_ACTION_HOLD_ERASE, R.string.button_shortcuts_action_hold_erase, R.string.button_shortcuts_short_hold_erase, R.string.button_shortcuts_desc_hold_erase ),
    new Action( TDSetting.SPEN_ACTION_TOGGLE_PRESET, R.string.button_shortcuts_action_preset, R.string.button_shortcuts_short_preset, R.string.button_shortcuts_desc_preset ),
    new Action( TDSetting.SPEN_ACTION_HOLD_ZOOM, R.string.button_shortcuts_action_zoom, R.string.button_shortcuts_short_zoom, R.string.button_shortcuts_desc_zoom ),
    new Action( TDSetting.SPEN_ACTION_HOLD_PAN, R.string.button_shortcuts_action_pan, R.string.button_shortcuts_short_pan, R.string.button_shortcuts_desc_pan ),
    new Action( TDSetting.SPEN_ACTION_BACK, R.string.button_shortcuts_action_back, R.string.button_shortcuts_action_back, R.string.button_shortcuts_desc_back ),
    new Action( TDSetting.SPEN_ACTION_NONE, R.string.button_shortcuts_action_none, 0, 0 )
  };

  static Action action( int value )
  {
    for ( Action action : ACTIONS ) if ( action.value == value ) return action;
    return ACTIONS[ACTIONS.length - 1];
  }
}
