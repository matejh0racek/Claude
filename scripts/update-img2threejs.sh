#!/bin/sh
# Refresh plugins/img2threejs from the original hoainho/img2threejs repo
# (the skill lives at that repo's root; it doesn't publish a Claude Code plugin).
set -eu

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
dest="$root/plugins/img2threejs/skills/img2threejs"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

git clone -q --depth 1 https://github.com/hoainho/img2threejs.git "$work/src"
rm -rf "$work/src/.git" "$work/src/.github"
rm -rf "$dest"
mkdir -p "$(dirname "$dest")"
cp -R "$work/src" "$dest"

version=$(awk '/^version:/{print $2; exit}' "$dest/SKILL.md")
python3 - "$root/plugins/img2threejs/.claude-plugin/plugin.json" "${version:-0.0.0}" <<'PY'
import json, sys
path, version = sys.argv[1], sys.argv[2]
data = json.load(open(path))
data["version"] = version
open(path, "w").write(json.dumps(data, indent=2) + "\n")
PY
echo "img2threejs updated to $version"
