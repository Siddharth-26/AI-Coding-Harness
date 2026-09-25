---
description: Run the tests and explain the next failing one in plain words (no solution, no algorithm hint)
argument-hint: [test class or method to focus on]
---
Focus: $ARGUMENTS

Follow `.claude/harness/RULES.md`. Run the tests with the command in `.claude/harness/REPO_NOTES.md`
(if it doesn't exist, tell me to run /onboard first). Take the focus test, or otherwise the first
failing test in the suggested order.

Tell me, in at most 8 lines:
- the test name and the behaviour it expects, in plain words (input → expected output, state or
  exception);
- the assertion that fails and the actual value;
- which production class and method must change (file:line), and what it does today instead.

Do not propose the approach or write code. If I want help with the approach I'll run /hint.
