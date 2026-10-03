# Claude

Matej's Claude skills, packaged as a Claude Code plugin marketplace (`matej-skills`).

| Plugin | What's in it | Upstream |
| --- | --- | --- |
| `taste-skill` | 13 frontend design skills: `design-taste-frontend` (main), minimalist, brutalist, high-end visual design, redesign, image-to-code, brandkit, image-gen, and more | [Leonxlnx/taste-skill](https://github.com/Leonxlnx/taste-skill) |
| `impeccable` | `/impeccable` (audit, critique, polish, …), 4 helper agents, and design-detector hooks | [pbakaus/impeccable](https://github.com/pbakaus/impeccable) |
| `playwright-cli` | Browser automation: open pages, click, fill, snapshot, screenshot, trace, mock requests, and generate Playwright tests. Needs the CLI: `npm install -g @playwright/cli` | [microsoft/playwright-cli](https://github.com/microsoft/playwright-cli) |

`taste-skill` and `impeccable` are pulled straight from their authors' repos, so `claude plugin marketplace update` gets their latest versions. Playwright doesn't publish a Claude Code plugin, so its skill lives in `plugins/playwright-cli/`. Refresh it with `scripts/update-playwright-cli.sh`.

## Use the skills everywhere

### Claude Code on your computer: once, for all projects

```sh
claude plugin marketplace add matejh0racek/Claude
claude plugin install taste-skill@matej-skills
claude plugin install impeccable@matej-skills
claude plugin install playwright-cli@matej-skills
npm install -g @playwright/cli   # the browser tool the playwright-cli skill drives
```

You can also run `/plugin` inside Claude Code and pick them from the menu. To update later, run `claude plugin marketplace update matej-skills`.

### Claude Code cloud sessions (claude.ai/code), for any repo

Cloud sessions start from a fresh container, so add the same commands to your environment's **setup script**. You'll find it in the environment menu in the session title bar, under **Edit → Setup script**:

```sh
claude plugin marketplace add matejh0racek/Claude \
  && claude plugin install taste-skill@matej-skills \
  && claude plugin install impeccable@matej-skills \
  && claude plugin install playwright-cli@matej-skills \
  && npm install -g @playwright/cli
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
