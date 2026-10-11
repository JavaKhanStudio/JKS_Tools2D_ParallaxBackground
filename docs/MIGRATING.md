# Moving a game from the downloaded jar to Maven Central

For games that use the parallax library as a jar copied into the project (built from the 2019-2023 code). From
2.1.0 the library is on Maven Central as `io.github.javakhanstudio:parallax-background`. Package names did not change
(`jks.tools2d.parallax...`), and old `.plax` files still load: nothing has to be re-exported.

The jar holds only the runtime library. The editor is a separate commercial product, not part of this library or its
releases, and is never a game dependency.

## 1. Change the build

In the module that uses the library (usually `core/build.gradle` in a libGDX project):

```groovy
repositories { mavenCentral() }   // or in settings.gradle, wherever the project declares repositories

dependencies {
    api "io.github.javakhanstudio:parallax-background:2.4.0"
}
```

Then remove:

- the old parallax jar (`libs/*.jar`, or wherever it was copied) and its `files('libs/...')` / `fileTree(...)` line;
- `com.esotericsoftware:kryo:5.0.0-RC1` (the library now brings Kryo 5.6.2);
- `com.fasterxml.jackson.core:jackson-databind`, if it was only there for the parallax;
- `gdx-box2d` and `amanithsvg`, if they were only there for the parallax (the library never used them).

The library brings, through Gradle, `com.badlogicgames.gdx:gdx:1.14.2`, `kryo:5.6.2` and `jackson-annotations`
(Jackson BOM 2.22.2). Check these against the game:

- **libGDX:** Gradle resolves `gdx` to 1.14.2 if the game asks for less. Set the game's `gdxVersion` to 1.14.2, so
  the backends, natives and extensions (box2d, freetype, controllers...) match `gdx`. Mixing versions fails at
  runtime (native or `NoSuchMethodError`), not at compile time.
- **Java:** the library is compiled for Java 11. A module still on Java 8 (`sourceCompatibility = 1.8`,
  `options.release = 8`) fails to resolve the dependency: raise it to 11 or newer.
- **Kryo:** if the game uses Kryo itself, it must work with Kryo 5.6.2. Kryo 4 has a different API.
- **Jackson:** the BOM can raise the game's own Jackson modules to 2.22.2.
- **GWT/HTML backend:** supported since 2.2.0, which the old jar was not: a browser game loads the page's JSON, see
  the README's "Ship to the browser (GWT)".

## 2. Fix what no longer compiles

Only the classes a game is likely to touch are listed.

| Old | New |
|---|---|
| `Utils_Parralax` | `Utils_Parallax` (same methods) |
| `try { new Parallax_Heart(path); } catch (FileNotFoundException e)` | The constructor no longer throws it: remove that catch (it no longer compiles if the try holds nothing else that throws it). |
| `new Parallax_Heart(worldCamera, staticCamera, batch, w, h)` (and the `pageModel` variant) | Still works but deprecated: `new Parallax_Heart(worldCamera, batch, w, h)` / `(worldCamera, batch, pageModel, w, h)` |
| `GVars_Serialization.objectMapper`, `prepareJson()` | Removed (Jackson databind is no longer used). |
| `GVars_Serialization.prepareKryo()` setting the static `kryo` | It now returns a new `Kryo`; call `GVars_Serialization.init()` to set the static field. |
| `parallaxReader.inTransfer` (field) | `parallaxReader.isInTransfer()` |
| `ParallaxPageReader.drawOnX` / `drawOnY`, `compute_Color_Transfert()`, `transfertTo(ArrayList)`, `get/set_newLayer_Color`, `get/set_oldLayer_Color` | Removed. Use `transfertIntoPage(...)`, `addColorTransfert(...)` and `getTint()`. |
| `ParallaxLayer.isRepeat_tileX/Y`, `setRepeat_tileX/Y`, `draw(batch, x, y, int)` | Removed; repeat is set on the page. |
| `ParallaxLayer.draw(batch, x, y, boolean)` | `drawMirror(batch, x, y, boolean)` |
| `WholePage_Model.findInternalLayer(AtlasRegion)` | Removed. |
| `SolarAstre`, `Enum_AstreType`, `Parallax_Utils_Astre`, `MyMixInForIgnoreType` | Removed (unused). |
| `new SquareBackground()`, `setHeight(boolean, float)`, `resetTransfert()` | Removed. |
| `new SquareBackground(top, bottom, height, ...)` | **Compiles but means something else:** the third argument is now the part of the screen left *uncovered* (0 = covers everything, 1 = nothing), not a height in pixels. |
| Subclasses of `Utils_Page` / `Parallax_Utils_Page` / `GVars_Serialization` | They are `final` now. |

## 3. Check what behaves differently

These compile unchanged but can change what the game shows.

- **Resize:** call `heart.resize(width, height)` from the game's `resize`. The old heart fixed the world height and
  the squares when it was built; the new one recomputes them. With your own camera, you still size that camera.
- **Dispose:** call `heart.dispose()` in the game's `dispose`, then `Gvars_Parallax.getManager().dispose()` for the
  atlases. `dispose()` frees the batch only if the heart created it.
- **Atlases live in the AssetManager now:** the atlas is loaded synchronously into `Gvars_Parallax.getManager()`
  (before, a second, never-freed copy was used). Don't `clear()` or `unload()` that manager, or the one passed to
  `setManager()`, while a page is shown, or the layers lose their textures. A layer whose region is missing throws
  `GdxRuntimeException`.
- **Your own moving camera:** layers are now laid out from the camera view's bottom-left corner and follow its zoom.
  The old version drew from world x=0 whatever the camera did, so remove any code that compensated for that (moving
  the background back, a separate static camera). With the heart's own camera nothing changes.
- **No repeat:** a page with repeat off on both X and Y used to draw nothing; each layer is now drawn once.
- **Cross-fades** (`transfertIntoPage`) now work in every repeat mode, keep the incoming layers scrolling, match
  layers from the front, no longer crash when the new page has fewer layers, and set `currentPage` to the new page at
  the end. A duration of 0 or less switches at once.
- **Tints** (`addColorTransfert`) now blend smoothly in every repeat mode, independently of cross-fades.
- **`relativePath`** (desktop): an atlas loaded from it is owned by the page and freed when the page is replaced or
  disposed. A null `relativePath` means internal assets. `transfertIntoPage` still loads the new page's atlas from
  internal assets.

## 4. `.plax` files

- Existing `.plax` files, their `.atlas` and PNGs load as they are. Keep them.
- Files exported by the editor from 2.1.0 on can only be read by the new library, not by the old jar.
- New files also store each layer's Flip Y (the old files dropped it) and Mirror (new setting).

## 5. Verify

1. `./gradlew clean build`, then check the old jar is really gone:
   `./gradlew :core:dependencies --configuration runtimeClasspath | grep -iE 'parallax|kryo|gdx:'` shows
   `parallax-background:2.4.0`, `kryo:5.6.2` and a single `gdx` version.
2. Run the game: every background shows, scrolls, fills the screen after resizing the window, and page changes
   and tints look right.
3. Android: build a release APK if the game shrinks code (R8/ProGuard). Kryo builds the page serializers by
   reflection; if loading a `.plax` fails only in release builds, add
   `-keep class jks.tools2d.parallax.pages.** { *; }`. The library uses Java APIs from Android API 24
   (`HashMap.merge`); below that, enable core library desugaring. Neither has been tested on a device.

See the [README](../README.md#using-the-library-in-a-game) for how to use the library.
