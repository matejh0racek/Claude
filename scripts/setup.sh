#!/bin/sh
# One-step setup: add (or refresh) the matej-skills marketplace, install every
# plugin it lists, and install the playwright-cli command the playwright-cli
# skill drives. Safe to re-run; it also updates what's already installed.
#
#   curl -fsSL https://raw.githubusercontent.com/matejh0racek/Claude/main/scripts/setup.sh | sh
set -eu

marketplace=matej-skills
repo=matejh0racek/Claude

if ! command -v claude >/dev/null 2>&1; then
  echo "setup: the 'claude' command was not found. Install Claude Code first: https://claude.com/claude-code" >&2
  exit 1
fi

if claude plugin marketplace list 2>/dev/null | grep -q "$marketplace"; then
  claude plugin marketplace update "$marketplace"
else
  claude plugin marketplace add "$repo"
fi

# Read plugin names from the marketplace manifest.
manifest_url="https://raw.githubusercontent.com/$repo/main/.claude-plugin/marketplace.json"
if command -v node >/dev/null 2>&1; then
  plugins=$(curl -fsSL "$manifest_url" | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>console.log(JSON.parse(s).plugins.map(p=>p.name).join(" ")))')
else
  plugins=$(curl -fsSL "$manifest_url" | python3 -c 'import json,sys; print(" ".join(p["name"] for p in json.load(sys.stdin)["plugins"]))')
fi

installed=$(claude plugin list 2>/dev/null || true)
for plugin in $plugins; do
  if printf '%s\n' "$installed" | grep -q "$plugin@$marketplace"; then
    claude plugin update "$plugin@$marketplace"
  else
    claude plugin install "$plugin@$marketplace"
  fi
done

if command -v npm >/dev/null 2>&1; then
  npm install -g @playwright/cli \
    || echo "setup: couldn't install @playwright/cli globally; run 'sudo npm install -g @playwright/cli' yourself." >&2
else
  echo "setup: npm not found, so the playwright-cli command wasn't installed (the other skills work without it)." >&2
fi

echo "setup: done. Installed: $plugins"
