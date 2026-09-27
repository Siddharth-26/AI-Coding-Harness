#!/usr/bin/env bash
# Runs HelloInterview's CLI from this folder: public npm registry, company git settings ignored.
# Applies only while this script runs; nothing else on the machine changes.
cd "$(dirname "$0")"
npm_config_registry=https://registry.npmjs.org/ \
GIT_CONFIG_GLOBAL=/dev/null GIT_CONFIG_NOSYSTEM=1 \
npx --yes @hellointerview/ai-coding@latest start "$@"
