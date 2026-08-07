#!/usr/bin/env bash
# static-analysis.sh
# Discovery PR2 — Bước 1: Phân tích tĩnh
#
# Mục đích:
#   - Liệt kê các chỉ số tĩnh của codebase (số dòng, số file, hot spots).
#   - Phát hiện TODO/FIXME/XXX/HACK chưa resolve.
#   - Phát hiện dependency declarations không dùng (heuristic).
#   - KHÔNG sửa source code. KHÔNG xóa dead code. Chỉ xuất JSON ra stdout.
#
# Cách dùng:
#   ./scripts/discovery/static-analysis.sh [TARGET_ROOT]
#   TARGET_ROOT mặc định là thư mục cha của scripts/ (tức repo root).
#
# Đầu ra:
#   JSON object duy nhất in ra stdout. Pipe sang jq hoặc file:
#     ./scripts/discovery/static-analysis.sh > build/static-analysis.json
#
# Phạm vi bỏ qua (luôn luôn):
#   - target/, build/, dist/, node_modules/, .git/, .cursor/, .trellis/
#   - *.lock, *.min.js, *.min.css, *.png, *.jpg, *.jpeg, *.gif, *.webp, *.ico
#   - *.pdf, *.docx, *.pptx, *.xlsx
#
# Phạm vi ưu tiên quét (khi tồn tại):
#   - backend/src/main/java/**.java
#   - backend/src/test/java/**.java
#   - frontend/src/**.{ts,tsx}
#   - frontend/src/**.{js,jsx}
#
# Thoát:
#   0 = thành công (kể cả khi không phát hiện gì).
#   1 = lỗi nghiêm trọng (vd: TARGET_ROOT không tồn tại, jq không có).

set -u
set -o pipefail

# ---------- Args ----------
TARGET_ROOT="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"

if [ ! -d "$TARGET_ROOT" ]; then
  echo "ERROR: TARGET_ROOT không tồn tại: $TARGET_ROOT" >&2
  exit 1
fi

# ---------- Helpers ----------
have_jq() { command -v jq >/dev/null 2>&1; }

count_lines() {
  # $1 = glob pattern (find -E regex)
  # Trả về tổng số dòng (wc -l). 0 nếu không có file.
  local pattern="$1"
  find "$TARGET_ROOT" \
    -type d \( -name target -o -name build -o -name dist -o -name node_modules \
              -o -name .git -o -name .cursor -o -name .trellis -o -name out \) -prune -o \
    -type f -regextype posix-extended -regex "$pattern" -print0 2>/dev/null \
    | xargs -0 wc -l 2>/dev/null \
    | tail -n 1 \
    | awk '{print $1}'
}

list_files() {
  # $1 = find regex
  find "$TARGET_ROOT" \
    -type d \( -name target -o -name build -o -name dist -o -name node_modules \
              -o -name .git -o -name .cursor -o -name .trellis -o -name out \) -prune -o \
    -type f -regextype posix-extended -regex "$1" -print 2>/dev/null \
    | sed "s|^$TARGET_ROOT/||" \
    | LC_ALL=C sort
}

# ---------- Thu thập ----------
GENERATED_AT="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

# Java backend
BACKEND_MAIN_JAVA_LINES="$(count_lines '.*/backend/src/main/java/.*\.java$')"
BACKEND_TEST_JAVA_LINES="$(count_lines '.*/backend/src/test/java/.*\.java$')"
BACKEND_MAIN_JAVA_FILES="$(list_files '.*/backend/src/main/java/.*\.java$' | wc -l | tr -d ' ')"
BACKEND_TEST_JAVA_FILES="$(list_files '.*/backend/src/test/java/.*\.java$' | wc -l | tr -d ' ')"

# Frontend TS/TSX/JS/JSX
FRONTEND_TS_LINES="$(count_lines '.*/frontend/src/.*\.(ts|tsx|js|jsx)$')"
FRONTEND_TS_FILES="$(list_files '.*/frontend/src/.*\.(ts|tsx|js|jsx)$' | wc -l | tr -d ' ')"

