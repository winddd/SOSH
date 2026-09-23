# TraceScope / Boomslang ATC'26 Artifact

Run all commands from the repository root. Place experiment inputs under
`artifacts/data/figXX/inputs/`; results are written under
`artifacts/results/`.

## Download and unpack the experiment data

Git clone this repo and download the data `SOSH-artifacts-data.tgz` from the [Google Drive](https://drive.google.com/file/d/1Q1ySRLVatPaEYgxMcF9IiPc_BsZujugl/view?usp=drive_link) and place it in
the repository root.
In the `SOSH` repository root, extract the archive into `artifacts/`:

```bash
cd ~/git_repos/SOSH
mkdir artifacts  # if not exists
tar -xzf SOSH-artifacts-data.tgz -C artifacts 
```

The archive contains a top-level `data/` directory, so the command creates
`artifacts/data/figXX/inputs/`. If the archive is stored elsewhere, replace
`SOSH-artifacts-data.tgz` with its path.

Cobra
runs require the NVIDIA Container Toolkit and a CUDA-capable GPU; all other
runs are CPU-only.

## Build

Run from the repository root:

```bash
artifacts/scripts/build-image.sh
```

## Smoke test

```bash
artifacts/scripts/smoke-test.sh
```

## Java heap configuration

The paper's TraceScope results were collected on bare metal with a 40 GB Java
heap (`-Xmx40g`). For artifact runs, use a 64 GB Docker memory limit and the
same 40 GB Java heap:

```bash
DOCKER_MEMORY=64g JAVA_HEAP=40g artifacts/scripts/reproduce-fig06.sh
```

Use the same `DOCKER_MEMORY=64g JAVA_HEAP=40g` prefix for any other figure
script. The Docker memory-plus-swap
limit defaults to the same 64 GB value.

Running a figure without the prefix uses the reduced-memory artifact setting
(`-Xmx16g` in a 20 GB container), which is intended for smoke testing rather
than matching the paper's memory configuration.

## Figure 6

```bash
artifacts/scripts/reproduce-fig06.sh
```

Pass IDs from `artifacts/manifests/fig06-runs.tsv` to run selected rows:

```bash
artifacts/scripts/reproduce-fig06.sh f06_03 f06_04
```

Result: `artifacts/results/fig06.csv`.
Raw results: `artifacts/results/f06_XX.{stdout.log,time.txt}`. You may refer to  `.stdout.log` for  the standard output.


The transaction count reported for CockroachDB TPC-C in Figure 6 was corrected from  18.2K  to  17.3K. The original TiKV JuiceFS history could not be recovered, so we reran the experiment using another JuiceFS history with 5.8K transactions. The artifact reports the results of these reruns.

## Figure 7

```bash
artifacts/scripts/reproduce-fig07.sh
```

Result: `artifacts/results/fig07.txt`.

The script runs `cloc` on every unique file listed below.

Figure 7 splits each checker into three checker-specific components: generating
the IR, selecting a pruning policy, and generating SMT constraints. Counts are
approximate `cloc` SLOC excluding blank lines and comments. Paths in the table
are relative to `app/src/main/java/`.

| Checker | Component | File and physical lines (approximate SLOC)                                                                                                         | Reason                                                                                          |
| ------- | --------- | -------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------- |
| SSER    | Gen. IR   | `compile/v2/compilers/StrictSerCompiler.java:1-181` **(55)**                                                                               | Generates the strict-serializability IR, including real-time-order edges.                       |
| SSER    | Pruning   | `util/isolation/AllEdgesPruningSpec.java:1-24` **(12)**                                                                                    | SSER may use every edge admitted by its isolation specification.                                |
| SSER    | Gen. SMT  | `encoders/MonoSATEncoder.java:1-239` **(103)**                                                                                             | Uses the common MonoSAT graph encoding.                                                         |
| SER     | Gen. IR   | `compile/v2/compilers/SerCompiler.java:1-164` **(51)**                                                                                     | Generates the DSG and its unknown-edge choices.                                                 |
| SER     | Pruning   | `util/isolation/AllEdgesPruningSpec.java:1-24` **(12)** + `util/isolation/ExcludeRtoPruningSpec.java:1-21` **(12)** = **24** | Uses all serializability-relevant graph edges but excludes real-time-order edges.               |
| SER     | Gen. SMT  | `encoders/MonoSATEncoder.java:1-239` **(103)**                                                                                             | Reuses the common MonoSAT graph encoding.                                                       |
| RR      | Gen. IR   | `compile/v2/compilers/Pl299Compiler.java:1-218` **(59)**                                                                                   | Generates the PL-2.99/repeatable-read IR.                                                       |
| RR      | Pruning   | `util/isolation/NoPrwPruningSpec.java:1-21` **(10)** + `util/isolation/ExcludeRtoPruningSpec.java:1-21` **(12)** = **22**    | Excludes predicate anti-dependencies and real-time-order edges from its pruning relation.       |
| RR      | Gen. SMT  | `encoders/MonoSATEncoder.java:1-239` **(103)**                                                                                             | Reuses the common MonoSAT graph encoding.                                                       |
| SI      | Gen. IR   | `compile/v2/compilers/PlSiCompiler.java:1-114` **(95)**                                                                                    | Generates the snapshot-isolation IR.                                                            |
| SI      | Pruning   | `util/isolation/AllEdgesPruningSpec.java:1-24` **(12)** + `util/isolation/ExcludeRtoPruningSpec.java:1-21` **(12)** = **24** | Uses its isolation-relevant edges but excludes real-time-order edges during pruning.            |
| SI      | Gen. SMT  | `encoders/MonoSATEncoder.java:1-239` **(103)**                                                                                             | Reuses the common MonoSAT graph encoding.                                                       |
| RC      | Gen. IR   | `compile/v2/compilers/RcCompiler.java:1-166` **(48)**                                                                                      | Generates the read-committed IR.                                                                |
| RC      | Pruning   | `util/isolation/DependencyOnlyPruningSpec.java:1-21` **(10)**                                                                              | Conservatively uses only WR/WW/PWR/CB dependency edges.                                         |
| RC      | Gen. SMT  | `encoders/MonoSATEncoder.java:1-239` **(103)**                                                                                             | Reuses the common MonoSAT graph encoding.                                                       |
| PL-2+   | Gen. IR   | `compile/v2/compilers/SerCompiler.java:1-164` **(51)**                                                                                     | Reuses SER's DSG and superposition construction.                                                |
| PL-2+   | Pruning   | `util/isolation/DependencyOnlyPruningSpec.java:1-21` **(10)**                                                                              | Performs conservative G1-only pruning; the specialized solver handles G-single.                 |
| PL-2+   | Gen. SMT  | `solvers/PL2PlusSolver.java:1-468` **(176)**                                                                                               | Adds the PL-2+-specific G-single constraint.                                                    |
| PL-CS   | Gen. IR   | `compile/v2/compilers/SerCompiler.java:1-164` **(51)**                                                                                     | Reuses SER's DSG and superposition construction.                                                |
| PL-CS   | Pruning   | `util/isolation/DependencyOnlyPruningSpec.java:1-21` **(10)**                                                                              | Performs conservative G1-only pruning; the specialized solver handles object-labelled G-cursor. |
| PL-CS   | Gen. SMT  | `solvers/CursorStabilitySolver.java:1-356` **(221)**                                                                                       | Adds the object-labelled G-cursor constraint.                                                   |
| PL-FCV  | Gen. IR   | `compile/v2/compilers/SerCompiler.java:1-164` **(51)**                                                                                     | Reuses SER's DSG and superposition construction.                                                |
| PL-FCV  | Pruning   | `util/isolation/DependencyOnlyPruningSpec.java:1-21` **(10)**                                                                              | Performs conservative G1-only pruning; the specialized solver handles G-SIb.                    |
| PL-FCV  | Gen. SMT  | `solvers/PlFcvSolver.java:1-317` **(181)**                                                                                                 | Adds the PL-FCV start-order/G-SIb constraint.                                                   |

The generic matrix construction and reachability algorithm are framework code
and are not counted as checker-specific LOC. Pruning is now
parameterized by the one-method `PruningSpec` interface. PL-2+, PL-CS, PL-FCV,
and RC reuse `DependencyOnlyPruningSpec`. (NOTE: sicne  the  code  evolves, the exact LOC  may  be not exactly the same as the  paper.)

## Figure 8

```bash
artifacts/scripts/reproduce-fig08.sh
```

Result: `artifacts/results/fig08.txt`.

`cloc` counts physical source lines, excluding blank lines and comments. Each
row gives the baseline source root followed by the checker-specific files used
by the corresponding implementation in this repository.


| Checker | Counted paths and LOC |
|---|---|
| Cobra | Baseline: `artifacts/baselines/cobra/source/src/main/java` (**16650**). Our implementation: `app/src/main/java/compile/v1/CobraSerGraphCompiler.java` (**81**) + `app/src/main/java/util/isolation/AllEdgesPruningSpec.java` (**12**) + `app/src/main/java/util/isolation/ExcludeRtoPruningSpec.java` (**12**) + `app/src/main/java/encoders/MonoSATEncoder.java` (**103**) = **208**. |
| Viper | Baseline: `artifacts/baselines/viper/source/src` (**2919**). Our implementation: `app/src/main/java/compile/v1/ViperSiGraphCompiler.java` (**93**) + `app/src/main/java/util/isolation/AllEdgesPruningSpec.java` (**12**) + `app/src/main/java/util/isolation/ExcludeRtoPruningSpec.java` (**12**) + `app/src/main/java/encoders/MonoSATEncoder.java` (**103**) = **220**. |
| PolySI | Baseline: `artifacts/baselines/polysi/source/src/main/java` (**4075**). Our implementation: `app/src/main/java/compile/v1/PolySiGraphCompiler.java` (**125**) + `app/src/main/java/graphs/graphs/PolySIOriginalGraph.java` (**56**) + `app/src/main/java/graphs/graphs/PolySIMatrixGraph.java` (**345**) + `app/src/main/java/graphs/graphs/interfaces/PolySIABGraph.java` (**12**) + `app/src/main/java/optimizations/reachability/pruner/PolySIPruner.java` (**76**) + `app/src/main/java/solvers/PolySISolver.java` (**184**) = **798**. |

The paper reports 386 LOC for the original Mode `P` implementation. After
submission, we fixed a correctness bug in the PolySI checker by introducing
extra code. The corrected
implementation is therefore larger, and the artifact reports its current
798 LOC rather than the pre-fix number in the paper.

## Figure 9

```bash
artifacts/scripts/reproduce-fig09.sh
```

Result: `artifacts/results/fig09.csv`.
Raw results: artifacts/results/f09_XX.{stdout.log,time.txt}. You may refer to  `.stdout.log` for  the standard output.


The artifact contains a fix to a bug in PolySI checker in Boomslang/Tracescope that was discovered after the paper submission. The fix reduces PolySI's performance relative to the numbers reported in Fig. 9 of the submitted version. We use the corrected implementation in the artifact, and the corresponding PolySI results will be updated in the camera-ready version. The correction does not affect the qualitative conclusions of Fig. 9.

## Figure 10

```bash
artifacts/scripts/reproduce-fig10.sh
```

This runs both TraceScope and Cobra. Cobra requires an NVIDIA GPU. Combined
result: `artifacts/results/fig10.csv`. And the  results  may  be affected by the GPU performance. The  paper results  were run  with a 3060Ti.

Raw results: artifacts/results/f10_XX.{stdout.log,time.txt}. You may refer to `.stdout.log` for  the standard output.

## Figure 11

```bash
artifacts/scripts/reproduce-fig11.sh
```

This runs TraceScope, Viper, and PolySI. Combined result:
`artifacts/results/fig11.csv`.
Raw results: artifacts/results/f11_XX.{stdout.log,time.txt}. You may refer to `.stdout.log` for  the standard output.


## Figure 12

```bash
artifacts/scripts/reproduce-fig12.sh
```

This runs both TraceScope and Elle on the list-append histories. Elle uses
the serializable consistency model, a 600-second per-history timeout.

For Tracescope/Boomslang, `e2e` means  the  end-to-end time. `normal search: encode`  refers to the encoding time.
The `TraceScope without encoding` series is
derived from the same TraceScope run as `e2e - "normal search: encode"`.
The script computes this subtraction automatically, all in seconds. 

Result:
`artifacts/results/fig12.csv`.
Raw results: artifacts/results/f12_XX.{stdout.log,time.txt}. 

## Figure 13

```bash
artifacts/scripts/reproduce-fig13.sh
```

Figure 13 runs the four plotted settings: optimized, noPrio, noReach, and noOpt.
Each run has a 600-second timeout, set in `reproduce-fig13.sh`.
A timed-out run is recorded as `timeout` in `fig13.csv`.

Result: `artifacts/results/fig13.csv`.
Raw results: artifacts/results/f13_XX.{stdout.log,time.txt}. You may refer to `.stdout.log` for  the standard output.


## Figure 14

```bash
artifacts/scripts/reproduce-fig14.sh
```

Each run has a 600-second timeout.

Result: `artifacts/results/fig14.csv`.
Raw results: artifacts/results/f14_XX.{stdout.log,time.txt}. You may refer to `.stdout.log` for  the standard output.


## Figure 15

```bash
artifacts/scripts/reproduce-fig15.sh
```

This runs the 18 histories shown in the paper table, with a 600-second timeout per run.

Result: `artifacts/results/fig15.csv`. Its rows follow the paper table order, and
the `isolation`, `anomaly`, `database`, and `transactions` columns identify the
corresponding paper row directly. `time_sec` is the reproduced result and
`paper_time` is the value printed in the paper.
Raw results: artifacts/results/f15_XX.{stdout.log,time.txt}. You may refer to `.stdout.log` for  the standard output.
