#!/usr/bin/env bash
# Run the harness's own checks (the core building blocks, classes named *SelfCheck) and print one
# line per check file plus the total. Tests in your problem folders never run these.
set -uo pipefail
cd "$(dirname "$0")"

out="$(mvn test -Dtest='*SelfCheck' 2>&1)"
status=$?

echo "$out" | grep "Tests run:" | sed -E 's/^\[[A-Z]+\] //; s/, Time elapsed:[^-]*-+ in / in /'
if [[ $status -eq 0 ]]; then
  echo "SELF-CHECK PASSED"
else
  echo "$out" | grep -E "ERROR|FAIL" | head -30
  echo "SELF-CHECK FAILED (mvn exit code $status). Paste the lines above to debug."
fi
exit $status
