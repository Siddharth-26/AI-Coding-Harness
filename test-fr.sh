#!/usr/bin/env bash
# Run the FR tests (*FrTest) of the package named in Design.txt and print the FR checklist.
#   ./test-fr.sh              # package from Design.txt
#   ./test-fr.sh <package>    # or name one explicitly
set -euo pipefail
cd "$(dirname "$0")"
pkg="${1:-$(sed -n 's/^PACKAGE:[[:space:]]*\([a-z][a-z0-9.]*\).*/\1/p' Design.txt 2>/dev/null | head -1)}"
[[ -n "$pkg" ]] || { echo "Fill in 'PACKAGE: <name>' in Design.txt (or pass the package)." >&2; exit 1; }
path="com/lld/${pkg//.//}"

if ! find "src/test/java/$path" -name '*FrTest.java' 2>/dev/null | grep -q .; then
  echo "No *FrTest.java under src/test/java/$path yet. Generate them with /fr-tests." >&2
  exit 1
fi
mvn -q test -Dtest="$path/**/*FrTest"
