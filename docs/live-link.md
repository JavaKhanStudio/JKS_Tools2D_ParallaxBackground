# The live link

How an engine reads the page the Parallax Editor has open and hears each edit, with no Export click and no reload.
Move a slider in the editor and the game running beside it redraws (r492; Simon on deux-generaux r664: « To make it
manageable for the next projet as well »). This is the contract: one protocol, served by the editor, read by every
reader in every project. A change here is a change on both sides.

The editor is the server: plain HTTP and Server-Sent Events on the machine itself, nothing outside the JDK
(`com.sun.net.httpserver`) on its side and nothing outside the browser (`fetch`, `EventSource`) on a web reader's.
A desktop engine needs an HTTP client that can read a response as it arrives. It is a tool for making a page: a shipped
game reads its exported files (a reader that is not asked to connect fetches nothing from `127.0.0.1`).

## In one look

```
http://127.0.0.1:9797/live/
    page.jplax    the open page, the JSON Export writes
    <atlas>       the page's atlas, by the name in its atlasName
    <image>       each image the atlas names
    <effect>      each .p (and Godot scene) its PARTICLES layers name
    events        the stream: one event per edit
```

`/live/` reads as the folder Export writes, laid out the same, so a reader's ordinary load already works on it:

```js
await bg.load('http://127.0.0.1:9797/live/page.jplax');   // three.js: its atlas and images next to it
```

The stream only says when to read it again.

## The server

- **Address:** `127.0.0.1` only, never `0.0.0.0`. Port **9797** unless the person sets another. If it is taken (a second
  editor), the next free one up to 9806. The editor shows the URL in use where the link is switched on.
- **Methods:** `GET` and `OPTIONS`; anything else 405 or 501. Nothing can be written through the link.
- **Names:** only the files the open page names are served, each from a table built for that page. The path is looked up
  in that table, never joined to a folder, so `..` has nothing to reach. Anything else is 404. A query string is ignored:
  a client may add one to bust a cache.
- **On every response, 404s and the preflight included:**

  ```
  Access-Control-Allow-Origin: *
  Access-Control-Allow-Private-Network: true
  Access-Control-Expose-Headers: X-Plax-Rev
  Cache-Control: no-store
  ```

  `OPTIONS` answers 204 with `Access-Control-Allow-Methods: GET, OPTIONS` and `Access-Control-Allow-Headers: *` too.

**Any website open in a browser on that machine can read the open page while the link is on:** that is what
`Allow-Origin: *` means, and it is what lets a game on GitHub Pages connect. So the link serves the page and nothing
else, writes nothing, and the person can see when it is on.

## `GET /live/page.jplax`

`200`, `application/json`: the open page, **the same JSON Export writes for the same page**, as `.jplax` readers already
read it ([other-engines.md](other-engines.md)), built by the same code. A page whose images are all in its atlas is
byte for byte the `.jplax` Export would write at that moment.

The rev is **not in the body**, or the body would not be what Export writes: it is in a header, `X-Plax-Rev`, the `rev` of
the last event that moved the page. A reader rarely needs it: the events carry the same number.

Export itself differs from the link in one case, a page with loose images: see below.

## `GET /live/<name>`

The atlas, its images, the `.p` files and Godot scenes of the page's `PARTICLES` layers, under the names the page and the
atlas use (a relative path keeps its folders, as in an export folder). The atlas is `text/plain`, a PNG `image/png`.

An ETC2 copy (`.etc2.atlas`, `.zktx`) is not served: readers draw the atlas's own images here.

## A loose image

A page being painted has images not yet in any atlas, and may have no atlas at all. Export refuses that state: it
flattens first, or its layers drop. **The link serves them loose, unpacked, and a reader cannot tell:** a loose image is
one page of the page's atlas, holding one region the size of the image.

```
hills.png
size: 4096,1024
filter: Linear,Linear
repeat: none
hills
  bounds: 0,0,4096,1024
```

