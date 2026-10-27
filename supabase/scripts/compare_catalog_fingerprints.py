#!/usr/bin/env python3
"""Compare two JSON catalog snapshots and report deterministic schema drift.

Snapshots should be generated from the same version of
supabase/tests/catalog_fingerprint_json.sql, one against each database.
The tool compares metadata only; it never connects to a database or changes it.
"""
from __future__ import annotations

import argparse
import json
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any


@dataclass(frozen=True)
class Difference:
    path: str
    kind: str
    baseline: Any = None
    candidate: Any = None


def _json_path(parent: str, key: str | int) -> str:
    if isinstance(key, int):
        return f"{parent}[{key}]"
    escaped = str(key).replace("~", "~0").replace("/", "~1")
    return f"{parent}/{escaped}"


def _canonical(value: Any) -> str:
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False)


def _keyed_objects(items: list[Any]) -> dict[str, dict[str, Any]] | None:
    """Index catalog rows by their stable object_key, if every row supports it."""
    if not all(isinstance(item, dict) and isinstance(item.get("object_key"), str)
               for item in items):
        return None
    result = {item["object_key"]: item for item in items}
    return result if len(result) == len(items) else None


def compare_json(baseline: Any, candidate: Any, path: str = "") -> list[Difference]:
    """Return leaf-level differences using stable paths and object-key ordering."""
    differences: list[Difference] = []
    if isinstance(baseline, dict) and isinstance(candidate, dict):
        for key in sorted(baseline.keys() - candidate.keys()):
            differences.append(Difference(_json_path(path, key), "removed", baseline[key], None))
        for key in sorted(candidate.keys() - baseline.keys()):
            differences.append(Difference(_json_path(path, key), "added", None, candidate[key]))
        for key in sorted(baseline.keys() & candidate.keys()):
            differences.extend(compare_json(baseline[key], candidate[key], _json_path(path, key)))
        return differences

    if isinstance(baseline, list) and isinstance(candidate, list):
        if baseline == candidate:
            return []
        baseline_keyed = _keyed_objects(baseline)
        candidate_keyed = _keyed_objects(candidate)
        if baseline_keyed is not None and candidate_keyed is not None:
            for key in sorted(baseline_keyed.keys() - candidate_keyed.keys()):
                differences.append(Difference(_json_path(path, key), "removed", baseline_keyed[key], None))
            for key in sorted(candidate_keyed.keys() - baseline_keyed.keys()):
                differences.append(Difference(_json_path(path, key), "added", None, candidate_keyed[key]))
            for key in sorted(baseline_keyed.keys() & candidate_keyed.keys()):
                differences.extend(compare_json(
                    baseline_keyed[key], candidate_keyed[key], _json_path(path, key)
                ))
            return differences

        # Arrays without a stable identity are compared as canonical values. This
        # avoids positional cascades, while keeping the report deterministic.
        baseline_map = {_canonical(item): item for item in baseline}
        candidate_map = {_canonical(item): item for item in candidate}
        if len(baseline_map) == len(baseline) and len(candidate_map) == len(candidate):
            for key in sorted(baseline_map.keys() - candidate_map.keys()):
                differences.append(Difference(f"{path}[item={key}]", "removed", baseline_map[key], None))
            for key in sorted(candidate_map.keys() - baseline_map.keys()):
                differences.append(Difference(f"{path}[item={key}]", "added", None, candidate_map[key]))
            return differences
        return [Difference(path or "/", "changed", baseline, candidate)]

    if type(baseline) is not type(candidate) or baseline != candidate:
        differences.append(Difference(path or "/", "changed", baseline, candidate))
    return differences


def load_snapshot(path: Path) -> Any:
    try:
        with path.open(encoding="utf-8") as handle:
            return json.load(handle)
    except (OSError, json.JSONDecodeError) as exc:
        raise ValueError(f"cannot read valid JSON snapshot at {path}: {exc}") from exc


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("baseline", type=Path, help="trusted baseline JSON snapshot")
    parser.add_argument("candidate", type=Path, help="candidate database JSON snapshot")
    parser.add_argument(
        "--fail-on-diff",
        action="store_true",
        help="exit with status 1 when differences exist (useful in CI)",
    )
    parser.add_argument("--json", action="store_true", help="emit machine-readable JSON report")
    args = parser.parse_args(argv)

    try:
        baseline = load_snapshot(args.baseline)
        candidate = load_snapshot(args.candidate)
    except ValueError as exc:
        print(str(exc), file=sys.stderr)
        return 2

    differences = compare_json(baseline, candidate)
    if args.json:
        print(json.dumps(
            {
                "equal": not differences,
                "difference_count": len(differences),
                "differences": [
                    {
                        "path": item.path,
                        "kind": item.kind,
                        "baseline": item.baseline,
                        "candidate": item.candidate,
                    }
                    for item in differences
                ],
            },
            indent=2,
            sort_keys=True,
        ))
    elif not differences:
        print("Catalog snapshots match.")
    else:
        print(f"Catalog drift detected: {len(differences)} difference(s).")
        for item in differences:
            print(f"- {item.kind}: {item.path}")
            if item.kind != "removed":
                print(f"    candidate: {json.dumps(item.candidate, sort_keys=True)}")
            if item.kind != "added":
                print(f"    baseline:  {json.dumps(item.baseline, sort_keys=True)}")

    return 1 if differences and args.fail_on_diff else 0


if __name__ == "__main__":
    raise SystemExit(main())
