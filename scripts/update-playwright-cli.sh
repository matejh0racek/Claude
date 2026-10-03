#!/bin/sh
# Refresh plugins/playwright-cli from the latest @playwright/cli release.
# Playwright CLI ships its skill via `playwright-cli install --skills` rather
# than as a Claude Code plugin, so we generate it and commit the result here.
set -eu

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
dest="$root/plugins/playwright-cli/skills/playwright-cli"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

(cd "$work" && npx -y @playwright/cli@latest install --skills >/dev/null)
rm -rf "$dest"
mkdir -p "$(dirname "$dest")"
cp -R "$work/.claude/skills/playwright-cli" "$dest"

version=$(npm view @playwright/cli version)
python3 - "$root/plugins/playwright-cli/.claude-plugin/plugin.json" "$version" <<'PY'
import json, sys
path, version = sys.argv[1], sys.argv[2]
data = json.load(open(path))
data["version"] = version
open(path, "w").write(json.dumps(data, indent=2) + "\n")
PY
echo "playwright-cli skill updated to @playwright/cli $version"
