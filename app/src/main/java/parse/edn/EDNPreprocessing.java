package parse.edn;

import static us.bpsm.edn.Keyword.newKeyword;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import parse.LogsLoader;
import us.bpsm.edn.Keyword;
import us.bpsm.edn.parser.Parseable;
import us.bpsm.edn.parser.Parser;
import us.bpsm.edn.parser.Parsers;

public class EDNPreprocessing {

    private static final Keyword TYPE_KEY = newKeyword("type");
    private static final Keyword VALUE_KEY = newKeyword("value");
    private static final Keyword PROCESS_KEY = newKeyword("process");
    private static final Keyword INDEX_KEY = newKeyword("index");
    private static final Keyword TIME_KEY = newKeyword("time");
    private static final Keyword F_KEY = newKeyword("f");
    private static final Keyword OK_KEYWORD = newKeyword("ok");
    private static final Keyword READ_KEYWORD = newKeyword("r");
    private static final Keyword WRITE_KEYWORD = newKeyword("w");
    private static final Keyword APPEND_KEYWORD = newKeyword("append");

    /**
     * Preprocesses history.edn to:
     * 1. Only keep successful transactions (:type :ok)
     * 2. Replace nil in read operations with empty list []
     * 3. Convert append operations to write operations with accumulated values using
     *    the most recent read of the same key within the transaction.
     *    Assumes every append is preceded by a read of that key.
     *
     * @param inputPath     directory containing the history.edn file
     * @param inputFileName history file to preprocess (typically "history.edn")
     * @param outputPath    path to write the preprocessed file
     * @throws IOException if file operations fail
     */
    public static void preprocessHistoryEdn(String inputPath, String inputFileName, String outputPath) throws IOException {
        Path historyPath = Paths.get(inputPath, inputFileName);
        String rawText = Files.readString(historyPath);
        String wrappedText = LogsLoader.preProcess(rawText);

        Parseable parseable = Parsers.newParseable(wrappedText);
        Parser parser = Parsers.newParser(Parsers.defaultConfiguration());

        Object parsed = parser.nextValue(parseable);
        if (!(parsed instanceof List<?> transactions)) {
            throw new IOException("Unable to parse EDN history at " + historyPath);
        }

        List<String> preprocessedLines = new ArrayList<>();

        for (Object txnObj : transactions) {
            if (!(txnObj instanceof Map<?, ?> txn)) {
                continue;
            }
            Object typeObj = txn.get(TYPE_KEY);
            if (!OK_KEYWORD.equals(typeObj)) {
                continue;
            }

            String processedLine = processSuccessfulTransaction(txn);
            preprocessedLines.add(processedLine);
        }

        Files.write(Paths.get(outputPath), preprocessedLines);
    }

    /**
     * Process a successful transaction line according to the preprocessing rules
     */
    private static String processSuccessfulTransaction(Map<?, ?> txn) {
        Object valueObj = txn.get(VALUE_KEY);
        if (!(valueObj instanceof List<?> operations)) {
            return formatTransaction(txn, new ArrayList<>());
        }

        Map<Long, List<Long>> txnState = new HashMap<>();
        List<List<Object>> processedOps = processOperations(operations, txnState);
        return formatTransaction(txn, processedOps);
    }

    /**
     * Process the operations list according to the preprocessing rules
     */
    private static List<List<Object>> processOperations(List<?> operations,
            Map<Long, List<Long>> txnState) {
        List<List<Object>> processedOps = new ArrayList<>();

        for (Object opObj : operations) {
            if (!(opObj instanceof List<?> opList) || opList.isEmpty()) {
                continue;
            }
            Object typeObj = opList.get(0);
            if (!(typeObj instanceof Keyword opType)) {
                continue;
            }

            if (READ_KEYWORD.equals(opType)) {
                processedOps.add(processReadOperation(opList, txnState));
            } else if (APPEND_KEYWORD.equals(opType)) {
                processedOps.add(processAppendOperation(opList, txnState));
            } else {
                processedOps.add(copyOperation(opList));
            }
        }

        return processedOps;
    }

    private static List<Long> cloneLongList(List<?> rawValues) {
        List<Long> cloned = new ArrayList<>(rawValues.size());
        for (Object value : rawValues) {
            cloned.add(toLong(value));
        }
        return cloned;
    }

