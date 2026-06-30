# Roadmap, known bugs, and brainstorming

The next major planned feature is an overhaul of the symbols to match NSS conventions and be easier to use. In particular, I would like to solve the time-consuming tedium of "generic fill" for things like breakdown blocks and cobbles. 

## TODO
- Add safety warnings when importing vanilla TopoDroid stuff
- per-symbol defaults? locking?
- Option for rearranging render order (eg, survey station designations on top)
- Change the update check and versioning to track something besides `https://raw.githubusercontent.com/marcocorvi/speleoapks/main/tdversion.txt`

## Known bugs
- Large reference images, and reference images in general, are kinda low and cause lag
- Adjusting the number of preset buttons doesn't update that settings screen, causing the user to have to leave and re-enter that settings page to adjust the new preset bars
- Export to PNG, the north arrow and the scale bar are weird and can overlay the sketch 
  - station designation font size does not affect export size 
  - actually all the sketch settings might not be respected (eg leg lines size)?
- The "location is needed" pop-ups upon install even if permission was granted during installation. Vanilla bug.
- The `undo` action seems weird over many actions. Potentially vanilla bug
- Taking screenshot with volume-up doesn't work. Vanilla bug.
- Seems like the scaling of dashed line is odd, like it has a minimum size? Vanilla bug.
- Had a bug where I placed a section cross-section but then couldn't select it via the edit tool. Wasn't replicable but noted here.

## Future possible features / brainstorming
- Line-following for fast water area drawing
- shadow sketching/cosurvey
- investigate text-to-speech options
- zip export save location
- Add TopoDroid Sketch-specific settings to the installation splash window
- Add an option to disable all drawing except that from the stylus pen
  - Maybe still detect fingers for panning/zooming, drawing only from stylus?
- Performance check for large sketches
- Measure distance between two points on the 2D sketch screen
- Text objects for ceiling height circles
- Fix the text box/text input scaling. Enhance with fonts (Architects Daughter)?
- Sketch line collision to prevent sketching through another line
- Change the user-lines to wall-lines?
- copy/create new symbols
- Version control? - file/edit/shot/survey history. Scroll back and pick versions. Check the existing backup feature?
- Better PDF export
- At-station cross-section viewport support
- Use the new color picker to support more color settings everywhere
- Set usage profile to expert by default
- Per-screen action bindings (eg double tab the Active Key from the survey page to enter the last sketch. Double tap it in the sketch page works as the back action)
- Press-and-hold the stylus button while hovering to erase
- Always more actions. Some thoughts:
  - Take a shot
  - Download data via bluetooth from device (multi-device?)
  - switch between profile and plan (and cross-sections?)
  - press-and-hold to pan like Krita does
  - 2,3,4... finger tap and finger drag actions
  - switch between toolsets
  - switch between tools/brushes
- Pie in the sky: Advanced GPS/gnss tools. RTK when.
- Inventory/Rope Audit/Vandalism tracking tools
- Investigate and enhance the point symbols. They could be good. They need to be aligned with the NSS/UIS conventional symbols
  - Better, finer, sand symbol
  - Mud symbol in alignment with the NSS symbol
  - Bedrock symbol
  - cobbles
  - Randomized rock symbol?
  - better size range for symbols
- In-app symbol editor v2
- Sketch layers
- Opacity? that would be useful for doing fade-in-fade-out overlapping layers
- Opacity in symbol editor
- Display Cavway line features on the sketch (is this a thing already?)
- Bulk reassign splays? (I've been told this is a skill issue)
- Setting to automatically detect if there hasn't been any successful wifi or data connections in the last N minutes, and then toggle airplane mode to save battery?
- Legend, sketch info, etc viewport style box to use with the png export. Jealous of that Therion fanciness
- More naming options for png export? (create a name based off of the selected export options? eg append `s` for splay, `n` for north arrow)
- Option to toggle the display of backsights
- Option to automatically use the Cavway backsight mark to actually make backsights into backsights
  - Better alerting and information for bad backsights?
  - automatically label them as going from the `to` station to the `from` station (eg, from A1->A0)
  - Remotely clear unsent shots on the Cavway?
- Tweak bad backsight orange line to be a little more subtle
- Sound alerts/noises/haptics for specific events? (data successfully download, shots are good, shots are bad, pairing, multi-device noises?)
- Expand the preset functionality into more of saved-brushes functionality, adding the ability to save line/point/area brush types in addition to the current settings.
- Fdroid distribution and updating
