#!/usr/bin/env python3
"""Normalize TraceScope and baseline outputs into one comparison-friendly CSV."""

from __future__ import annotations

import csv
import json
import pathlib
import re
import sys
from collections import defaultdict
from typing import Any


COBRA_E2E = re.compile(r"Overall runtime\s*=\s*([0-9.]+)ms")
POLYSI_E2E = re.compile(r"^ENTIRE_EXPERIMENT:\s*([0-9.]+)ms", re.MULTILINE)
ELLE_VERDICT = re.compile(r"\t\s*(true|false|:unknown)\s*$", re.MULTILINE)
TIME_FIELDS = {
    "Elapsed (wall clock) time (h:mm:ss or m:ss)": "wall_sec",
    "Maximum resident set size (kbytes)": "max_rss_kb",
    "Exit status": "exit_status",
}

FIG15_TABLE_ROWS = [
    ("f15_yuga_g2a", "SER", "G2-anomaly", "YugaByte 1.3.1.0", "37.2K"),
    ("f15_yuga_disw", "SER", "Lost updates", "YugaByte 1.1.10.0", "2.8K"),
    ("f15_cock_g2", "SER", "G2-anomaly", "CockroachDB beta", "446"),
    ("f15_cock_blog", "SER", "Read uncommitted", "CockroachDB 2.1", "20"),
    ("f15_fauna", "SER", "Read skew", "FaunaDB 2.5.4", "8.2K"),
    ("f15_tapir_3clients", "SER", "Fractured reads", "Tapir", "547"),
    ("f15_tapir_4clients", "SER", "Fractured reads", "Tapir", "480"),
    ("f15_tapir_g1", "SER", "G1-anomaly", "Tapir", "13,156"),
    ("f15_tapir_g2", "SER", "G2-anomaly", "Tapir", "9,082"),
    ("f15_lost_update", "SI", "Lost updates", "MongoDB 4.2.6", "23.2K"),
    ("f15_aborted_read", "SI", "Aborted read", "MongoDB 4.2.6", "2.2K"),
    ("f15_cyclic", "SI", "G1c-anomaly", "MongoDB 4.2.6", "1.1K"),
    ("f15_future_read", "SI", "Read future writes", "MongoDB 4.2.6", "4.6K"),
    ("f15_tidb_skew", "SI", "Read skew", "TiDB 2.1.7", "9.1K"),
    ("f15_plume_maria", "SI", "Non-repeatable read", "MariaDB-Galera 10.4.22", "1K"),
    ("f15_plume_yuga", "SI", "G-SI-anomaly", "Yugabyte 2.11.1", "20"),
    ("f15_tapir_rt", "SSER", "Time inversion", "Tapir", "68"),
    ("f15_plume_antidote", "PL-2+", "Thin-air read", "AntidoteDB 0.2.2", "120"),
]


def infer_checker(run_name: str) -> str:
    for checker in ("cobra", "polysi", "viper", "elle"):
        if f"_{checker}_" in run_name:
            return checker
    return "tracescope"


def duration_seconds(value: str) -> float:
    parts = value.split(":")
    if len(parts) == 2:
        minutes, seconds = parts
        return int(minutes) * 60 + float(seconds)
    if len(parts) == 3:
        hours, minutes, seconds = parts
        return int(hours) * 3600 + int(minutes) * 60 + float(seconds)
    raise ValueError(f"unsupported elapsed-time value: {value!r}")


def read_time_file(path: pathlib.Path) -> dict[str, Any]:
    if not path.is_file():
        return {}
    text = path.read_text(encoding="utf-8", errors="replace")
    metrics: dict[str, Any] = {}
    signal = re.search(r"Command terminated by signal\s+(\d+)", text)
    if signal:
        metrics["signal"] = int(signal.group(1))
    for line in text.splitlines():
        stripped = line.strip()
        for label, field in TIME_FIELDS.items():
            prefix = f"{label}:"
            if not stripped.startswith(prefix):
                continue
            value = stripped[len(prefix) :].strip()
            if field == "wall_sec":
                metrics[field] = duration_seconds(value)
            else:
                metrics[field] = int(value)
    return metrics


def load_paper_metadata(manifests_dir: pathlib.Path) -> dict[str, dict[str, str]]:
    metadata: dict[str, dict[str, str]] = {}
    for path in sorted(manifests_dir.glob("fig*-runs.tsv")):
        with path.open(encoding="utf-8", newline="") as stream:
            for row in csv.DictReader(stream, delimiter="\t"):
                metadata[row["id"]] = {
                    "figure": path.name.split("-", 1)[0],
                    "label": row["label"],
                    "paper_value": row["paper_value"],
                }
    baseline_manifest = manifests_dir / "baseline-runs.tsv"
    if baseline_manifest.is_file():
        with baseline_manifest.open(encoding="utf-8", newline="") as stream:
            for row in csv.DictReader(stream, delimiter="\t"):
                metadata[row["id"]] = {
                    "figure": row["figure"],
                    "label": row["label"],
                    "paper_value": row["paper_value"],
                }
    return metadata


