#!/usr/bin/env bash

upsert_javascript_string_config() {
  local file="$1"
  local variable="$2"
  local value="$3"
  local escaped_value
  local replacement
  local temporary
  local replaced="false"

  if [[ ! "$variable" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
    echo "Invalid JavaScript configuration variable: $variable" >&2
    return 1
  fi

  if [[ "$value" == *$'\n'* || "$value" == *$'\r'* ]]; then
    echo "$variable must not contain line breaks" >&2
    return 1
  fi

  escaped_value="${value//\\/\\\\}"
  escaped_value="${escaped_value//\"/\\\"}"
  replacement="var ${variable} = \"${escaped_value}\";"
  temporary="$(mktemp "${file}.XXXXXX")"

  while IFS= read -r line || [[ -n "$line" ]]; do
    if [[ "$line" =~ ^(//)?var[[:space:]]+${variable}[[:space:]]*= ]]; then
      if [[ "$replaced" == "false" ]]; then
        printf '%s\n' "$replacement" >> "$temporary"
        replaced="true"
      fi
    else
      printf '%s\n' "$line" >> "$temporary"
    fi
  done < "$file"

  if [[ "$replaced" == "false" ]]; then
    printf '%s\n' "$replacement" >> "$temporary"
  fi

  mv "$temporary" "$file"
}
