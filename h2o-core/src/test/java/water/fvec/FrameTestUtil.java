package water.fvec;

import org.junit.Assert;
import org.junit.Ignore;
import water.DKV;
import water.Futures;
import water.Key;
import water.parser.BufferedString;

/**
 * Methods to access frame internals.
 */
@Ignore("Support for tests, but no actual tests here")
public class FrameTestUtil {


  // Build real Vecs from loose Chunks, and finalize this Frame.  Called once
  // after any number of [create,close]NewChunks.
  static Frame finalizePartialFrame( long[] espc, String[][] domains, byte[] types ) {
    // Compute elems-per-chunk.
    // Roll-up elem counts, so espc[i] is the starting element# of chunk i.
    int nchunk = espc.length;
    long espc2[] = new long[nchunk+1]; // Shorter array
    long x=0;                   // Total row count so far
    for( int i=0; i<nchunk; i++ ) {
      espc2[i] = x;             // Start elem# for chunk i
      x += espc[i];             // Raise total elem count
    }
    espc2[nchunk]=x;            // Total element count in last

    // For all Key/Vecs - insert Vec header
    Futures fs = new Futures();
    _vecs = new Vec[_keys.length];
    for( int i=0; i<_keys.length; i++ ) {
      // Insert Vec header
      Vec vec = _vecs[i] = new Vec( _keys[i],
          Vec.ESPC.rowLayout(_keys[i],espc2),
          domains!=null ? domains[i] : null,
          types[i]);
      // Here we have to save vectors since
      // saving during unlock will invoke Frame vector
      // refresh
      DKV.put(_keys[i],vec,fs);
    }
    fs.blockForPending();
    unlock();
  }

  public static Frame createFrame(String fname, long[] chunkLayout, String[][] data) {
    Frame f = new Frame(Key.<Frame>make(fname));
    preparePartialFrame(new String[]{"C0"});
    f.update();
    // Create chunks
    byte[] types = new byte[] {Vec.T_STR};
    for (int i=0; i<chunkLayout.length; i++) {
      createNC(fname, data[i], i, (int) chunkLayout[i], types);
    }
    // Reload frame from DKV
    f = DKV.get(fname).get();
    // Finalize frame
    finalizePartialFrame(chunkLayout, new String[][] { null }, types);
    return f;
  }

  public static NewChunk createNC(String fname, String[] data, int cidx, int len, byte[] types) {
    NewChunk[] nchunks = Frame.createNewChunks(fname, types, cidx);
    for (int i=0; i<len; i++) {
      nchunks[0].addStr(data[i] != null ? data[i] : null);
    }
    Frame.closeNewChunks(nchunks);
    return nchunks[0];
  }

  public static Frame createFrame(String fname, long[] chunkLayout) {
    // Create a frame
    Frame f = new Frame(Key.<Frame>make(fname));
    f.preparePartialFrame(new String[]{"C0"});
    f.update();
    byte[] types = new byte[] {Vec.T_NUM};
    // Create chunks
    for (int i=0; i<chunkLayout.length; i++) {
      createNC(fname, i, (int) chunkLayout[i], types);
    }
    // Reload frame from DKV
    f = DKV.get(fname).get();
    // Finalize frame
    f.finalizePartialFrame(chunkLayout, new String[][] { null }, types);
    return f;
  }

  public static NewChunk createNC(String fname, int cidx, int len, byte[] types) {
    NewChunk[] nchunks = Frame.createNewChunks(fname, types, cidx);
    int starVal = cidx * 1000;
    for (int i=0; i<len; i++) {
      nchunks[0].addNum(starVal + i);
    }
    Frame.closeNewChunks(nchunks);
    return nchunks[0];
  }

  public static void assertValues(Frame f, String[] expValues) {
    assertValues(f.vec(0), expValues);
  }

  public static void assertValues(Vec v, String[] expValues) {
    Assert.assertEquals("Number of rows", expValues.length, v.length());
    BufferedString tmpStr = new BufferedString();
    for (int i = 0; i < v.length(); i++) {
      if (v.isNA(i)) Assert.assertEquals("NAs should match", null, expValues[i]);
      else Assert.assertEquals("Values should match", expValues[i], v.atStr(tmpStr, i).toString());
    }
  }

  public static String[] collectS(Vec v) {
    String[] res = new String[(int) v.length()];
    BufferedString tmpStr = new BufferedString();
      for (int i = 0; i < v.length(); i++)
        res[i] = v.isNA(i) ? null : v.atStr(tmpStr, i).toString();
    return res;
  }
}
