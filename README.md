# Lattice Terminal

**Lattice Terminal** is a high-performance terminal emulator pipeline written in Kotlin.

It is designed as a professional, modular terminal stack: a headless screen-state
core, a strict byte-stream parser, a transport/session layer, reusable input and
rendering modules, and UI frontends that can be swapped without touching terminal
grid internals.

Lattice is structured as a production-grade terminal engine that can support local PTYs, 
future SSH connectors, standalone desktop applications, IDE integrations, and test/benchmark
environments from the same core pipeline.

## Why Lattice

Most terminal projects collapse parsing, grid mutation, rendering, input, and
transport I/O into one tightly coupled system. Lattice deliberately separates
those responsibilities.

- The **core** is a headless terminal memory engine.
- The **parser** converts byte streams into semantic terminal commands.
- The **integration layer** maps parser semantics onto public core APIs.
- The **input layer** encodes keyboard, paste, focus, and mouse events into
  host-bound bytes.
- The **session layer** serializes parser/core mutation and host-bound writes.
- The **render API/cache** expose primitive render frames without leaking grid
  internals.
- The **UI modules** paint and handle user interaction without knowing about PTY
  internals.
- The **transport layer** lets PTY, SSH, and test connectors share the same
  terminal pipeline.

This keeps terminal correctness, rendering, and transport logic independently
testable and replaceable.

## Architecture

- **terminal-protocol**  
  Dependency-free protocol vocabulary: control-code constants, ANSI/DEC mode ids,
  mouse/input mode constants, and shared low-level terminal protocol definitions.

- **terminal-parser**  
  Converts host byte streams into semantic terminal commands. It owns ANSI/DEC
  state-machine behavior, UTF-8 decoding, OSC/DCS handling, and grapheme
  segmentation before dispatching printable clusters.

- **terminal-integration**  
  Bridges parser commands to the public core APIs and host metadata callbacks.
  This is where parser semantics become core operations such as cursor movement,
  erasing, SGR attributes, modes, titles, hyperlinks, and device responses.

- **terminal-core**  
  The headless terminal memory engine. It owns screen state, scrollback, cursor
  state, mode state, attributes, clusters, resizing, mutation physics, and
  render-frame publication. It does not parse byte streams, encode input, perform
  I/O, or paint UI.

- **terminal-input**  
  Encodes platform-agnostic keyboard, paste, focus, and mouse events into
  host-bound terminal byte sequences. It supports application cursor/keypad
  modes, bracketed paste, focus reporting, xterm-style mouse encodings, and
  explicit input policy handling.

- **terminal-render-api**  
  Defines dependency-free primitive render-frame contracts: cells, cursor,
  clusters, attributes, line metadata, and frame access. UI modules consume this
  instead of touching core internals.

- **terminal-render-cache**  
  Copies render-frame data into renderer-side primitive caches for UI consumers.
  It keeps UI rendering decoupled from live core mutation and avoids exposing
  core grid storage.

- **terminal-transport-api**  
  Defines the connector contract for byte-stream transports such as PTY, SSH, and
  test connectors.

- **terminal-session**  
  Owns runtime ordering and synchronization. It serializes parser/core mutation,
  response draining, UI input bytes, transport writes, and lifecycle boundaries.

- **terminal-pty**  
  Provides PTY4J-backed local-process connectors for shells such as PowerShell,
  `cmd.exe`, WSL, Bash, and other local terminal programs.

- **terminal-ui-swing**  
  A reusable Swing terminal component. It owns painting, cursor presentation,
  selection, input event mapping, clipboard/font/settings abstractions, viewport
  state, and scrollbar behavior. It stays independent of IntelliJ APIs and PTY
  specifics.

- **terminal-ui-swing-demo**  
  A standalone manual-test host that opens the Swing component on a local
  PTY-backed session.

- **terminal-testkit**  
  Shared connector fakes and fixtures for cross-module tests.

- **terminal-benchmarks**  
  JMH benchmarks for parser, buffer, render-frame, and mutation hot paths.

## Core Engine

The Lattice core is intentionally headless. It is the terminal’s memory and
physics layer, not a UI toolkit.

