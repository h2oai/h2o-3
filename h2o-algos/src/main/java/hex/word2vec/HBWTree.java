package hex.word2vec;

import water.Key;
import water.Keyed;

import java.util.Arrays;

class HBWTree extends Keyed<HBWTree> {
  private static final int MAX_CODE_LENGTH = 40;

  int[][] _code;
  int[][] _point;

  public HBWTree() {}

  private HBWTree(Key<HBWTree> key, int size) {
    super(key);
    _code = new int[size][];
    _point = new int[size][];
  }

  static HBWTree buildHuffmanBinaryWordTree(long[] wordCounts) {
    final int size = wordCounts.length;

    long[] count = new long[size * 2 - 1];
    int[] binary = new int[size * 2 - 1];
    int[] parent_node = new int[size * 2 - 1];

    System.arraycopy(wordCounts, 0, count, 0, size);
    Arrays.fill(count, size, size * 2 - 1, (long) 1e15);

    // Following algorithm constructs the Huffman tree by adding one node at a time
    int min1i, min2i, pos1, pos2;
    pos1 = size - 1;
    pos2 = size;
    for (int i = 0; i < size - 1; i++) {
      // First, find two smallest nodes 'min1, min2'
      if (pos1 >= 0) {
        if (count[pos1] < count[pos2]) {
          min1i = pos1;
          pos1--;
        } else {
          min1i = pos2;
          pos2++;
        }
      } else {
        min1i = pos2;
        pos2++;
      }
      if (pos1 >= 0) {
        if (count[pos1] < count[pos2]) {
          min2i = pos1;
          pos1--;
        } else {
          min2i = pos2;
          pos2++;
        }
      } else {
        min2i = pos2;
        pos2++;
      }
      count[size + i] = count[min1i] + count[min2i];
      parent_node[min1i] = size + i;
      parent_node[min2i] = size + i;
      binary[min2i] = 1;
    }
    HBWTree t = new HBWTree(Key.<HBWTree>make(), size);
    int[] point = new int[MAX_CODE_LENGTH];
    int[] code = new int[MAX_CODE_LENGTH];
    // Now assign binary code to each vocabulary word
    for (int j = 0; j < size; j++) {
      int k = j;
      int m = 0;
      while (true) {
        int val = binary[k];
        code[m] = val;
        point[m] = k;
        m++;
        k = parent_node[k];
        if (k == 0) break;
      }
      t._code[j] = new int[m];
      t._point[j] = new int[m + 1];
      t._point[j][0] = size - 2;
      for (int l = 0; l < m; l++) {
        t._code[j][m - l - 1] = code[l];
        t._point[j][m - l] = point[l] - size;
      }
    }
    return t;
  }

}