def final_status(row: dict[str, Any]) -> str:
    if row.get("timeout_sec"):
        return "timeout"
    if row.get("signal"):
        return f"signal_{row['signal']}"
    if row.get("exit_status") not in (None, 0):
        return f"exit_{row['exit_status']}"
    if row.get("verdict") in ("SAT", "UNSAT", "ACCEPT", "REJECT"):
        return "completed"
    return "unknown"


def jsonl_rows(path: pathlib.Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    attempts: defaultdict[str, int] = defaultdict(int)
    with path.open(encoding="utf-8") as stream:
        for line_number, line in enumerate(stream, 1):
            if not line.strip():
                continue
            try:
                record = json.loads(line)
            except json.JSONDecodeError as error:
                raise SystemExit(f"{path}:{line_number}: invalid JSON: {error}") from error
            if len(record) != 1:
                raise SystemExit(f"{path}:{line_number}: expected one run-name key")
            run_name, payload = next(iter(record.items()))
            attempts[run_name] += 1
            row: dict[str, Any] = {
                "source": path.name,
                "run_name": run_name,
                "attempt": attempts[run_name],
                "checker": infer_checker(run_name),
            }
            if isinstance(payload, dict):
                row.update(payload)
                if isinstance(payload.get("sat"), bool):
                    row["verdict"] = "SAT" if payload["sat"] else "UNSAT"
            rows.append(row)
    return rows


def baseline_row(stdout_path: pathlib.Path) -> dict[str, Any]:
    run_name = stdout_path.name.removesuffix(".stdout.log")
    checker = infer_checker(run_name)
    text = stdout_path.read_text(encoding="utf-8", errors="replace")
    row: dict[str, Any] = {
        "source": stdout_path.name,
        "run_name": run_name,
        "attempt": 1,
        "checker": checker,
    }
    pattern = None
    if checker == "cobra":
        pattern = COBRA_E2E
    elif checker == "polysi":
        pattern = POLYSI_E2E
    if pattern is not None:
        match = pattern.search(text)
        if match:
            row["e2e"] = float(match.group(1)) / 1000.0
    if "[[[[ ACCEPT ]]]]" in text:
        row["verdict"] = "ACCEPT"
    elif "[[[[ REJECT ]]]]" in text:
        row["verdict"] = "REJECT"
    elif checker == "elle":
        match = ELLE_VERDICT.search(text)
        if match and match.group(1) == "true":
            row["verdict"] = "ACCEPT"
        elif match and match.group(1) == "false":
            row["verdict"] = "REJECT"
    return row


def write_fig12_plot_csv(rows: list[dict[str, Any]], output: pathlib.Path) -> int:
    """Write one numeric row per duration for direct plotting in a spreadsheet."""
    tracescope: dict[int, dict[str, Any]] = {}
    elle: dict[int, dict[str, Any]] = {}

    for row in rows:
        run_name = str(row["run_name"])
        trace_match = re.fullmatch(r"f12_(\d+)", run_name)
        elle_match = re.fullmatch(r"f12_elle_(\d+)", run_name)
        if trace_match:
            duration = int(trace_match.group(1))
            if row.get("attempt", 0) >= tracescope.get(duration, {}).get("attempt", 0):
                tracescope[duration] = row
        elif elle_match:
            duration = int(elle_match.group(1))
            if row.get("attempt", 0) >= elle.get(duration, {}).get("attempt", 0):
                elle[duration] = row

    durations = sorted(set(tracescope) | set(elle))
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(
            stream,
            fieldnames=[
                "run_id",
                "TraceScope",
                "Elle",
                "TraceScope w/o encoding",
            ],
        )
        writer.writeheader()
        for duration in durations:
            trace_row = tracescope.get(duration, {})
            trace_e2e = trace_row.get("e2e")
            encoding = trace_row.get("normal search: encode")
            without_encoding: float | str = ""
            if isinstance(trace_e2e, (int, float)) and isinstance(encoding, (int, float)):
                without_encoding = round(float(trace_e2e) - float(encoding), 6)
            writer.writerow(
                {
                    "run_id": f"f12_{duration}",
                    "TraceScope": trace_e2e if trace_e2e is not None else "",
                    "Elle": elle.get(duration, {}).get("e2e", ""),
                    "TraceScope w/o encoding": without_encoding,
                }
            )
    return len(durations)


def write_fig15_table_csv(rows: list[dict[str, Any]], output: pathlib.Path) -> int:
    """Write Figure 15 results in the same row order and vocabulary as the paper."""
    latest = {}
    for row in rows:
        run_name = str(row["run_name"])
        if row.get("attempt", 0) >= latest.get(run_name, {}).get("attempt", 0):
            latest[run_name] = row

    output.parent.mkdir(parents=True, exist_ok=True)
    fieldnames = [
        "table_row",
        "isolation",
        "anomaly",
        "database",
        "transactions",
        "run_name",
        "status",
        "verdict",
        "time_sec",
        "paper_time",
        "wall_sec",
        "max_rss_kb",
        "raw_result",
    ]
    written = 0
    with output.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=fieldnames)
        writer.writeheader()
        for table_row, (run_name, isolation, anomaly, database, transactions) in enumerate(
            FIG15_TABLE_ROWS, 1
        ):
            if run_name not in latest:
                continue
            row = latest[run_name]
            writer.writerow(
                {
                    "table_row": table_row,
                    "isolation": isolation,
                    "anomaly": anomaly,
                    "database": database,
                    "transactions": transactions,
                    "run_name": run_name,
                    "status": row.get("status", ""),
                    "verdict": row.get("verdict", ""),
                    "time_sec": row.get("e2e", ""),
                    "paper_time": row.get("paper_value", ""),
                    "wall_sec": row.get("wall_sec", ""),
                    "max_rss_kb": row.get("max_rss_kb", ""),
                    "raw_result": row.get("source", ""),
                }
            )
            written += 1
    return written


