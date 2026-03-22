#!/usr/bin/env bash
set -euo pipefail

ROOT_DEFAULT="$(pwd)"
if command -v git >/dev/null 2>&1; then
  if git_root="$(git rev-parse --show-toplevel 2>/dev/null)"; then
    ROOT_DEFAULT="$git_root"
  fi
fi

ROOT="$ROOT_DEFAULT"
OUTPUT=""
MAX_FILE_BYTES=250000

usage() {
  cat <<USAGE
Usage: $(basename "$0") [options]

Build a single markdown context file for LLM review.

Options:
  -o, --output PATH      Output file path (default: <root>/project_context_for_llm.md)
  -r, --root PATH        Project root (default: git root or current directory)
  -m, --max-bytes N      Max bytes per file before truncation (default: ${MAX_FILE_BYTES})
  -h, --help             Show this help
USAGE
}

while (($#)); do
  case "$1" in
    -o|--output)
      shift
      OUTPUT="${1:-}"
      ;;
    -r|--root)
      shift
      ROOT="${1:-}"
      ;;
    -m|--max-bytes)
      shift
      MAX_FILE_BYTES="${1:-}"
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
  shift || true
done

if [[ -z "$OUTPUT" ]]; then
  OUTPUT="$ROOT/project_context_for_llm.md"
fi

ROOT="$(cd "$ROOT" && pwd)"
mkdir -p "$(dirname "$OUTPUT")"
OUTPUT_ABS="$(cd "$(dirname "$OUTPUT")" && pwd)/$(basename "$OUTPUT")"

if ! [[ "$MAX_FILE_BYTES" =~ ^[0-9]+$ ]]; then
  echo "--max-bytes must be an integer" >&2
  exit 1
fi

# Paths ignored entirely (glob patterns over project-relative paths).
IGNORED_PATH_GLOBS=(
  ".git/*"
  "*/.git/*"
  ".godot/*"
  "*/.godot/*"
  ".idea/*"
  "*/.idea/*"
  ".vscode/*"
  "*/.vscode/*"
  "imported/*"
  "*/imported/*"
  "logs/*"
  "*/logs/*"
  "tmp/*"
  "*/tmp/*"
  "node_modules/*"
  "*/node_modules/*"
  "backend/target/*"
  "client/assets/downloaded/*"
)

# File patterns likely not useful for LLM code understanding.
IGNORED_FILE_GLOBS=(
  "*.png"
  "*.jpg"
  "*.jpeg"
  "*.gif"
  "*.webp"
  "*.ico"
  "*.bmp"
  "*.tga"
  "*.glb"
  "*.gltf"
  "*.ogg"
  "*.mp3"
  "*.wav"
  "*.zip"
  "*.tar"
  "*.gz"
  "*.7z"
  "*.pdf"
  "*.bin"
  "*.exe"
  "*.dll"
  "*.so"
  "*.dylib"
)

is_ignored_path_glob() {
  local rel="$1"
  for glob in "${IGNORED_PATH_GLOBS[@]}"; do
    if [[ "$rel" == $glob ]]; then
      return 0
    fi
  done
  return 1
}

is_ignored_file_by_glob() {
  local rel="$1"
  for glob in "${IGNORED_FILE_GLOBS[@]}"; do
    if [[ "$rel" == $glob ]]; then
      return 0
    fi
  done
  return 1
}

guess_lang() {
  local rel="$1"
  case "$rel" in
    *.rs) echo "rust" ;;
    *.gd) echo "gdscript" ;;
    *.tscn|*.tres) echo "ini" ;;
    *.toml) echo "toml" ;;
    *.json) echo "json" ;;
    *.yaml|*.yml) echo "yaml" ;;
    *.md) echo "markdown" ;;
    *.lua) echo "lua" ;;
    *.sql) echo "sql" ;;
    *.sh) echo "bash" ;;
    *.xml|*.svg) echo "xml" ;;
    *.env|*.example) echo "dotenv" ;;
    *) echo "text" ;;
  esac
}

rel_path() {
  local abs="$1"
  local rel="${abs#$ROOT/}"
  if [[ "$abs" == "$ROOT" ]]; then
    rel="."
  fi
  printf '%s' "$rel"
}

is_text_file() {
  local file="$1"
  # grep -Iq returns success for text-like content and failure for binary.
  LC_ALL=C grep -Iq . "$file" 2>/dev/null || [[ ! -s "$file" ]]
}

timestamp="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
branch="unknown"
commit="unknown"
if command -v git >/dev/null 2>&1 && git -C "$ROOT" rev-parse --git-dir >/dev/null 2>&1; then
  branch="$(git -C "$ROOT" rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)"
  commit="$(git -C "$ROOT" rev-parse HEAD 2>/dev/null || echo unknown)"
fi

mapfile -t all_files < <(find "$ROOT" -type f | sort)

included_files=()
skipped_binary=()
skipped_glob=()
skipped_output=()

for abs in "${all_files[@]}"; do
  rel="$(rel_path "$abs")"

  if [[ "$abs" == "$OUTPUT_ABS" ]]; then
    skipped_output+=("$rel")
    continue
  fi

  if is_ignored_path_glob "$rel"; then
    skipped_glob+=("$rel")
    continue
  fi

  if is_ignored_file_by_glob "$rel"; then
    skipped_glob+=("$rel")
    continue
  fi

  if ! is_text_file "$abs"; then
    skipped_binary+=("$rel")
    continue
  fi

  included_files+=("$abs")
done

{
  echo "# Project Context Bundle"
  echo
  echo "Generated: ${timestamp}"
  echo "Root: ${ROOT}"
  echo "Branch: ${branch}"
  echo "Commit: ${commit}"
  echo "Max bytes per file: ${MAX_FILE_BYTES}"
  echo
  echo "## File Tree (Included Text Files)"
  echo
  for abs in "${included_files[@]}"; do
    rel="$(rel_path "$abs")"
    printf -- "- %s\n" "$rel"
  done

  echo
  echo "## Skipped"
  echo
  echo "- Ignored by glob: ${#skipped_glob[@]}"
  echo "- Binary/non-text: ${#skipped_binary[@]}"
  echo "- Output file self-skip: ${#skipped_output[@]}"
  echo
  echo "### Active Skip Rules"
  echo "- Path globs:"
  for pattern in "${IGNORED_PATH_GLOBS[@]}"; do
    printf -- "  - %s\n" "$pattern"
  done
  echo "- File globs:"
  for pattern in "${IGNORED_FILE_GLOBS[@]}"; do
    printf -- "  - %s\n" "$pattern"
  done

  echo
  echo "## File Contents"
  echo

  for abs in "${included_files[@]}"; do
    rel="$(rel_path "$abs")"
    size="$(wc -c < "$abs" | tr -d ' ')"
    lang="$(guess_lang "$rel")"

    echo "### FILE: ${rel}"
    echo "Path: ${rel}"
    echo "Bytes: ${size}"
    echo
    echo "\`\`\`\`${lang}"

    if (( size > MAX_FILE_BYTES )); then
      head -c "$MAX_FILE_BYTES" "$abs"
      echo
      echo "[TRUNCATED: original ${size} bytes, limit ${MAX_FILE_BYTES} bytes]"
    else
      cat "$abs"
    fi

    echo
    echo "\`\`\`\`"
    echo
  done
} > "$OUTPUT_ABS"

echo "Context bundle written to: $OUTPUT_ABS"
echo "Included files: ${#included_files[@]}"
echo "Skipped by glob: ${#skipped_glob[@]}"
echo "Skipped binary/non-text: ${#skipped_binary[@]}"