- **TerminalBuffer** is the facade coordinating state, mutation, cursor, mode,
  reader, and inspector surfaces.
- **ScreenBuffer** owns one complete screen arena: history ring, cluster store,
  cursor, saved cursor, and scroll margins.
- **Line** stores cells in flat primitive arrays:
  - `IntArray` codepoints, sentinels, and cluster handles
  - `IntArray` packed primary attributes
  - soft-wrap metadata
- **MutationEngine** owns spatial cell physics:
  overwrite, deferred wrap, scroll, erase, wide-cell annihilation, insertion,
  deletion, and line editing.
- **CursorEngine** owns cursor movement, save/restore, tabbing, and cursor
  positioning behavior.
- **TerminalResizer** reflows the primary screen and deep-copies surviving
  cluster payloads into a fresh arena.

The hot paths are built around primitive arrays, explicit ownership boundaries,
and low-allocation mutation.

## Unicode Boundary

The core is cluster-capable but not a grapheme segmenter.

- `writeCodepoint` and `writeText` are scalar convenience entrypoints.
- `writeCluster` is the parser-facing entrypoint for pre-segmented grapheme
  clusters.
- The parser owns UTF-8 decoding, malformed-sequence recovery, grapheme
  segmentation, charset mapping, and printable cluster dispatch.

This separation keeps Unicode text processing out of grid mutation logic while
still allowing the core to store wide characters, combining sequences, emoji, and
ZWJ clusters correctly.

## Render Pipeline

Lattice separates render data from UI painting.

- The core exposes primitive render-frame contracts through `terminal-render-api`.
- `terminal-render-cache` copies visible frame data into renderer-owned primitive
  caches.
- UI components render from cached frame data instead of reading live core arrays.
- Renderers group cells by opaque style keys and glyph/cluster runs rather than
  allocating per-cell objects.

The renderer treats core packed attributes as opaque grouping keys. It does not
decode core’s internal attribute layout. Visual style resolution is kept behind
explicit render-facing contracts so core storage can evolve without breaking UI
modules.

## Transport and Session Model

Transport connectors own raw byte-stream I/O. `TerminalSession` owns terminal
ordering.

- Parser/core mutation is serialized.
- Host-bound writes are serialized.
- UI input bytes and terminal response bytes share one ordered host-output path.
- Transport-specific code stays outside parser, core, input, and UI modules.
- PTY, SSH, and test connectors can use the same parser/core/input/render
  pipeline.

The PTY implementation is only one backend. It is not baked into the UI or core.

## Behavioral Notes

- Wide characters and grapheme clusters are stored explicitly in the grid.
- Deferred wrapping follows terminal behavior: writing in the last column marks a
  pending wrap; the next printable character performs the wrap.
- Resize reflows the primary screen, wipes the alternate screen, and resets both
  buffers’ scroll regions to the full viewport.
- ED 3 follows xterm/VTE-style semantics: it clears scrollback history while
  preserving the visible viewport.
- Alternate-screen variants, cursor save/restore, bracketed paste, focus
  reporting, SGR mouse, OSC titles, OSC 8 hyperlinks, SGR attributes, and safe
  device responses are modeled as explicit protocol behavior rather than hidden
  UI hacks.

## Development

Requirements:

- JDK 21 or higher (preferably JBR 21+)
- Gradle 7.4 or higher


Run all tests:
```bash
./gradlew test
```
Launch the Swing PTY demo:
```bash
 ./gradlew :terminal-ui-swing-demo:run
```
On Windows, the demo uses PowerShell by default so commands like ls and cat
work. To launch a custom shell:
```bash
./gradlew :terminal-ui-swing-demo:run --args="cmd.exe"
```
Or for WSL:
```bash
./gradlew :terminal-ui-swing-demo:run --args="wsl.exe"
```

## Project Goal

Lattice Terminal is built to become a serious terminal foundation: correct enough
for modern shells and TUIs, modular enough for IDE integration, and fast enough
to justify replacing older JVM terminal stacks.

It is not tied to one UI toolkit, one shell, or one transport. The terminal
engine, parser, input encoder, transport runtime, and renderer are separate by
design.
