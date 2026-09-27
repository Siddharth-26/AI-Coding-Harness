#!/usr/bin/env bash
# lld.sh: set up, build and test ONE problem folder.
#
# A problem folder is any folder holding your Design.txt (any capitalisation), with your code in
# main/ and its tests in test/. It can sit next to this script or inside src/main/java:
#
#   lrucache/
#     Design.txt              yours. Nothing in the harness edits it.
#     pom.xml                 made by `init`: this folder becomes a small Maven project
#     main/lrucache/...       the code (package = folder name, unless Design.txt names one)
#     test/lrucache/...       its tests: <Name>FrTest, <Name>NfrTest (same package as the code)
#     test/com/lld/core/...   test kit copied by `init`: RequirementReporter, ConcurrentRunner, Perf, MutableClock
#
# Usage: the folder can be left out when you are inside it; otherwise the newest Design.txt wins.
#   ./lld.sh init    <folder>                                create pom.xml, main/, test/ and the test kit
#   ./lld.sh test    <folder> [fr | nfr | all | Class | Class#method]    run tests (default: all)
#   ./lld.sh howto   <folder>                                the exact commands to run this folder's tests
#   ./lld.sh compile <folder>
#   ./lld.sh run     <folder> [MainClass]
#   ./lld.sh drift   <folder>                                every "DRIFT" comment: where code differs from Design.txt
#   ./lld.sh core    <folder>                                copy the com.lld.core building blocks into main/com/lld/core
#   ./lld.sh list                                            problem folders, newest Design.txt first
#
# Works with the bash 3.2 that ships with macOS. Offline: MAVEN_ARGS=-o ./lld.sh test ... (Maven 3.9+).
set -euo pipefail
unset CDPATH   # otherwise `cd` can print the path and corrupt $(cd ... && pwd)

harness="$(cd "$(dirname "$0")" && pwd -P)"
folder=""

die() { echo "$*" >&2; exit 1; }

usage() {
  cat <<'EOF'
./lld.sh init    <folder>                              create pom.xml, main/, test/ and the test kit
./lld.sh test    <folder> [fr|nfr|all|Class|Class#method]   run tests (default: all)
./lld.sh howto   <folder>                              print the commands to run this folder's tests
./lld.sh compile <folder>
./lld.sh run     <folder> [MainClass]
./lld.sh drift   <folder>                              list every DRIFT comment (code vs Design.txt)
./lld.sh core    <folder>                              copy the com.lld.core building blocks into main/
./lld.sh list                                          problem folders, newest Design.txt first
Leave out <folder> when you are inside it; otherwise the newest Design.txt is used.
EOF
}

# Path for messages: relative to the harness root when inside it.
show() {
  case "$1" in
    "$harness") echo "." ;;
    "$harness"/*) echo "${1#"$harness"/}" ;;
    *) echo "$1" ;;
  esac
}

# $1 quoted for pasting into a shell, when it needs it (spaces etc.).
shq() {
  case "$1" in
    *[!A-Za-z0-9_./-]*) printf "'%s'\n" "$1" ;;
    *) printf '%s\n' "$1" ;;
  esac
}

# The Design.txt in folder $1 (any capitalisation), or nothing.
design_in() {
  local f
  for f in "$1"/[Dd][Ee][Ss][Ii][Gg][Nn].[Tt][Xx][Tt]; do
    if [[ -f "$f" ]]; then echo "$f"; return 0; fi
  done
  return 0
}

# Every Design.txt under the harness root, newest first.
list_designs() {
  local files=() f
  while IFS= read -r -d '' f; do
    files+=("$f")
  done < <(find "$harness" \( -name target -o -name templates -o -name clones -o -name .git \
             -o -name .idea -o -name node_modules -o -name .claude -o -name .kiro \) -prune \
             -o -type f -iname 'design.txt' -print0 2> /dev/null)
  if [[ ${#files[@]} -gt 0 ]]; then
    ls -t "${files[@]}"
  fi
}

# Sets $folder (absolute path). $1 = the folder argument, or "" to find it.
resolve_folder() {
  if [[ -n "$1" ]]; then
    [[ -d "$1" ]] || die "Not a folder: $1"
    folder="$(cd "$1" && pwd -P)"
    return 0
  fi
  local d
  d="$(pwd -P)"
  while [[ "$d" != "/" ]]; do
    if [[ -n "$(design_in "$d")" ]]; then
      folder="$d"
      return 0
    fi
    [[ "$d" == "$harness" ]] && break
    d="$(dirname "$d")"
  done
  local all newest
  all="$(list_designs)"
  newest="${all%%$'\n'*}"
  [[ -n "$newest" ]] || die "No Design.txt found. Create <folder>/Design.txt, then run: ./lld.sh init <folder>"
  folder="$(cd "$(dirname "$newest")" && pwd -P)"
  echo "(no folder given: using $(show "$folder"), the newest Design.txt; pass a folder to pick another)" >&2
}

# For `test` and `run`: is the first argument a folder, or a test selector / class name?
# Selectors are fr, nfr, all, Class, Class#method, *Pattern, pkg.Class.
is_folder_arg() {
  [[ -d "$1" ]] && return 0
  case "$1" in
    "" | fr | nfr | all | *'#'* | *'*'*) return 1 ;;
    */*) return 0 ;;
    *.*) return 1 ;;
    [[:lower:]]*) return 0 ;;
  esac
  return 1
}

