# cs-knowledge-base

Obsidian vault for CS notes, synced across devices with Git. No paid sync service.

- **Remote:** `https://github.com/zekkrron/cs-knowledge-base.git`
- **Branch:** `main`
- **Sync model:** Git is the only sync mechanism. Obsidian Sync and iCloud are deliberately not used.

---

## The one rule

**Work on one device at a time. Pull before you start, push before you stop.**

Git cannot merge two versions of a drawing. Every painful failure in this setup comes
from editing on two devices without syncing in between. Everything else is just setup.

---

## Windows / PC setup

### 1. Install Git

Download from [git-scm.com](https://git-scm.com/download/win) and install with the defaults.

Then verify it's reachable from a terminal:

```powershell
git --version
```

If you get `'git' is not recognized`, Git installed but isn't on your PATH. Fix it once:

```powershell
$gitDir = "C:\Program Files\Git\cmd"
$old = [Environment]::GetEnvironmentVariable("Path","User")
[Environment]::SetEnvironmentVariable("Path", $old.TrimEnd(';') + ';' + $gitDir, "User")
```

Open a **new** terminal afterwards — existing ones keep the old environment.

### 2. Set your Git identity

Must match your other devices so commit history stays consistent:

```powershell
git config --global user.name "Akash Singh"
git config --global user.email "akash.singh13503@gmail.com"
```

### 3. Clone the vault

```powershell
git clone https://github.com/zekkrron/cs-knowledge-base.git C:\Vaults\cs-knowledge-base
```

First push or pull will prompt for GitHub sign-in. Git Credential Manager ships with
Git for Windows and opens a browser window; after that, credentials are cached.
If you're ever asked for a password in the terminal, use a
[personal access token](https://github.com/settings/tokens) instead — GitHub no longer
accepts account passwords for Git operations.

### 4. Open it in Obsidian

Install [Obsidian](https://obsidian.md/download), then **Open folder as vault** and select
`C:\Vaults\cs-knowledge-base`.

When prompted about **Obsidian Sync**, skip it. Git handles syncing here.

### 5. Install plugins

Plugin code is not tracked by Git (see [What isn't synced](#what-isnt-synced)), so install
plugins on each device:

1. **Settings → Community plugins → Turn on community plugins**
2. **Browse** → search → **Install** → **Enable**

Plugins currently in use are listed in `.obsidian/community-plugins.json`. At minimum:

- **Excalidraw** (by zsviczian) — freehand drawing and diagrams

### 6. Daily workflow

```powershell
cd C:\Vaults\cs-knowledge-base
git pull                          # before you start working
# ... take notes ...
git add .
git commit -m "notes"
git push                          # before you stop
```

---

## iPad setup

The Obsidian **Git plugin does not work reliably on iOS** — it uses a JavaScript Git
implementation that iOS kills on memory limits, causing crashes on clone and pull.
Do not install it. Use the **GitSync** app instead, which has a native core.

### 1. Install the apps

Install **Obsidian** and **GitSync** from the App Store. GitSync's free tier covers
everything needed here: one repo, pull, push, and Shortcuts triggers.

### 2. Create an empty vault

1. Open Obsidian → **Create new vault** → name it `cs-knowledge-base`
2. **Do not enable "Store in iCloud."** iCloud plus Git produces conflicts that are
   miserable to untangle.
3. Skip the **Obsidian Sync** prompt.
4. Create a throwaway note so the folder is easy to locate in step 4.

### 3. Connect GitSync to GitHub

1. Open GitSync → authenticate with **GitHub** via OAuth
2. Enter author details matching your PC: `Akash Singh` /
   `akash.singh13503@gmail.com`

### 4. Clone into the vault folder

1. **Clone** → select `cs-knowledge-base` from the repo list
2. Browse to the Obsidian vault folder from step 2
3. Choose **overwrite** when warned about existing contents — that's just the
   throwaway note

Reopen Obsidian. Seeing your notes means it worked.

### 5. Turn off Scribble

iPadOS converts Apple Pencil handwriting into typed text by default, which breaks
freehand drawing. Disable it in the iPad's **Settings app → Apple Pencil → Scribble**.

### 6. Install plugins

Same as the PC: **Settings → Community plugins → Turn on community plugins → Browse**.
Install Excalidraw here too.

### 7. Automate syncing (optional but recommended)

This makes the "pull before, push after" rule automatic:

1. **Shortcuts** app → **Library** → **+** → find **GitSync** → add **Sync Now**
2. **Automation** tab → **+** → **App** → select **Obsidian**
3. Tick both **Is Opened** and **Is Closed**
4. Set **Run Immediately**, action **Sync Now**, and save

Opening Obsidian now pulls; closing it pushes.

### 8. Manual syncing

Always available, and worth using as a fallback since iOS occasionally skips background
automations. Open GitSync and tap **Sync** (pull then push), or use its home screen
widget. `Fetch`, `Pull`, and `Push` are available separately.

Nothing inside Obsidian itself syncs — Obsidian has no awareness of Git.

---

## What isn't synced

See `.gitignore`. Deliberate exclusions:

| Path | Why |
|---|---|
| `.obsidian/workspace.json` | Per-device UI state (open panes, cursor position). Rewritten constantly and a top source of pointless conflicts. |
| `.obsidian/plugins/` | Plugin program code. Multi-megabyte bundles that would grow repo history on every update. Install per device instead. |
| `.trash/` | Obsidian's soft-delete folder. |

**Your content is always tracked.** Notes and Excalidraw drawings
(`*.excalidraw.md`) are ordinary vault files and are fully versioned. Excluding
`.obsidian/plugins/` excludes the app, never the artwork.

---

## Troubleshooting

**`'git' is not recognized` on Windows** — Git isn't on your PATH. See PC step 1, and
remember to open a new terminal.

**Merge conflict after editing two devices** — Git wrote conflict markers into the
affected files. For notes, open and resolve them by hand. For drawings, the JSON is
stroke data and not realistically editable, so pick one version:

```powershell
git checkout --ours "path/to/drawing.excalidraw.md"    # keep local
git checkout --theirs "path/to/drawing.excalidraw.md"  # keep remote
git add . ; git commit
```

Avoid this by respecting [the one rule](#the-one-rule).

**iPad changes aren't on GitHub** — the background automation was likely skipped by
iOS. Open GitSync and tap Sync manually.

**Drawings won't render on a device** — Excalidraw isn't installed there. Plugin code
isn't synced by design; install it via Community plugins.
