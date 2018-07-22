package water.fvec;

import org.apache.commons.lang.ArrayUtils;
import org.junit.Ignore;
import water.DKV;
import water.Key;
import water.Scope;
import water.rapids.Env;
import water.rapids.Session;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Random;

/**
 * Class used for creating simple test frames using builder pattern
 * <p>
 * Example usage:
 * <pre>{@code
 * final Frame builder = new TestFrameBuilder()
 *   .withName("testFrame")
 *   .withColNames("ColA", "ColB", "ColC")
 *   .withVecTypes(Vec.T_NUM, Vec.T_STR, Vec.T_CAT)
 *   .withDataForCol(0, ard(Double.NaN, 1, 2, 3, 4, 5.6, 7))
 *   .withDataForCol(1, ar("A", "B", "C", "E", "F", "I", "J"))
 *   .withDataForCol(2, ar("A", "B,", "A", "C", "A", "B", "A"))
 *   .withChunkLayout(2, 2, 2, 1)
 *   .build();
 * }
 * </pre>
 * Data for categorical column are set in the same way as for string column and leves are created automatically.</br>
 * All methods in this builder are optional:
 * <ul>
 * <li> Frame name is created it not provided.</li>
 * <li> Column names are created automatically if not provided.</li>
 * <li> Vector types are initialized to all T_NUMs when not provided. For example, creating empty frame (
 *   no data, co columns) can be created as {@code Frame fr = new TestFrameBuilder().build()}.</li>
 * <li> Column data are initialized to empty array when not provided. The following example creates frames with 2 columns,
 *   but no data. {@code Frame fr = new TestFrameBuilder().withVecTypes(Vec.T_NUM).build()}.</li>
 * <li> Only one chunk is created when chunk layout is not provided.</li>
 * </ul>
 *
 * The frame created will be automatically tracked in the currently active {@link Scope}.
 */
@Ignore
public class TestFrameBuilder {

  private static final long NOT_SET = -1;
  private HashMap<Integer, String[]> stringData = new HashMap<>();
  private HashMap<Integer, double[]> numericData = new HashMap<>();
  private String frameName;
  private byte[] vecTypes;
  private String[] colNames;
  private long[] chunkLayout;
  private int numCols;
  private Key<Frame> key;
  private long numRows = NOT_SET;
  private String[][] domains = null;
  private HashMap<Integer, Integer[]> categoriesPerCol = new HashMap<>();

  /**
   * Sets the name for the frame. Default name is created if this method is not called.
   */
  public TestFrameBuilder withName(String frameName) {
    throwIf(frameName.startsWith("$"), "Frame name " + frameName + " may only be used with a Session object.");
    this.frameName = frameName;
    return this;
  }

  public TestFrameBuilder withName(String frameName, Session session) {
    return withName(new Env(session).expand(frameName));
  }


  /**
   * Sets the names for the columns. Default names are created if this method is not called.
   */
  public TestFrameBuilder withColNames(String... colNames) {
    this.colNames = colNames;
    return this;
  }

  /**
   * Sets the vector types. Vector types are initialized to empty array if this method is not called.
   */
  public TestFrameBuilder withVecTypes(byte... vecTypes) {
    this.vecTypes = vecTypes;
    return this;
  }


  /**
   *  Genarate random double data for a particular column
   * @param column for which to set data
   * @param size size of randomly generated column
   * @param min minimal value to generate
   * @param max maximum value to generate
   */
  public TestFrameBuilder withRandomIntDataForCol(int column, int size, int min, int max, long seed) {
    assert max > min;
    assert seed + size * size <= Long.MAX_VALUE;
    double[] arr = new double[size];
    for(int i = 0; i < size; i++) {
      arr[i] = min + new Random(seed + i * size).nextInt(max - min);
    }
    numericData.put(column, arr);
    return this;
  }

  /**
   *  Genarate random double data for a particular column
   * @param column for which to set data
   * @param size size of randomly generated column
   * @param min minimal value to generate
   * @param max maximum value to generate
   */
  public TestFrameBuilder withRandomDoubleDataForCol(int column, int size, int min, int max, long seed) {
    assert max >= min;
    double[] arr = new double[size];
    for(int i = 0; i < size; i++) {
      arr[i] = min + (max - min) * new Random(seed + i * size).nextDouble();
    }
    numericData.put(column, arr);
    return this;
  }

