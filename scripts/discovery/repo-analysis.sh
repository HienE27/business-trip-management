#!/usr/bin/env bash
# repo-analysis.sh
# Discovery PR2 — Bước 2: Phân tích repo (git + cấu trúc thư mục)
#
# Mục đích:
#   - Tóm tắt trạng thái git (branch, HEAD, dirty, ahead/behind).
#   - Liệt kê cấu trúc top-level và sub-package quan trọng.
#   - Phát hiện file "có vẻ dead" theo heuristic đơn giản (file không được
#     reference trong các file khác trong cùng cây thư mục).
#   - KHÔNG sửa source code. KHÔNG xóa file. Chỉ xuất JSON ra stdout.
#
# Cách dùng:
#   ./scripts/discovery/repo-analysis.sh [TARGET_ROOT]
#
# Đầu ra:
#   JSON object duy nhất in ra stdout.
#
# Thoát:
#   0 = thành công.
#   1 = lỗi nghiêm trọng (TARGET_ROOT không phải git repo, jq thiếu khi strict).

set -u
set -o pipefail

TARGET_ROOT="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"

if [ ! -d "$TARGET_ROOT" ]; then
  echo "ERROR: TARGET_ROOT không tồn tại: $TARGET_ROOT" >&2
  exit 1
fi

cd "$TARGET_ROOT"

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "ERROR: $TARGET_ROOT không phải git repo" >&2
  exit 1
fi

have_jq() { command -v jq >/dev/null 2>&1; }

GENERATED_AT="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

# ---------- Git snapshot ----------
BRANCH="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo 'DETACHED')"
HEAD_SHA="$(git rev-parse HEAD 2>/dev/null || echo 'unknown')"
HEAD_SHORT="$(git rev-parse --short HEAD 2>/dev/null || echo 'unknown')"

# Working tree status (short)
STATUS_RAW="$(git status --porcelain 2>/dev/null || true)"

# Ahead/behind vs origin/<current-branch> (nếu có tracking)
TRACKING=""
AHEAD=""
BEHIND=""
if git rev-parse --abbrev-ref --symbolic-full-name '@{u}' >/dev/null 2>&1; then
  TRACKING="$(git rev-parse --abbrev-ref --symbolic-full-name '@{u}' 2>/dev/null)"
  AHEAD="$(git rev-list --count '@{u}'..HEAD 2>/dev/null || echo 0)"
  BEHIND="$(git rev-list --count HEAD..'@{u}' 2>/dev/null || echo 0)"
fi

# Recent commits (15)
RECENT_RAW="$(git log --pretty=format:'%h%x09%ad%x09%s' --date=short -n 15 2>/dev/null || true)"

# Local branches + last commit date
BRANCHES_RAW="$(git for-each-ref --format='%(refname:short)%09%(committerdate:short)%09%(subject)' refs/heads/ 2>/dev/null || true)"

# Remote branches
REMOTES_RAW="$(git remote -v 2>/dev/null || true)"

# ---------- Repo layout ----------
# Top-level entries (bỏ .git)
TOP_LEVEL="$(ls -1A 2>/dev/null | grep -v '^\.git$' || true)"

# Backend package depth-1
BACKEND_PACKAGES="$(ls -1 backend/src/main/java/com/hospital/scheduler 2>/dev/null || true)"

# Frontend src/app routes
FRONTEND_ROUTES="$(find frontend/src/app -mindepth 1 -maxdepth 3 -type d 2>/dev/null \
  | sed "s|^$TARGET_ROOT/||" | LC_ALL=C sort || true)"

# ---------- Heuristic: file "có vẻ dead" ----------
# Định nghĩa: file .ts/.tsx/.java không xuất hiện trong bất kỳ import/reference nào
# trong các file khác trong cùng cây thư mục cha (tối đa 2 cấp). Đây chỉ là
# HINT, không phải kết luận — reviewer phải verify thủ công.
DEAD_HINTS_RAW=""
if [ -d backend/src/main/java ]; then
  DEAD_HINTS_RAW="$(find backend/src/main/java -type f -name '*.java' 2>/dev/null \
    | while read -r f; do
        base="$(basename "$f" .java)"
        # Bỏ qua test files & well-known entry points
        case "$base" in
          HospitalSchedulerApplication|application|SecurityConfig|WebMvcConfig|JacksonConfig|OpenApiConfig) continue ;;
        esac
        # Tìm reference trong các file java khác (trừ chính nó)
        refs="$(grep -rln --include='*.java' "$base" backend/src/main/java 2>/dev/null | grep -v "^$f\$" | wc -l)"
        if [ "$refs" = "0" ]; then
          echo "$f"
        fi
      done | head -n 50 || true)"