    private static long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private static List<Object> processReadOperation(List<?> opList,
            Map<Long, List<Long>> txnState) {
        if (opList.size() < 2) {
            return copyOperation(opList);
        }
        List<Object> result = new ArrayList<>(3);
        result.add(READ_KEYWORD);

        long key = toLong(opList.get(1));
        result.add(key);

        Object value = opList.size() > 2 ? opList.get(2) : null;
        if (value == null) {
            List<Long> empty = new ArrayList<>();
            txnState.put(key, empty);
            result.add(new ArrayList<>(empty));
        } else if (value instanceof List<?>) {
            List<Long> cloned = cloneLongList((List<?>) value);
            txnState.put(key, cloned);
            result.add(new ArrayList<>(cloned));
        } else {
            long numeric = toLong(value);
            List<Long> singleton = new ArrayList<>();
            singleton.add(numeric);
            txnState.put(key, singleton);
            result.add(new ArrayList<>(singleton));
        }

        return result;
    }

    private static List<Object> processAppendOperation(List<?> opList,
            Map<Long, List<Long>> txnState) {
        if (opList.size() < 3) {
            return copyOperation(opList);
        }
        long key = toLong(opList.get(1));
        long appendedValue = toLong(opList.get(2));

        List<Long> currentState = txnState.get(key);
        if (currentState == null) {
            currentState = new ArrayList<>();
        } else {
            currentState = new ArrayList<>(currentState);
        }
        currentState.add(appendedValue);
        txnState.put(key, currentState);

        List<Object> result = new ArrayList<>(3);
        result.add(WRITE_KEYWORD);
        result.add(key);
        result.add(new ArrayList<>(currentState));
        return result;
    }

    private static List<Object> copyOperation(List<?> opList) {
        List<Object> result = new ArrayList<>(opList.size());
        for (int i = 0; i < opList.size(); i++) {
            Object value = opList.get(i);
            if (i == 0 && value instanceof Keyword) {
                result.add(value);
            } else if (value instanceof Number) {
                result.add(toLong(value));
            } else if (value instanceof List<?>) {
                result.add(cloneLongList((List<?>) value));
            } else {
                result.add(value);
            }
        }
        return result;
    }

    private static String formatTransaction(Map<?, ?> txn, List<List<Object>> operations) {
        StringBuilder builder = new StringBuilder();
        builder.append("{:index ")
                .append(toLong(txn.get(INDEX_KEY)))
                .append(", :time ")
                .append(toLong(txn.get(TIME_KEY)))
                .append(", :type ")
                .append(formatValue(txn.get(TYPE_KEY)))
                .append(", :process ")
                .append(toLong(txn.get(PROCESS_KEY)))
                .append(", :f ")
                .append(formatValue(txn.get(F_KEY)))
                .append(", :value ")
                .append(formatOperationsVector(operations))
                .append('}');
        return builder.toString();
    }

    private static String formatOperationsVector(List<List<Object>> operations) {
        StringBuilder builder = new StringBuilder();
        builder.append('[');
        for (int i = 0; i < operations.size(); i++) {
            if (i > 0) {
                builder.append(' ');
            }
            builder.append(formatVector(operations.get(i)));
        }
        builder.append(']');
        return builder.toString();
    }

    private static String formatVector(List<?> values) {
        StringBuilder builder = new StringBuilder();
        builder.append('[');
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(' ');
            }
            builder.append(formatValue(values.get(i)));
        }
        builder.append(']');
        return builder.toString();
    }

    private static String formatValue(Object value) {
        if (value == null) {
            return "nil";
        }
        if (value instanceof Keyword keyword) {
            return keyword.toString();
        }
        if (value instanceof List<?>) {
            return formatVector((List<?>) value);
        }
        if (value instanceof Number number) {
            return Long.toString(number.longValue());
        }
        if (value instanceof String str) {
            return '"' + str.replace("\"", "\\\"") + '"';
        }
        return value.toString();
    }

    public static void main(String[] args) throws IOException {
        String inputPath = args.length > 0
                ? args[0]
                : "/home/windkl/git_repos/Boomslang/test_logs/edn/stolon_append_20250916T151600.776-0400";
        String inputFileName = "history.edn";
        String outputPath = args.length > 1
                ? args[1]
                : Paths.get(inputPath, "history_preprocessed.edn").toString();

        System.out.println("Preprocessing " + inputPath + " -> " + outputPath);
        preprocessHistoryEdn(inputPath, inputFileName, outputPath);
        System.out.println("Finished preprocessing. Output written to " + outputPath);
    }
}