  /**
   * Genarate random binary data for a particular column
   *
   * @param column for which to set data
   */
  public TestFrameBuilder withRandomBinaryDataForCol(int column, int size, long seed) {
    String[] arr = new String[size];
    Random generator = new Random();
    long multiplierFromRandomClass = 0x5DEECE66DL;
    assert seed + size * multiplierFromRandomClass < Long.MAX_VALUE;
    for(int i = 0; i < size; i++) {
      generator.setSeed(seed + i * multiplierFromRandomClass);
      arr[i] = Boolean.toString( generator.nextBoolean());
    }
    stringData.put(column, arr);
    return this;
  }

  /**
   * Sets data for a particular column
   *
   * @param column for which to set data
   * @param data   array of string data
   */
  public TestFrameBuilder withDataForCol(int column, String[] data) {
    stringData.put(column, data);
    return this;
  }

  /**
   * Sets data for a particular column
   *
   * @param column for which to set data
   * @param data   array of double data
   */
  public TestFrameBuilder withDataForCol(int column, double[] data) {
    numericData.put(column, data);
    return this;
  }

  /**
   * Sets data for a particular column
   *
   * @param column for which to set data
   * @param data   array of long data
   */
  public TestFrameBuilder withDataForCol(int column, long[] data) {
    if(data == null){
      numericData.put(column, null);
    }else {
      double[] doubles = new double[data.length];
      for (int i = 0; i < data.length; i++) {
        doubles[i] = data[i];
      }
      numericData.put(column, doubles);
    }
    return this;
  }

  public TestFrameBuilder withChunkLayout(long... chunkLayout) {
    this.chunkLayout = chunkLayout;
    return this;
  }


  public Frame build() {
    prepareAndCheck();

    // Create a frame
    Frame f = new Frame(key);
    f.preparePartialFrame(colNames);
    f.update();

    // Create chunks
    int cidx = 0;
    long start = 0;
    for (long chnkSize : chunkLayout) {
      createChunks(start, chnkSize, cidx);
      cidx++;
      start = start + chnkSize;
    }

    // Reload frame from DKV
    f = DKV.get(key).get();
    // Finalize frame
    f.finalizePartialFrame(chunkLayout, domains, vecTypes);
    Scope.track(f);
    return f;
  }


  //--------------------------------------------------------------------------------------------------------------------
  // Private
  //--------------------------------------------------------------------------------------------------------------------

  private void prepareAndCheck(){
    // this check has to be run as the first one
    checkVecTypes();
    checkNames();
    // check that we have data for all columns and all columns have the same number of elements
    checkColumnData();
    checkFrameName();
    checkChunkLayout();
    prepareCategoricals();
  }

  // Utility method to get unique values from categorical domain
  private String[] getUniqueValues(HashMap<String, Integer> mapping){
    String[] values = new String[mapping.size()];
    for (String key : mapping.keySet())
      values[mapping.get(key)] = key;
    return values;
  }

  // Utility method to convert domain into categories
  private Integer[] getCategories(HashMap<String, Integer> mapping, String[] original){
    Integer[] categoricals = new Integer[original.length];
    for(int i = 0; i < original.length; i++) {
      categoricals[i] = mapping.get(original[i]);
    }
    return categoricals;
  }

  // Utility method to get mapping from domain member to its level
  private HashMap<String, Integer> getMapping(String[] array){
   HashMap<String, Integer> mapping = new HashMap<>();
    int level = 0;
    for (String item : array) {
      if ((item != null) && (! mapping.containsKey(item))) {
        mapping.put(item, level);
        level++;
      }
    }
    return mapping;
  }

  private void prepareCategoricals(){
    // domains is not null if there is any T_CAT
    for (int colIdx = 0; colIdx < vecTypes.length; colIdx++) {
      if(vecTypes[colIdx]==Vec.T_CAT){
        HashMap<String, Integer> mapping = getMapping(stringData.get(colIdx));
        Integer[] categories = getCategories(mapping, stringData.get(colIdx));
        domains[colIdx] = getUniqueValues(mapping);
        categoriesPerCol.put(colIdx, categories);
      }else{
        if(domains != null) {
          domains[colIdx] = null;
        }
      }
    }
  }

