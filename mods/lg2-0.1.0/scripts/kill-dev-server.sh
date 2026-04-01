#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MOD_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
SERVER_ROOT="$(cd "${MOD_DIR}/../.." && pwd)"
LOCK_FILE="${SERVER_ROOT}/world/session.lock"
SERVER_ARGFILE="${MOD_DIR}/build/loom-cache/argFiles/runServer"
SERVER_MAIN_FLAG="fabric.dli.main=net.fabricmc.loader.impl.launch.knot.KnotServer"

declare -a SERVER_PIDS=()

contains_pid() {
	local pid="$1"
	local existing
	for existing in "${SERVER_PIDS[@]:-}"; do
		if [[ "${existing}" == "${pid}" ]]; then
			return 0
		fi
	done
	return 1
}

add_server_pid() {
	local pid="$1"
	if [[ -z "${pid}" || ! "${pid}" =~ ^[0-9]+$ ]]; then
		return
	fi
	if [[ ! -d "/proc/${pid}" ]]; then
		return
	fi
	if contains_pid "${pid}"; then
		return
	fi
	SERVER_PIDS+=("${pid}")
}

cmdline_for_pid() {
	local pid="$1"
	if [[ ! -r "/proc/${pid}/cmdline" ]]; then
		return 1
	fi
	tr '\0' ' ' < "/proc/${pid}/cmdline"
}

looks_like_dev_server_pid() {
	local pid="$1"
	local cmdline
	cmdline="$(cmdline_for_pid "${pid}" 2>/dev/null || true)"
	if [[ -z "${cmdline}" ]]; then
		return 1
	fi
	[[ "${cmdline}" == *"${SERVER_MAIN_FLAG}"* ]] || [[ "${cmdline}" == *"${SERVER_ARGFILE}"* ]] || [[ "${cmdline}" == *"net.fabricmc.devlaunchinjector.Main nogui"* ]]
}

collect_lock_holder_pids() {
	if [[ ! -e "${LOCK_FILE}" ]]; then
		return
	fi
	if command -v lsof >/dev/null 2>&1; then
		while IFS= read -r pid; do
			if looks_like_dev_server_pid "${pid}"; then
				add_server_pid "${pid}"
			fi
		done < <(lsof -t "${LOCK_FILE}" 2>/dev/null | sort -u)
	fi
}

collect_server_command_pids() {
	if ! command -v pgrep >/dev/null 2>&1; then
		return
	fi
	while IFS= read -r pid; do
		add_server_pid "${pid}"
	done < <(pgrep -f "${SERVER_MAIN_FLAG}" || true)
	while IFS= read -r pid; do
		add_server_pid "${pid}"
	done < <(pgrep -f "${SERVER_ARGFILE}" || true)
}

wait_for_exit() {
	local pid="$1"
	local remaining_checks="${2:-40}"
	while (( remaining_checks > 0 )); do
		if [[ ! -d "/proc/${pid}" ]]; then
			return 0
		fi
		sleep 0.2
		remaining_checks=$((remaining_checks - 1))
	done
	return 1
}

collect_lock_holder_pids
collect_server_command_pids

if [[ "${#SERVER_PIDS[@]}" -eq 0 ]]; then
	echo "Dev server process not found."
	exit 0
fi

echo "Stopping dev server PID(s): ${SERVER_PIDS[*]}"

for pid in "${SERVER_PIDS[@]}"; do
	if [[ -d "/proc/${pid}" ]]; then
		kill "${pid}" 2>/dev/null || true
	fi
done

declare -a FORCE_PIDS=()
for pid in "${SERVER_PIDS[@]}"; do
	if [[ -d "/proc/${pid}" ]] && ! wait_for_exit "${pid}" 40; then
		FORCE_PIDS+=("${pid}")
	fi
done

if [[ "${#FORCE_PIDS[@]}" -gt 0 ]]; then
	echo "Force killing stuck dev server PID(s): ${FORCE_PIDS[*]}"
	for pid in "${FORCE_PIDS[@]}"; do
		if [[ -d "/proc/${pid}" ]]; then
			kill -9 "${pid}" 2>/dev/null || true
		fi
	done
fi

echo "Done."
