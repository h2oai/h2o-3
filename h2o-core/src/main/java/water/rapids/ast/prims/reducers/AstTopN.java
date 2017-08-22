package water.rapids.ast.prims.reducers;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;

import java.util.PriorityQueue;

import static java.lang.StrictMath.min;

public class AstTopN extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"frame", "col", "nPercent", "grabTopN"};
  }

  @Override
  public String str() {
    return "topn";
  }

  @Override
  public int nargs() {
    return 1 + 4;
  } // function name plus 4 arguments.

  @Override
  public String example() {
    return "(topn frame col nPercent getBottomN)";
  }

  @Override
  public String description() {
    return "Return the top N percent rows for a numerical column as a frame with two columns.  The first column " +
            "will contain the original row indices of the chosen values.  The second column contains the top N row" +
            "values.  If grabTopN is -1, we will return the bottom N percent.  If grabTopN is 1, we will return" +
            "the top N percent of rows";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot[] asts) { // implementation with PriorityQueue
    Frame frOriginal = stk.track(asts[1].exec(env)).getFrame(); // get the 2nd argument and convert it to a Frame
    int colIndex = (int) stk.track(asts[2].exec(env)).getNum();     // column index of interest
    double nPercent = stk.track(asts[3].exec(env)).getNum();        //  top or bottom percentage of row to return
    int grabTopN = (int) stk.track(asts[4].exec(env)).getNum();   // 0, return top, 1 return bottom percentage
    long numRows = Math.round(nPercent * 0.01 * frOriginal.numRows()); // number of rows to return

    String[] finalColumnNames = {"Original_Row_Indices", frOriginal.name(colIndex)}; // set output frame names
    GrabTopNPQ grabTask = new GrabTopNPQ(finalColumnNames, numRows, grabTopN, frOriginal.vec(colIndex).isInt());
    grabTask.doAll(frOriginal.vec(colIndex));
    return new ValFrame(grabTask._sortedOut);
  }

  public class GrabTopNPQ<E extends Comparable<E>> extends MRTask<GrabTopNPQ<E>> {
    final String[] _columnName;   // name of column that we are grabbing top N for
    PriorityQueue _sortQueue;
    long[] _rowIndices;  // store original row indices of values that are grabbed
    long[] _lValues;         // store the grabbed values
    double[] _dValues;  // store grabbed longs
    Frame _sortedOut;   // store the final result of sorting
    final int _rowSize;   // number of top or bottom rows to keep
    final int _flipSign;   // 1 for top values, -1 for bottom values
    boolean _csLong = false;      // chunk of interest is long

    private GrabTopNPQ(String[] columnName, long rowSize, int flipSign, boolean isLong) {
      _columnName = columnName;
      _rowSize = (int) rowSize;
      _flipSign = flipSign;
      _csLong = isLong;
    }

    @Override
    public void map(Chunk cs) {
      _sortQueue = new PriorityQueue<RowValue<E>>(); // instantiate a priority queue
      long startRow = cs.start();           // absolute row offset

      for (int rowIndex = 0; rowIndex < cs._len; rowIndex++) {  // stuff our chunks into priorityQueue
        long absRowIndex = rowIndex + startRow;
        if (!cs.isNA(rowIndex)) { // skip NAN values
          addOneValue(cs, rowIndex, absRowIndex, _sortQueue);
        }
      }

      // copy the PQ into the corresponding arrays
      if (_csLong) {
        _lValues = new long[_sortQueue.size()];
        copyPQ2ArryL(_sortQueue, _lValues);
      } else {
        _dValues = new double[_sortQueue.size()];
        copyPQ2ArryD(_sortQueue, _dValues);
      }
    }

    public void copyPQ2ArryL(PriorityQueue sortQueue, long[] values) {
      //copy values on PQ into arrays in sorted order
      int qSize = sortQueue.size();
      _rowIndices = new long[qSize];

      for (int index = qSize - 1; index >= 0; index--) {
        RowValue tempPairs = (RowValue) sortQueue.poll();
        _rowIndices[index] = tempPairs.getRow();
        values[index] = (long) tempPairs.getValue();
      }
    }

    public <T> void copyPQ2ArryD(PriorityQueue sortQueue, double[] values) {
      //copy values on PQ into arrays in sorted order
      int qSize = sortQueue.size();
      _rowIndices = new long[qSize];

      for (int index = qSize - 1; index >= 0; index--) {
        RowValue tempPairs = (RowValue) sortQueue.poll();
        _rowIndices[index] = tempPairs.getRow();
        values[index] = (double) tempPairs.getValue();
      }
    }

    @Override
    public void reduce(GrabTopNPQ<E> other) {
      // do a combine here of two arrays.  Note the value always store values that are increasing
      if (_csLong)
        mergeArraysL(other._rowIndices, other._lValues);
      else
        mergeArraysD(other._rowIndices, other._dValues);
    }

    public void mergeArraysL(long[] otherRow, long[] otherValue) {
      // grab bottom and grab top are slightly different
      int finalArraySize = min(this._rowSize, this._lValues.length + otherValue.length);

      long[] newRow = new long[finalArraySize];
      long[] newValues = new long[finalArraySize]; // desired values are at start of array
      int thisRowIndex = 0;
      int otherRowIndex = 0;
      for (int index = 0; index < finalArraySize; index++) {
        if ((thisRowIndex < this._lValues.length) && (otherRowIndex < otherValue.length)) {
          if ((((Long) this._lValues[thisRowIndex]).compareTo(otherValue[otherRowIndex]) * this._flipSign >= 0)) {
            newRow[index] = this._rowIndices[thisRowIndex];
            newValues[index] = this._lValues[thisRowIndex++];
          } else {
            newRow[index] = otherRow[otherRowIndex];
            newValues[index] = otherValue[otherRowIndex++];
          }
        } else { // one of the array is done!
          if (thisRowIndex < this._lValues.length) { // otherArray is done
            newRow[index] = this._rowIndices[thisRowIndex];
            newValues[index] = this._lValues[thisRowIndex++];
          } else { // thisArray is done.  Use the other one
            newRow[index] = otherRow[otherRowIndex];
            newValues[index] = otherValue[otherRowIndex++];
          }
        }
      }
      this._rowIndices = newRow;
      this._lValues = newValues;
    }

    public void mergeArraysD(long[] otherRow, double[] otherValue) {
      // grab bottom and grab top are slightly different
      int finalArraySize = min(this._rowSize, this._rowIndices.length + otherRow.length);

      long[] newRow = new long[finalArraySize];
      double[] newValues = new double[finalArraySize]; // desired values are at start of array
      int thisRowIndex = 0;
      int otherRowIndex = 0;
      for (int index = 0; index < finalArraySize; index++) {
        if ((thisRowIndex < this._dValues.length) && (otherRowIndex < otherValue.length)) {
          if (((Double) this._dValues[thisRowIndex]).compareTo(otherValue[otherRowIndex]) * this._flipSign >= 0) {
            newRow[index] = this._rowIndices[thisRowIndex];
            newValues[index] = this._dValues[thisRowIndex++];
          } else {
            newRow[index] = otherRow[otherRowIndex];
            newValues[index] = otherValue[otherRowIndex++];
          }
        } else { // one of the array is done!
          if (thisRowIndex < this._dValues.length) { // otherArray is done
            newRow[index] = this._rowIndices[thisRowIndex];
            newValues[index] = this._dValues[thisRowIndex++];
          } else { // thisArray is done.  Use the other one
            newRow[index] = otherRow[otherRowIndex];
            newValues[index] = otherValue[otherRowIndex++];
          }
        }
      }
      this._rowIndices = newRow;
      this._dValues = newValues;
    }

    @Override
    public void postGlobal() {  // copy the sorted heap into a vector and make a frame out of it.
      Vec[] xvecs = new Vec[2];   // final output frame will have two chunks, original row index, top/bottom values
      long actualRowOutput = this._rowIndices.length; // due to NAs, may not have enough rows to return
      for (int index = 0; index < xvecs.length; index++)
        xvecs[index] = Vec.makeZero(actualRowOutput);

      for (int index = 0; index < actualRowOutput; index++) {
        xvecs[0].set(index, this._rowIndices[index]);
        xvecs[1].set(index, _csLong ? this._lValues[index] : this._dValues[index]);
      }
      _sortedOut = new Frame(_columnName, xvecs);
    }

    /*
    This function will add one value to the sorted priority queue.
 */
    public void addOneValue(Chunk cs, int rowIndex, long absRowIndex, PriorityQueue sortHeap) {
      RowValue currPair = null;
      if (_csLong) {  // long chunk
        long a = cs.at8(rowIndex);
        currPair = new RowValue(absRowIndex, a, _flipSign);

      } else {                      // other numeric chunk
        double a = cs.atd(rowIndex);
        currPair = new RowValue(absRowIndex, a, _flipSign);
      }
      sortHeap.offer(currPair);   // add pair to PriorityQueue
      if (sortHeap.size() > _rowSize) {
        sortHeap.poll();      // remove head if exceeds queue size
      }
    }
  }

  /*
  Small class to implement priority entry is a key/value pair of original row index and the
  corresponding value.  Implemented the compareTo function and comparison is performed on
  the value.
   */
  public class RowValue<E extends Comparable<E>> implements Comparable<RowValue<E>> {
    private long _rowIndex;
    private E _value;
    boolean _increasing;  // true if grabbing for top N, false for bottom N
    int _flipSign;        // 1 to grab top and -1 to grab bottom

    public RowValue(long rowIndex, E value, int flipSign) {
      this._rowIndex = rowIndex;
      this._value = value;
      this._flipSign = flipSign;
    }

    public E getValue() {
      return this._value;
    }

    public long getRow() {
      return this._rowIndex;
    }

    @Override
    public int compareTo(RowValue<E> other) {
      return (this.getValue().compareTo(other.getValue()) * this._flipSign);
    }
  }
}