# First existing file named $2 under directory $1 (a find -name pattern), or nothing.
first_file() {
  local f
  [[ -d "$1" ]] || return 0
  while IFS= read -r f; do
    echo "$f"
    return 0
  done < <(find "$1" -type f -name "$2" 2> /dev/null)
  return 0
}

pkg_name() {
  local p
  p="$(basename "$1" | tr '[:upper:]' '[:lower:]' | tr -cd 'a-z0-9')"
  case "$p" in "" | [0-9]*) p="p$p" ;; esac
  echo "$p"
}

artifact_name() {
  local a
  a="$(basename "$1" | tr '[:upper:]' '[:lower:]' | tr -c 'a-z0-9\n' '-' | sed -e 's/^-*//' -e 's/-*$//')"
  echo "${a:-problem}"
}

need_mvn() {
  command -v mvn > /dev/null 2>&1 || die "mvn is not on your PATH. Install Maven (brew install maven) or use IntelliJ's run arrows."
}

do_init() {
  local created=() f src
  mkdir -p "$folder/main" "$folder/test"
  if [[ ! -f "$folder/pom.xml" ]]; then
    sed "s/__ARTIFACT__/$(artifact_name "$folder")/" "$harness/templates/problem-pom.xml" > "$folder/pom.xml"
    created+=("pom.xml")
  fi
  for f in RequirementReporter ConcurrentRunner Perf MutableClock; do
    src="$harness/src/test/java/com/lld/core/$f.java"
    [[ -f "$src" ]] || src="$harness/src/main/java/com/lld/core/$f.java"
    if [[ ! -f "$folder/test/com/lld/core/$f.java" ]]; then
      mkdir -p "$folder/test/com/lld/core"
      cp "$src" "$folder/test/com/lld/core/$f.java"
      created+=("test/com/lld/core/$f.java")
    fi
  done
  local pkg
  pkg="$(pkg_name "$folder")"
  echo "Problem folder: $(show "$folder")"
  if [[ ${#created[@]} -eq 0 ]]; then
    echo "  already set up (nothing changed)"
  else
    for f in "${created[@]}"; do echo "  created $f"; done
  fi
  if [[ -n "$(design_in "$folder")" ]]; then
    echo "  Design.txt: $(basename "$(design_in "$folder")") (not touched)"
  else
    echo "  Design.txt: none yet. Write it here before @scaffold / @fr-tests."
  fi
  echo "  code goes in main/$pkg/, tests in test/$pkg/ (package $pkg, unless your Design.txt names another)"
}

ensure_setup() {
  if [[ ! -f "$folder/pom.xml" ]]; then
    do_init
    echo
  fi
}

do_compile() {
  need_mvn
  ensure_setup
  mvn -q -f "$folder/pom.xml" test-compile
  echo "COMPILED ($(show "$folder"))"
}

do_test() {
  need_mvn
  ensure_setup
  local sel="${1:-all}" filter="" label="" pattern=""
  case "$sel" in
    fr)  filter='*FrTest';  label="FR tests";  pattern='*FrTest.java' ;;
    nfr) filter='*NfrTest'; label="NFR tests"; pattern='*NfrTest.java' ;;
    all) filter='';         label="all tests"; pattern='*Test.java' ;;
    *)   filter="$sel";     label="$sel" ;;
  esac
  if [[ -n "$pattern" && -z "$(first_file "$folder/test" "$pattern")" ]]; then
    case "$sel" in
      fr)  die "No *FrTest.java in $(show "$folder")/test yet. Generate them with: @fr-tests $(shq "$(show "$folder")")" ;;
      nfr) die "No *NfrTest.java in $(show "$folder")/test yet. Generate them with: @nfr-tests $(shq "$(show "$folder")")" ;;
      *)   die "No tests in $(show "$folder")/test yet. Generate them with: @fr-tests $(shq "$(show "$folder")")" ;;
    esac
  fi
  local args=(-q -f "$folder/pom.xml" test)
  if [[ -n "$filter" ]]; then
    args+=("-Dtest=$filter")
  fi
  echo "Running $label in $(show "$folder")"
  local status=0
  mvn "${args[@]}" || status=$?
  echo
  if [[ $status -eq 0 ]]; then
    echo "TESTS PASSED ($label, $(show "$folder"))"
  else
    echo "TESTS FAILED ($label, $(show "$folder"), mvn exit $status)."
    echo "Each FAIL line above names the requirement; a compile error shows file:line."
  fi
  return $status
}

