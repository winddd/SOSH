package parse;

import org.apache.commons.lang3.NotImplementedException;

import parse.binary.BinaryParser;
import parse.binary.BinaryRMWRangeParser;
import parse.binary.BinaryHybridSnapshotParser;
import parse.cobra.CobraHistoryParser;
import parse.edn.EDNKVParser;
import parse.eiger.EigerParser;
import parse.json.JsonKvParser;
import parse.json.JsonHybridSnapshotParser;
import parse.juicefs.JuiceFSParser;
import parse.plume.PlumeParser;
import parse.tapir.TapirHistoryParser;
import parse.timekiller.TimekillerParser;
import util.Config;
import util.enumtypes.HISTORY_FORMAT;

/**
 * Factory for creating format-specific transaction history parsers.
 *
 * <p>Boomslang supports multiple transaction history formats from different database systems
 * and benchmarking frameworks. This factory provides a unified interface for instantiating
 * the appropriate parser based on the history format specified in the configuration.</p>
 *
 * <h2>Supported Formats</h2>
 * <ul>
 *   <li>{@code JSON} - Standard JSON format used by Jepsen and similar frameworks</li>
 *   <li>{@code BINARY} - Compact binary format for large-scale logs</li>
 *   <li>{@code BINARY_RMW} - Binary format with read-modify-write and range query support</li>
 *   <li>{@code HYBRID_SNAPSHOT_BINARY} - Binary format with hybrid snapshot epoch splitting (mixed snapshot/current reads)</li>
 *   <li>{@code HYBRID_SNAPSHOT_JSON} - JSON format with hybrid snapshot epoch splitting (mixed snapshot/current reads)</li>
 *   <li>{@code COBRA} - Format specific to Cobra database</li>
 *   <li>{@code TAPIR} - Format from TAPIR transactional protocol</li>
 *   <li>{@code JUICEFS} - JuiceFS metadata transaction logs</li>
 *   <li>{@code EDN} - Extensible Data Notation format (Clojure-based)</li>
 *   <li>{@code PLUME} - Plume distributed database format</li>
 *   <li>{@code TIMEKILLER} - TimeKiller benchmark format</li>
 *   <li>{@code EIGER} - Eiger causal consistency system format</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * Config cfg = Config.builder()
 *     .historyFormat(HISTORY_FORMAT.JSON)
 *     .build();
 * Parser parser = KVParserFactory.getParser(HISTORY_FORMAT.JSON, cfg);
 * KVHistory history = parser.parse(inputPath);
 * }</pre>
 *
 * <h2>Design Notes</h2>
 * <p>All parsers implement the {@link Parser} interface, which defines a contract for
 * converting raw transaction logs into the internal {@link history.KVHistory} representation.
 * The factory pattern decouples client code from parser implementation details and allows
 * easy extension to support additional formats.</p>
 *
 * @see Parser
 * @see util.enumtypes.HISTORY_FORMAT
 * @see history.KVHistory
 * @since 1.0
 */
public class KVParserFactory {
  /**
   * Creates a parser instance for the specified history format.
   *
   * <p>The factory instantiates the appropriate parser implementation based on the
   * format parameter. Each parser is configured with the provided {@link Config} object,
   * which may contain format-specific parsing options.</p>
   *
   * @param format the transaction history format to parse
   * @param cfg the configuration object containing parsing options and system settings
   * @return a parser instance capable of reading the specified format
   * @throws NotImplementedException if the format is not recognized or not yet implemented
   * @see HISTORY_FORMAT
   */
  public static Parser getParser(HISTORY_FORMAT format, Config cfg) {
    Parser parser;
    switch (format) {
      case JSON -> parser = new JsonKvParser(cfg);
      case BINARY -> parser = new BinaryParser(cfg);
      case BINARY_RMW -> parser = new BinaryRMWRangeParser(cfg);
      case HYBRID_SNAPSHOT_BINARY -> parser = new BinaryHybridSnapshotParser(cfg);
      case HYBRID_SNAPSHOT_JSON -> parser = new JsonHybridSnapshotParser(cfg);
      case COBRA -> parser = new CobraHistoryParser(cfg);
      case TAPIR -> parser = new TapirHistoryParser(cfg);
      case JUICEFS -> parser = new JuiceFSParser(cfg);
      case EDN -> parser = new EDNKVParser(cfg);
      case PLUME -> parser = new PlumeParser(cfg);
      case TIMEKILLER -> parser = new TimekillerParser(cfg);
      case EIGER -> parser = new EigerParser(cfg);
      default -> throw new NotImplementedException("Only json format is allowed");
    }

    return parser;
  }
}
