# Case 3: Auditing JuiceFS metadata operations

This case is just Figure 6's TiKV SI JuiceFS row (`f06_09`). No separate history
or checking script is needed.

From the repository root, with the artifact image already built:

```bash
source artifacts/env.sh.example
artifacts/scripts/reproduce-fig06.sh f06_09
```
