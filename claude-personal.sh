#!/usr/bin/env bash
# Start Claude Code for this project under a SEPARATE, personal profile.
#
#   ./claude-personal.sh              # the harness itself; first run: /login, personal account
#   ./claude-personal.sh <repo-dir>   # a repo you were given, after ./attach.sh <repo-dir>
#
# What the separate profile (CLAUDE_CONFIG_DIR) gives you:
#   - its own login, so your personal account is used, not a work SSO/Bedrock setup
#   - none of your other profile's MCP servers (Jira, Slack, GitLab...), global CLAUDE.md,
#     settings, or chat history
# Work-related API variables are cleared for this process only; your shell is unchanged.
set -euo pipefail
harness="$(cd "$(dirname "$0")" && pwd -P)"
workdir="$harness"
if [[ $# -gt 0 && -d "$1" ]]; then
  workdir="$(cd "$1" && pwd -P)"
  shift
fi
if [[ "$workdir" != "$harness" && ! -f "$workdir/.claude/harness/ATTACHED" ]]; then
  echo "Run $harness/attach.sh \"$workdir\" first: it adds the commands and fences Claude into that repo." >&2
  exit 1
fi
cd "$workdir"

policy_dir="/Library/Application Support/ClaudeCode"
if [[ -d "$policy_dir" ]]; then
  echo "NOTE: '$policy_dir' exists: your organisation manages Claude Code on this Mac." >&2
  echo "      Its policy overrides personal settings. After starting, run /status and confirm the" >&2
  echo "      account is your personal one and the provider is not Bedrock. If not, don't use it." >&2
fi

export CLAUDE_CONFIG_DIR="$HOME/.claude-personal"
unset CLAUDE_CODE_USE_BEDROCK CLAUDE_CODE_USE_VERTEX AWS_BEARER_TOKEN_BEDROCK \
      ANTHROPIC_BASE_URL ANTHROPIC_API_KEY ANTHROPIC_AUTH_TOKEN ANTHROPIC_MODEL \
      ANTHROPIC_SMALL_FAST_MODEL ANTHROPIC_DEFAULT_OPUS_MODEL ANTHROPIC_DEFAULT_SONNET_MODEL

[[ -f .claude/settings.local.json ]] || "$harness/lockdown.sh" "$workdir"
exec claude "$@"
