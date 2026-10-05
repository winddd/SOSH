# Case 5: Supporting mixed isolation guarantees

The paper's 38 LOC refers to `resolveCandidateWritesWithRyowPolicy()` in
`app/src/main/java/compile/v2/modules/ModuleUtility.java` (lines 112–149),
which implements the `MUST_RYOW`, `MUST_EXT`, and `EITHER` read-your-own-writes
policies. The current method has 38 physical lines, or 22 code lines with `cloc`.
