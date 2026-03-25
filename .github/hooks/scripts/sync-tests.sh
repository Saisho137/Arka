#!/usr/bin/env python3
"""
Hook: sync-tests
Event: PostToolUse
Purpose: After editing a Java source file, remind the agent to keep tests in sync.
         Pattern: src/main/java/**/*.java -> src/test/java/**/*Test.java
"""

import json
import os
import sys


def get_file_paths(tool_name: str, tool_input: dict) -> list[str]:
    """Extract all edited file paths from the tool input."""
    if tool_name == "multi_replace_string_in_file":
        return [
            r.get("filePath", r.get("file_path", ""))
            for r in tool_input.get("replacements", [])
            if r.get("filePath", r.get("file_path", ""))
        ]
    return [tool_input.get("filePath", tool_input.get("file_path", ""))]


def derive_test_path(source_path: str) -> str | None:
    """Derive the expected test file path from a source file path."""
    if "src/main/java" not in source_path:
        return None
    test_path = source_path.replace("src/main/java", "src/test/java")
    if test_path.endswith(".java") and not test_path.endswith("Test.java"):
        test_path = test_path[:-5] + "Test.java"
    return test_path


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
        if not file_path:
            continue
        test_path = derive_test_path(file_path)
        if not test_path:
            continue

        base = os.path.basename(file_path)
        test_base = os.path.basename(test_path)

        # Resolve relative paths against CWD (workspace root)
        abs_test = test_path if os.path.isabs(test_path) else os.path.join(os.getcwd(), test_path)

        if os.path.isfile(abs_test):
            lines.append(
                f"  - '{base}' was modified → review '{test_base}' to ensure coverage is still valid."
            )
        else:
            lines.append(
                f"  - '{base}' was modified → '{test_base}' does not exist at '{test_path}'. Consider adding unit tests for the changed logic."
            )

    if lines:
        msg = "[sync-tests] Test sync check:\n" + "\n".join(lines)
        print(json.dumps({"systemMessage": msg}))


if __name__ == "__main__":
    main()
