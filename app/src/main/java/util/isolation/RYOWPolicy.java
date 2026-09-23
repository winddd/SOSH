package util.isolation;

public enum RYOWPolicy {
  /* If there is a most-recent write, must read from this write. */
  MUST_RYOW,
  /* No matter if there is a most-recent write, must from other txns. */
  MUST_EXT,
  /* Can read from either most-recent self write or other txns. */
  EITHER
}
