package water.userapi;

import water.Key;
import water.Scope;
import water.fvec.*;
import water.parser.BufferedString;
import water.parser.ParseDataset;
import water.rapids.ast.prims.advmath.AstStratifiedSplit;
import water.udf.specialized.Enums;
import water.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static water.rapids.ast.prims.advmath.AstStratifiedSplit.*;
/**
 * Simplified Frame wrapper for simple usages
 * 
 * Created by vpatryshev on 2/20/17.
 */
public class Dataset {
  Frame frame;

  private Dataset(Frame frame) {
    this.frame = frame;
  }

  public static Dataset readFile(String path) {
    return read(FileUtils.locateFile(path));
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
  
  public Dataset oneHotEncode(String... ignore) {
    try {
      return new Dataset(Enums.oneHotEncoding(frame, ignore));
    } catch (IOException ioe) {
      throw new DataException("Failed to do oneHotEncoding", ioe);
    }
  }
  
  // the new vec is named (hard-coded so far) "test_train_split"
  Dataset addSplittingColumn(String colName, Double ratio, long seed) {
    final Frame splitter = Scope.track(AstStratifiedSplit.split(frame, frame.vec(colName), ratio, seed, SplittingDom));
    Frame newFrame = Scope.track(frame.clone());
    newFrame.add(splitter.names(), splitter.vecs());
    return new Dataset(newFrame);
  }

  TrainAndValid stratifiedSplit(String colName, Double ratio, long seed) {
    Dataset blend = addSplittingColumn(colName, ratio, seed);
    Map<String, Frame> split = blend.splitBy(TestTrainSplitColName, SplittingDom);
    
    return new TrainAndValid(
        new Dataset(split.get(SplittingDom[0])),
        new Dataset(split.get(SplittingDom[1])));
  }
  
  public TrainAndValid stratifiedSplit(String colName, Double ratio) {
    return stratifiedSplit(colName, ratio, System.currentTimeMillis());
  }
  
  private Frame select(final String what, String colname) {
    
    final int expected = Arrays.asList(frame.vec(colname).domain()).indexOf(what);
    
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
    return frame.names();
  }
  
  public Vec vec(String name) {
    return frame.vec(name);
  }

  @Override
  public String toString() {
    return "Dataset{frame=" + frame + '}';
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof Dataset && Objects.equals(frame, ((Dataset) o).frame);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(frame)*7+1;
  }

  static class TrainAndValid {
    final Dataset train;
    final Dataset valid;
    
    public TrainAndValid(Dataset train, Dataset valid) {
      this.train = train;
      this.valid = valid;
    }
    
    @Override
    public boolean equals(Object o) {
      if (!(o instanceof TrainAndValid)) return false;

      TrainAndValid that = (TrainAndValid) o;

      return Objects.equals(train, that.train) && 
             Objects.equals(valid, that.valid);
    }

    @Override
    public int hashCode() {
      return 31 * Objects.hashCode(train) + Objects.hashCode(valid);
    }

    @Override
    public String toString() {
      return "TrainAndValid{" +
          "train=" + train +
          ", valid=" + valid +
          '}';
    }
  }
}
