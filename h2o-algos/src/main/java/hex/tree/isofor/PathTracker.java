package hex.tree.isofor;

import water.fvec.Chunk;

/**
 * Helper class - encodes lengths of paths for observations separately for OOB and when they were used for tree building.
 */
class PathTracker {

  static int encodeNewPathLength(Chunk tree, int row, int depth, boolean wasOOB) {
    final long old_len_enc = tree.at8(row);
    final long len_enc = addNewPathLength(old_len_enc, depth, wasOOB);
    tree.set(row, len_enc);
    return decodeTotalPathLength(len_enc); 
  }

  static int decodeOOBPathLength(Chunk tree, int row) {
    return decodeOOBPathLength(tree.at8(row));
  }

  private static int decodeTotalPathLength(long lengthEncoded) {
    long total_len = (lengthEncoded >> 31) + (lengthEncoded & 0x7fffffff);
    assert total_len == (int) total_len;
    return (int) total_len;
  }

  static int decodeOOBPathLength(long lengthEncoded) {
    return (int) (lengthEncoded >> 31);
  }

  static long addNewPathLength(long oldLengthEncoded, int depth, boolean wasOOB) {
    if (wasOOB) {
      return oldLengthEncoded + ((long) depth << 31);
    } else {
      return oldLengthEncoded + depth;
    }
  }


}
