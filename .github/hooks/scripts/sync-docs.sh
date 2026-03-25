#!/usr/bin/env python3
"""
Hook: sync-docs
Event: PostToolUse
Purpose: After editing source code, remind the agent to update the microservice
         README.md if the change affects public APIs, configuration, or behavior.

Triggers on: .java, .gradle, .yaml, .yml source files (not test files, not READMEs).
"""

import json
import os
import sys


DOC_RELEVANT_EXTENSIONS = {".java", ".gradle", ".yaml", ".yml", ".properties"}
SKIP_PATTERNS = ("src/test", "README.md", "readme.md")


def get_file_paths(tool_name: str, tool_input: dict) -> list[str]:
    """Extract all edited file paths from the tool input."""
    if tool_name == "multi_replace_string_in_file":
        return [
            r.get("filePath", r.get("file_path", ""))
            for r in tool_input.get("replacements", [])
            if r.get("filePath", r.get("file_path", ""))
        ]
    return [tool_input.get("filePath", tool_input.get("file_path", ""))]


def should_check_docs(file_path: str) -> bool:
    """Return True if this file change may require documentation updates."""
    if not file_path:
        return False
    if any(pat in file_path for pat in SKIP_PATTERNS):
        return False
    _, ext = os.path.splitext(file_path)
    return ext.lower() in DOC_RELEVANT_EXTENSIONS


def find_readme(file_path: str) -> str | None:
    """Find the README.md of the microservice owning this file."""
    parts = file_path.replace("\\", "/").split("/")
    for i, part in enumerate(parts):
        if part.startswith("ms-"):
            # Reconstruct up to the ms- root
            if os.path.isabs(file_path):
                ms_root = "/" + "/".join(parts[1 : i + 1])
            else:
                ms_root = "/".join(parts[: i + 1])
            candidate = os.path.join(ms_root, "README.md")
            if os.path.isfile(candidate):
                return candidate
            return None  # ms- root found but no README
    return None


def main():
    try:
        data = json.load(sys.stdin)
    except Exception:
        sys.exit(0)

    EDIT_TOOLS = {
        "create_file",
        "replace_string_in_file",
        "multi_replace_string_in_file",
    }

    tool_name = data.get("tool_name", "")
    if tool_name not in EDIT_TOOLS:
        sys.exit(0)

    tool_input = data.get("tool_input", {})
    file_paths = get_file_paths(tool_name, tool_input)

    lines = []
    for file_path in dict.fromkeys(file_paths):  # preserve order, deduplicate
        if not should_check_docs(file_path):
            continue

        base = os.path.basename(file_path)
        readme = find_readme(file_path)

        if readme:
            lines.append(
                f"  - '{base}' was modified → if this changes public APIs, "
                f"configuration, or notable behavior, update '{readme}'."
            )
        else:
            lines.append(
                f"  - '{base}' was modified → if this changes public APIs, "
                f"configuration, or notable behavior, update the relevant README.md."
            )

    if lines:
        msg = "[sync-docs] Documentation sync check:\n" + "\n".join(lines)
        print(json.dumps({"systemMessage": msg}))


if __name__ == "__main__":
    main()
