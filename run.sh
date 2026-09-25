#!/usr/bin/env bash
# Run the Demo of the package named in Design.txt.
#   ./run.sh              # package from Design.txt
#   ./run.sh <package>    # or name one explicitly
set -euo pipefail
cd "$(dirname "$0")"
pkg="${1:-$(sed -n 's/^PACKAGE:[[:space:]]*\([a-z][a-z0-9.]*\).*/\1/p' Design.txt 2>/dev/null | head -1)}"
[[ -n "$pkg" ]] || { echo "Fill in 'PACKAGE: <name>' in Design.txt (or pass the package)." >&2; exit 1; }
mvn -q compile exec:java -Dexec.mainClass="com.lld.${pkg}.Demo"
