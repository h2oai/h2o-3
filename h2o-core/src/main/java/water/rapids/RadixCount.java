package water.rapids;

import water.*;
import water.fvec.Chunk;
import water.util.MathUtils;

import java.math.BigInteger;
import static java.math.BigInteger.ONE;
import static java.math.BigInteger.ZERO;

class RadixCount extends MRTask<RadixCount> {
  static class Long2DArray extends Iced {
    Long2DArray(int len) { _val = new long[len][]; }
    long _val[][];
  }
  private Long2DArray _counts;
  private final int _shift;
  private final int _col;
  private final BigInteger _base;
  // used to determine the unique DKV names since DF._key is null now and
  // before only an RTMP name anyway
  private final boolean _isLeft; 
  private final int _id_maps[][];
  private final int _ascending;
  final long _mergeId;

  RadixCount(boolean isLeft, BigInteger base, int shift, int col, int id_maps[][], int ascending, long mergeId) {
    _isLeft = isLeft;
    _base = base;
    _col = col;
    _shift = shift;
    _id_maps = id_maps;
    _ascending = ascending;
    _mergeId = mergeId;
  }

  // make a unique deterministic key as a function of frame, column and node
  // make it homed to the owning node.  Add current system time to make it not
  // repeatable.  This can cause problem if sort is used in cross-validation

  /***
   * make a unique deterministic key as a function of frame, column and node make it homed to the owning node. 
   * Add current system time to make it not repeatable.  This can cause problem if sort is used in cross-validation
   */
  static Key getKey(boolean isLeft, int col, long mergeId, H2ONode node) {
    return Key.make("__radix_order__MSBNodeCounts_col" + col + "_node" + node.index() + "_" + mergeId + 
            (isLeft ? "_LEFT" : "_RIGHT"));
    // Each node's contents is different so the node number needs to be in the key
    // TODO: need the biggestBit in here too, that the MSB is offset from
  }

  @Override protected void setupLocal() {
    _counts = new Long2DArray(_fr.anyVec().nChunks());
  }

  @Override public void map( Chunk chk ) {
    long tmp[] = _counts._val[chk.cidx()] = new long[256];
    boolean isIntVal = chk.vec().isCategorical() || chk.vec().isInt();
    // TODO: assert chk instanceof integer or enum; -- but how since many
    if (chk.vec().isCategorical()) {
      assert _id_maps[0].length > 0;
      assert _base.compareTo(ZERO)==0;
      if (chk.vec().naCnt() == 0) {
        for (int r=0; r<chk._len; r++) {
          int ctrVal = _isLeft?BigInteger.valueOf(_id_maps[0][(int)chk.at8(r)]+1).shiftRight(_shift).intValue()
                  :BigInteger.valueOf((int)chk.at8(r)+1).shiftRight(_shift).intValue();
          tmp[ctrVal]++;
        }
      } else {
        for (int r=0; r<chk._len; r++) {
          if (chk.isNA(r)) tmp[0]++;
          else {
            int ctrVal = _isLeft?BigInteger.valueOf(_id_maps[0][(int)chk.at8(r)]+1).shiftRight(_shift).intValue()
                    :BigInteger.valueOf((int)chk.at8(r)+1).shiftRight(_shift).intValue();
            tmp[ctrVal]++;
          }
        }
      }
    } else if (!(_isLeft && chk.vec().isCategorical())) {
      if (chk.vec().naCnt() == 0) { // no NAs in column
        // There are no NA in this join column; hence branch-free loop. Most
        // common case as should never really have NA in join columns.
        for (int r = 0; r < chk._len; r++) {    // note that 0th bucket here is for rows to exclude from merge result
          long ctrVal = isIntVal ?
                  BigInteger.valueOf(chk.at8(r)*_ascending).subtract(_base).add(ONE).shiftRight(_shift).longValue():
                  MathUtils.convertDouble2BigInteger(_ascending*chk.atd(r)).subtract(_base).add(ONE).shiftRight(_shift).longValue();
          tmp[(int) ctrVal]++;  // ctrVal is the MSB value of chk.at8(r)
        }
      } else {    // contains NAs in column
        // There are some NA in the column so have to branch.  TODO: warn user
        // NA are present in join column
        for (int r=0; r<chk._len; r++) {
          if (chk.isNA(r)) tmp[0]++;
          else {
            long ctrVal = isIntVal ?
                    BigInteger.valueOf(_ascending*chk.at8(r)).subtract(_base).add(ONE).shiftRight(_shift).longValue():
                    MathUtils.convertDouble2BigInteger(_ascending*chk.atd(r)).subtract(_base).add(ONE).shiftRight(_shift).longValue();
            tmp[(int) ctrVal]++;
          }

          // Done - we will join NA to NA as data.table does
          // TODO: allow NA-to-NA join to be turned off.  Do that in bmerge as a simple low-cost switch.
          // Note that NA and the minimum may well both be in MSB 0 but most of
          // the time we will not have NA in join columns
        }
      }
    }
  }

  @Override protected void closeLocal() {
    DKV.put(getKey(_isLeft, _col, _mergeId, H2O.SELF), _counts, _fs, true);
    // just the MSB counts per chunk on this node.  Most of this spine will be empty here.  
    // TODO: could condense to just the chunks on this node but for now, leave sparse.
    // We'll use this sparse spine right now on this node and the reduce happens on _o and _x later
  }
}
