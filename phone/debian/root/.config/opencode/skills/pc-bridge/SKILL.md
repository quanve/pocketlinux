---
name: PC bridge
description: Check and use the PC bridge: pcsh status, run commands on Linux or Windows, bootstrap strings, and USB HID connection. Use when diagnosing or setting up a PC from the phone.
---

# PC bridge

`pcsh` runs commands on a PC that connected to the phone over the shared
network (the bridge listens on `192.168.0.3:4444`). Every command starts a new
process: `cd`, variables, and stdin do not persist between calls.

## Commands

- `pcsh status` — whether the bridge is running, the PC OS, hostname, and IP.
- `pcsh "command"` — run a command and return `rc` plus output
  (Linux: bash; Windows: PowerShell).
- `pcsh -t 30 "command"` — same with a timeout in seconds.
- `pcsh --bootstrap linux` / `pcsh --bootstrap windows` — print the one-line
  bootstrap command that the phone types on the PC over USB HID.

## If the PC is not connected

1. `pcsh status`.
2. Ask the user to open **Agent → PC** and press:
   - **TERMINAL + TYPE** for Linux;
   - **WIN+R + TYPE** for Windows.
   The phone types the bootstrap over USB HID and the PC connects back.
3. Run `pcsh status` again.

HID typing rebuilds the USB gadget, so adb disappears for a few seconds. This
is normal.

## Rules

- Windows: use `cd C:\; ...`, full paths, and `Get-*`/`Test-*`; do not rely on
  interactive programs.
- Linux: normal bash, but every command runs in a new process.
- Do not start programs that wait for stdin.
- Output is limited to 256 KiB. For large files use `tail`, `grep`,
  `Get-Content -Tail`, or `Select-String`.
- Destructive commands (`rm -rf`, `mkfs`, `dd`, partition or registry edits)
  require explicit user confirmation.
- If the PC is not on the same network, the bridge cannot help: use the USB
  share (skill `usb-share`) or HID.
- PC files are also available through `/mnt/pc` when it is mounted (SSHFS);
  this is faster than moving data through `pcsh`.
