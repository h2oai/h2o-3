package water.util;

import water.Keyed;

/**
 * Keyed type to represent empty result type (aka null pointer).
 */
final public class KeyedVoid extends Keyed<KeyedVoid> {

  @Override
  protected long checksum_impl() {
    return 43;
  }
}
