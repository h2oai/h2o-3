package water.userapi;

import water.DKV;
import water.Iced;
import water.Key;
import water.Scope;
import water.fvec.*;
import water.parser.ParseDataset;
import water.rapids.ast.prims.advmath.AstStratifiedSplit;
import water.udf.specialized.Enums;
import water.util.FileUtils;
import water.util.FrameUtils;
import water.util.VecUtils;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;

import static water.rapids.ast.prims.advmath.AstStratifiedSplit.*;
/**
 * Simplified Frame wrapper for simple usages
 * 
 * Created by vpatryshev on 2/20/17.
 */
public class Dataset extends Iced<Dataset> 
 {
  private Key<Frame> frameKey;

  Dataset() {}
  
  Dataset(Frame frame) {
    frameKey = FrameUtils.save(frame);
  }

  public static Dataset readFile(String path) {
    return read(FileUtils.locateFile(path.trim()));
  }
  
  public static Dataset read(File file) {
    try {
      FileUtils.checkFile(file, file.getCanonicalPath());
      NFSFileVec nfs = NFSFileVec.make(file);
      return new Dataset(Scope.track(ParseDataset.parse(Key.make(), nfs._key)));
    } catch (IOException ioe) {
      throw new DataException("Could not read " + file, ioe);
    }
  }
  
  public static Dataset onVecs(Map<String, Vec> map) {
    return new Dataset(new Frame(
        map.keySet().toArray(new String[map.size()]),
        map.values().toArray(new Vec[map.size()])));
  }
  
  public Frame frame() { return DKV.getGet(frameKey); }
  
  public Dataset oneHotEncode(String... ignore) {
    try {
      final Frame hotFrame = Enums.oneHotEncoding(frame(), ignore);
      return new Dataset(hotFrame);
    } catch (IOException ioe) {
      throw new DataException("Failed to do oneHotEncoding", ioe);
    }
  }
  
  // the new vec is named (hard-coded so far) "test_train_split"
  Dataset addSplittingColumn(String colName, Double ratio, long seed) {
    Frame frame = frame();
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
    return new Dataset(newFrame);
  }

  TrainAndValid stratifiedSplit(String colName, Double ratio, long seed) {
    Dataset blend = addSplittingColumn(colName, ratio, seed);
    Map<String, Frame> split = blend.splitBy(TestTrainSplitColName, SplittingDom);
    
    return new TrainAndValid(
        split.get(SplittingDom[0]),
        split.get(SplittingDom[1]));
  }
  
  public TrainAndValid stratifiedSplit(String colName, Double ratio) {
    return stratifiedSplit(colName, ratio, System.currentTimeMillis());
  }
  
  private Frame select(final String what, String colname) {
    Frame frame = frame();
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
  
  private Map<String, Frame> splitBy(String colname, String[] splittingDom) {
    Map<String, Frame> m = new HashMap<>(splittingDom.length);
    for (String val : splittingDom) m.put(val, select(val, colname));
    return m;
  }
  
  public String[] domain() {
    return frame().names();
  }
  
  public Vec vec(String name) {
    return frame().vec(name);
  }

  @Override
  public String toString() {
    return "Dataset{frame=" + frame() + '}';
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof Dataset && Objects.equals(frame(), ((Dataset) o).frame());
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(frame())*7+1;
  }

  public Vec dropColumn(int i) {
    return frame().remove(i);
  }

  public void renameColumns(String... newName) {
    Frame frame = frame();
    System.arraycopy(newName, 0, frame._names, 0, Math.min(frame.numCols(), newName.length));
  }

  public void makeCategorical(String colName) {
    Frame frame = frame();
    Vec srcCol = frame.vec(colName);
    byte type = srcCol.get_type();
    Vec categorical = 
        type == Vec.T_STR ? VecUtils.stringToCategorical(srcCol) :
        type == Vec.T_NUM ? VecUtils.numericToCategorical(srcCol) : srcCol;
    frame.replace(colName, Scope.track(categorical));
    srcCol.remove();
  }

  public String[] domainOf(String colName) {
    return frame().vec(colName).domain();
  }

  public long length() {
    Frame f = frame();
    Vec v = f == null ? null : f.anyVec();
    return v == null ? 0 : v.length();
  }

  public void removeColumn(String... names) {
    Frame f = frame();
    for (String name : names) {
      Vec v = f.remove(name);
      v.remove();
      Scope.untrack(v._key);
    }
  }

 }
