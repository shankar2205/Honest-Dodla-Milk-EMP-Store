#!/usr/bin/env python3
"""Audit Supabase migration filenames without renaming or applying migrations.

Legacy numeric prefixes are reported, not treated as a replayable chronology.
New timestamped migrations must use unique 14-digit versions.
"""
from collections import defaultdict
from pathlib import Path
import re
import sys

root = Path("supabase/migrations")
if not root.is_dir():
    sys.exit("ERROR: supabase/migrations directory not found")

files = sorted(p for p in root.glob("*.sql") if p.is_file())
versions = defaultdict(list)
legacy = defaultdict(list)
errors = []

for path in files:
    prefix = path.name.split("_", 1)[0]
    if prefix.isdigit() and len(prefix) == 14:
        versions[prefix].append(path.name)
    elif prefix.isdigit():
        legacy[prefix].append(path.name)
    else:
        errors.append(f"Unrecognized migration filename prefix: {path.name}")

print(f"Scanned {len(files)} SQL migration files.")
collisions = {v: names for v, names in legacy.items() if len(names) > 1}
if collisions:
    print("Legacy numeric prefix collisions (documented; do not rename blindly):")
    for version, names in sorted(collisions.items()):
        print(f"  {version}: {', '.join(sorted(names))}")
else:
    print("No legacy numeric prefix collisions found.")

for version, names in sorted(versions.items()):
    if len(names) > 1:
        errors.append(f"Duplicate timestamped migration version {version}: {', '.join(names)}")

timestamped = [p.name for p in files if re.match(r"^\d{14}_.*\.sql$", p.name)]
for path in files:
    prefix = path.name.split("_", 1)[0]
    if len(prefix) == 14 and prefix.isdigit() and not re.match(r"^\d{14}_.+\.sql$", path.name):
        errors.append(f"Timestamped migration must include a descriptive suffix: {path.name}")

if errors:
    print("Migration filename audit failed:")
    for error in errors:
        print(f"  ERROR: {error}")
    sys.exit(1)

print(f"Timestamped migrations: {len(timestamped)}; versions are unique.")
print("Filename audit passed. This does not prove migration replay safety or schema equivalence.")
