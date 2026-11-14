#!/bin/bash
# Prints the build number (Core standard "versioning", rule 2, project exception: only dev is long-lived for now, see
# ADR 013): the count of the distinct commits of origin/dev and HEAD after a fetch, or the number in the DEV/BETA tag
# on HEAD (a rebuild).
# Run it in the project root. It refuses, with a reason on stderr and exit code 1, whenever the number cannot be exact;
# there is no fallback number. build.gradle.kts runs it before anything compiles.
refuse() { echo "$1" >&2; exit 1; }

# No system or user config: no include, core.* or replace setting outside the repository can change the number.
g() { GIT_CONFIG_NOSYSTEM=1 GIT_CONFIG_GLOBAL=/dev/null git "$@"; }

for name in GIT_DIR GIT_WORK_TREE GIT_GRAFT_FILE GIT_REPLACE_REF_BASE GIT_CONFIG_COUNT GIT_CONFIG_PARAMETERS GIT_NO_REPLACE_OBJECTS; do
  [ -z "${!name+x}" ] || refuse "$name is set; git must read this project's own history with no injected config"
done
top=$(g rev-parse --show-toplevel 2>/dev/null) || refuse "no git repository at the project root"
[ "$(cd "$top" && pwd -P)" = "$(pwd -P)" ] || refuse "git's top level ($top) is not the project root"
g rev-parse --verify -q HEAD >/dev/null || refuse "no commits, so no build number"
[ -z "$(g for-each-ref refs/replace)" ] || refuse "replace refs exist"
grafts=$(g rev-parse --path-format=absolute --git-path info/grafts) || refuse "cannot locate the git directory"
[ ! -e "$grafts" ] || refuse "grafts exist"
[ "$(g rev-parse --is-shallow-repository)" = "false" ] || refuse "the history is shallow, so the count would be too low"

# The fetch uses the person's own git config: GitHub credentials live there.
GIT_TERMINAL_PROMPT=0 git fetch -q --prune origin >/dev/null || refuse "git fetch origin failed; the count needs the current branches"
g rev-parse --verify -q "refs/remotes/origin/dev^{commit}" >/dev/null || refuse "origin/dev is missing"

# A rebuild of a DEV or BETA tag keeps the tag's number (the same commit, the same build).
tagged=$(g tag --points-at HEAD | sed -nE 's/^v[0-9]+\.[0-9]+\.[0-9]+\.(DEV|BETA)\.([0-9]+)$/\2/p' | sort -n | tail -1)
count=${tagged:-$(g rev-list --count refs/remotes/origin/dev HEAD)}
[ "$count" -ge 1 ] 2>/dev/null && [ "$count" -le 2100000000 ] || refuse "build number '$count' is not a positive Int"
echo "$count"
