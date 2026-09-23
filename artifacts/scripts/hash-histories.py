#!/usr/bin/env python3
"""Create stable tree hashes for the Figure 6 parser inputs."""

from __future__ import annotations

import csv
import hashlib
import pathlib
import sys


def selected_files(path: pathlib.Path, history_format: str) -> list[pathlib.Path]:
    if history_format == "eiger":
        return [candidate for candidate in (path.with_name(path.name + "_I.txt"),
                                             path.with_name(path.name + "_RW.txt"))
                if candidate.is_file()]
    if not path.is_dir():
        raise SystemExit(f"History is not a directory: {path}")
    if history_format == "cobra":
        files = path.rglob("T*.log")
    elif history_format == "binary":
        files = path.rglob("J*.log")
    elif history_format == "juicefs":
        files = path.rglob("*.log")
    elif history_format == "tapir":
        files = (p for p in path.rglob("*") if p.is_file() and (p.suffix == ".log" or p.name == "full.txt"))
    else:
        raise SystemExit(f"Unsupported format {history_format!r} for {path}")
    return sorted(p for p in files if p.is_file())


def tree_digest(anchor: pathlib.Path, files: list[pathlib.Path]) -> tuple[str, int]:
    digest = hashlib.sha256()
    total_bytes = 0
    relative_root = anchor if anchor.is_dir() else anchor.parent
    for path in files:
        relative = path.relative_to(relative_root)
        digest.update(relative.as_posix().encode("utf-8"))
        digest.update(b"\0")
        with path.open("rb") as stream:
            while block := stream.read(1024 * 1024):
                digest.update(block)
                total_bytes += len(block)
        digest.update(b"\0")
    return digest.hexdigest(), total_bytes


def main() -> int:
    if len(sys.argv) != 2:
        print(f"Usage: {sys.argv[0]} OUTPUT", file=sys.stderr)
        return 2

    artifact_dir = pathlib.Path(__file__).resolve().parent.parent
    data_root = artifact_dir / "data" / "fig06"
    output = pathlib.Path(sys.argv[1])
    manifest = artifact_dir / "manifests" / "fig06-runs.tsv"

    rows: list[tuple[str, str, int, int, str]] = []
    with manifest.open(newline="", encoding="utf-8") as stream:
        for record in csv.DictReader(stream, delimiter="\t"):
            relative = record["history_relative_to_figure"]
            history = data_root / relative
            files = selected_files(history, record["format"])
            if not files:
                raise SystemExit(f"No parser inputs selected for {record['id']}: {history}")
            digest, total_bytes = tree_digest(history, files)
            rows.append((record["id"], digest, len(files), total_bytes, relative))
            print(f"{record['id']}: {len(files)} files, {total_bytes} bytes, {digest}")

    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8") as stream:
        stream.write("id\tsha256\tfile_count\ttotal_bytes\thistory_relative_to_figure\n")
        for row in rows:
            stream.write("\t".join(map(str, row)) + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
