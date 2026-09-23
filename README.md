# Boomslang Release

## Run the included rw_10k history

This is the raw `boomslang.jar` command for the included `rw_10k` history.
Run it once for each mode: `B_SER`, `B_RR`, `B_SI`, and `B_RC`.
The MonoSAT native library is expected to be installed in `/usr/local/lib`.
Run the command from the project root.
Call `./build.sh` first to generate `app/build/libs/boomslang.jar`.

```bash
export BOOMSLANG_HOME="$(pwd)"
./build.sh
MODE=B_SER
OUTPUT_FILE="boomslang_rw_10k_${MODE}.json"

java -Djava.library.path=/usr/local/lib -ea \
  -Dlog4j.configurationFile="$BOOMSLANG_HOME/log4j2.xml" \
  -jar "$BOOMSLANG_HOME/app/build/libs/boomslang.jar" \
  -h "$BOOMSLANG_HOME/test_logs/rw_10k/rw_10k" \
  -config "$BOOMSLANG_HOME/config.yaml" \
  -format cobra \
  -fromFile false \
  -solver monosat \
  -m "$MODE" \
  -run_name "rw_10k_${MODE}" \
  -output "$OUTPUT_FILE" \
  -session \
  -no_hashmap
```

## Run unit tests

Run tests from the project root. The release includes the session-order blackbox
TestNG test and its fixtures under `test_logs/unittest2`.

```bash
export BOOMSLANG_HOME="$(pwd)"
./gradlew --no-daemon --no-watch-fs :app:test
```

To run only the included session-order blackbox test:

```bash
export BOOMSLANG_HOME="$(pwd)"
./gradlew --no-daemon --no-watch-fs :app:test \
  --tests history.BlackboxWithSessionOrderTest
```
