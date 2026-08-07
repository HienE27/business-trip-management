#!/usr/bin/env bash
# context-analysis.sh
# Discovery PR2 — Bước 3: Phân tích ngữ cảnh (docs, contracts, migrations)
#
# Mục đích:
#   - Liệt kê tài liệu hiện có trong docs/ để reviewer nắm bức tranh tổng thể.
#   - Phát hiện các bản migration DB gần đây (Flyway V*.sql).
#   - Tìm các file "spec/contract" có thể còn sót (SPEC.md, *_CONTRACT.md,
#     *_AUDIT.md, etc.) và đếm số dòng để ước lượng độ lớn.
#   - KHÔNG sửa source code. Chỉ xuất JSON ra stdout.
#
# Cách dùng:
#   ./scripts/discovery/context-analysis.sh [TARGET_ROOT]
#
# Đầu ra:
#   JSON object duy nhất in ra stdout.

set -u
set -o pipefail

TARGET_ROOT="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"

if [ ! -d "$TARGET_ROOT" ]; then
  echo "ERROR: TARGET_ROOT không tồn tại: $TARGET_ROOT" >&2
  exit 1
fi

cd "$TARGET_ROOT"

have_jq() { command -v jq >/dev/null 2>&1; }

GENERATED_AT="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

# ---------- docs inventory ----------
DOCS_ROOT="$TARGET_ROOT/docs"

# Tất cả file .md/.txt trong docs/ (đệ quy), trừ các file lớn (audit dumps > 50KB)
DOC_FILES_RAW="$(find "$DOCS_ROOT" -type f \
  \( -name '*.md' -o -name '*.txt' \) 2>/dev/null \
  | while read -r f; do
      size="$(wc -c < "$f" 2>/dev/null || echo 0)"
      rel="${f#$TARGET_ROOT/}"
      printf '%s\t%s\n' "$size" "$rel"
    done | LC_ALL=C sort -rn || true)"

# Tổng số file + tổng dung lượng
DOCS_COUNT="$(printf '%s\n' "$DOC_FILES_RAW" | grep -c . || echo 0)"
DOCS_TOTAL_BYTES="$(printf '%s\n' "$DOC_FILES_RAW" | awk -F'\t' '{s+=$1} END{print s+0}')"

# Top 10 file docs lớn nhất
DOCS_TOP_RAW="$(printf '%s\n' "$DOC_FILES_RAW" | head -n 10)"

# Phân loại docs theo nhóm
SPEC_FILES="$(find "$DOCS_ROOT" -type f -iname '*SPEC*' 2>/dev/null | sed "s|^$TARGET_ROOT/||" | LC_ALL=C sort || true)"
AUDIT_FILES="$(find "$DOCS_ROOT" -type f -iname '*AUDIT*' 2>/dev/null | sed "s|^$TARGET_ROOT/||" | LC_ALL=C sort || true)"
CONTRACT_FILES="$(find "$DOCS_ROOT" -type f -iname '*CONTRACT*' 2>/dev/null | sed "s|^$TARGET_ROOT/||" | LC_ALL=C sort || true)"
RELEASE_FILES="$(find "$DOCS_ROOT" -type f -iname 'RELEASE*' -o -iname 'RELEASE_NOTES*' 2>/dev/null | sed "s|^$TARGET_ROOT/||" | LC_ALL=C sort || true)"

# Tài liệu gần đây (sửa trong 30 ngày qua)
RECENT_DOCS_RAW="$(find "$DOCS_ROOT" -type f -mtime -30 \
  \( -name '*.md' -o -name '*.txt' \) 2>/dev/null \
  | sed "s|^$TARGET_ROOT/||" | LC_ALL=C sort || true)"

# ---------- DB migrations ----------
MIGRATIONS_RAW=""
if [ -d backend/src/main/resources/db/migration ]; then
  MIGRATIONS_RAW="$(ls -1 backend/src/main/resources/db/migration/ 2>/dev/null | LC_ALL=C sort -r | head -n 20)"
fi