def main() -> int:
    if len(sys.argv) not in (3, 5) or (len(sys.argv) == 5 and sys.argv[3] != "--figure"):
        print(
            f"Usage: {sys.argv[0]} RESULTS_DIR OUTPUT.csv [--figure figXX]",
            file=sys.stderr,
        )
        return 2

    results_dir = pathlib.Path(sys.argv[1])
    output = pathlib.Path(sys.argv[2])
    selected_figure = sys.argv[4] if len(sys.argv) == 5 else None
    metadata = load_paper_metadata(results_dir.parent / "manifests")
    rows: list[dict[str, Any]] = []
    jsonl_stems: set[str] = set()

    for path in sorted(results_dir.glob("*.jsonl")):
        jsonl_stems.add(path.stem)
        rows.extend(jsonl_rows(path))

    # Cobra and PolySI emit text rather than JSONL. Viper already emits JSONL.
    for path in sorted(results_dir.glob("*.stdout.log")):
        run_name = path.name.removesuffix(".stdout.log")
        checker = infer_checker(run_name)
        if run_name in jsonl_stems:
            continue
        if checker not in ("cobra", "polysi", "viper", "elle") and run_name not in metadata:
            continue
        rows.append(baseline_row(path))

    if not rows:
        raise SystemExit(f"No supported results found under {results_dir}")

    for row in rows:
        run_name = str(row["run_name"])
        stdout_path = results_dir / f"{run_name}.stdout.log"
        if stdout_path.is_file():
            stdout_text = stdout_path.read_text(encoding="utf-8", errors="replace")
            timeout_match = re.search(r"^RUN_TIMEOUT_SECONDS=(\d+) exceeded$", stdout_text, re.MULTILINE)
            if timeout_match:
                row["timeout_sec"] = int(timeout_match.group(1))
        timeout_record = results_dir / f"{run_name}.timeout.txt"
        if timeout_record.is_file():
            timeout_value = timeout_record.read_text(encoding="utf-8").strip()
            if timeout_value.isdigit() and int(timeout_value) > 0:
                row["timeout_sec"] = int(timeout_value)
        row.update(read_time_file(results_dir / f"{run_name}.time.txt"))
        if row.get("checker") == "elle" and "e2e" not in row and "wall_sec" in row:
            row["e2e"] = row["wall_sec"]
        row.update(metadata.get(run_name, {}))
        if not row.get("figure"):
            match = re.match(r"f(\d+)_", run_name)
            if match:
                row["figure"] = f"fig{int(match.group(1)):02d}"
        row["status"] = final_status(row)

    if selected_figure is not None:
        rows = [row for row in rows if row.get("figure") == selected_figure]
        if selected_figure == "fig13":
            allowed_ids = {
                run_name for run_name, info in metadata.items()
                if info["figure"] == "fig13"
            }
            rows = [row for row in rows if row["run_name"] in allowed_ids]
        if not rows:
            raise SystemExit(
                f"No supported results for {selected_figure} under {results_dir}"
            )

    if selected_figure == "fig12":
        row_count = write_fig12_plot_csv(rows, output)
        print(f"Wrote {row_count} Figure 12 plot rows to {output}")
        return 0

    if selected_figure == "fig15":
        row_count = write_fig15_table_csv(rows, output)
        print(f"Wrote {row_count} Figure 15 table rows to {output}")
        return 0

    preferred = [
        "figure",
        "run_name",
        "attempt",
        "checker",
        "status",
        "verdict",
        "e2e",
        "wall_sec",
        "max_rss_kb",
        "exit_status",
        "signal",
        "paper_value",
        "label",
        "source",
    ]
    all_fields = {key for row in rows for key in row}
    fieldnames = [field for field in preferred if field in all_fields]
    fieldnames.extend(sorted(all_fields - set(fieldnames)))
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)

    print(f"Wrote {len(rows)} normalized rows to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
