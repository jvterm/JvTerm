---
name: terminal-ui-rendering
description: Use when implementing or reviewing terminal UI work such as Swing painting, render-frame consumption, text runs, fonts, colors, cursor rendering, selection, viewport/scrollback behavior, standalone UI wiring, or IntelliJ UI integration.
---

# Terminal UI Rendering Skill

Use this skill for UI/rendering work across:

- `terminal-ui-swing`
- `terminal-ui-standalone`
- `terminal-ui-intellij`
- related changes in `terminal-render-api`
- related changes in `terminal-render-cache`

This skill is a workflow. Module ownership rules belong in each module's
`AGENTS.md`.

## 1. Classify the Change

Before editing, identify the owning module.

Reusable Swing component behavior - `terminal-ui-swing`
Java2D painting, cursor, selection, viewport - `terminal-ui-swing`
Swing settings/font/color abstractions - `terminal-ui-swing`
Render-frame contract changes - `terminal-render-api`
Render-frame caching/publishing changes - `terminal-render-cache`
Standalone `JFrame`, config, PTY startup - `terminal-ui-standalone`
IntelliJ tool window, theme, actions, lifecycle - `terminal-ui-intellij`

Do not put host-specific code into reusable UI code.

## 2. Enforce the Boundary

For Swing UI changes, verify:

- no IntelliJ Platform imports
- no dependency on `terminal-pty`
- no PTY, SSH, WebSocket, or transport-specific logic
- no ANSI/VT/OSC/DCS parsing
- no direct terminal core mutation
- no duplicated render-cache responsibility
- no process/session creation policy
- no IDE or standalone lifecycle policy

The UI consumes render-frame state and sends user intent through the
session/input boundary.

## 3. Preserve the Component Model

The reusable Swing component is `TerminalPanel`.

Expected internal shape:

    TerminalPanel : JLayeredPane
      ├── TerminalSurface : JComponent
      └── CursorOverlay   : JComponent

`TerminalPanel` owns internal layer assembly.

Host modules must not manually assemble `TerminalSurface` and `CursorOverlay`.

`TerminalSurface` paints terminal content, selection, and decorations.

`CursorOverlay` owns cursor presentation and cursor-only repainting.

## 4. Protect Rendering Hot Paths

Rendering must be run-based, not cell-based.

Prefer renderer logic organized around:

- background runs
- text runs
- decoration runs
- selection ranges
- cursor overlay
- complex cluster fallback

Reject hot-path designs that do this:

    for each cell:
        allocate object
        resolve font
        create Color
        create String
        drawString(...)

Avoid per-cell allocation of:

- `Color`
- `String`
- `Font`
- coordinate objects
- temporary cell wrappers
- collections/iterators

Avoid `drawString` once per cell.

Use primitive packed values where practical, especially packed ARGB colors.

## 5. Keep Text Rendering Split by Complexity

The ASCII/simple-text path must stay fast.

Use this mental model:

    ASCII/simple text
      -> fast run builder
      -> cached font
      -> grouped draw

    Unicode fallback / clusters
      -> slower isolated path
      -> TextLayout or equivalent
      -> no pollution of ASCII path

Font fallback must be cached.

Resolve text by runs where possible, not independently for every cell.

Complex clusters may use slower APIs, but they must not force the whole renderer
onto the slow path.

## 6. Snapshot State Before Rendering

Rendering must consume immutable state.

Before painting or off-EDT rendering, state should already be snapshotted:

- terminal fonts
- cell metrics
- resolved palette
- text rendering hints
- HiDPI scale
- cursor settings
- selection colors

Do not read live theme, font, scale, metric, or palette state while painting.

A renderer must not observe half-applied theme changes.

## 7. Validate Metrics

Cell metrics must be explicit and frozen together.

Required metrics:

- cell width
- cell height
- baseline
- underline position
- strikethrough position
- overline position
- cursor stroke width

Do not compute font metrics ad hoc during row painting.

When changing metrics, check resize math and cursor positioning.

## 8. Validate Color Semantics

Color resolution should use packed ARGB values in hot paths.

The color model must define:

- default foreground/background
- ANSI 16 colors
- indexed 256-color palette
- truecolor RGB
- bold-as-bright behavior
- faint/dim behavior
- inverse
- conceal
- selection foreground/background
- cursor foreground/background

When adding OSC dynamic color support, route palette changes through the same
snapshot/publish mechanism as theme changes.

## 9. Respect Swing Threading

Swing component state belongs to the EDT.

Off-EDT rendering is allowed only with strict ownership rules:

- publish immutable frame or strip snapshots
- never mutate images or buffers currently visible to the EDT
- use double buffering or equivalent ownership transfer
- do not touch Swing component state off the EDT
- snapshot settings before worker rendering starts

If a change introduces worker rendering, review publication and ownership before
reviewing performance.

## 10. Keep Host Integration Thin

Standalone integration may provide:

- `JFrame` assembly
- local config/settings provider
- system clipboard adapter
- system font fallback adapter
- PTY-backed session startup
- app/window lifecycle

IntelliJ integration may provide:

- tool window assembly
- IntelliJ settings provider
- IntelliJ font/color/theme adapter
- `CopyPasteManager` clipboard adapter
- IntelliJ actions and toolbar wiring
- disposal/lifecycle integration
- IntelliJ scrollbar/search/chrome integration

Neither host module should fork or duplicate Swing renderer logic.

## 11. Review Input and Selection Carefully

Keyboard handling must send terminal intent through the session/input boundary.

Mouse handling must keep selection state UI-local unless terminal mouse protocol
events are intentionally encoded and sent to the session.

Selection should avoid allocations during drag.

Selection painting should operate by row ranges where possible.

Cursor blinking should not repaint terminal content unless content actually
changed.

## 12. Test Deterministically

Prefer render-frame replay and model tests before live PTY or IDE tests.

Useful tests:

- render-frame replay without a live PTY
- dirty-row repaint behavior
- viewport offset math
- selection range math
- keyboard translation through the session/input boundary
- mouse selection behavior
- resize-to-columns/rows calculation
- color resolution
- inverse/faint/conceal/selection interactions
- cursor shape and repaint bounds
- settings snapshot rebuild behavior
- render-cache consumption

Avoid requiring a real PTY or IntelliJ runtime for reusable Swing UI tests.

## 13. Final Review Checklist

Before accepting a UI/rendering change, verify:

- module ownership is correct
- no forbidden dependencies were added
- rendering remains run-based
- ASCII/simple-text path remains fast
- complex text fallback is isolated
- no per-cell allocation was introduced
- settings, metrics, and palette are snapshotted
- Swing state stays on the EDT
- off-EDT buffers, if any, have clear ownership
- host integration remains adapter-only
- deterministic tests cover the behavior