# ---------- Tóm tắt theo subdir docs ----------
DOC_SUBDIRS_RAW=""
for d in "$DOCS_ROOT"/*/; do
  [ -d "$d" ] || continue
  name="$(basename "$d")"
  count="$(find "$d" -type f \( -name '*.md' -o -name '*.txt' \) 2>/dev/null | wc -l | tr -d ' ')"
  DOC_SUBDIRS_RAW="$DOC_SUBDIRS_RAW"$'\n'"$name"$'\t'"$count"
done
DOC_SUBDIRS_RAW="$(printf '%s' "$DOC_SUBDIRS_RAW" | sed '/^$/d')"

# ---------- Open API / contract files (heuristic) ----------
OPENAPI_FILE=""
for cand in "backend/src/main/resources/openapi.yaml" "backend/src/main/resources/openapi.yml" \
            "backend/src/main/resources/api.yaml" "backend/src/main/resources/api.yml" \
            "docs/openapi.yaml" "docs/openapi.yml"; do
  if [ -f "$cand" ]; then
    OPENAPI_FILE="$cand"
    break
  fi
done

# ---------- Output ----------
if have_jq; then
  DOCS_TOP_JSON="$(printf '%s\n' "$DOCS_TOP_RAW" | jq -R -s 'split("\n") | map(select(length>0) | capture("^(?<bytes>[0-9]+)\t(?<path>.+)$") | {bytes: (.bytes|tonumber), path: .path})')"
  SPEC_JSON="$(printf '%s\n' "$SPEC_FILES" | jq -R -s 'split("\n") | map(select(length>0))')"
  AUDIT_JSON="$(printf '%s\n' "$AUDIT_FILES" | jq -R -s 'split("\n") | map(select(length>0))')"
  CONTRACT_JSON="$(printf '%s\n' "$CONTRACT_FILES" | jq -R -s 'split("\n") | map(select(length>0))')"
  RELEASE_JSON="$(printf '%s\n' "$RELEASE_FILES" | jq -R -s 'split("\n") | map(select(length>0))')"
  RECENT_DOCS_JSON="$(printf '%s\n' "$RECENT_DOCS_RAW" | jq -R -s 'split("\n") | map(select(length>0))')"
  MIGRATIONS_JSON="$(printf '%s\n' "$MIGRATIONS_RAW" | jq -R -s 'split("\n") | map(select(length>0))')"
  DOC_SUBDIRS_JSON="$(printf '%s\n' "$DOC_SUBDIRS_RAW" | jq -R -s 'split("\n") | map(select(length>0) | capture("^(?<name>[^\t]+)\t(?<count>[0-9]+)$") | {name: .name, count: (.count|tonumber)})')"

  jq -n \
    --arg generated_at "$GENERATED_AT" \
    --arg target_root "$TARGET_ROOT" \
    --argjson docs_count "$DOCS_COUNT" \
    --argjson docs_total_bytes "$DOCS_TOTAL_BYTES" \
    --argjson docs_top "$DOCS_TOP_JSON" \
    --argjson specs "$SPEC_JSON" \
    --argjson audits "$AUDIT_JSON" \
    --argjson contracts "$CONTRACT_JSON" \
    --argjson releases "$RELEASE_JSON" \
    --argjson recent_docs "$RECENT_DOCS_JSON" \
    --argjson migrations "$MIGRATIONS_JSON" \
    --argjson docs_subdirs "$DOC_SUBDIRS_JSON" \
    --arg openapi "${OPENAPI_FILE:-}" \
    '{
      generated_at: $generated_at,
      target_root: $target_root,
      docs: {
        total_files: $docs_count,
        total_bytes: $docs_total_bytes,
        top10_largest: $docs_top,
        subdirs: $docs_subdirs,
        recent_30d: $recent_docs,
        spec_files: $specs,
        audit_files: $audits,
        contract_files: $contracts,
        release_files: $releases
      },
      migrations: {
        backend_recent_20: $migrations
      },
      openapi: (if $openapi == "" then null else $openapi end)
    }'
else
  echo "WARNING: jq không có — output có thể không parse được." >&2
  cat <<EOF
{
  "generated_at": "$GENERATED_AT",
  "target_root": "$TARGET_ROOT",
  "docs": {
    "total_files": $DOCS_COUNT,
    "total_bytes": $DOCS_TOTAL_BYTES,
    "spec_files_count": $(printf '%s' "$SPEC_FILES" | grep -c . || echo 0),
    "audit_files_count": $(printf '%s' "$AUDIT_FILES" | grep -c . || echo 0),
    "contract_files_count": $(printf '%s' "$CONTRACT_FILES" | grep -c . || echo 0),
    "release_files_count": $(printf '%s' "$RELEASE_FILES" | grep -c . || echo 0),
    "recent_30d_count": $(printf '%s' "$RECENT_DOCS_RAW" | grep -c . || echo 0),
    "subdirs_raw": "$(printf '%s' "$DOC_SUBDIRS_RAW" | tr '\n' '|')"
  },
  "migrations": {
    "backend_recent_20_raw": "$(printf '%s' "$MIGRATIONS_RAW" | tr '\n' '|')"
  },
  "openapi": "${OPENAPI_FILE:-}"
}
EOF
fi

exit 0