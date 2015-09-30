package water.rapids;

import water.Futures;
import water.H2O;
import water.MRTask;
import water.MemoryManager;
import water.fvec.C8Chunk;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.AtomicUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class RadixCount extends MRTask<RadixCount> {
  long _counts[][][];
  int _nChunks;
  RadixCount(int nChunks) { _counts = new long[8][nChunks][]; _nChunks = nChunks; }
  @Override public void map( Chunk chk ) {
    for (int b=0; b<8; b++) _counts[b][chk.cidx()] = new long[256];
    //long tmp[][] = _counts[chk.cidx()] = new long[8][256];
    assert chk instanceof C8Chunk;   // alternatively: chk.getClass().equals(C8Chunk.class)
    for (int r=0; r<chk._len; r++) {
      for (int b=0; b<8; b++) {
        _counts[b][chk.cidx()][(int) (chk.at8(r) >> (b*8) & 0xffL)]++;  // forget the L => wrong answer with no type warning from IntelliJ
      }
    }
  }
  @Override public void reduce(RadixCount g) {
    if (g._counts != _counts) {
      // merge the spine across two nodes
      System.out.println("Not yet implemented");
      throw H2O.unimpl();
      /*for (int c=0; c<_nChunks; c++) {
        if (g._counts[c] != null) {
          assert _counts[c] == null;
          _counts[c] = g._counts[c];
        } else {
          assert _counts[c] != null;
        }
      }*/
    }
  }
}


class MoveByFirstByte extends MRTask<MoveByFirstByte> {
  private static final int MAXVECLONG = 134217728, MAXVECBYTE = 1073741824;  // 2^30 / 8 or 1.   Since (int)2^31 is error and we want later to do >> for speed
  int _byte, _width;
  long _counts[][][], _targetOrder[][][];
  byte _targetX[][][];
  MoveByFirstByte(int Byte, long targetOrder[][][], byte targetX[][][], long counts[][][], int width) { _byte = Byte; _targetOrder = targetOrder; _targetX = targetX; _counts = counts; _width=width; }
  @Override public void map(Chunk chk[]) {
    long myCounts[] = _counts[_byte][chk[0].cidx()];
    long DateMin = 0;
    if (chk.length>1) DateMin = (long)chk[1].vec().min();
    for (int r=0; r<chk[0]._len; r++) {    // tight, branch free and cache efficient (surprisingly)
      long thisx = chk[0].at8(r);
      int group = (int) (thisx>>(_byte*8) & 0xffL);
      long target = myCounts[group]++;
      int chunk = (int)(target / MAXVECLONG);   // TO DO - this'll be wrong in the case of large keys and more than 1 chunk
      int offset = (int)(target % MAXVECLONG);
      _targetOrder[group][chunk][offset] = (long)r + chk[0].start();    // move i and the index.

      chunk = (int)((target * _width) / MAXVECBYTE);
      offset = (int)((target * _width) % MAXVECBYTE);

      int firstWidth = 5;
      byte thisTargetX[] = _targetX[group][chunk];
      for (int i = firstWidth-1; i >= 0; i--) {
        thisTargetX[offset+i] = (byte)(thisx & 0xFF);
        thisx >>= 8;
      }
      int secondWidth = 2;  // i.e. width - Byte
      if (chk.length > 1) {
        thisx = (chk[1].at8(r) - DateMin) / 3600000L;  // TO DO: time should be UTC but seems to be TZ including BST 1 hr shifts, hence /hours not /days here. Otherwise it combined 2015-03-08 & 2015-03-09
        assert thisx >= 0 && thisx <= 441*24;  // 441 is date range 2014-01-11 to 2015-03-28
        for (int i = _width-1; i >= _width-secondWidth; i--) {
          thisTargetX[offset+i] = (byte)(thisx & 0xFF);
          thisx >>= 8;
        }
      }
      // TODO: maintain min and max of each group here, for counting sort on next iteration?
      // 256 * 2 * 4K = 2MB < L2 cache
    }
  }
}
// each thread can now work group by group.  We need full size o[] for the result anyway (albeit not in Vec shape).  Only 1 vector's worth could be saved (targetX).
// generally this may be on each node (max 256 nodes in this setup currently. TO DO: generalize and bit walk the first MSD)

