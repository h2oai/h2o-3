package water.rapids;

/**
 * Created by mdowle on 10/1/15.
 */

// Since we have a single key field in H2O (different to data.table), bmerge() becomes a lot simpler (no
// need for recursion through join columns) with a downside of transfer-cost should we not need all the key.

public class BinaryMerge {
  long _retFirst[];  // The row number of the first right table's index key that matches
  long _retLen[];    // How many rows does it match to?
  byte _leftKey[/*n2GB*/][/*i mod 2GB * _keySize*/];
  byte _rightKey[][];
  boolean _allLen1 = true;
  int _keySize;  // Same size key both sides. TO DO: not only left and right different sizes, but composition different. Vectors of min and range/numbytes needed to be attached to key, vector along key columns

  BinaryMerge(byte[][] leftKey, byte[][] rightKey, long leftN, long rightN, int keySize) {   // In X[Y], 'left'=i and 'right'=x
    _leftKey = leftKey;
    _rightKey = rightKey;
    _retFirst = new long[(int)leftN];    // TO DO: allow more than 2bn
    _retLen = new long[(int)leftN];
    _keySize = keySize;
    bmerge_r(-1, leftN, -1, rightN);
  }

  int keycmp(byte x[][], long xi, byte y[][], long yi) {   // TO DO - faster way closer to CPU like batches of long compare, maybe.
    xi *= _keySize;
    yi *= _keySize;   // x[] and y[] are len keys. All keys fixed length and left size same size as right size, currently.  Same return value as strcmp in C. <0 => xi<yi
    // TO DO: rationalize x and y being chunked into 2GB pieces.  Take x[0][] and y[0][] outside loop / function
    int len = _keySize;
    while (len > 1 && x[0][(int)xi] == y[0][(int)yi]) { xi++; yi++; len--; }
    return (x[0][(int)xi] - y[0][(int)yi]);   // For comparison don't need to 0xff both sides, can compare the signed bytes.  for getting back from -1 to 255
  }

  void bmerge_r(long lLowIn, long lUppIn, long rLowIn, long rUppIn) {
    // TO DO: parallel each of the 256 bins
    long lLow = lLowIn, lUpp = lUppIn, rLow = rLowIn, rUpp = rUppIn;
    long mid, tmpLow, tmpUpp;
    long lr = lLow + (lUpp - lLow) / 2;   // i.e. (lLow+lUpp)/2 but being robust to one day in the future someone somewhere overflowing long; e.g. 32 exabytes of 1-column ints
    while (rLow < rUpp - 1) {
      mid = rLow + (rUpp - rLow) / 2;
      int cmp = keycmp(_leftKey, lr, _rightKey, mid);  // -1, 0 or 1, like strcmp
      if (cmp < 0) {
        rUpp = mid;
      } else if (cmp > 0) {
        rLow = mid;
      } else { // rKey == lKey including NA == NA
        // branch mid to find start and end of this group in this column
        // TO DO?: not if mult=first|last and col<ncol-1
        tmpLow = mid;
        tmpUpp = mid;
        while (tmpLow < rUpp - 1) {
          mid = tmpLow + (rUpp - tmpLow) / 2;
          if (keycmp(_leftKey, lr, _rightKey, mid) == 0) tmpLow = mid;
          else rUpp = mid;
        }
        while (rLow < tmpUpp - 1) {
          mid = rLow + (tmpUpp - rLow) / 2;
          if (keycmp(_leftKey, lr, _rightKey, mid) == 0) tmpUpp = mid;
          else rLow = mid;
        }
        break;
      }
    }
    // rLow and rUpp now surround the group in the right table.

    // The left table key may (unusually, and not recommended, but sometimes needed) be duplicated.
    // Linear search outwards from left row. Most commonly, the first test shows this left key is unique.
    // This saves i) re-finding the matching rows in the right for all the dup'd left and ii) recursive bounds logic gets awkward if other left rows can find the same right rows
    // Related to 'allow.cartesian' in data.table.
    // TO DO:  if index stores attribute that it is unique then we don't need this step. However, each of these while()s would run at most once in that case, which may not be worth optimizing.
    tmpLow = lr + 1;
    while (tmpLow<lUpp && keycmp(_leftKey, tmpLow, _leftKey, lr)==0) tmpLow++;
    lUpp = tmpLow;
    tmpUpp = lr - 1;
    while (tmpUpp>lLow && keycmp(_leftKey, tmpUpp, _leftKey, lr)==0) tmpUpp--;
    lLow = tmpUpp;
    // lLow and lUpp now surround the group in the left table.  If left key is unique then lLow==lr-1 and lUpp==lr+1.

    long len = rUpp - rLow - 1;  // if value found, rLow and rUpp surround it, unlike standard binary search where rLow falls on it
    if (len > 1) _allLen1 = false;
    for (long j = lLow + 1; j < lUpp; j++) {   // usually iterates once only for j=lr, but more than once if there are dup keys in left table
      _retFirst[(int)j] = rLow + 1;
      _retLen[(int)j] = len;
    }
    // TO DO: check assumption that retFirst and retLength are initialized to 0, for case of no match
    // Now branch (and TO DO in parallel) to merge below and merge above
    if (lLow > lLowIn && rLow > rLowIn)
      bmerge_r(lLowIn, lLow + 1, rLowIn, rLow+1);
    if (lUpp < lUppIn && rUpp < rUppIn)
      bmerge_r(lUpp-1, lUppIn, rUpp-1, rUppIn);
  }
}