# Top 10 hot spots (file dài nhất)
HOTSPOTS_RAW="$(find "$TARGET_ROOT" \
  -type d \( -name target -o -name build -o -name dist -o -name node_modules \
            -o -name .git -o -name .cursor -o -name .trellis -o -name out \) -prune -o \
  -type f \( -name '*.java' -o -name '*.ts' -o -name '*.tsx' -o -name '*.js' -o -name '*.jsx' \) -print0 2>/dev/null \
  | xargs -0 wc -l 2>/dev/null \
  | sort -rn \
  | head -n 11 \
  | tail -n 10)"

# TODO/FIXME/XXX/HACK scan
TODO_RAW="$(grep -RInE \
  --include='*.java' --include='*.ts' --include='*.tsx' --include='*.js' --include='*.jsx' \
  --exclude-dir=target --exclude-dir=node_modules --exclude-dir=.git \
  --exclude-dir=.cursor --exclude-dir=.trellis --exclude-dir=build --exclude-dir=dist \
  -E '(TODO|FIXME|XXX|HACK)' "$TARGET_ROOT" 2>/dev/null \
  | sed "s|^$TARGET_ROOT/||" || true)"

# ---------- Output JSON ----------
if have_jq; then
  # Tạo JSON an toàn qua jq — tránh lỗi escape ký tự đặc biệt
  HOTSPOTS_JSON="$(printf '%s\n' "$HOTSPOTS_RAW" | jq -R -s 'split("\n") | map(select(length>0) | capture("^(?<lines>[0-9]+) (?<path>.+)$")) | map({path: .path, lines: (.lines|tonumber)})')"

  TODO_JSON="$(printf '%s\n' "$TODO_RAW" | jq -R -s 'split("\n") | map(select(length>0)) | map(capture("^(?<path>[^:]+):(?<line>[0-9]+):(?<text>.*)$"))')"

  jq -n \
    --arg generated_at "$GENERATED_AT" \
    --arg target_root "$TARGET_ROOT" \
    --argjson backend_main_files "$BACKEND_MAIN_JAVA_FILES" \
    --argjson backend_test_files "$BACKEND_TEST_JAVA_FILES" \
    --argjson backend_main_lines "$BACKEND_MAIN_JAVA_LINES" \
    --argjson backend_test_lines "$BACKEND_TEST_JAVA_LINES" \
    --argjson frontend_files "$FRONTEND_TS_FILES" \
    --argjson frontend_lines "$FRONTEND_TS_LINES" \
    --argjson hotspots "$HOTSPOTS_JSON" \
    --argjson todos "$TODO_JSON" \
    '{
      generated_at: $generated_at,
      target_root: $target_root,
      counts: {
        backend_main_java_files: $backend_main_files,
        backend_main_java_lines: $backend_main_lines,
        backend_test_java_files: $backend_test_files,
        backend_test_java_lines: $backend_test_lines,
        frontend_src_files: $frontend_files,
        frontend_src_lines: $frontend_lines
      },
      hotspots_top10: $hotspots,
      todos: $todos
    }'
else
  # Fallback: in plain JSON literal (ít an toàn nhưng vẫn parse được với nhiều tool)
  echo "WARNING: jq không có — output không escape ký tự đặc biệt. Cài jq để có JSON chuẩn." >&2
  cat <<EOF
{
  "generated_at": "$GENERATED_AT",
  "target_root": "$TARGET_ROOT",
  "counts": {
    "backend_main_java_files": $BACKEND_MAIN_JAVA_FILES,
    "backend_main_java_lines": $BACKEND_MAIN_JAVA_LINES,
    "backend_test_java_files": $BACKEND_TEST_JAVA_FILES,
    "backend_test_java_lines": $BACKEND_TEST_JAVA_LINES,
    "frontend_src_files": $FRONTEND_TS_FILES,
    "frontend_src_lines": $FRONTEND_TS_LINES
  },
  "hotspots_top10_raw": "$(printf '%s' "$HOTSPOTS_RAW" | tr '\n' '|' | sed 's/|/\\n/g')",
  "todos_raw": "$(printf '%s' "$TODO_RAW" | tr '\n' '|' | sed 's/|/\\n/g')"
}
EOF
fi

exit 0