class dradix extends H2O.H2OCountedCompleter<dradix> {
  private static final int MAXVECLONG = 134217728, MAXVECBYTE = 1073741824;
  long _counts[][];
  long _o[][], _otmp[][];
  byte _x[][], _xtmp[][];
  byte keytmp[];
  //public long _groupSizes[][];

  // outputs ...
  long _groups[][], _nGroup[];
  int _whichGroup;

  long _start, _len;
  int _byte;
  int _keySize;   // bytes

  dradix(long groups[][], long nGroup[], int whichGroup, byte x[][], long o[][], long len, int keySize) {
    _start = 0;
    _len = len;
    _byte = _keySize = keySize;
    keytmp = new byte[_keySize];
    _counts = new long[keySize][256];
    _xtmp = new byte[x.length][];
    _otmp = new long[o.length][];
    assert x.length == o.length;
    // TO DO: just for max group in this byte possible?  Think not.  Just like data.table at this point.
    for (int i=0; i<x.length; i++) {    // Seems like no deep clone available in Java. Maybe System.arraycopy but maybe that needs target to be allocated first
      _xtmp[i] = Arrays.copyOf(x[i], x[i].length);
      _otmp[i] = Arrays.copyOf(o[i], o[i].length);
    }
    // TO DO: a way to share this working memory between threads.  Just create enough for the 4 threads active at any one time, basically.  Not 256 allocations.
    _x = x;
    _o = o;
    //_groupSizes = new long[256][];
    _groups = groups;
    _nGroup = nGroup;
    _whichGroup = whichGroup;
    _groups[_whichGroup] = new long[(int)Math.min(MAXVECLONG, len) ];   // at most len groups (i.e. all groups are 1 row)
  }
  @Override protected void compute2() {
    run(_start, _len, _keySize-1);
    tryComplete();
  }
  public void push(long size) {
    //int batch = (int)(_nGroup[_whichGroup] / (MAXVEC/1024));
    //int offset = (int)(_nGroup[_whichGroup] % (MAXVEC/1024));
    //if (batch > 255) throw H2O.unimpl();  // TO DO: shallow copy and grow
    //if (_groupSizes[batch] == null) _groupSizes[batch] = new long[MAXVEC/1024];   // TO DO:  constrain by vec.length().  Since that is upper bound on number of groups
    //if (_nGroup[_whichGroup] >= MAXVECLONG) throw H2O.unimpl();
    _groups[_whichGroup][(int)_nGroup[_whichGroup]++] = size;  // java will check bounds for us
  }

  public boolean less(byte x[], int xi, byte y[], int yi) {   // TO DO - faster way closer to CPU like batches of long compare, somehow.  strcmp in C?
    // strictly less.  big endian byte array
    xi*=_keySize; yi*=_keySize;   // x[] and y[] are keys of length len bytes
    int len=_keySize;
    while (len>0 && x[xi] == y[yi]) { xi++; yi++; len--; }
    return(len>0 ? (x[xi] & 0xff) < (y[yi] & 0xff) : false);   // 0xff for getting back from -1 to 255
  }

  public boolean equal(byte x[], int xi, byte y[], int yi) {   // TO DO - faster way closer to CPU like batches of long compare, somehow.  strcmp in C?
    // strictly less.  big endian byte array
    xi*=_keySize; yi*=_keySize;   // x[] and y[] are keys of length len bytes
    int len=_keySize;
    while (len>0 && x[xi] == y[yi]) { xi++; yi++; len--; }
    return(len==0);
  }
  // to do - combine less and equal into one strcmp style -1,0,+1.  Needed by bmerge.

