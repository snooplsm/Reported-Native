# Complaint animations

Approved vector artwork and 30 fps motion capture shared by native Android and Swift iOS. These are vector Lotties with no raster frames or web views.

## Regenerate native assets

Install requirements.txt in a Python virtual environment, then run `python convert.py` and `python install_assets.py` from this directory. The converter reads the compressed motion capture, simplifies redundant sampled keyframes with a 0.03-unit tolerance, and preserves named groups. The final sample holds the ending state to avoid interpolating through the offscreen loop reset. The installer combines the eight-second red-light and six-second stop-sign scenes and writes identical JSON to both platform resource folders.

## Revise artwork

Run create.py to build the SVG gallery. Add export-motion.js and a button calling exportMotion() to the generated gallery, run export-server.py, open localhost:8767, and click the export button. Recompress motion-capture.json into motion-capture.json.gz after editing. Source SVGs are static except for the combined parking preview; the captured motion is the animation timeline.

Timelines: bike lane 8s; crosswalk 18s; red light/stop sign 14s; reckless driving 3.2s; illegal parking 12s. Crosswalk footsteps repeat evenly within its loop. The signal stays red until the pedestrian clears the road.

Native export extends the original street geometry to a 320 × 320 canvas. It does not scale the actors or join separate road strips. Playback loops continuously in both apps and pauses with screen/app visibility. Run `python test_export.py` to check reset holds, native asset parity, and the second parking scene.

`palettes.py` defines light and dark colors. The installer emits matching `_dark.json` variants for both apps, changing only color properties, including traffic-signal keyframes. iOS selects with SwiftUI colorScheme; Android uses the active Material surface brightness so app theme overrides are respected.

Run `python create_variants.py` after conversion to generate the open-passenger-door bike lane and parking variants, plus the Prime van bike lane variant. The script reuses captured motion and exports SVG sources and light/dark Lottie JSON. These are additional showcase scenes; the default app categories are preserved.
