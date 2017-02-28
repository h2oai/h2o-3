package water.userapi;

import water.DKV;
import water.Key;
import water.Scope;
import water.fvec.*;
import water.parser.ParseDataset;
import water.parser.ParseSetup;
import water.rapids.ast.prims.advmath.AstStratifiedSplit;
import water.udf.specialized.Enums;
import water.util.ArrayUtils;
import water.util.FileUtils;
import water.util.VecUtils;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static water.rapids.ast.prims.advmath.AstStratifiedSplit.SplittingDom;

/**
 * ETL library for User API.
 *
 * The library expose a set of ETL functions divided into 3 main groups
 * corresponding to 3 main concepts:
 *   - Load: create a new Frame from given input source
 *   - Transform: modify existing Frame (e.g., append a new vector, modify values)
 *   - Extract: create new frames from from an existing frame (e.g., subset of rows, split)
 *
 * Notes:
 *  - should work for frame with/without keys
 *  - frame locking is not used ( what happen if i call lock on frame without key)
 *
 */
public class Etl {

  /**
   * Load part of Etl library.
   *
   * This part creates a new Frame from a given file.
   */
  public static class Load {

    /** Create a new frame from a file specified by the path.
     * 
     * @param path
     * @return
     * @throws IOException
     */
    public static Frame readFile(String path) throws IOException {
      return readFile(FileUtils.locateFile(path.trim()));
    }

    /**
     * Create a new frame from a specified file.
     * 
     * @param file
     * @return
     * @throws IOException
     */
    public static Frame readFile(File file) throws IOException {
      return readFile(file.toURI());
    }

    /**
     * Create a new frame from file(s) specified by URI(s).
     * 
     * @param uri
     * @param uris
     * @return
     * @throws IOException
     */
    public static Frame readFile(URI uri, URI... uris) throws IOException {
      URI[] inputUris = new URI[1 + uris.length];
      inputUris[0] = uri;
      if (uris.length > 0) {
        System.arraycopy(uris, 0, inputUris, 1, inputUris.length);
      }
      return Scope.track(water.util.FrameUtils.parseFrame(
          Key.make(ParseSetup.createHexName(baseName(inputUris[0]))),
          inputUris));
    }

    private static String baseName(URI uri) {
      String s = uri.toString();
      return s.substring(s.lastIndexOf('/') + 1);
    }

    // No DKV.put?
    // TODO: pass frame name
    public static Frame createFrame(Map<String, Vec> map) {
      return new Frame(
          map.keySet().toArray(new String[map.size()]),
          map.values().toArray(new Vec[map.size()]));
    }
  }

  /**
   * Transform part of the ETL library.
   *
   * It introduces in-place frame transformations:
   *  F -&gt; F
   */
  public static class Transform {

    /**
     * 
     * @param frame   input frame
     * @param ignore
     * @return
     */
    public static Frame oneHotEncode(Frame frame, String... ignore) {
      try {
        return Enums.oneHotEncoding(frame, ignore);
      } catch (IOException ioe) {
        throw new DataException("Failed to do oneHotEncoding", ioe);
      }
    }

    /**
     *
     * @param frame
     * @param colName
     * @param ratio
     * @param seed
     * @return
     */
    static Frame stratifiedSplitColumn(Frame frame, String colName, Double ratio, long seed) {
      // Get column to split on
      final Vec vec = frame.vec(colName);
      assert vec != null : "Column " + colName + " missing in frame " + frame;
      final Vec splitDefVec = Scope.track(AstStratifiedSplit.split(vec, ratio, seed, SplittingDom));
      Frame result = new Frame(new String[] {"stratified_split_column"}, new Vec[] {vec});

      return result;
    }

    /**
     * Transform given column(s) to a categorical column(s).
     * The transformation make a categorical copy of the column and replace original column.
     *
     * @param frame  input frame
     * @param colNames names of columns to transform into categorical
     * @return
     */
    public static Frame makeCategorical(Frame frame, String ... colNames) {
      frame.write_lock();
      try {
        for (String colName : colNames) {
          frame.replace(colName, frame.vec(colName).toCategoricalVec()).remove();
        }
        return frame.update();
      } finally {
        frame.unlock();
      }
    }
    
    public static Frame dropColumn(Frame f, String... colNames) {
      for (String colName : colNames) {
        Vec v = f.remove(colName);
        v.remove();
        Scope.untrack(v._key);
      }
      return f;
    }

    public static String[] domainOf(Frame frame, String colName) {
      return frame.vec(colName).domain();
    }
  }

  /**
   * Extract part of ETL library.
   *
   * Global concept:
   *   F to F[] : transformation from Frame to a list of Frames.
   *
   */
  public static class Extract {
    
    /** Extract part of frame defined by a categorical column and a value to extract.
     *
     * The extracted rows contains defined value in the defined categorical column.
     *
     * @param frame  frame to extract a new frame from.
     * @param colName  categorical vector
     * @param whereValue  defines rows which should be extracted
     * @return  a new frame composed from rows which have whereValue in the specified
     * categorical vector.
     */
    private static Frame select(Frame frame, String colName, final String whereValue) {
      Vec vec = frame.vec(colName);
      final String[] domain = vec.domain();
      final int expected = Arrays.asList(domain).indexOf(whereValue);

      final FrameFilter filter = new FrameFilter(frame, colName) {

        public boolean accept(Chunk c, int i) {
          long val = c.at8(i);
          return expected == val;
        }
      };
      return Scope.track(filter.eval());
    }

    /**
     * Split given frame into multiple frames based on given categorical vector.
     *
     * @param frame  input frame
     * @return  map of frames, each entry key corresponds to a level from specified categorical
     * vector, and value represents a selected frame.
     */
    public static Map<String, Frame> splitByColumn(Frame frame,
                                                   Frame splitFrame) {
      assert splitFrame.numCols() == 1 : "Split frame should contain a single column!";
      assert splitFrame.lastVec().isCategorical() : "Split frame column has to be categorical!";
      
      String[] splitColDomain = frame.lastVec().domain();
      String splitColName = frame.name(0);
      Frame temp = frame.add(splitFrame);
      Map<String, Frame> m = new HashMap<>(splitColDomain.length);
      for (String val : splitColDomain)
        m.put(val, select(temp, splitColName, val));
      return m;
    }

  }

  public static long numRows(Frame f) {
    return f.numRows();
  }
}
