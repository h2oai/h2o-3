package water.rapids.ast.prims.filters.dropduplicates;

/**
 * Determines which duplicated row is kept during row de-duplication process.
 */
public enum KeepOrder {
  First, // Retain first, drop rest
  Last // Retain last, drop rest
}
