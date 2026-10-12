# Parallax readers: Doctrine

> **PROPOSED** by the Doctrine upkeep (r3) on 2026-10-12. It binds nothing until Simon
> approves it on the board's Doctrine page.

**D1. A page made once looks the same in every engine that reads it.**
No engine-flavoured version of a page: every reader is held to the libGDX frames.

**D2. The page belongs to no engine: a new engine adapts to the file, the file never bends to an engine.**
What an engine needs, its reader works out; the saved page gains no engine-only field.

**D3. A port is trusted because it is proven, not because it was written with care.**
A reader change lands on a frame comparison that would fail without it, never on review alone.

**D4. Each reader can be understood, tested and shipped by someone who only knows that one engine.**
Its own folder, build, tests, README and CI job; no reader's build or tests reach into another's.

**D5. A game developer gets a reader the way their engine usually gets things, and keeps getting its fixes.**
A package the engine's own tools install, never sources to paste.
