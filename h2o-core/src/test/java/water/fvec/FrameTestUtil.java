package water.fvec;

import org.junit.Ignore;
import water.DKV;
import water.Key;
import water.parser.ValueString;

/**
 * Methods to access frame internals.
 */
@Ignore("Support for tests, but no actual tests here")
public class FrameTestUtil {

  public static Frame createFrame(String fname, long[] chunkLayout, String[][] data) {
    Frame f = new Frame(Key.make(fname));
    f.preparePartialFrame(new String[]{"C0"});
    f.update(null);
    // Create chunks
    for (int i=0; i<chunkLayout.length; i++) {
      createNC(fname, data[i], i, (int) chunkLayout[i]);
    }
    // Reload frame from DKV
    f = DKV.get(fname).get();
    // Finalize frame
    f.finalizePartialFrame(chunkLayout, new String[][] { null }, new byte[] {Vec.T_STR});
    return f;
  }

  public static NewChunk createNC(String fname, String[] data, int cidx, int len) {
    NewChunk[] nchunks = Frame.createNewChunks(fname, cidx);
    ValueString vs = new ValueString();
    for (int i=0; i<len; i++) {
      nchunks[0].addStr(data[i] != null ? vs.setTo(data[i]) : null);
    }
    Frame.closeNewChunks(nchunks);
    return nchunks[0];
  }

  public static Frame createFrame(String fname, long[] chunkLayout) {
    // Create a frame
    Frame f = new Frame(Key.make(fname));
    f.preparePartialFrame(new String[]{"C0"});
    f.update(null);
    // Create chunks
    for (int i=0; i<chunkLayout.length; i++) {
      createNC(fname, i, (int) chunkLayout[i]);
    }
    // Reload frame from DKV
    f = DKV.get(fname).get();
    // Finalize frame
    f.finalizePartialFrame(chunkLayout, new String[][] { null }, new byte[] {Vec.T_NUM});
    return f;
  }

  public static NewChunk createNC(String fname, int cidx, int len) {
    NewChunk[] nchunks = Frame.createNewChunks(fname, cidx);
    int starVal = cidx * 1000;
    for (int i=0; i<len; i++) {
      nchunks[0].addNum(starVal + i);
    }
    Frame.closeNewChunks(nchunks);
    return nchunks[0];
  }
}
