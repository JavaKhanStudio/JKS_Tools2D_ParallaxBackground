# Session handoffs

A working session that stops mid-way parks what it did here, so the next one does not have to
work it all out again. These notes are for whoever works on this repo. Anyone *using* the
library should read the [README](../../README.md) instead.

- One file per handoff, named `YYYY-MM-DD-what-it-was-about.md`, starting with a `# ` heading.
- The file is the source of truth. The Atelier board indexes this folder (`atelier ingest`),
  skipping this README, and keeps only a short summary of each file so a handoff can be picked.
- Name files, commits and commands. A handoff that only says "worked on the editor" is no use
  to anyone.
- This repository is public, so a handoff here is published with the next push. Never paste
  anything from `HQ/` or any other credential into one.
