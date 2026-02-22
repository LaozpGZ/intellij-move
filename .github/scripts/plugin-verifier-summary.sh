#!/usr/bin/env bash
set -euo pipefail

REPORT_ROOT="${1:-build/reports/pluginVerifier}"
MAX_INTERNAL="${PLUGIN_VERIFIER_MAX_INTERNAL_API_USAGES:-}"
MAX_SCHEDULED="${PLUGIN_VERIFIER_MAX_SCHEDULED_FOR_REMOVAL_API_USAGES:-}"
MAX_DEPRECATED="${PLUGIN_VERIFIER_MAX_DEPRECATED_API_USAGES:-}"
ENFORCEMENT="${PLUGIN_VERIFIER_BUDGET_ENFORCEMENT:-strict}"

normalize_budget() {
  local var_name="$1"
  local value="$2"
  if [ -z "${value}" ]; then
    echo ""
    return
  fi
  if [[ "${value}" =~ ^[0-9]+$ ]]; then
    echo "${value}"
    return
  fi
  echo "Invalid ${var_name}='${value}', ignore this budget" >&2
  echo ""
}

MAX_INTERNAL="$(normalize_budget "PLUGIN_VERIFIER_MAX_INTERNAL_API_USAGES" "${MAX_INTERNAL}")"
MAX_SCHEDULED="$(normalize_budget "PLUGIN_VERIFIER_MAX_SCHEDULED_FOR_REMOVAL_API_USAGES" "${MAX_SCHEDULED}")"
MAX_DEPRECATED="$(normalize_budget "PLUGIN_VERIFIER_MAX_DEPRECATED_API_USAGES" "${MAX_DEPRECATED}")"

if [ "${ENFORCEMENT}" != "strict" ] && [ "${ENFORCEMENT}" != "warn" ]; then
  echo "Unknown PLUGIN_VERIFIER_BUDGET_ENFORCEMENT='${ENFORCEMENT}', fallback to 'strict'"
  ENFORCEMENT="strict"
fi

if [ ! -d "${REPORT_ROOT}" ]; then
  echo "Plugin Verifier report directory not found: ${REPORT_ROOT}"
  exit 0
fi

count_usages() {
  local file="$1"
  if [ ! -f "${file}" ]; then
    echo 0
    return
  fi
  grep -cve '^[[:space:]]*$' "${file}" || true
}

extract_verdict_count() {
  local verdict="$1"
  local label="$2"
  local value
  value="$(printf '%s\n' "${verdict}" | grep -Eo "[0-9]+ usages of ${label}" | head -n1 | grep -Eo '^[0-9]+' || true)"
  if [ -z "${value}" ]; then
    echo 0
  else
    echo "${value}"
  fi
}

summary_lines=()
violations=0
found_report=0

enforce_budget() {
  local label="$1"
  local current="$2"
  local budget="$3"
  if [ -z "${budget}" ]; then
    return
  fi
  if [ "${current}" -le "${budget}" ]; then
    return
  fi
  if [ "${ENFORCEMENT}" = "strict" ]; then
    violations=1
    summary_lines+=("  - budget violation: ${label} (${current}) > allowed (${budget})")
  else
    summary_lines+=("  - budget warning: ${label} (${current}) > allowed (${budget})")
  fi
}

while IFS= read -r verdict_file; do
  found_report=1
  verdict_dir="$(dirname "${verdict_file}")"
  verdict_rel="${verdict_dir#${REPORT_ROOT}/}"

  verdict_text="$(tr '\n' ' ' < "${verdict_file}" | sed 's/[[:space:]]\+/ /g')"
  internal_count="$(count_usages "${verdict_dir}/internal-api-usages.txt")"
  deprecated_count="$(extract_verdict_count "${verdict_text}" "deprecated API")"
  experimental_count="$(extract_verdict_count "${verdict_text}" "experimental API")"
  scheduled_count="$(extract_verdict_count "${verdict_text}" "scheduled for removal API")"
  if [ "${internal_count}" -eq 0 ]; then
    internal_count="$(extract_verdict_count "${verdict_text}" "internal API")"
  fi

  summary_lines+=("- \`${verdict_rel}\`: ${verdict_text} (internal=${internal_count}, scheduled=${scheduled_count}, deprecated=${deprecated_count}, experimental=${experimental_count})")

  enforce_budget "internal" "${internal_count}" "${MAX_INTERNAL}"
  enforce_budget "scheduled" "${scheduled_count}" "${MAX_SCHEDULED}"
  enforce_budget "deprecated" "${deprecated_count}" "${MAX_DEPRECATED}"
done < <(find "${REPORT_ROOT}" -type f -name "verification-verdict.txt" | sort)

if [ "${found_report}" -eq 0 ]; then
  summary_lines+=("- no verification-verdict.txt found under \`${REPORT_ROOT}\`")
fi

echo "Plugin Verifier Summary"
echo "Budget enforcement mode: ${ENFORCEMENT}"
printf '%s\n' "${summary_lines[@]}"

if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  {
    echo "### Plugin Verifier Summary"
    echo ""
    echo "- budget enforcement mode: \`${ENFORCEMENT}\`"
    printf '%s\n' "${summary_lines[@]}"
  } >> "${GITHUB_STEP_SUMMARY}"
fi

if [ "${violations}" -ne 0 ]; then
  echo "Plugin Verifier usage budget exceeded."
  exit 1
fi
