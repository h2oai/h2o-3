package hex.createframe.postprocess;

import hex.createframe.CreateFramePostprocessStep;
import water.DKV;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;

import java.util.HashMap;
import java.util.Random;

/**
 * Action to shuffle the columns of the frame.
 */
public class ShuffleColumnsCfps extends CreateFramePostprocessStep {
  private boolean reassignNames;
  private boolean responseFirst;

  public ShuffleColumnsCfps() {}

  /**
   * @param reassignNames If true, the columns will be renamed within each group starting with a common alpha-prefix.
   *                      I.e. if the original frame had columns [A1, A3, B2, B5] then after shuffling it may look like
   *                      this: [B1, A1, A2, B2]. In this new frame column "A1" may have been either "A1" or "A3" in
   *                      the original frame.
   *                      If false, each vec will keep its name.
   * @param responseFirst If true, the "response" column will be moved to the beginning of the frame. Otherwise it
   *                      will be shuffled together with the rest of the columns.
   */
  public ShuffleColumnsCfps(boolean reassignNames, boolean responseFirst) {
    this.reassignNames = reassignNames;
    this.responseFirst = responseFirst;
  }

  @Override
  public void exec(Frame fr, Random rng) {
    // Initial shuffle
    int numCols = fr.numCols();
    if (numCols == 0) return;
    int[] idx = ArrayUtils.seq(0, numCols);
    ArrayUtils.shuffleArray(idx, rng);

    // Move the response column to the beginning of the frame
    if (responseFirst) {
      int responseIndex = ArrayUtils.find(fr.names(), "response");
      if (responseIndex == -1) responseIndex = ArrayUtils.find(fr.names(), "Response");
      if (responseIndex >= 0) {
        int shuffledIndex = ArrayUtils.find(idx, responseIndex);
        idx[shuffledIndex] = idx[0];
        idx[0] = responseIndex;
      }
    }

    // Construct shuffled arrays of names and vecs
    Vec[] newVecs = new Vec[numCols];
    String[] newNames = new String[numCols];
    for (int i = 0; i < numCols; ++i) {
      newVecs[i] = fr.vec(idx[i]);
      newNames[i] = fr.name(idx[i]);
    }

    // Rename columns in order to hide the fact that they were shuffled
    if (reassignNames) {
      HashMap<String, Integer> prefixCounts = new HashMap<>();
      for (int i = 0; i < numCols; i++){
        String prefix = removeNumericSuffix(newNames[i]);
        int count = prefixCounts.containsKey(prefix)? prefixCounts.get(prefix) + 1 : 1;
        prefixCounts.put(prefix, count);
        if (!newNames[i].equals("response"))
          newNames[i] = prefix + count;
      }
    }

    // Reshape the original dataframe
    fr.restructure(newNames, newVecs);
    DKV.put(fr);
  }


  /**
   * Helper function which strips the provided name from any numeric suffix in the end.
   * Equivalent to <code>name.rstrip("0123456789")</code> in Python.
   */
  public static String removeNumericSuffix(String name) {
    int i = name.length();
    while (--i >= 0) {
      char ch = name.charAt(i);
      if (ch < '0' || ch > '9') break;
    }
    return name.substring(0, i + 1);
  }
}