  private void createChunks(long start, long length, int cidx) {
    NewChunk[] nchunks = Frame.createNewChunks(key.toString(), vecTypes, cidx);
    for (int i = (int) start; i < start + length; i++) {

      for (int colIdx = 0; colIdx < vecTypes.length; colIdx++) {
        switch (vecTypes[colIdx]) {
          case Vec.T_NUM:
            nchunks[colIdx].addNum(numericData.get(colIdx)[i]);
            break;
          case Vec.T_STR:
            nchunks[colIdx].addStr(stringData.get(colIdx)[i]);
            break;
          case Vec.T_TIME:
            nchunks[colIdx].addNum(numericData.get(colIdx)[i]);
            break;
          case Vec.T_CAT:
            Integer cat = categoriesPerCol.get(colIdx)[i];
            if (cat != null)
              nchunks[colIdx].addCategorical(cat);
            else
              nchunks[colIdx].addNA();
            break;
          default:
            throw new UnsupportedOperationException("Unsupported Vector type for the builder");

        }
      }
    }
    Frame.closeNewChunks(nchunks);
  }

  // this check has to be called as the first one
  private void checkVecTypes() {
    if(vecTypes==null){
      if (colNames == null) {
        vecTypes = new byte[0];
      } else {
        vecTypes = new byte[colNames.length];
        for (int i = 0; i < colNames.length; i++)
          vecTypes[i] = Vec.T_NUM;
      }
    }
    numCols = vecTypes.length;

    for(int i=0; i<vecTypes.length; i++){
      switch (vecTypes[i]){
        case Vec.T_TIME:
        case Vec.T_NUM:
          if(numericData.get(i)==null){
            numericData.put(i, new double[0]); // init with no data as default
          }
          break;
        case Vec.T_CAT:
          // initiate domains if there is any categorical column and fall-through
          domains = new String[vecTypes.length][];
        case Vec.T_STR:
          if(stringData.get(i)==null){
            stringData.put(i, new String[0]); // init with no data as default
          }
          break;
      }
    }

  }

  private void checkNames() {
    if (colNames == null) {
      colNames = new String[vecTypes.length];
      for (int i = 0; i < vecTypes.length; i++) {
        colNames[i] = "col_" + i;
      }
    }else {
      throwIf(colNames.length != vecTypes.length, "Number of vector types and number of column names differ.");
    }
  }

  private void checkFrameName() {
    if (frameName == null) {
      key = Key.make();
    } else {
      key = Key.make(frameName);
    }
  }

  private void checkChunkLayout() {
    // this expects that method checkColumnData has been executed
    if (chunkLayout != null) {
      // sum all numbers in the chunk layout, it should be smaller than the number of rows in the frame
      int sum = 0;
      for (long numPerChunk : chunkLayout) {
        sum += numPerChunk;
      }
      throwIf(sum > numRows, "Chunk layout contains bad elements. Total sum is higher then available number of elements.");
    } else {
      // create chunk layout - by default 1 chunk
      chunkLayout = new long[]{numRows};
    }
  }

  private void checkColumnData() {
    for (int colIdx = 0; colIdx < numCols; colIdx++) {
      switch (vecTypes[colIdx]) {
        case Vec.T_TIME: // fall-through to T_NUM
        case Vec.T_NUM:
          if (numRows == NOT_SET) {
            numRows = numericData.get(colIdx).length;
          } else {
            throwIf(numRows != numericData.get(colIdx).length, "Columns have different number of elements");
          }
          break;
        case Vec.T_CAT: // fall-through to T_CAT
        case Vec.T_STR:
          if (numRows == NOT_SET) {
            numRows = stringData.get(colIdx).length;
          } else {
            throwIf(numRows != stringData.get(colIdx).length, "Columns have different number of elements");
          }
          break;
        default:
          throw new UnsupportedOperationException("Unsupported Vector type for the builder");
      }
    }
  }

  private void throwIf(boolean condition, String msg){
    if(condition){
      throw new IllegalArgumentException(msg);
    }
  }
}

