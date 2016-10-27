package water.fvec;

import water.DKV;
import water.Key;

import java.util.HashMap;

/**
 * Class used for creating simple testing frames using builder pattern
 * <p>
 * Example usage:
 * {@code
 * final Frame builder = new TestFrameBuilder()
 * .withName("testFrame")
 * .withColNames("ColA", "ColB")
 * .withVecTypes(Vec.T_NUM, Vec.T_STR)
 * .withDataForCol(0, ard(Double.NaN, 1, 2, 3, 4, 5.6, 7))
 * .withDataForCol(1, ar("A", "B", "C", "E", "F", "I", "J"))
 * .withChunkLayout(2, 2, 2, 1)
 * .build();
 * }
 */
public class TestFrameBuilder {
  private static final long NOT_SET = -1;
  private HashMap<Integer, String[]> stringData = new HashMap<>();
  private HashMap<Integer, double[]> numericData = new HashMap<>();
  private String frameName;
  private byte[] vecTypes;
  private String[] colNames;
  private long[] chunkLayout;
  private int numCols;
  Key<Frame> key;
  private long numRows = NOT_SET;

  private void createChunks(long start, long length, int cidx) {
    NewChunk[] nchunks = Frame.createNewChunks(frameName, vecTypes, cidx);
    for (int i = (int) start; i < start + length; i++) {

      for (int colIdx = 0; colIdx < vecTypes.length; colIdx++) {
        switch (vecTypes[colIdx]) {
          case Vec.T_NUM:
            nchunks[colIdx].addNum(numericData.get(colIdx)[i]);
            break;
          case Vec.T_STR:
            nchunks[colIdx].addStr(stringData.get(colIdx)[i]);
            break;
          default:
            throw new UnsupportedOperationException("Unsupported Vector type for the builder");

        }
      }
    }
    Frame.closeNewChunks(nchunks);
  }

  public int getNumCols() {
    return numCols;
  }

  public long getNumRows() {
    return numRows;
  }

  public String[] getDataForStrCol(int colNum) {
    return stringData.get(colNum);
  }

  public double[] getDataForNumCol(int colNum) {
    return numericData.get(colNum);
  }

  public long[] getChunkLayout() {
    return chunkLayout;
  }

  public String[] getColNames() {
    return colNames;
  }

  public byte[] getVecTypes() {
    return vecTypes;
  }

  public String getFrameName() {
    return frameName;
  }

  /**
   * Sets the name for the frame. Default name is created if this method is not called.
   */
  public TestFrameBuilder withName(String frameName) {
    this.frameName = frameName;
    return this;
  }

  /**
   * Sets the names for the columns. Default names are created if this method is not called.
   */
  public TestFrameBuilder withColNames(String... colNames) {
    this.colNames = colNames;
    return this;
  }

  /**
   * Sets the vector types.
   */
  public TestFrameBuilder withVecTypes(byte... vecTypes) {
    this.vecTypes = vecTypes;
    this.numCols = vecTypes.length;
    return this;
  }

  /**
   * Sets data for a particular column
   *
   * @param column for which to set data
   * @param data   array of data
   */
  public TestFrameBuilder withDataForCol(int column, String[] data) {
    stringData.put(column, data);
    return this;
  }

  /**
   * Sets data for a particular column
   *
   * @param column for which to set data
   * @param data   array of data
   */
  public TestFrameBuilder withDataForCol(int column, double[] data) {
    numericData.put(column, data);
    return this;
  }

  /**
   * Sets data for a particular column
   *
   * @param column for which to set data
   * @param data   array of data
   */
  public TestFrameBuilder withDataForCol(int column, long[] data) {
    double[] d = new double[data.length];
    for (int i = 0; i < data.length; i++) {
      d[i] = data[i];
    }
    numericData.put(column, d);
    return this;
  }

  private void checkVecTypes() {
    assert vecTypes != null && vecTypes.length != 0 : "Vec types has to be specified";
  }

  public TestFrameBuilder withChunkLayout(long... chunkLayout) {
    this.chunkLayout = chunkLayout;
    return this;
  }

  private void checkNames() {
    if (colNames == null || colNames.length == 0) {
      colNames = new String[vecTypes.length];
      for (int i = 0; i < vecTypes.length; i++) {
        colNames[i] = "col_" + i;
      }
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
      // sum all numbers in it, it should be smaller than number of rows in the frame
      int sum = 0;
      for (long numPerChunk : chunkLayout) {
        sum += numPerChunk;
      }
      assert sum <= numRows : "Chunk layout contains bad elements. Total sum is higher then available number of elements";
    } else {
      // create chunk layout - by default 1 chunk
      chunkLayout = new long[]{numRows};
    }
  }

  private void checkColumnData() {
    for (int colIdx = 0; colIdx < numCols; colIdx++) {

      switch (vecTypes[colIdx]) {
        case Vec.T_NUM:
          assert numericData.get(colIdx) != null : "Data for col " + colIdx + " has to be set";
          if (numRows == NOT_SET) {
            numRows = numericData.get(colIdx).length;
          } else {
            assert numRows == numericData.get(colIdx).length : "Columns has different number of elements";
          }
          break;
        case Vec.T_STR:
          assert stringData.get(colIdx) != null : "Data for col " + colIdx + " has to be set";
          if (numRows == NOT_SET) {
            numRows = stringData.get(colIdx).length;
          } else {
            assert numRows == stringData.get(colIdx).length : "Columns has different number of elements";
          }
          break;
        default:
          throw new UnsupportedOperationException("Unsupported Vector type for the builder");
      }
    }
  }

  public Frame build() {
    checkVecTypes();
    checkNames();
    // check that we have data for all columns and all columns has the same number of elements
    checkColumnData();
    checkFrameName();
    checkChunkLayout();

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
    f = DKV.get(frameName).get();
    // Finalize frame
    f.finalizePartialFrame(chunkLayout, null, vecTypes);
    return f;
  }
}
