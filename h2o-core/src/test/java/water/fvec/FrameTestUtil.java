package water.fvec;

import hex.CreateFrame;
import org.junit.Assert;
import org.junit.Ignore;
import water.DKV;
import water.Key;
import water.MRTask;
import water.TestBase;
import water.parser.BufferedString;
import water.util.Log;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Methods to access frame internals.
 */
@Ignore("Support for tests, but no actual tests here")
public class FrameTestUtil extends TestBase {

  public static Frame createFrame(String fname, long[] chunkLayout, String[][] data) {
    Frame f = new Frame(Key.<Frame>make(fname));
    f.preparePartialFrame(new String[]{"C0"});
    f.update();
    // Create chunks
    byte[] types = new byte[] {Vec.T_STR};
    for (int i=0; i<chunkLayout.length; i++) {
      createNC(fname, data[i], i, (int) chunkLayout[i], types);
    }
    // Reload frame from DKV
    f = DKV.get(fname).get();
    // Finalize frame
    f.finalizePartialFrame(chunkLayout, new String[][] { null }, types);
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

  /**
   * Look for the number of times a particular value (integer) appears in a
   * column of a dataframel.  The columnInd is where you expect your value to
   * be found and columnIndRowValue is the column ID which identifies your
   * actual data row number where that value is found.
   */
  public static class CountIntValueRows extends MRTask<CountIntValueRows> {
    public long _numberAppear;
    public long _value;
    public int _columnIndex;  // column where the values are to be counted
    public int _columnIndRowValue; // column that contains row indices
    public ArrayList<Long> _specialRows;


    public CountIntValueRows(long value, int columnInd, int columnIndRowValue, Frame fr) {
      if (fr.vec(columnInd).isCategorical() || fr.vec(columnInd).isInt()) {
        _value = value;
        _columnIndex = columnInd;
        _numberAppear = 0;
        _specialRows = new ArrayList<Long>();
        _columnIndRowValue = columnIndRowValue;
      } else {
        throw new IllegalArgumentException("The column data type must be categorical or integer.");
      }
    }

    public void map(Chunk[] chks) {
      int numRows = chks[0].len();
      for (int index = 0; index < numRows; index++) {
        if (chks[_columnIndex].at8(index) == _value) {
          _specialRows.add(chks[_columnIndRowValue].at8(index));
          _numberAppear++;
        }
      }
    }

    public void reduce(CountIntValueRows that) {
      _numberAppear += that._numberAppear;
    }

    public long getNumberAppear() {
      return _numberAppear;
    }
  }

  /**
   * This task will create a Frame containing row indices
   */
  public static class Create1IDColumn extends MRTask<Create1IDColumn> {
    Frame _oneColumnFrame;

    public Create1IDColumn(int numRows) {
      CreateFrame cf = new CreateFrame();
      cf.rows=numRows;
      cf.cols=1;
      cf.categorical_fraction = 0.0;
      cf.integer_fraction = 1.0;
      cf.binary_fraction = 0.0;
      cf.time_fraction = 0.0;
      cf.string_fraction = 0.0;
      cf.binary_ones_fraction = 0.0;
      cf.has_response=false;
      _oneColumnFrame = cf.execImpl().get();
    }

    public void map(Chunk chks) {
      int numRows = chks.len();
      int rowOffset = (int) chks.start();
      for (int index = 0; index < numRows; index++) {
        chks.set(index, (rowOffset+index));
      }
    }

    public Frame returnFrame() {
      return _oneColumnFrame;
    }
  }

  /**
   * For a column that contains row indices, this task will count and make sure all
   * the rows are present by looking for this row indices.
   */
  public static class CountAllRowsPresented extends MRTask<CountAllRowsPresented> {
    int[] _counters;  // keep tracks of number of row indices
    int _columnIndex;
    ArrayList<Integer> _badRows;

    public CountAllRowsPresented(int columnInd, Frame fr) {
      if (fr.vec(columnInd).isCategorical() || fr.vec(columnInd).isInt()) {
        _columnIndex = columnInd;
        long numRows = fr.numRows();
        _counters = new int[(int)numRows];
        Arrays.fill(_counters, 1);
        _badRows = new ArrayList<Integer>();
      } else {
        throw new IllegalArgumentException("The column data type must be categorical or integer.");
      }
    }

    public void map(Chunk[] chks) {
      int numRows = chks[0].len();
      for (int index = 0; index < numRows; index++) {
        long temp = chks[_columnIndex].at8(index);
        _counters[(int)temp]--;
      }
    }

    public ArrayList<Integer> findMissingRows() {
      int numBad = 0;
      for (int index=0; index < _counters.length; index++) {
        if (_counters[index] != 0) {
          numBad++;
          _badRows.add(index);
          Log.info("Missing row "+index+" in final result with counter value "+_counters[index]);
        }
      }
      Log.info("Total number of problem rows: "+numBad);
      return _badRows;
    }
  }
}