(The `bounds` line is indented: unindented, the readers' parsers take it for a new region.)

- **Nothing is repacked**, so painting costs one image's refetch, and no reader needs a new idea. libGDX's parser, Godot's
  and three.js's read this atlas (`tools/r492-live-link/atlas-parsers.sh`, `browser-check.py`).
- **With an atlas**, its text comes first, unchanged, and each loose image's page after it, so no region's
  `regionPosition` moves.
- **With no atlas yet**, the served atlas is named `live.atlas`, and `atlasName` in the JSON says so: the one place the
  link changes a field.
- **The JSON keeps every layer**, loose ones too, each naming its image's region at `regionPosition` 0. A region name is
  the image's file name without `.png`, unique among the atlas's (`_2`, `_3`, as `Utils_Saving.packTextures` names them).
  That name replaces the path on disk a project stores: a path never leaves the machine.
- **What it is not:** the `.jplax` Export writes after flattening. Same layers and numbers, other region names and another
  atlas. Pixel art sets `filter: Nearest,Nearest`; a mipmapped atlas is shown un-mipmapped until it is flattened.
- Every loose image is a PNG on the link, whatever the file on disk.

## `GET /live/events`

`200`, `text/event-stream`, for as long as the server runs, **also with no page open**. It starts with `retry: 2000`, and
sends a comment (`: alive`) at least every 15 s so a dead client is noticed.

```
data: {"v":1,"rev":1793734720508,"imagesRev":1793734718249,"open":true,"page":true,"images":false}
```

| Field | Meaning |
|-------|---------|
| `v` | The protocol's version, 1. A reader refuses one it does not know, and says so. |
| `rev` | Moves on every event. Starts at the server's clock in milliseconds and only rises, so a restarted editor never repeats a rev a reader holds. Fits a JavaScript number. |
| `imagesRev` | The `rev` of the last event that moved a file other than the page. |
| `open` | Whether the editor has a page open. |
| `page` | `true`: read `page.jplax` again. |
| `images` | The files to read again, names as above, the atlas first if it is one of them; `false` for none. Despite the name it holds every file but the page: atlas, images, `.p`. |

**One event per edit, at most one every 150 ms.** An edit never waits longer than that for its event, and the last step
of a drag always gets one. Events falling in the same 150 ms are merged: their `images` joined. It is not a debounce that
waits for the drag to stop: the page has to follow the slider. The editor's limit from edit to event is 500 ms.

**A slider costs the page, never an image.** `images` moves only when an image really changed: a repack, a repaint, a
new loose image. An image repainted alone is `"page":false`.

**The first event of every connection says everything:** the current revs, `"page":true`, and every file in `images`. No
history is kept and `Last-Event-ID` is not read. So a first load and a reconnect are the same code.

**No page open:** `{"open":false,"page":false,"images":false}`, and `page.jplax` and every file are 404. Opening a page
sends `"open":true`, `"page":true` and the page's files: another page opened in place of the first is the same event.

## What a reader does

Hold the `rev` and the `imagesRev` of the last event you read (`null` at the start). On each event:

1. `v` unknown: stop, and say so. `open` false: **keep drawing what you have**, and stop there.
2. `page` true and `rev` differs from yours: read `page.jplax`.
3. `imagesRev` differs from yours: read the files in `images`. You may read more than it names (a whole atlas again);
   never read any when `images` is `false`.
4. **Draw only a complete set.** Build the new layers to one side and swap when all of it arrived, so a frame never
   holds a new page on old regions. One read at a time: an event arriving during a read is remembered, the latest one
   only, and read after it. A page your reader refuses (a kind it does not know) keeps the last page on screen, says
   why, and counts as read: the next edit may fix it. A read that failed (the connection, a 404) keeps the last page
   and holds no new rev: close the stream and open a new one after 2 s, whose first event names everything again.
5. **Rebuild in place, not a cross-fade and not a reset.** What the page does not hold stays the game's: the camera, a
   `PlaxRings`' radius and ring settings, the speeds the game sets, a tint, detail levels, hooks. And each new layer
   takes the distance its old counterpart has scrolled from its decal, matched from the front as a cross-fade's are
   ([other-engines.md](other-engines.md)), and the effect clock runs on: otherwise the whole page jumps on every step
   of a slider, and an edited decal must still move its layer.

**When the link drops** a browser's `EventSource` retries by itself after a network error, but not always: when
`readyState` is `CLOSED` (2), which a refused permission gives (below), it never will. So on an `error`, if the stream is
closed, open a new one after 2 s; keep drawing all the while. A desktop client reconnects the same way. Use `127.0.0.1`
in URLs, not `localhost`, which may resolve to an address the server does not listen on.

## Reaching it from a browser

| Page served from | What it takes |
|------------------|---------------|
| `http://127.0.0.1:<port>`: a game under development on this machine | Nothing. |
| `https://…` (GitHub Pages) | The **site's permission** (below). With it, `http://127.0.0.1` is not blocked as mixed content. |
| `http://…` on another machine | Refused like https without the permission; whether Chrome offers it there is not checked. Serve over https. |

All three on Chrome 156.

Chrome's Local Network Access stops a website reaching loopback until the person allows that site, once. So
`Access-Control-Allow-Private-Network` alone, which was the whole answer in the Private Network Access days, no longer
is: it stays for browsers that still ask for it. In Chrome 156, with no permission, the `fetch` is refused and the
`EventSource` ends `CLOSED`, with the console saying `Permission was denied for this request to access the loopback
address space`. A reader whose stream closes that way says so on the game's console, naming the site settings: it looks
exactly like an editor that is not running.

`tools/r492-live-link/browser-check.py` holds this down: refused without the permission, working with it granted by the
test. **Not checked: the prompt itself** (headless Chrome refuses without asking), Firefox, and Safari, which as far as
we know blocks `http://127.0.0.1` from an https page: serve the game locally there.

## Testing against it: `tools/live-link-stub.py`

The editor's and the readers' tickets test against one fixture, which serves files as the editor serves its page:

```bash
tools/live-link-stub.py engines/rounds/round1/s01.jplax core/test-data/samples     # port 9797
tools/live-link-stub.py PAGE.jplax ATLAS_DIR --loose DIR --port 0                  # any free port, printed first
```

Change the `.jplax` and the stream says a slider moved. Change the atlas, one of its images or a `.p`, and it names that
file. A PNG in `--loose DIR` is a loose image: repainted, added or resized. Delete the `.jplax` and no page is open;
write it back and one opens. Write by rename if a reader may be reading (the editor builds its answers in memory, and has
no half-written file to serve): an unreadable page keeps the last good one, but an image caught half-written is served as
it is. The stub reads its files 25 times a second, stdlib only.

- `tools/live_link_stub_test.py` (CI, `tools/python-tests.sh`) holds the stub to this document, over real HTTP.
- `tools/r492-live-link/browser-check.py OUT` is the real three.js reader in a real Chrome on an https origin: a layer's
  number, an image repainted, a page with no atlas yet, each redrawn within 2 s, and the permission above.
- `tools/r492-live-link/atlas-parsers.sh` is the generated atlas in libGDX's and Godot's parsers.

## What this is not

Not a way to edit: nothing flows back to the editor. Not a transport for a saved project: a `.plaxpj` holds paths on one
machine, and the link serves what those paths hold instead. Not something a page's file format knows of: no stored field
changed, and `.plax`, `.jplax` and `.plaxpj` read as before.
