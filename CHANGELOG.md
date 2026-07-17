# Changelog

Everything TopoDroid Sketch changes relative to vanilla TopoDroid. Future releases get their own section.

## v0.40.1 (July 2026) -- first alpha

Everything since forking from TopoDroid 6.4.27.


**Architecture**
- Changed things so I could work in Android Studio. This was probably unnecessary. I'm a noob. Also to run on Windows, I accidentally wiped my linux drive.
- Disabled the upstream TopoDroid release check in the About dialog so TopoDroid Sketch no longer checks `marcocorvi/speleoapks` for app updates
- Migrated naming, app manifest, strings etc from TopoDroid to TopoDroid Sketch. The apps can be installed side-by-side. The underlying java package/class names and such are unchanged.
  - _The default storage location is now `Documents/TopoDroid Sketch/` instead of `Documents/TDX`_
  - The versioning was changed from vanilla TopoDroid. See [docs/versioning.md](docs/versioning.md) for the details. Not well tested. 

**Lines**

- Added a setting, "Fixed line pattern density", which disables the auto-scaling of lines (most notable with dashed lines, eg pits and ceiling ledges)
- Added a straight line option in addition to the existing Fine, Normal, Coarse, Bezier, and Simplified lines styles
- Added a snapping line style that snaps a drawn line to the nearest 22.5, 45, or 90 degree angle
- Changed the vanilla morphing of line-symbols from warp-to-fit to a rigid-stamping that prevents the ugly morphing of things like ceiling ledges
- Extended the line symbols with additional terms; sketch_effect, carriers, rigid stamps, dash-on segments, and advance. These are used to make prettier curved brush lines. This bumped the TDVersion.SYMBOL_VERSION from 44 to 45. However, this is still compatible with vanilla TopoDroid. Probably.
- Changed most of the lines from the default speleo symbol pack to be white

*Line Weights*
- Added support for point and line symbols to follow a line weight setting 

*Sketch Lines*
- Added three new "sketch lines", which are programmatically generated custom line symbols based on the existing `user` lines 
- The sketch lines has three variants: Thin, Standard, and Thick (user-fine, user-standard, user-thick).
- Added per-variant width settings for new sketching lines, with defaults of 1.0x, 2.0x, and 5.0x.
- Added color options to the new sketch lines
- Made the three sketch lines default in the recent-line toolbar
- Compatibility intent: when exported and imported into vanilla TopoDroid, the new sketch lines should fall back into the `user` line type. It'll be ugly, but still compatible. The current emulator has only smoke-tested side-by-side install/launch with an already-installed vanilla 6.4.27; real vanilla ZIP import still needs a compatible vanilla APK/device.
- If exported with the personal line box checked, it can be imported into another copy of TopoDroid Sketch and the lines are preserved

**Cross-Section Viewports**
- Added the ability to place cross-sections directly on the plan sketch in a viewport style experience. These can be moved around and edited by selecting them in edit mode (may require TopoDroid to be in Expert mode in the main settings)
  - To do this, use the `section` line tool and draw across the passage like normal TopoDroid. Then select the "place on plan" button in the resulting pop-up window. Tap where you want to place your cross-section. Its position can be further adjusted in edit mode.  
- Display of sketch references for cross-sections (legs, splays, etc) can be toggled by selecting the cross-section in edit mode
- Not currently supported for station cross-sections

**Reference Image**
- Added the ability to place a reference image on a sketch (eg, a photo for a cross-section). The image can be scaled, moved, rotated, and its opacity and visibility can be changed. The reference image is included with the PNG export if it's visible

**Line Presets**
- Added drawing presets to the sketch screen, which appear as "P1" and "P2". These are intended to allow a sketcher to switch between drawing thin, detailed lines, and smooth, straight lines
- Fine's defaults are a line style of `fine` and a line point spacing of 1
- Smooth's defaults are a line style of `bezier` and a line point spacing of 10
- Straight's defaults are a line style of `straight` and a line point spacing of 5
- Added a presets menu to the sketch settings screen to allow customization of each preset. Users can also add up to 8 preset slots.

**Toolbar Overhaul**
- Added a setting for an overhauled toolbar/recents bar. This is on by default under Settings -> Secondary sketch settings -> Toolbar mode
- The new toolbar replaces the old recents-style functionality with manually selected slots, which are saved on a per-survey basis
  - To change the tool in a slot, select the slot, tap the >> button the far right, and select the new tool/brush you want from the palette
- Added an option for multiple toolbar rows. These rows can be locked to a specific toolset (eg line, point, area

**S Pen, Active Key, and Volume Button Support**
- Added support for the S Pen button for single click, double click, and long click inputs
    - *Note that this is only tested with the IP68 S pen that has no bluetooth and no battery. It may not work with other pens. Additionally, the pen must be held close to the screen in order for the button to work*
- Added the following actions:
  - Undo: Perform the undo action in the sketch screen
  - Redo: Performs the redo action in the sketch screen
  - Toggle palette: Toggles the recently used bottom palette between LINE, POINT, and AREA
- Toggle preset: Toggles the active line drawing preset between Preset 1 and Preset 2
  - Back: Goes back a screen (eg exit sketch page to shot list, or goes back one screen in the settings)
  - Toggle erase/sketch: Toggles between the erase sketch mode and the drawing sketch mode
- Added the ability to bind actions to S Pen button inputs in the *TopoDroid main settings -> Devices -> Action Key Bindings* menu
- Default S Pen key bindings are `undo` for single click, `back` on double click, and `toggle preset` on long-click
- Added the ability to bind actions to the Samsung Active key in the *TopoDroid main settings -> Devices -> Action Key Bindings* menu
- Default Active Key bindings are `toggle erase/sketch` on single press, `back` on double press, and `toggle preset` on long press
- Added the ability to bind actions to the Volume Up and Volume Down keys (single / long / double press each) in the same *Action Key Bindings* menu. Default binding for all six is `none`. If an action is bound, it overwrites the ability to change volume with that key while the app is open. This also overrides the busted volume-up screenshot action present in vanilla topodroid

**PNG Sketch Export**
- Added a PNG export option for sketches
- Stations, legs, splays, grid, scale bar (kinda meh), north direction, and background transparency are all toggleable options
- The output can be scaled from 0.05 to 4.0. The default of 1.00 is great for handing to a cartographer, but the files it produces are too large to really view on the tablet. I recommend 0.25 scale for that.
- The default filename is `<survey_name>_<sketch_name>_<sketch_type (eg plan, profile)>_YYYY-MM-DD.png`. Example: `F-Survey_toob_plan_2026-04-15.png`.

**Testing**
- Added tests to cover import/export, vanilla compatibility, some regression testing. See [docs/testing.md](docs/testing.md) for details.

**misc UI**
- Added a new, more capable color picker widget
- Added sketch grid appearance settings for both grid width and grid color
- Added the new 1 foot sketch-grid unit alongside the existing 2 feet, yard, meter, and 10 cm options
- Added an option (on by default) for overlapping areas (eg water) to darken instead of lighten. This is mostly for drawing deeper water pools
- Made the default icon size large
- Added an editor in the palette selection window to allow for in-app editing of most symbols. Built-in symbols are not currently supported.
