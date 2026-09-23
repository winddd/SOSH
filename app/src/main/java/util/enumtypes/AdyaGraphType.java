package util.enumtypes;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Catalog of the serialization graph variants introduced in Adya's thesis.
 * Each entry tracks the canonical abbreviation and a short description to
 * make the intent explicit when wiring multi-graph encodings.
 */
public enum AdyaGraphType {
  DSG("Direct Serialization Graph", "Conflict graph over committed transactions"),
  USG("Unfolded Serialization Graph", "Operation-level expansion of the DSG for a chosen transaction"),
  SUSG("Start-ordered Unfolded Serialization Graph", "USG augmented with start-dependency edges"),
  LDSG("Labeled Direct Serialization Graph", "DSG variants that retain item identifiers (Section 4.3)"),
  RSG("Realtime Serialization Graph", "DSG extended with real-time order edges"),
  DTG("Direct Transaction Graph", "Runtime analogue of the DSG used for executing transactions"),
  UTG("Unfolded Transaction Graph", "Runtime analogue of the USG focusing on read actions"),
  STG("Start-ordered Transaction Graph", "DTG annotated with start-dependencies for runtime SI"),
  SUTG("Start-ordered Unfolded Transaction Graph", "UTG augmented with start-dependency edges"),
  MSG("Mixed Serialization Graph", "Graph that treats SQL statements as PL-3 sub-transactions"),
  MSSG("Mixed Statement Serialization Graph", "MSG augmented with a dedicated node per SQL statement"),
  PL_3U_DSG("PL-3U Direct Serialization Graph", "DSG containing all update transactions of H and transaction Ti");

  private final String fullName;
  private final String summary;

  AdyaGraphType(String fullName, String summary) {
    this.fullName = fullName;
    this.summary = summary;
  }

  public String abbreviation() {
    return name();
  }

  public String fullName() {
    return fullName;
  }

  public String summary() {
    return summary;
  }

  public static Optional<AdyaGraphType> fromAbbreviation(String value) {
    if (value == null) {
      return Optional.empty();
    }
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    return Arrays.stream(values())
        .filter(type -> type.name().equals(normalized))
        .findFirst();
  }
}
