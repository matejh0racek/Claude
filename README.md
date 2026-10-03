# Claude

Matej's Claude skills, packaged as a Claude Code plugin marketplace (`matej-skills`).

| Plugin | What's in it | Upstream |
| --- | --- | --- |
| `taste-skill` | 13 frontend design skills: `design-taste-frontend` (main), minimalist, brutalist, high-end visual design, redesign, image-to-code, brandkit, image-gen, and more | [Leonxlnx/taste-skill](https://github.com/Leonxlnx/taste-skill) |
| `impeccable` | `/impeccable` (audit, critique, polish, …), 4 helper agents, and design-detector hooks | [pbakaus/impeccable](https://github.com/pbakaus/impeccable) |
| `playwright-cli` | Browser automation: open pages, click, fill, snapshot, screenshot, trace, mock requests, and generate Playwright tests. Needs the CLI: `npm install -g @playwright/cli` | [microsoft/playwright-cli](https://github.com/microsoft/playwright-cli) |
| `design-md` | 74 ready-made DESIGN.md design systems (Stripe, Linear, Vercel, Apple, Notion, …). Say "make it look like Linear" and Claude drops the matching DESIGN.md into your project and builds from it | [VoltAgent/awesome-design-md](https://github.com/VoltAgent/awesome-design-md) |

`taste-skill` and `impeccable` are pulled straight from their authors' repos, so `claude plugin marketplace update` gets their latest versions. Playwright and awesome-design-md don't publish Claude Code plugins, so they're packaged here in `plugins/playwright-cli/` and `plugins/design-md/`. Refresh them with `scripts/update-playwright-cli.sh` and `scripts/update-design-md.sh`.

## Use the skills everywhere

### Claude Code on your computer: once, for all projects

```sh
curl -fsSL https://raw.githubusercontent.com/matejh0racek/Claude/main/scripts/setup.sh | sh
```

[`scripts/setup.sh`](scripts/setup.sh) adds this marketplace, installs every plugin in it, and installs the `playwright-cli` command. Run the same line again any time to update everything, including plugins added to the marketplace later. Restart Claude Code afterwards.

### Claude Code cloud sessions (claude.ai/code), for any repo

Cloud sessions start from a fresh container. Paste the same line into your environment's **setup script**, which you'll find in the environment menu in the session title bar, under **Edit → Setup script**:

```sh
curl -fsSL https://raw.githubusercontent.com/matejh0racek/Claude/main/scripts/setup.sh | sh
```

`playwright-cli` opens Google Chrome by default, which cloud containers don't have. To use their preinstalled Chromium instead, add these under **Environment variables** in the same Edit screen:

```
PLAYWRIGHT_MCP_BROWSER=chromium
PLAYWRIGHT_MCP_EXECUTABLE_PATH=/opt/pw-browsers/chromium
```

If the setup script ever can't reach this repo, install from the upstream marketplaces directly:

```sh
claude plugin marketplace add Leonxlnx/taste-skill && claude plugin install taste-skill@taste-skill
claude plugin marketplace add pbakaus/impeccable && claude plugin install impeccable@impeccable
```

Sessions on *this* repo also pick the plugins up from `.claude/settings.json`.

### Claude app chats (web, desktop, mobile)

The app takes skills as ZIP uploads:

```sh
scripts/build-app-zips.sh      # writes dist/app-skills/<skill>.zip
```

Upload each ZIP in **Settings → Capabilities → Skills → Upload skill** (code execution must be on). Re-run the script and re-upload whenever you want newer versions. Impeccable's hooks and helper agents only work in Claude Code, so in the app you get the skill on its own.

## Adding your own skills later

Create `plugins/<name>/.claude-plugin/plugin.json` and `plugins/<name>/skills/<skill>/SKILL.md`. Then add `{"name": "<name>", "source": "./plugins/<name>"}` to `.claude-plugin/marketplace.json`. The ZIP script picks local plugins up automatically.
