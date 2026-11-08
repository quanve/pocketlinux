---
name: Example plugin
description: Template skill for a phone-lab plugin. Copy the example plugin directory, rename it, and edit the run script and this skill.
---

# Example plugin

The `example` plugin is the smallest possible phone-lab plugin. It exists to
show the file layout and the dispatch rules.

## Layout

```text
plugins/<name>/
├── plugin.conf          # NAME and DESCRIPTION
├── run                  # executable; receives all arguments after `pl <name>`
├── install.sh           # optional; runs inside the Debian chroot
└── skill/SKILL.md       # optional; installed as an opencode skill
```

## Usage

```sh
pl example hello
```

## Adding your own plugin

1. Copy `plugins/example` to `plugins/<name>`.
2. Set `NAME` and `DESCRIPTION` in `plugin.conf`.
3. Edit `run`. It can be any executable script; use `/bin/sh`, bash, Python,
   or a compiled binary.
4. Add `skill/SKILL.md` if the opencode agent should discover the plugin.
5. Run `./install.sh` again to deploy the plugin into the chroot.

Plugin commands are installed under `/usr/local/lib/phone-lab/plugins/<name>`
inside the chroot. The `pl` dispatcher runs `run` with the remaining arguments.
