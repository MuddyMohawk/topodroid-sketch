# TopoDroid Sketch

This is a fork of the main TopoDroid repository. It is focused on adding features to better support a paper-style free hand sketching style instead of using symbols.
The intended workflow is to create beautiful sketches on the sketching screen and export those as images (right now, done via system screenshot)

This was essentially entirely vibe-coded with Codex.

Testing and development was done with a Cavway X1 and an Active Tab 3.

This was written in English; other translations are likely not working

#### TODO
- Change branding, name, versioning, etc to TopoDroid Sketch
- Add safety warnings when importing vanilla TopoDroid stuff
- Switch the defaults for profile 2 to a line-point spacing of 15 instead of 20
- Add a 4th sketch line. I love sketch lines.
- An actual build pipeline?
- Rename the drawing "profiles" to something else to avoid confusion with the extended profile view. Presets?
- Change defaults for Active Key. Double tap to go back?
- Add support for binding actions to the volume buttons

#### TODO bugs:
- There's some weird differences in the back key via S Pen stylus vs Active Key
- Exporting to PNG with Grid lines on and transparent background off results in a pure black background. The grid lines aren't being preserved.
- Export to PNG, the north arrow and the scale bar are weird and can overlay the sketch 
  - station designation font size does not affect export size 
  - actually all the sketch settings might be respected (eg leg line size)

#### Future Possible Features / Brainstorming
- Sketch line collision to prevent sketching through another line
- Version control - file/edit/shot/survey history. Scroll back and pick versions. Check the existing backup feature?
- Better PDF export
- At-station cross-section viewport support
- Draw cross-sections by tracing over a picture
- Use the new color picker to support more color settings everywhere
- Palm detection? This might already be a thing
- Press-and-hold the stylus button while hovering to erase
- Option for double row/larger recents palette
- Side-by-side installation with vanilla TopoDroid
- Set button size to large by default
- Set usage profile to expert by default
- Per-screen action bindings (eg double tab the Active Key from the survey page to enter the last sketch. Double tap it in the sketch page works as the back action)
- Additional inputs using volume buttons, active key
  - Note that currently volume-down opens the menu/help page for the current screen
- Always more actions. Some thoughts:
  - Take a shot
  - Download data via bluetooth from device (multi-device?)
  - switch between profile and plan (and cross-sections?)
- Pie in the sky: Advanced GPS/gnss tools. RTK when.
- Inventory/Rope Audit/Vandalism tracking tools
- Change the blend mode of the areas so overlapping areas get darker, not brighter. Mostly to create water pools
- Investigate and enhance the point symbols. They could be good. They need to be aligned with the NSS conventional symbols
  - Better, finer, sand symbol
  - Mud symbol in alignment with the NSS symbol
  - Bedrock symbol
  - cobbles
  - Randomized rock symbol?
- Sketch layers
- Opacity? that would be useful for doing fade-in-fade-out overlapping layers
- Display Cavway line features on the sketch (is this a thing already?)
- Sort shots by their ordering, not their shot ID
- Bulk reassign splays?
- Setting to automatically detect if there hasn't been any successful wifi or data connections in the last N minutes, and then toggle airplane mode to save battery
- Long-press erase mode for the S pen. Attempted once, was bugged and didn't work.
- Legend, sketch info, etc viewport style box to use with the png export. Jealous of that Therion fanciness
- More naming options for png export? (create a name based off of the selected export options? eg append `s` for splay, `n` for north arrow)
- Option to toggle the display of backsights
- Option to automatically use the Cavway backsight mark to actually make backsights into backsights
  - Better alerting and information for bad backsights?
  - automatically label them as going from the `to` station to the `from` station (eg, from A1->A0)
- Tweak bad backsight orange line to be a little more subtle
- Sound alerts/noises for specific events? (data successfully download, shots are good, shots are bad, pairing, multi-device noises?)

### TopoDroid Sketch v1.15.5 Changelog:

- Changed things so I could work in Android Studio. This was probably unnecessary. I'm a noob.

