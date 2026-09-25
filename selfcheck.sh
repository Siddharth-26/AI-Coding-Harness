#!/usr/bin/env bash
# Run the harness's own tests (the core building blocks). They are named *SelfCheck so a normal
# `mvn test` / ./test-fr.sh never runs them in the interview; run this once after cloning.
set -euo pipefail
cd "$(dirname "$0")"
mvn -q test -Dtest='*SelfCheck'
