# Terminal Workspace Agent Guide

`jvterm-workspace` owns host-neutral terminal workspace state for products
that present one or more local terminal sessions.

## Responsibility

This module may:

- define terminal launch profiles and profile discovery policy.
- track open terminal tabs and selected tab identity.
- coordinate local PTY-backed session creation through `jvterm-pty`.
- coordinate SSH-backed session creation through `jvterm-ssh` using runtime
  credentials supplied by the product host.
- propagate host-neutral tab events such as title changes, bell, failures, and
  close notifications.

## Boundary

This module must not:

- import Swing, AWT, IntelliJ Platform APIs, or other UI toolkits.
- parse terminal protocols.
- mutate terminal core internals.
- encode keyboard, paste, focus, or mouse bytes directly.
- own PTY stream threads, SSH network threads, or transport implementation
  details.

PTY process lifecycle stays in `jvterm-pty`; SSH connection/channel lifecycle
stays in `jvterm-ssh`; session synchronization stays in `jvterm-session`; UI
modules adapt workspace state to visual containers.