  public void insert(long start, int len)   // only for small len
/*  orders both x and o by reference in-place. Fast for small vectors, low overhead.
    don't be tempted to binsearch backwards here, have to shift anyway;
    many memmove would have overhead and do the same thing.
    when nalast == 0, iinsert will be called only from within iradix, where o[.] = 0
    for x[.]=NA is already taken care of */
  {
    int batch = (int) (start / MAXVECLONG);
    int batch1 = (int) (start+len-1)/MAXVECLONG;
    assert batch==0;
    if (batch == batch1)  {
      // Within the same batch. Likely very often since len<=30
      byte _xbatch[] = _x[batch];  // taking this outside the loop does indeed make quite a big different (hotspot isn't catching this, then)
      long _obatch[] = _o[batch];
      int offset = (int) (start % MAXVECLONG);  // TO DO: doesn't consider alignment
      for (int i=1; i<len; i++) {
        if (less(_xbatch, offset+i, _xbatch, offset+i-1)) {
          System.arraycopy(_xbatch, (offset+i)*_keySize, keytmp, 0, _keySize);
          int j = i - 1;
          long otmp = _obatch[offset+i];
          do {
            System.arraycopy(_xbatch, (offset+j)*_keySize, _xbatch, (offset+j+1)*_keySize, _keySize);
            _obatch[offset+j+1] = _obatch[offset+j];
            j--;
          } while (j >= 0 && less(keytmp, 0, _xbatch, offset+j));
          System.arraycopy(keytmp, 0, _xbatch, (offset+j+1)*_keySize, _keySize);
          _obatch[offset + j + 1] = otmp;
        }
      }
      int tt = 0;
      for (int i=offset+1; i<offset+len; i++) {
        if (equal(_xbatch, i, _xbatch, i - 1)) tt++;
        else { push(tt + 1); tt = 0; }
      }
      push(tt+1);
    } else {
      assert batch == batch1-1;
      throw H2O.unimpl();
      // In a 20 million example we get nowhere near this limit
      // TODO:  We don't need to allocate nearly as much as MAXVEC for each 256 slot.  Much less and grow it.
    }
  }

