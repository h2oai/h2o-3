package hex;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.H2O;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.util.ArrayUtils;

import static water.fvec.FrameTestUtil.assertValues;
import static water.fvec.FrameTestUtil.createFrame;
import static water.util.FrameUtils.generateNumKeys;

public class FrameSplitterTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(5); }

  @Test public void splitTinyFrame() {
    Frame   dataset = null;
    double[] ratios  = ard(0.5f);
    Frame[] splits  = null;

    try {
      dataset = frame(ar("COL1"), ear(0, 1, 2, 3, 4, 5, 6, 7, 8, 9));
      FrameSplitter fs = new FrameSplitter(dataset, ratios, generateNumKeys(dataset._key, ratios.length+1), null);
      H2O.submitTask(fs).join();
      splits = fs.getResult();
      Assert.assertEquals("The expected number of split frames is ratios.length+1", ratios.length+1, splits.length);
      for (Frame f : splits)
        Assert.assertEquals("The expected number of rows in partition.", 5, f.numRows() );
    } finally {
      // cleanup
      if (dataset!=null) dataset.delete();
      if (splits!=null)
        for(Frame sf : splits) if (sf!=null) sf.delete();
    }
  }

  /** Test for [PUB-410] - AIOOBE in NewChunk if there are NA in String chunks. */
  @Test public void splitStringFrame() {
    // NA in data
    String fname = "test1.hex";
    long[] chunkLayout = ar(2L, 2L, 3L);
    String[][] data = ar(ar("A", "B"), ar(null, "C"), ar("D", "E", "F"));
    testScenario(fname, chunkLayout, data);

    // NAs everywhere
    fname = "test2.hex";
    chunkLayout = ar(2L, 1L);
    data = ar( ar((String) null, (String) null), ar((String) null) );
    testScenario(fname, chunkLayout, data);

    // NAs at the end of chunks
    fname = "test3.hex";
    chunkLayout = ar(2L, 2L, 2L);
    data = ar(ar("A", "B"), ar("C", null), ar((String) null, (String) null));
    testScenario(fname, chunkLayout, data);
  }

  @Test
  public void splitStringFramePUBDEV468() {
    // NAs at the end of chunks
    String fname = "test4.hex";
    long[] chunkLayout = ar(3L, 3L);
    String[][] data = ar(ar("A", null, "B"), ar("C", "D", "E"));
    testScenario(fname, chunkLayout, data);
  }

  /** Test scenario for splitting 1-vec frame of strings. */
  static void testScenario(String fname, long[] chunkLayout, String[][] data) {
    Frame f = createFrame(fname, chunkLayout, data);
    double[] ratios  = ard(0.5f);
    Frame[] splits  = null;
    long len = ArrayUtils.sum(chunkLayout);
    Assert.assertEquals("Number of rows should match to chunk layout.", len, f.numRows());
    // Compute expected splits
    String[] split0 = new String[(int) len / 2];
    String[] split1 = new String[(int) (len-split0.length)];
    int idx = 0;
    for (String[] d: data) {
      for (String s: d)
        if (idx < split0.length) split0[idx++] = s;
        else split1[idx++ - split0.length] = s;
    }

    try {
      FrameSplitter fs = new FrameSplitter(f, ratios, generateNumKeys(f._key, ratios.length+1), null);
      H2O.submitTask(fs).join();
      splits = fs.getResult();
      assertValues(splits[0], split0);
      assertValues(splits[1], split1);
    } finally {
      f.delete();
      if (splits!=null)
        for(Frame sf : splits) if (sf!=null) sf.delete();
    }
  }

  @Test public void computeEspcTest() {
    // Split inside chunk
    long [] espc   = ar(0L, 2297L, 4591, 7000L);
    double[] ratios = ard(0.5f);
    long[][] result = FrameSplitter.computeEspcPerSplit(espc, espc[espc.length-1], ratios);
    Assert.assertArrayEquals(ar(ar(0L, 2297L, 3500L), ar(0L, 1091L, 3500L)), result);

    // Split inside chunk #2
    espc   = ar(0L, 1500L, 3000L, 4500L, 7000L);
    ratios = ard(0.5f);
    result = FrameSplitter.computeEspcPerSplit(espc, espc[espc.length-1], ratios);
    Assert.assertArrayEquals(ar(ar(0L, 1500L, 3000L, 3500L), ar(0L, 1000L, 3500L)), result);

    // Split on chunk boundary
    espc   = ar(0L, 1500L, 3500L, 4500L, 7000L);
    ratios = ard(0.5f);
    result = FrameSplitter.computeEspcPerSplit(espc, espc[espc.length-1], ratios);
    Assert.assertArrayEquals(ar(ar(0L, 1500L, 3500L), ar(0L, 1000L, 3500L)), result);
  }

  @Test public void test() {
    // Load data
    Frame f = parse_test_file(Key.make("iris.csv"), "smalldata/iris/iris.csv");
    long numRows = f.numRows();
    Assert.assertEquals(150, numRows);
    // Perform frame split via API
    try {
      SplitFrame sf = new SplitFrame();
      sf.dataset = f;
      sf.ratios = new double[] { 0.5, 0.5 };
      sf.destination_frames = new Key[] { Key.make("train.hex"), Key.make("test.hex")};
      // Invoke the job
      sf.exec().get();
      Assert.assertTrue("The job is not in DONE state, but in " + sf._state, sf.isDone());
      Key[] ksplits = sf.destination_frames;
      Frame[] fsplits = new Frame[ksplits.length];
      for (int i=0; i<ksplits.length; i++) fsplits[i] = DKV.get(ksplits[i]).get();
      Assert.assertEquals("Number of splits", 2, ksplits.length);
      Assert.assertEquals("1. split 75rows", 75, fsplits[0].numRows());
      Assert.assertEquals("2. split 75rows", 75, fsplits[1].numRows());
      fsplits[0].delete();
      fsplits[1].delete();
    } finally {
      f.delete();
    }
  }
}