fi

# ---------- Output ----------
if have_jq; then
  STATUS_JSON="$(printf '%s\n' "$STATUS_RAW" | jq -R -s 'split("\n") | map(select(length>0))')"
  RECENT_JSON="$(printf '%s\n' "$RECENT_RAW" | jq -R -s 'split("\n") | map(select(length>0) | capture("^(?<sha>[0-9a-f]+)\t(?<date>[0-9-]+)\t(?<subject>.*)$"))')"
  BRANCHES_JSON="$(printf '%s\n' "$BRANCHES_RAW" | jq -R -s 'split("\n") | map(select(length>0) | capture("^(?<name>[^\t]+)\t(?<date>[0-9-]+)\t(?<subject>.*)$"))')"
  REMOTES_JSON="$(printf '%s\n' "$REMOTES_RAW" | jq -R -s 'split("\n") | map(select(length>0) | capture("^(?<name>[^\t]+)\t(?<url>[^\t]+)$"))')"
  TOP_LEVEL_JSON="$(printf '%s\n' "$TOP_LEVEL" | jq -R -s 'split("\n") | map(select(length>0))')"
  BACKEND_PKG_JSON="$(printf '%s\n' "$BACKEND_PACKAGES" | jq -R -s 'split("\n") | map(select(length>0))')"
  FRONTEND_ROUTES_JSON="$(printf '%s\n' "$FRONTEND_ROUTES" | jq -R -s 'split("\n") | map(select(length>0))')"
  DEAD_HINTS_JSON="$(printf '%s\n' "$DEAD_HINTS_RAW" | jq -R -s 'split("\n") | map(select(length>0))')"

  jq -n \
    --arg generated_at "$GENERATED_AT" \
    --arg target_root "$TARGET_ROOT" \
    --arg branch "$BRANCH" \
    --arg head "$HEAD_SHA" \
    --arg head_short "$HEAD_SHORT" \
    --arg tracking "$TRACKING" \
    --argjson ahead "${AHEAD:-0}" \
    --argjson behind "${BEHIND:-0}" \
    --argjson status "$STATUS_JSON" \
    --argjson recent_commits "$RECENT_JSON" \
    --argjson branches "$BRANCHES_JSON" \
    --argjson remotes "$REMOTES_JSON" \
    --argjson top_level "$TOP_LEVEL_JSON" \
    --argjson backend_packages "$BACKEND_PKG_JSON" \
    --argjson frontend_routes "$FRONTEND_ROUTES_JSON" \
    --argjson dead_hints "$DEAD_HINTS_JSON" \
    '{
      generated_at: $generated_at,
      target_root: $target_root,
      git: {
        branch: $branch,
        head: $head,
        head_short: $head_short,
        tracking: (if $tracking == "" then null else $tracking end),
        ahead: $ahead,
        behind: $behind,
        working_tree_status: $status,
        recent_commits: $recent_commits,
        branches: $branches,
        remotes: $remotes
      },
      layout: {
        top_level: $top_level,
        backend_packages: $backend_packages,
        frontend_routes: $frontend_routes
      },
      dead_hints: $dead_hints
    }'
else
  echo "WARNING: jq không có — output có thể không parse được." >&2
  cat <<EOF
{
  "generated_at": "$GENERATED_AT",
  "target_root": "$TARGET_ROOT",
  "git": {
    "branch": "$BRANCH",
    "head": "$HEAD_SHA",
    "head_short": "$HEAD_SHORT",
    "tracking": "$TRACKING",
    "ahead": ${AHEAD:-0},
    "behind": ${BEHIND:-0},
    "working_tree_status_lines": $(printf '%s' "$STATUS_RAW" | wc -l),
    "recent_commits_raw": "$(printf '%s' "$RECENT_RAW" | tr '\n' '|' | sed 's/|/\\n/g')",
    "branches_raw": "$(printf '%s' "$BRANCHES_RAW" | tr '\n' '|' | sed 's/|/\\n/g')",
    "remotes_raw": "$(printf '%s' "$REMOTES_RAW" | tr '\n' '|')"
  },
  "layout": {
    "top_level": "$(printf '%s' "$TOP_LEVEL" | tr '\n' ',')",
    "backend_packages": "$(printf '%s' "$BACKEND_PACKAGES" | tr '\n' ',')"
  },
  "dead_hints_count": $(printf '%s' "$DEAD_HINTS_RAW" | grep -c . || echo 0)
}
EOF
fi

exit 0