  public void run(long start, long len, int Byte) {

    // System.out.println("run " + start + " " + len + " " + Byte);
    if (len < 200) {               // N_SMALL=200 is guess based on limited testing. Needs calibrate().
      // Was 50 based on sum(1:50)=1275 worst -vs- 256 cummulate + 256 memset + allowance since reverse order is unlikely.
      insert(start, (int)len);   // when nalast==0, iinsert will be called only from within iradix.
      // TO DO:  inside insert it doesn't need to compare the bytes so far as they're known equal,  so pass Byte (NB: not Byte-1) through to insert()
      // TO DO:  Maybe transposing keys to be a set of _keySize byte columns might in fact be quicker - no harm trying. What about long and varying length string keys?
      return;
    }
    int batch = 0;
    byte _xbatch[] = _x[batch];  // taking this outside the loop does indeed make quite a big different (hotspot isn't catching this, then)
    long thisHist[] = _counts[Byte];
    // thisHist reused and carefully set back to 0 below so we don't need to clear it now
    int idx = (int)start*_keySize + _keySize-Byte-1;
    int which=-1;
    for (long i = 0; i < len; i++) {
      which = 0xff & _xbatch[idx];
      thisHist[which]++;
      idx += _keySize;
      // maybe TO DO:  shorten key by 1 byte on each iteration, so we only need to thisx && 0xFF.  No, because we need for construction of final table key columns.
    }
    if (thisHist[which] == len) {
      // one bin has count len and the rest zero => next byte quick
      thisHist[which] = 0;  // important, clear for reuse
      if (Byte == 0) push(len);
      else run(start, len, Byte - 1);
      return;
    }
    long rollSum = 0;
    for (int c = 0; c < 256; c++) {
      long tmp = thisHist[c];
      if (tmp == 0) continue;
      thisHist[c] = rollSum;
      rollSum += tmp;
    }
    long _obatch[] = _o[batch];
    byte _xtmpbatch[] = _xtmp[batch];  // TO DO- ignoring >2^31 for now.  No batching.  Wish we had 64bit indexing in Java!
    long _otmpbatch[] = _otmp[batch];
    idx = (int)start*_keySize + _keySize-Byte-1;
    for (int i = 0; i < len; i++) {
      long target = thisHist[0xff & _xbatch[idx]]++;
      _otmpbatch[(int)(target)] = _obatch[(int)start+i];   // this must be kept in 8 bytes longs
      System.arraycopy( _xbatch, ((int)start+i)*_keySize, _xtmpbatch, (int)target*_keySize, _keySize );
      idx += _keySize;
      //  Maybe TO DO:  this can be variable byte width and smaller widths as descend through bytes (TO DO: reverse byte order so always doing &0xFF)
    }

    System.arraycopy(_otmpbatch, 0, _obatch, (int)start, (int)len);  // always use the beginning of _otmp and _xtmp just to reuse the first hot pages
    System.arraycopy(_xtmpbatch, 0, _xbatch, (int)start*_keySize, (int)len*_keySize);

    long itmp = 0;
    for (int i=0; i<256; i++) {
      if (thisHist[i]==0) continue;
      long thisgrpn = thisHist[i] - itmp;
      if (thisgrpn == 1 || Byte == 0) {
        // We push() here, rather than a sweep afterwards, to avoid anymore wide access to x across nodes.
        push(thisgrpn);   // Now that key across multi-columns, there is not need to flip group locator. In data.table do we push() in all-one case (unique data) or do we wait? (TO DO)
      } else {
        run(start+itmp, thisgrpn, Byte-1);
      }
      itmp = thisHist[i];
      thisHist[i] = 0;  // important, to save clearing counts on next iteration
    }
  }
}

class assignG extends H2O.H2OCountedCompleter<dradix> {
  private static final int MAXVECLONG = 134217728, MAXVECBYTE = 1073741824;
  long _gOut[][];   // wide writing to here, but thread safe due to each location written once only
  long _groups[];   // the group sizes
  long _nGroup;     // length of _groups because we over-allocated it
  long _o[][];      // the order for this segment
  long _firstGroupNum;
  assignG(long gOut[][], long groups[], long nGroup, long firstGroupNum, long o[][]) {
    _gOut = gOut;
    _groups = groups;
    _nGroup = nGroup;
    _firstGroupNum = firstGroupNum;
    _o = o;
  }
  @Override protected void compute2() {
    int oi = 0, batch=0;
    long _ob[] = _o[batch];
    long gOutBatch[] = _gOut[batch];
    for (int i=0; i<_nGroup; i++) {
      for (int j = 0; j < _groups[i]; j++) {
        long target = _ob[oi++];
        assert target < MAXVECLONG;
        gOutBatch[(int)(target)] = _firstGroupNum+i;
        if (oi == MAXVECLONG) {
          assert false;
          _ob = _o[++batch];
          oi -= MAXVECLONG;
        }
      }
    }
    tryComplete();
  }
}


class runSum extends MRTask<runSum> {
  private static final int MAXVECLONG = 134217728, MAXVECBYTE = 1073741824;
  long _g[];
  double _res[];
  runSum(long g[][], double res[]) { _g = g[0]; _res = res;}
  @Override public void map(Chunk chk) {
    // TODO: if spans 2 MAXVEC in g.  Currently hard coded to g[0]
    int j = (int)chk.start();
    for (int r=0; r<chk._len; r++) {
      AtomicUtils.DoubleArray.add(_res, (int) _g[j++], chk.at8(r));
    }
  }
}


/*
void cummulate(long x[][], long a, long b) {
    long rollSum = 0;
    for (int j=0; j<b; j++)
    for (int i=0; i<a; i++) {
        long tmp = x[i][j];
        x[i][j] = rollSum;
        rollSum += tmp;
    }
}
*/


