#!/usr/bin/env python3
"""Checks that classes which are read or written with Gson keep their names in the release build.

R8 renames classes and fields of a release build. Gson needs the real field names, so every class that
goes through Gson has to be listed in app/proguard-rules.pro and here.
"""
import re
import sys

MAPPING = "app/build/outputs/mapping/release/mapping.txt"
GSON_CLASSES = [
    "com.kutlusoy.tvsig.remote.RemoteSettings",
    "com.kutlusoy.tvsig.repositories.OfflinePolicy$FileMeta",
    "com.kutlusoy.tvsig.models.MediaFile",
]
FIELD = re.compile(r"^\s+(?:[\w.$\[\]]+)\s+(\w+)\s+->\s+(\w+)$")


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else MAPPING
    problems = []
    lines = open(path, encoding="utf-8").read().splitlines()
    for name in GSON_CLASSES:
        header = next((i for i, line in enumerate(lines) if line.startswith(name + " -> ")), None)
        if header is None:
            problems.append(f"{name} is not in the mapping (removed by R8?)")
            continue
        renamed_to = lines[header].split(" -> ")[1].rstrip(":")
        if renamed_to != name:
            problems.append(f"{name} was renamed to {renamed_to}")
        for line in lines[header + 1:]:
            if not line.startswith(" "):
                break
            match = FIELD.match(line)
            if match and match.group(1) != match.group(2):
                problems.append(f"{name}: field {match.group(1)} was renamed to {match.group(2)}")
    if problems:
        print("Gson classes are not kept, add them to app/proguard-rules.pro:", file=sys.stderr)
        for problem in problems:
            print("  " + problem, file=sys.stderr)
        sys.exit(1)
    print(f"keep rules ok for {len(GSON_CLASSES)} Gson classes")


if __name__ == "__main__":
    main()
