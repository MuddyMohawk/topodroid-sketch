# TopoDroid Sketch

This is a fork of the main TopoDroid repository. It is focused on adding features to better support a paper-style free hand sketching style instead of using symbols.
The intended workflow is to create beautiful sketches on the sketching screen and export those as images (right now, done via system screenshot)

This was essentially entirely vibe-coded with Codex.

#### TODO
- Change branding, name, versioning, etc to TopoDroid Sketch
- Add safety warnings when importing vanilla TopoDroid stuff
- Add toggle erase action for S pen. Consider change long press to press and hold, revert on release
- Add S pen default actions
- Switch the defaults for profile 2 to a line-point spacing of 15 instead of 20
- Add a 4th sketch line. I love sketch lines.
- An actual build pipeline?
- Rename the drawing "profiles" to something else to avoid confusion with the extended profile view. Presets?

#### Future Possible Features / Brainstorming
- Sketch line collision to prevent sketching through another line
- PNG export
- Better PDF export
- Better cross-section support?
- Draw cross sections by tracing over a picture
- Use the new color picker to support more color settings everywhere
- Palm detection, if possible
- Press-and-hold while hovering to erase
- Option for double row/larger recents palette
- Change recents palette to set favorites instead
- Side-by-side installation with vanilla TopoDroid
- Set button size to large by default
- Set usage profile to expert by default
- Per-screen S Pen options/action bindings
- Additional inputs using volume buttons, active key
  - Note that currently volume-down opens the menu/help page for the current screen
- Always more actions. Some thoughts:
  - Take a shot
  - switch between profile and plan (and cross sections?)
  - Toggle erase (might be better off with the long press hover thingy)
- Pie in the sky: Advanced GPS/gnss tools
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

### TopoDroid Sketch v1.10.1 Changelog:

- Changed things so I could work in Android Studio. This was probably unnecessary. I'm a noob.

**Sketch Lines**
- Added three new "sketch lines", which are based on the existing `user` lines 
- The sketch lines has three variants: Thin, Standard, and Thick (user-fine, user-standard, user-thick).
- Added per-variant width settings for new sketching lines, with defaults of 1.0x, 2.0x, and 5.0x.
- Added color options to the new sketch lines
- Made the three sketch lines default in the recent-line toolbar
- If exported and imported into the `TopoDroidX-6.4.25-36`, the new sketch lines fall back into the `user` line type. It'll be ugly, but still compatible.
- If exported with the personal line box checked, it can be imported into another copy of TopoDroid Sketch and the lines are preserved

**UI**
- Added a new, more capable color picker widget
- Added sketch grid appearance settings for both grid width and grid color
- Added the new 1 foot sketch-grid unit alongside the existing 2 feet, yard, meter, and 10 cm options

**UX**
- Added two drawing "profiles" to the sketch screen, which appear as "P1" and "P2". These are intended to allow a sketcher to switch between drawing thin, detailed lines, and smooth, straight lines
  - Profile 1's defaults are a line style of `fine` and a line point spacing of 1
  - Profile 2's defaults are a line style of `bezier` and a line point spacing of 20 (todo: switch to 15)
- Added a "profiles" menu to the sketch settings screen to allow customization of each profile

**S Pen Support**
- Added support for the S Pen button for single click, double click, and long click inputs
    - *Note that this is only tested with the IP68 S pen that has no bluetooth and no battery. It may not work with other pens. Additionally, the pen must be held close to the screen in order for the button to work*
- Added the following actions:
  - Undo: Perform the undo action in the sketch screen
  - Redo: Performs the redo action in the sketch screen
  - Toggle palette: Toggles the recently used bottom pallete between LINE, POINT, and AREA
  - Toggle profile: Toggles the line drawing profile between Profile 1 and Profile 2
  - Back: Goes back a screen (eg exit sketch page to shot list, or goes back one screen in the settings)
- Added the ability to bind actions to S Pen button inputs in the *TopoDroid main settings -> Devices -> S Pen actions* menu

**Bug Fixes**
- Fixed the length-unit/default regression so explicit feet vs meters values are handled correctly on load, live updates, and settings import/export. I think this was pre-existing and not caused by me.

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