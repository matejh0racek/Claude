#!/bin/sh
# Build one upload-ready ZIP per skill for the Claude app
# (Settings -> Capabilities -> Skills -> Upload skill).
#
# Reads the plugin list from .claude-plugin/marketplace.json, fetches each
# plugin's current upstream source, and zips every skills/<name>/ folder.
#
# Usage: scripts/build-app-zips.sh [output-dir]   (default: ./dist/app-skills)
set -eu

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
out=${1:-"$root/dist/app-skills"}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
mkdir -p "$out"
out=$(CDPATH= cd -- "$out" && pwd)

# Emit "name<TAB>git-url<TAB>subdir" for each plugin in the marketplace.
python3 - "$root/.claude-plugin/marketplace.json" > "$work/plugins.tsv" <<'PY'
import json, sys
for p in json.load(open(sys.argv[1]))["plugins"]:
    s = p["source"]
    if isinstance(s, str):
        print(f"{p['name']}\tlocal\t{s}")
    elif s["source"] == "github":
        print(f"{p['name']}\thttps://github.com/{s['repo']}.git\t.")
    elif s["source"] in ("git-subdir", "url"):
        print(f"{p['name']}\t{s['url']}\t{s.get('path', '.')}")
PY

while IFS="$(printf '\t')" read -r name url sub; do
  if [ "$url" = local ]; then
    src="$root/$sub"
  else
    git clone -q --depth 1 "$url" "$work/$name"
    src="$work/$name/$sub"
  fi
  for skill in "$src"/skills/*/; do
    [ -f "$skill/SKILL.md" ] || continue
    # Name the folder and ZIP after the frontmatter `name:`, which can differ
    # from the upstream folder name (e.g. brutalist-skill -> industrial-brutalist-ui).
    skill_name=$(awk '/^name:/{sub(/^name:[ \t]*/,""); gsub(/["\047\r]/,""); print; exit}' "$skill/SKILL.md")
    [ -n "$skill_name" ] || skill_name=$(basename "$skill")
    mkdir -p "$work/stage"
    cp -R "$skill" "$work/stage/$skill_name"
    (cd "$work/stage" && zip -qr "$out/$skill_name.zip" "$skill_name" -x '*/bin/*')
    rm -rf "$work/stage/$skill_name"
    echo "$name: $out/$skill_name.zip"
  done
done < "$work/plugins.tsv"