// Since we have a single key field in H2O (different to data.table), bmerge() becomes a lot simpler (no need for recursion through join columns) with a downside of transfer-cost should we not need all the key

//public class bmerge {
//  public ArrayList<Object> _matchLen;
//  Query(ArrayList leftIndex, ArrayList rightIndex) {    // leftIndex.o and .x
//    // TO DO: parallel each of the 256 bins
//    long lLow=-1, lUpp=lN, rLow=-1, rUpp=rN;
//    long lr = lLow + (lUpp-lLow)/2;   // i.e. (lLow+lUpp)/2 but being robust to one day in the future someone somewhere overflowing long; e.g. 32 exabytes of 1-column ints
//    lKey = leftIndex.x[lr];
//    while(rLow < rUpp-1) {
//      mid = rLow + (rUpp-rLow)/2;
//      rKey = rightIndex.x[mid];
//      int cmp = less(rKey, lKey);  // -1, 0 or 1.  Perhaps rename to keycmp similar to strcmp in C with the same familiar API
//      if (cmp == -1) {          // relies on NA_INTEGER == INT_MIN, tested in init.c
//        rLow = mid;
//      } else if (cmp == 1) {   // TO DO: is *(&xlow, &xupp)[0|1]=mid more efficient than branch?
//        rUpp = mid;
//      } else { // rKey == lKey including NA == NA
//        // branch mid to find start and end of this group in this column
//        // TO DO?: not if mult=first|last and col<ncol-1
//        tmpLow = mid;
//        tmpUpp = mid;
//        while(tmpLow<rUpp-1) {
//          mid = tmpLow + (rUpp-tmpLow)/2;
//          rKey = rightIndex.x[mid];
//          if (less(rKey,lKey)==0) tmpLow=mid; else rUpp=mid;
//        }
//        while(rLow<tmpUpp-1) {
//          mid = rLow + (tmpUpp-rLow)/2;
//          rKey = rightIndex.x[mid];
//          if (less(rKey,lKey)==0) tmpUpp=mid; else rLow=mid;
//        }
//        // xLow and xUpp now surround the group in the right table
//        break;
//      }
//    }
//    // If the left table row key is duplicated (most usually when not using all the join columns) then find its extent
//    // now. Saves i) re-finding the matching rows in the right and ii) bound setting gets awkward if other left rows can find the same right rows
//    tmplow = lir;
//    tmpupp = lir;
//    while(tmplow<iupp-1) {   // TO DO: could double up from lir rather than halving from iupp
//      mid = tmplow + (iupp-tmplow)/2;
//      xval.i = INTEGER(ic)[ o ? o[mid]-1 : mid ];   // reuse xval to search in i
//      if (xval.i == ival.i) tmplow=mid; else iupp=mid;
//    }
//    while(ilow<tmpupp-1) {
//      mid = ilow + (tmpupp-ilow)/2;
//      xval.i = INTEGER(ic)[ o ? o[mid]-1 : mid ];
//      if (xval.i == ival.i) tmpupp=mid; else ilow=mid;
//    }
//    // ilow and iupp now surround the group in ic, too
//    break;
//
//  }
//
//
//// new helper class A extend MRTask.  In its reduce it'll get another A.  Make another class B extends ICED that wraps a bunch of long arrays (like I have now).
//// A contains B as a member. The long arrays are easy to serialize automatically.   As you reduce two A's, do with the two B's. 'this' in the reduce will have the target A.
//// B gets initialized in the setupLocal() of A on each node.  Check in A's reduce that the two B's have different pointers else return.   See Arno's DeepLearningTask;
//// DeepLearningModelInfo is his B.
//
//
//// One Query calls several MRTask in a chain. Advantage being more split up, easier to time.
//// Several parallel, tight, branch free loops, faster than one heavy DKV pass per row
//
//
//  public class Query {
//    private static final int MAXVECLONG = 134217728, MAXVECBYTE = 1073741824;   // 2^31 bytes > java max (2^31-1), so 2^30 / 8 bytes per long.   TO DO - how to make global?
//    public ArrayList<Object> _returnList;
//    Query(Frame groupCols, Vec toSum, boolean retGrp) {
//      System.out.println("Calling RadixCount ...");
//      long t0 = System.nanoTime();
//      long t00 = t0;
//      int nChunks = groupCols.anyVec().nChunks();
//
//      long counts[][][] = new RadixCount(nChunks).doAll(groupCols.vec(0))._counts;
//      System.out.println("Time of RadixCount: " + (System.nanoTime() - t0) / 1e9); t0 = System.nanoTime();
//      // for (int c=0; c<5; c++) { System.out.print("First 10 for chunk "+c+" byte 0: "); for (int i=0; i<10; i++) System.out.print(counts[0][c][i] + " "); System.out.print("\n"); }
//
//      long totalHist[] = new long[256];
//      for (int c=0; c<nChunks; c++) {
//        for (int h=0; h<256; h++) {
//          totalHist[h] += counts[5][c][h];   // TO DO: hard coded 5 here
//        }
//      }
//
//      for (int b=0; b<8; b++) {
//        for (int h=0; h<256; h++) {
//          long rollSum = 0;
//          for (int c = 0; c < nChunks; c++) {
//            long tmp = counts[b][c][h];
//            counts[b][c][h] = rollSum;
//            rollSum += tmp;
//          }
//        }
//      }
//      // Any radix skipping needs to be detected with a loop over node results to ensure no use of those bits on any node.
//      System.out.println("Time to cumulate counts: " + (System.nanoTime() - t0) / 1e9); t0 = System.nanoTime();
//
//      // TO DO:  by this stage we know now the width of byte field we need.  So allocate it tight up to MAXVEC
//      // TO DO: reduce to 5 if we're only passed the first column
//      int keySize = 7;
//      // Create jagged array
//      _returnList = new ArrayList<> ();
//      // long o[][][] = new long[256][][];
//      // byte x[][][] = new byte[256][][];  // for each bucket,  there might be > 2^31 bytes, so an extra dimension for that
//      long o[][][];
//      _returnList.add(o = new long[256][][]);
//      byte x[][][];
//      _returnList.add(x = new byte[256][][]);  // for each bucket,  there might be > 2^31 bytes, so an extra dimension for that
//
//      for (int c=0; c<256; c++) {
//        if (totalHist[c] == 0) continue;
//        int d;
//        int nbatch = (int)(totalHist[c] * Math.max(keySize,8) / MAXVECBYTE);   // TODO. can't be 2^31 because 2^31-1 was limit. If we use 2^30, instead of /, can we do >> for speed?
//        int rem = (int)(totalHist[c] * Math.max(keySize,8) % MAXVECBYTE);
//        assert nbatch==0;  // in the case of 20m rows, we should always be well within a batch size
//        // The Math.max ensures that batches are aligned, even for wide keys.  For efficiency inside insert() above so it doesn't have to cross boundaries.
//        o[c] = new long[nbatch + (rem>0?1:0)][];
//        x[c] = new byte[nbatch + (rem>0?1:0)][];
//        assert nbatch==0;
//        for (d=0; d<nbatch; d++) {
//          o[c][d] = new long[MAXVECLONG];
//          // TO DO?: use MemoryManager.malloc8()
//          x[c][d] = new byte[MAXVECBYTE];
//        }
//        if (rem>0) {
//          o[c][d] = new long[rem];
//          x[c][d] = new byte[rem * keySize];
//        }
//      }
//      System.out.println("Time to allocate o[][] and x[][]: " + (System.nanoTime() - t0) / 1e9); t0 = System.nanoTime();
//      // NOT TO DO:  we do need the full allocation of x[] and o[].  We need o[] anyway.  x[] will be as dense as possible.
//      // o is the full ordering vector of the right size
//      // x is the byte key aligned with o
//      // o AND x are what bmerge() needs. Pushing x to each node as well as o avoids inter-node comms.
//
//      new MoveByFirstByte(5, o, x, counts, keySize).doAll(groupCols);  // feasibly, that we could move by byte 5 and then skip the next byte.  Too complex case though and rare so simplify
//      System.out.println("Time to MoveByFirstByte: " + (System.nanoTime() - t0) / 1e9); t0 = System.nanoTime();
//
//      // Add check that this first split is reasonable.  e.g. if it were just 2, it definitely would not be enough.   90 is enough though.  Need to fill L2 with pages.
//      // for counted completer 0:255
//      long groups[][] = new long[256][];  //  at most MAXVEC groups per radix, currently
//      long nGroup[] = new long[257];   // one extra to make undo of cumulate easier
//      Futures fs = new Futures();
//      for (int i=0; i<256; i++) {
//        if (totalHist[i] > 0)
//          fs.add(H2O.submitTask(new dradix(groups, nGroup, i, x[i], o[i], totalHist[i], keySize)));
//      }
//      fs.blockForPending();
//      long nGroups = 0;
//      for (int i = 0; i < 257; i++) {
//        long tmp = nGroup[i];
//        nGroup[i] = nGroups;
//        nGroups += tmp;
//      }
//      System.out.println("Time to recursive radix: " + (System.nanoTime() - t0) / 1e9 ); t0 = System.nanoTime();
//      System.out.println("Total groups found: " + nGroups);
//
//      // We now have o and x that bmerge() needs
//
//      long nrow = groupCols.anyVec().length();
//
//      long g[][] = new long[(int)(1 + nrow / MAXVECLONG)][];
//      int c;
//      for (c=0; c<nrow/MAXVECLONG; c++) {
//        g[c] = new long[MAXVECLONG];
//      }
//      g[c] = new long[(int)(nrow % MAXVECLONG)];
//      fs = new Futures();
//      for (int i=0; i<256; i++) {
//        if (totalHist[i] > 0)
//          fs.add(H2O.submitTask(new assignG(g, groups[i], nGroup[i+1]-nGroup[i], nGroup[i], o[i])));
//        // reuse the x vector we allocated before to store the group numbers.  i.e. a perfect and ordered hash, stored alongside table
//      }
//      fs.blockForPending();
//      System.out.println("Time to assign group index (length nrows): " + (System.nanoTime() - t0) / 1e9 ); t0 = System.nanoTime();
//
//      //Vec res = Vec.makeZero(nGroups);  // really slow to assign to
//      double res[] = new double[(int)nGroups];
//      new runSum(g, res).doAll(toSum);
//
//      // TO DO: Create final frame with 3 columns
//
//      System.out.println("Time to sum column: " + (System.nanoTime() - t0) / 1e9 );
//      System.out.println("Total time: " + (System.nanoTime() - t00) / 1e9);
//
//      for (int i = 0; i < 5; i++) System.out.format("%7.0f\n", res[i]);
//      System.out.println("---");
//      for (int i = 4; i >= 0; i--) System.out.format("%7.0f\n", res[(int)nGroups-i-1]);
//
///*
//        System.out.println("Head and tail:");
//        int head=10, done=0, batch = -1;
//        while (done < head) {
//            while (o[++batch] == null) ;
//            for (int i = 0; done < head && i < o[batch][0].length; i++)
//                System.out.format("%7d: %16d\n", done++, vec.at8(o[batch][0][i]));
//        }
//
//        System.out.println("--------");
//        batch = 256;
//        done = 0;
//        while (done < head) {
//            while (o[--batch] == null) ;
//            for (int i = o[batch][0].length - 1; done < head && i >= 0; i--)
//                System.out.format("%7d: %16d\n", vec.length() - ++done, vec.at8(o[batch][0][i]));
//        }
//        System.out.print("\n");
//*/
//
//    }
//  }
//
//
//
