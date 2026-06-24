#!/usr/bin/env bash
# Rewrites every Icons.<style>.<Name> call site to AppIcons.<id> using the
# rewrite-map produced by gen.sh, then fixes imports: drops material.icons
# imports and adds the io.grimoire.app.ui.icon wildcard where AppIcons is used.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SRC="$ROOT/app/src/main/java"
MAP="$ROOT/tools/icongen/rewrite-map.tsv"

# 1) Replace references (longest refs first so AutoMirrored.Filled.X matches before any prefix).
while IFS=$'\t' read -r ref id; do
  [ -z "$ref" ] && continue
  esc="Icons\\.$(printf '%s' "$ref" | sed 's/\./\\./g')"
  grep -rlZ --include='*.kt' -E "$esc"'\b' "$SRC" 2>/dev/null \
    | xargs -0 -r sed -i -E "s/${esc}\\b/AppIcons.${id}/g"
done < <(awk '{print length($1), $0}' "$MAP" | sort -rn | cut -d' ' -f2-)

# 2) Fix imports in every file that now references AppIcons (excluding the registry itself).
grep -rlZ --include='*.kt' 'AppIcons\.' "$SRC" \
  | while IFS= read -r -d '' f; do
      case "$f" in */io/grimoire/app/ui/icon/*) continue;; esac
      # Drop all Material Icons imports (Icons object + filled/outlined/automirrored glyphs).
      sed -i -E '/^import androidx\.compose\.material\.icons\./d' "$f"
      # Add the registry wildcard import once, before the first existing import.
      if ! grep -q '^import io\.grimoire\.app\.ui\.icon\.\*$' "$f"; then
        sed -i '0,/^import /s//import io.grimoire.app.ui.icon.*\n&/' "$f"
      fi
    done

echo "rewrite complete"