do_run() {
  need_mvn
  ensure_setup
  local want="${1:-}" file="" f mains=()
  while IFS= read -r f; do
    mains+=("$f")
  done < <(grep -rlE --include='*.java' 'static[[:space:]]+void[[:space:]]+main[[:space:]]*\(' "$folder/main" 2> /dev/null || true)
  [[ ${#mains[@]} -gt 0 ]] || die "No class with a main() method in $(show "$folder")/main."
  if [[ -n "$want" ]]; then
    for f in "${mains[@]}"; do
      if [[ "$(basename "$f" .java)" == "$want" ]]; then file="$f"; fi
    done
    [[ -n "$file" ]] || die "No main() in a class named $want under $(show "$folder")/main."
  else
    for f in "${mains[@]}"; do
      case "$(basename "$f" .java)" in Main | Demo | App) file="$f" ;; esac
    done
    [[ -n "$file" ]] || file="${mains[0]}"
    if [[ ${#mains[@]} -gt 1 ]]; then
      echo "(several classes have main(); running $(basename "$file" .java). Pass the class name to pick another)" >&2
    fi
  fi
  local pkg cls
  pkg="$(awk '/^[ \t]*package[ \t]/ { sub(/^[ \t]*package[ \t]+/, ""); sub(/[ \t]*;.*/, ""); print; exit }' "$file")"
  cls="$(basename "$file" .java)"
  if [[ -n "$pkg" ]]; then cls="$pkg.$cls"; fi
  mvn -q -f "$folder/pom.xml" compile exec:java -Dexec.mainClass="$cls"
}

do_drift() {
  local out line n=0
  out="$(grep -rn --include='*.java' 'DRIFT' "$folder/main" "$folder/test" 2> /dev/null || true)"
  if [[ -z "$out" ]]; then
    echo "No DRIFT comments in $(show "$folder"): the code claims to follow Design.txt as written."
    return 0
  fi
  while IFS= read -r line; do
    echo "${line#"$folder"/}"
    n=$((n + 1))
  done <<< "$out"
  echo "$n place(s) where the code differs from Design.txt ($(show "$folder"))"
}

do_core() {
  local src="$harness/src/main/java/com/lld/core" dst="$folder/main/com/lld/core" rel n=0
  while IFS= read -r rel; do
    rel="${rel#./}"
    if [[ -e "$folder/test/com/lld/core/$rel" ]]; then
      continue   # already in the test kit (MutableClock)
    fi
    if [[ ! -e "$dst/$rel" ]]; then
      mkdir -p "$(dirname "$dst/$rel")"
      cp "$src/$rel" "$dst/$rel"
      n=$((n + 1))
    fi
  done < <(cd "$src" && find . -type f -name '*.java')
  echo "Copied $n building-block files into $(show "$dst") (existing files left alone)."
  echo "Use them as com.lld.core.*; don't edit them."
}

do_howto() {
  local here q fr nfr frcls nfrcls one="" up="" rel
  here="$(show "$folder")"
  q="$(shq "$here")"
  fr="$(first_file "$folder/test" '*FrTest.java')"
  nfr="$(first_file "$folder/test" '*NfrTest.java')"
  frcls="$(basename "${fr:-<Name>FrTest.java}" .java)"
  nfrcls="$(basename "${nfr:-<Name>NfrTest.java}" .java)"
  if [[ -n "$fr" ]]; then
    one="$(awk '$0 ~ /@Test[ \t\r]*$/ || $0 ~ /@Test[ \t(]/ { t = 1 }
                t && /void[ \t]+[A-Za-z0-9_]+[ \t]*\(/ { s = $0; sub(/^.*void[ \t]+/, "", s); sub(/[ \t]*\(.*/, "", s); print s; exit }' "$fr")"
  fi
  one="${one:-methodName}"
  # the way back from the folder to this script
  case "$folder" in
    "$harness"/*)
      rel="${folder#"$harness"/}"
      while [[ -n "$rel" ]]; do
        up="../$up"
        case "$rel" in */*) rel="${rel#*/}" ;; *) rel="" ;; esac
      done
      up="${up}lld.sh"
      ;;
    *) up="$(shq "$harness/lld.sh")" ;;
  esac
  cat <<EOF
How to run the tests of $here
  From the harness root ($harness):
    ./lld.sh test $q fr          FR tests  ($frcls)
    ./lld.sh test $q nfr         NFR tests ($nfrcls)
    ./lld.sh test $q             all tests
    ./lld.sh test $q '$frcls#$one'      one test
  From inside $here/:
    $up test fr          $up test nfr          $up test
    mvn -q test -Dtest='*FrTest'      mvn -q test -Dtest='*NfrTest'      (plain Maven, no script)
  IntelliJ: right-click $here/pom.xml > Add as Maven Project (once), then the green arrow next to
    $frcls or $nfrcls, or next to a single test method.
  Where the code differs from Design.txt: ./lld.sh drift $q
EOF
}

do_list() {
  local all f d
  all="$(list_designs)"
  if [[ -z "$all" ]]; then
    echo "No problem folders yet (no Design.txt under $harness)."
    return 0
  fi
  while IFS= read -r f; do
    d="$(dirname "$f")"
    if [[ -f "$d/pom.xml" ]]; then
      echo "$(show "$d")"
    else
      echo "$(show "$d")    (not set up yet: ./lld.sh init $(shq "$(show "$d")"))"
    fi
  done <<< "$all"
}

cmd="${1:-help}"
if [[ $# -gt 0 ]]; then shift; fi

given=""
case "$cmd" in
  test | run)
    if [[ $# -gt 0 ]] && is_folder_arg "$1"; then
      given="$1"
      shift
    fi
    ;;
  list | help | -h | --help) ;;
  *)
    if [[ $# -gt 0 ]]; then
      given="$1"
      shift
    fi
    ;;
esac

case "$cmd" in
  init)
    if [[ -n "$given" && ! -d "$given" ]]; then mkdir -p "$given"; fi
    resolve_folder "$given"
    do_init
    ;;
  compile) resolve_folder "$given"; do_compile ;;
  test)    resolve_folder "$given"; do_test "${1:-all}" ;;
  run)     resolve_folder "$given"; do_run "${1:-}" ;;
  drift)   resolve_folder "$given"; do_drift ;;
  core)    resolve_folder "$given"; do_core ;;
  howto)   resolve_folder "$given"; do_howto ;;
  list)    do_list ;;
  help | -h | --help) usage ;;
  *) usage >&2; exit 1 ;;
esac