**Sketch Lines**
- Added three new "sketch lines", which are based on the existing `user` lines 
- The sketch lines has three variants: Thin, Standard, and Thick (user-fine, user-standard, user-thick).
- Added per-variant width settings for new sketching lines, with defaults of 1.0x, 2.0x, and 5.0x.
- Added color options to the new sketch lines
- Made the three sketch lines default in the recent-line toolbar
- If exported and imported into the `TopoDroidX-6.4.25-36`, the new sketch lines fall back into the `user` line type. It'll be ugly, but still compatible.
- If exported with the personal line box checked, it can be imported into another copy of TopoDroid Sketch and the lines are preserved

**Cross-Section Viewports**
- Added the ability to place cross-sections directly on the plan sketch in a viewport style experience. These can be moved around and edited by selecting them in edit mode (may require TopoDroid to be in Expert mode in the main settings)
  - To do this, use the `section` line tool and draw across the passage like normal TopoDroid. Then select the "place on plan" button in the resulting pop-up window. Tap where you want to place your cross-section. Its position can be further adjusted in edit mode.  
- Display of sketch references for cross-sections (legs, splays, etc) can be toggled by selecting the cross-section in edit mode
- Not currently supported for station cross-sections

**Line Presets**
- Added two drawing "profiles" to the sketch screen, which appear as "P1" and "P2". These are intended to allow a sketcher to switch between drawing thin, detailed lines, and smooth, straight lines
  - Profile 1's defaults are a line style of `fine` and a line point spacing of 1
  - Profile 2's defaults are a line style of `bezier` and a line point spacing of 20 (todo: switch to 15)
- Added a "profiles" menu to the sketch settings screen to allow customization of each profile

**S Pen  and Active Key Support**
- Added support for the S Pen button for single click, double click, and long click inputs
    - *Note that this is only tested with the IP68 S pen that has no bluetooth and no battery. It may not work with other pens. Additionally, the pen must be held close to the screen in order for the button to work*
- Added the following actions:
  - Undo: Perform the undo action in the sketch screen
  - Redo: Performs the redo action in the sketch screen
  - Toggle palette: Toggles the recently used bottom pallete between LINE, POINT, and AREA
  - Toggle profile: Toggles the line drawing profile between Profile 1 and Profile 2
  - Back: Goes back a screen (eg exit sketch page to shot list, or goes back one screen in the settings)
  - Toggle erase/sketch: Toggles between the erase sketch mode and the drawing sketch mode
- Added the ability to bind actions to S Pen button inputs in the *TopoDroid main settings -> Devices -> Action Key Bindings* menu
- Added the ability to bind actions to the Samsung Active key in the *TopoDroid main settings -> Devices -> Action Key Bindings* menu

**PNG Sketch Export**
- Added a PNG export option for sketches
- Stations, legs, splays, grid, scale bar (kinda meh), north direction, and background transparency are all toggleable options
- The output can be scaled from 0.25 to 4.0. The default of 1.00 is great for handing to a cartographer, but the files it produces are too large to really view on the tablet. I recommend 0.25 scale for that.
- The default filename is `<survey_name>_<sketch_name>_<sketch_type (eg plan, profile)>_YYYY-MM-DD.png`. Example: `F-Survey_toob_plan_2026-04-15.png`. 

**misc UI**
- Added a new, more capable color picker widget
- Added sketch grid appearance settings for both grid width and grid color
- Added the new 1 foot sketch-grid unit alongside the existing 2 feet, yard, meter, and 10 cm options

# topodroid

[![Join the chat at https://gitter.im/marcocorvi/topodroid](https://badges.gitter.im/marcocorvi/topodroid.svg)](https://gitter.im/marcocorvi/topodroid?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
TopoDroid code base

This is the TopoDroid app (com.topodroid.TDX) repository.

TopoDroid is a productivity Android app for cave surveying.
It is specially designed to do cave surveying with the DistoX (v. 1, 2, and BLE), the Cavway X1, the BRIC (4 and 5), the SAP (5 and 6) and DiscoX,
although it can be profitably used even without it.

Visit the website https://sites.google.com/site/speleoapps for more informations about TopoDroid, and in particular for the old version changes doc.

The most recent TopoDroid apks, as well as recent version changes info, are on http://marcocorvi.altervista.org/caving/speleoapps/speleoapks/TopoDroidApks.html 

All the code is provided under GNU General Public Licence v. 3