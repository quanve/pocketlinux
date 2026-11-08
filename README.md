# phone-lab

Turn an Android phone into a pocket Linux workstation with a Debian chroot, an
opencode agent, USB HID keyboard/mouse, a PC bridge, and a USB flash-drive mode.

The phone stays an ordinary Android device. Everything is installed as files and
Magisk modules; the ROM and the vendor partition are not modified.

## What it does

- **Debian 13 arm64 chroot** as the primary Linux environment.
- **opencode v2** server and Android client for agent sessions.
- **PC bridge**: run commands on a Linux or Windows PC from the phone over the
  shared network; bootstrap it with USB HID when there is no network.
- **USB HID**: type text and move the mouse on a connected PC.
- **USB share**: expose a FAT32 image as a normal `PHONELAB` flash drive, with
  MTP and adb restored automatically on import.
- **zapret control** for a separately installed zapret Magisk module.
- **Plugins**: add a command, an agent skill, or a small feature without editing
  the core.

## Requirements

- Phone: realme RMX3834 (realme Note 50) with LineageOS 23.x GSI, Android 16,
  Magisk root, and Termux.
- PC: Linux with `adb`, `bash`, `tar`, and SSH; or Windows with `adb` and
  PowerShell 5.1+ for the flash kit.
- Build host for the Android app: JDK 21, Android SDK 35, Gradle 8.11.
- Build host for the Debian rootfs: `debootstrap`, `tar`, and network access.
- Exactly one phone connected over USB while installing. `pc/check-device.sh`
  refuses to touch any other device.

## Quick start

```sh
git clone https://github.com/quanve/pocketlinux.git phone-lab
cd phone-lab
./install.sh
```

`./install.sh` installs the Debian chroot, USB HID helpers, the zapret wrapper,
and the Android app. It builds the app first if the APK is missing.

Then connect the PC:

```sh
./pc/connect.sh
```

On the phone, open Termux:

```sh
linux
cd /mnt/pc
opencode
```

`./pc/disconnect.sh` tears the channel down.

## Installation components

```sh
./install.sh                  # Debian + HID + zapret wrapper + app
./install.sh debian app       # selected components
./install.sh pocket           # optional network/security tools
./install.sh --list
./install.sh --uninstall app
```

The same components can be managed directly:

```sh
./pc/install-module.sh debian-chroot
./pc/install-module.sh usb-hid
./pc/install-module.sh zapret-toggle
./pc/install-module.sh android-app
./pc/install-module.sh pocket-tools
./pc/uninstall-module.sh <name>
```

### Debian rootfs and opencode

`modules/debian-chroot/install.sh` expects prebuilt payloads:

```sh
./pc/build-debian-rootfs.sh
```

The opencode arm64 release is expected at
`$PL_STORAGE/build/opencode-linux-arm64.tar.gz` (`PL_STORAGE` defaults to
`/storage/phone-lab` or `~/Disk/phone-lab`). Override `PL_STORAGE` to use another
location.

### Android app

```sh
./pc/build-app.sh
./pc/install-app.sh
```

The debug APK is written to
`app/app/build/outputs/apk/debug/app-debug.apk`.

### zapret

`modules/zapret-toggle` installs only the `zapret` wrapper and a Termux widget
shortcut. The zapret Magisk module itself must be installed separately. After
that:

```sh
pl zapret on|off|status
```

## Daily use

Inside Termux or the Debian chroot:

```sh
pl status
pl zapret on|off|status
pl hid "hello"
pl pcmount user@host
pl pcumount
pl share status|local|sync [dir]|export [dir]|import|off
pl plugins
pl <plugin> [args]
```

The app exposes the same features as screens: Status, Agent, Zapret, USB HID,
PC channel, Terminal, and Output. The Agent screen talks to the local opencode
HTTP API on `127.0.0.1:4096`.

## PC bridge

`pc/connect.sh` sets up `adb reverse tcp:2222 tcp:22` and mounts the PC home
directory into the chroot with SSHFS. From the chroot, `pcsh` runs commands on
the PC:

```sh
pcsh status
pcsh "uname -a"
pcsh -t 30 "systemctl --failed"
```

For a PC without adb, use the same network:

```sh
pcmount user@PC_IP
```

The bridge is installed as a boot service and listens on the phone at
`192.168.0.3:4444` by default.

## USB share

The share is a 4 GB FAT32 image at `/data/linux/share.img`, label `PHONELAB`.
Only one side writes at a time:

```sh
share status
share export /root/work      # phone -> PC; MTP and adb turn off
share import                 # PC -> phone; MTP and adb return
```

The agent has the `usb-share` skill and can manage the drive itself.

## Plugins

A plugin is a directory under `plugins/`:

```text
plugins/<name>/
├── plugin.conf          # NAME and DESCRIPTION
├── run                  # executable; receives arguments after `pl <name>`
├── install.sh           # optional; runs inside the chroot
└── skill/SKILL.md       # optional; installed as an opencode skill
```

Example:

```sh
pl example hello
```

To add a feature:

1. Copy `plugins/example` to `plugins/<name>`.
2. Edit `plugin.conf` and `run`.
3. Add `skill/SKILL.md` if the agent should discover it.
4. Run `./install.sh debian` again to deploy plugins and skills.

Plugins are installed to `/usr/local/lib/phone-lab/plugins/<name>` inside the
chroot. Skills are installed to `/root/.config/opencode/skills/<name>`.

Private plugins can be kept out of git. Add the directory to `.gitignore`;
use `git add -f` only when you intend to publish it.

## Flash kit

The flash kit is a self-contained copy for a PC without git:

```sh
./pc/sync-flash.sh --payload
```

It regenerates `flash/` from the sources, refreshes `CHECKSUMS.txt`, and writes
`$PL_STORAGE/images/phone-lab-flash.zip`. The generated `flash/phone`,
`flash/termux`, `flash/plugins`, and payload files are not tracked in git.

Linux PC:

```sh
cd flash/pc/linux
./install-phone.sh
./connect-adb.sh
```

Windows PC:

```powershell
cd flash\pc\windows
powershell -ExecutionPolicy Bypass -File .\install-phone.ps1
```

## Repository layout

```text
app/              Android app (Kotlin + Jetpack Compose)
modules/          installable components
pc/               host-side scripts and PC bootstrap
phone/            scripts installed on the phone and inside Debian
plugins/          optional features and agent skills
flash/            generated offline kit
install.sh        unified installer
```

## Security

- The app receives permanent root in Magisk. Install it only on your own phone.
- Private keys (`pc/phone_lab_key`, `flash/keys/phone_lab_key`) are ignored by
  git. Never publish them.
- The opencode server binds to `127.0.0.1` and uses basic auth. The password is
  stored in `/data/linux/debian/root/.config/phone-lab/server-password`.
- The PC bridge listens on the local network. Use it only on trusted networks.
- Destructive agent actions require explicit confirmation.

## Hardware limits

- Wi-Fi `sprd-wlan`: no monitor mode or packet injection (scan/AP/P2P only).
- The kernel has no CIFS/NFS support; use FUSE/SSHFS instead.
- The USB gadget may rebind itself, so adb can disappear for a few seconds.
  Reconnect the cable or run `./pc/connect.sh` again.
- MTP notifications may be missing on some GSIs even when MTP works.

## License

MIT. See `LICENSE`.
