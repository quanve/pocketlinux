---
name: USB share
description: Manage the PHONELAB USB flash drive: mount the image, copy files, export the drive to a PC, import it back, and check status. Use when files must move between the phone and a PC without adb or a network.
---

# USB share

The phone exposes the FAT32 image `/data/linux/share.img` (label `PHONELAB`) as
a normal USB flash drive. A PC sees it without adb, drivers, or networking.
Inside Debian, use the `share` command (root is required; the agent already runs
as root).

## Commands

- `share status` — image, state (`local` / `exported` / `none`), and mount point.
- `share local` — mount the image for the phone and the agent at `/mnt/share`.
- `share sync [DIR]` — copy `DIR` (default `/root/work`) into the image.
  The image must be mounted locally.
- `share export [DIR]` — mount and copy `DIR` if needed, then unmount the image
  and expose it to the PC as a USB drive. adb and file transfer (MTP) are
  disabled while the drive is exported.
- `share import` — take the drive back from the PC and mount the image again.
  USB returns to the mode it had before export (usually MTP + adb).
- `share off` — unmount the image locally.
- `share init [MB]` — create the image (default 4096 MB) only if it does not exist.

## Workflow

1. Always start with `share status`.
2. If the state is `exported`, the drive belongs to the PC. Do not write to
   `/mnt/share`; run `share import` first.
3. Put results in the workspace and copy them into the image:
   `share sync /root/work/study/<...>`, or write directly to `/mnt/share`
   after `share local`.
4. To hand files to the user on a PC: `share export <folder>`.
   Tell the user that the `PHONELAB` drive is connected and can be copied.
5. When the user says copying is finished: `share import`, then check
   `share status`. Files written from the PC appear in `/mnt/share` after import.
6. `share init` never overwrites an existing image. Do not call it without need.

## Important

- While the drive is exported, do not run HID typing and do not export again:
  HID and mass storage rebuild the same USB gadget.
- `export`/`import` disable and restore adb and MTP; this is normal.
  After `import` the phone is back in File transfer mode.
- Only one side may write to the image at a time: while `exported` it is the PC,
  after `import` it is the phone.
- If a command fails, show its output and `share status`; do not repeat
  `export` many times.
- Files on the drive are flat: `share sync DIR` copies the contents of `DIR`
  into the drive root.
