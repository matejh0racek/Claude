#!/bin/sh
# Refresh plugins/design-md from VoltAgent/awesome-design-md and regenerate
# the catalog in its SKILL.md. Only each site's DESIGN.md is kept.
set -eu

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
skill="$root/plugins/design-md/skills/design-md"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

git clone -q --depth 1 https://github.com/VoltAgent/awesome-design-md.git "$work/src"
rm -rf "$skill/designs"
mkdir -p "$skill/designs"
for f in "$work/src"/design-md/*/DESIGN.md; do
  name=$(basename "$(dirname "$f")")
  mkdir -p "$skill/designs/$name"
  cp "$f" "$skill/designs/$name/DESIGN.md"
done
cp "$work/src/LICENSE" "$skill/LICENSE"
commit=$(git -C "$work/src" rev-parse --short HEAD)

# Rewrite the catalog between the markers in SKILL.md.
python3 - "$skill" "$commit" <<'PY'
import os, re, sys
skill, commit = sys.argv[1], sys.argv[2]
names = sorted(os.listdir(os.path.join(skill, "designs")))
catalog = (f"<!-- catalog:start (VoltAgent/awesome-design-md@{commit}, {len(names)} designs) -->\n"
           + ", ".join(f"`{n}`" for n in names) + "\n<!-- catalog:end -->")
path = os.path.join(skill, "SKILL.md")
text = open(path).read()
text = re.sub(r"<!-- catalog:start.*?<!-- catalog:end -->", catalog, text, flags=re.S)
open(path, "w").write(text)
print(f"design-md: {len(names)} designs from awesome-design-md@{commit}")
PY
