#!/usr/bin/env bash
# Run the NFR tests (*NfrTest) of the package named in Design.txt and print the NFR checklist
# with measured numbers.
#   ./test-nfr.sh             # package from Design.txt
#   ./test-nfr.sh <package>   # or name one explicitly
set -euo pipefail
cd "$(dirname "$0")"
pkg="${1:-$(sed -n 's/^PACKAGE:[[:space:]]*\([a-z][a-z0-9.]*\).*/\1/p' Design.txt 2>/dev/null | head -1)}"
[[ -n "$pkg" ]] || { echo "Fill in 'PACKAGE: <name>' in Design.txt (or pass the package)." >&2; exit 1; }
path="com/lld/${pkg//.//}"

if ! find "src/test/java/$path" -name '*NfrTest.java' 2>/dev/null | grep -q .; then
  echo "No *NfrTest.java under src/test/java/$path yet. Generate them with /nfr-tests." >&2
  exit 1
fi
mvn -q test -Dtest="$path/**/*NfrTest"
