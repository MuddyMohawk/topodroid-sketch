# TopoDroid Sketch

This is a fork of the main TopoDroid repository. It is focused on adding features to better support a paper-style free hand sketching style instead of using symbols.
The intended workflow is to create beautiful sketches on the sketching screen and export those as images (right now, done via system screenshot)

This was essentially entirely vibe-coded with Codex.


#### Planned Features
- Active Tab Stylus button support
- Quick switch between preset line styles (smooth, straight, fine)
- Line colors per sketch line? (new color picker means colors everywhere!)
- PNG export?
- Better cross-section support?

### TopoDroid Sketch v1.3 Changelog:

- Changed things so I could work in Android Studio. I'm a noob.
- Added three built-in sketch line variants: Thin, Standard, and Thick (user-fine, user-standard, user-thick).
- Added per-variant width settings for new sketching lines, with defaults of 1.0x, 2.0x, and 5.0x.
- Added a new color picker
- Added color options to the new sketch lines
- Added sketch grid appearance settings for both grid width and grid color
- Made the three sketch lines default in the recent-line toolbar
- If exported and imported into the TopoDroidX-6.4.25-36, the new sketch lines fall back into the `user` line type. It'll be ugly, but still compatible.
- If exported with the personal line box checked, it can be imported into another copy of TopoDroid Sketch and the lines are preserved
- Added the new 1 foot sketch-grid unit alongside the existing 2 feet, yard, meter, and 10 cm options
- Fixed the length-unit/default regression so explicit feet vs meters values are handled correctly on load, live updates, and settings import/export.
- TODO: Investigate backsight validation and cavway labels. 
- TODO: Do we want another line 

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