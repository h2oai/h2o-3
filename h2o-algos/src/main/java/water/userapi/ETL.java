package water.userapi;

import water.Key;
import water.Scope;
import water.fvec.*;
import water.parser.ParseDataset;
import water.rapids.ast.prims.advmath.AstStratifiedSplit;
import water.udf.specialized.Enums;
import water.util.FileUtils;
import water.util.VecUtils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static water.rapids.ast.prims.advmath.AstStratifiedSplit.SplittingDom;
import static water.rapids.ast.prims.advmath.AstStratifiedSplit.TestTrainSplitColName;

/**
 * ETL library for User API
 * 
 * Usage: see, e.g. UserapiGBMTest#testStochasticGBMHoldout
 * 
 * Created by vpatryshev on 2/27/17.
 */
public class ETL {
  
  public static Frame readFile(String path) {
    return read(FileUtils.locateFile(path.trim()));
  }

  public static Frame read(File file) {
    try {
      FileUtils.checkFile(file, file.getCanonicalPath());
      NFSFileVec nfs = NFSFileVec.make(file);
      return Scope.track(ParseDataset.parse(Key.make(), nfs._key));
    } catch (IOException ioe) {
      throw new DataException("Could not read " + file, ioe);
    }
  }

  public static Frame onVecs(Map<String, Vec> map) {
    return new Frame(
        map.keySet().toArray(new String[map.size()]),
        map.values().toArray(new Vec[map.size()]));
  }

  public static Frame oneHotEncode(Frame frame, String... ignore) {
    try {
      return Enums.oneHotEncoding(frame, ignore);
    } catch (IOException ioe) {
      throw new DataException("Failed to do oneHotEncoding", ioe);
    }
  }

  static Frame addSplittingColumn(Frame frame, String colName, Double ratio, long seed) {
    final Vec vec = frame.vec(colName);
    assert vec != null : "Column " + colName + " missing in frame " + frame;
    final Frame splitter = Scope.track(AstStratifiedSplit.split(vec, ratio, seed, SplittingDom));
    Frame newFrame = Scope.track(frame.clone());

// the following lines are good for checking if we have what we want
//    Vec v0 = splitter.vec(0);
//    int[] counters = new int[v0.domain().length];
//    for (int i = 0; i < v0.length(); i++) {
//      long x = v0.at8(i);
//      counters[(int)x]++;
//    }

    newFrame._key = null;
    newFrame.add(splitter.names(), splitter.vecs());
    return newFrame;
  }

  private static Frame select(Frame frame, final String what, String colname) {
    Vec vec = frame.vec(colname);
    final String[] domain = vec.domain();
    final int expected = Arrays.asList(domain).indexOf(what);

    final FrameFilter filter = new FrameFilter(frame, colname) {

      public boolean accept(Chunk c, int i) {
        long val = c.at8(i);
        return expected == val;
      }
    };
    return Scope.track(filter.eval());
  }

  private static Map<String, Frame> splitBy(Frame frame, String colname, String[] splittingDom) {
    Map<String, Frame> m = new HashMap<>(splittingDom.length);
    for (String val : splittingDom) m.put(val, select(frame, val, colname));
    return m;
  }

  static TrainAndValid stratifiedSplit(Frame frame, String colName, Double ratio, long seed) {
    Frame blend = addSplittingColumn(frame, colName, ratio, seed);
    Map<String, Frame> split = splitBy(blend, TestTrainSplitColName, SplittingDom);

    return new TrainAndValid(
        split.get(SplittingDom[0]),
        split.get(SplittingDom[1]));
  }

  static TrainAndValid stratifiedSplit(Frame frame, String colName, Double ratio) {
    return stratifiedSplit(frame, colName, ratio, System.currentTimeMillis());
  }

  public static void renameColumns(Frame frame, String... newName) {
    System.arraycopy(newName, 0, frame._names, 0, Math.min(frame.numCols(), newName.length));
  }

  public static void makeCategorical(Frame frame, String colName) {
    Vec srcCol = frame.vec(colName);
    byte type = srcCol.get_type();
    Vec categorical =
        type == Vec.T_STR ? VecUtils.stringToCategorical(srcCol) :
            type == Vec.T_NUM ? VecUtils.numericToCategorical(srcCol) : srcCol;
    frame.replace(colName, Scope.track(categorical));
    srcCol.remove();
  }
  
  public static long length(Frame f) {
    Vec v = f == null ? null : f.anyVec();
    return v == null ? 0 : v.length();
  }

  public static void removeColumn(Frame f, String... names) {
    for (String name : names) {
      Vec v = f.remove(name);
      v.remove();
      Scope.untrack(v._key);
    }
  }

  public static String[] domainOf(Frame frame, String colName) {
    return frame.vec(colName).domain();
  }

}
