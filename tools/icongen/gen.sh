#!/usr/bin/env bash
# Fetches each Material Symbols (Outlined) icon used in the app as a ready-made
# Compose ImageVector .kt from Google's render endpoint, rewrites it into an
# AppIcons extension property under ui/icon/, and emits a rewrite-map for the
# call-site migration. Run from anywhere: tools/icongen/gen.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SRC="$ROOT/app/src/main/java"
OUT="$SRC/io/grimoire/app/ui/icon"
MAP="$ROOT/tools/icongen/rewrite-map.tsv"
BASE="https://fonts.gstatic.com/render/v1/Material+Symbols+Outlined/24dp"

mkdir -p "$OUT"
: > "$MAP"

# Symbol-name overrides where snake_case(Name) != the Material Symbols id.
declare -A SYM=(
  [HelpOutline]=help [ErrorOutline]=error [WarningAmber]=warning
  [BookmarkBorder]=bookmark [Inventory2]=inventory_2
)
# Ids rendered filled (FILL=1) because the fill conveys state/emphasis.
declare -A FILL=( [Bookmark]=1 [Notifications]=1 [Star]=1 [CheckCircle]=1 [PushPin]=1 )
# Base names that must auto-mirror in RTL (plus all AutoMirrored.* refs).
declare -A MIRROR=(
  [ArrowBack]=1 [ArrowForward]=1 [ArrowForwardIos]=1 [KeyboardArrowRight]=1
  [Label]=1 [MenuBook]=1 [NavigateBefore]=1 [NavigateNext]=1 [OpenInNew]=1 [List]=1
)

snake() { echo "$1" | sed -E 's/([A-Z])/_\l\1/g; s/^_//'; }

declare -A DONE=()
fail=0

refs=$(grep -rhoE "Icons\.(Default|Filled|Outlined|AutoMirrored\.(Filled|Outlined))\.[A-Za-z0-9]+" "$SRC" \
  | sed -E 's/^Icons\.//' | sort -u)

while read -r ref; do
  [ -z "$ref" ] && continue
  name="${ref##*.}"
  style="${ref%.*}"
  id="$name"
  # Disambiguate outlined variants of icons that also appear filled elsewhere.
  if [ "$style" = "Outlined" ] && { [ "$name" = CheckCircle ] || [ "$name" = Edit ] || [ "$name" = Image ]; }; then
    id="${name}Outlined"
  fi
  printf '%s\t%s\n' "$ref" "$id" >> "$MAP"

  [ -n "${DONE[$id]:-}" ] && continue
  DONE[$id]=1

  sym="${SYM[$name]:-$(snake "$name")}"
  fill=0; [ "${FILL[$id]:-0}" = 1 ] && fill=1
  mir=0; case "$style" in AutoMirrored*) mir=1;; esac; [ "${MIRROR[$name]:-0}" = 1 ] && mir=1

  url="${BASE}/${sym}.kt?var=opsz,wght,FILL,GRAD,ROND@24,400,${fill},0,0"
  code=$(curl -sS --compressed -L -o "$OUT/$id.kt.tmp" -w "%{http_code}" "$url" || echo 000)
  if [ "$code" != "200" ]; then
    echo "FAIL  $id  (symbol=$sym, http=$code)" >&2
    rm -f "$OUT/$id.kt.tmp"; fail=1; continue
  fi

  V=$(grep -oE 'public val [A-Za-z0-9_]+:' "$OUT/$id.kt.tmp" | head -1 | sed -E 's/public val ([A-Za-z0-9_]+):/\1/')
  sed -i -E \
    -e 's/^package .*/package io.grimoire.app.ui.icon/' \
    -e "s/\\bpublic val ${V}:/public val AppIcons.${id}:/" \
    -e "s/\\b_${V}\\b/_${id}/g" \
    "$OUT/$id.kt.tmp"
  [ "$mir" = 1 ] && sed -i -E 's/(viewportHeight = 24f,)/\1\n          autoMirror = true,/' "$OUT/$id.kt.tmp"
  mv "$OUT/$id.kt.tmp" "$OUT/$id.kt"
  echo "OK    $id  <- $sym  fill=$fill mir=$mir"
done <<< "$refs"

exit $fail
