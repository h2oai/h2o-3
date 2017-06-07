package water.rapids.ast.prims.reducers;

import water.MRTask;
import water.fvec.C8Chunk;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;

import java.io.Serializable;
import java.util.PriorityQueue;

import static java.lang.StrictMath.min;

public class AstTopN extends AstPrimitive {
		@Override
		public String[] args() {
				return new String[]{"frame", "col", "nPercent", "getBottomN"};
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
												"values.  If getBottomN is 1, we will return the bottom N percent.  If getBottomN is 0, we will return" +
												"the top N percent of rows";
		}

		@Override
		public ValFrame apply(Env env, Env.StackHelp stk, AstRoot[] asts) { // implementation with PriorityQueue
				Frame frOriginal = stk.track(asts[1].exec(env)).getFrame(); // get the 2nd argument and convert it to a Frame
				int colIndex = (int) stk.track(asts[2].exec(env)).getNum();     // column index of interest
				double nPercent = stk.track(asts[3].exec(env)).getNum();        //  top or bottom percentage of row to return
				int getBottomN = (int) stk.track(asts[4].exec(env)).getNum();   // 0, return top, 1 return bottom percentage
				int totColumns = frOriginal.numCols();
				long numRows = Math.round(nPercent * 0.01 * frOriginal.numRows()); // number of rows to return

				String[] finalColumnNames = {"Original_Row_Indices", frOriginal.name(colIndex)}; // set output frame names
				GrabTopNPQ grabTask = new GrabTopNPQ(finalColumnNames, numRows, (getBottomN == 0));
				grabTask.doAll(frOriginal.vec(colIndex));
				return new ValFrame(grabTask._sortedOut);
		}

		public class GrabTopNPQ<E extends Comparable<E>> extends MRTask<GrabTopNPQ<E>> {
				final String[] _columnName;   // name of column that we are grabbing top N for
				PriorityQueue _sortQueue;
				Frame _sortedOut;   // store the final result of sorting
				final int _rowSize;   // number of top or bottom rows to keep
				final boolean _increasing;  // sort with Top values first if true.
				boolean _csLong = false;      // chunk of interest is long

				private GrabTopNPQ(String[] columnName, long rowSize, boolean increasing) {
						_columnName = columnName;
						_rowSize = (int) rowSize;
						_increasing = increasing;
				}

				@Override
				public void map(Chunk cs) {
						_sortQueue = new PriorityQueue<RowValue<E>>(); // instantiate a priority queue
						_csLong = cs instanceof C8Chunk;
						Long startRow = cs.start();           // absolute row offset

						for (int rowIndex = 0; rowIndex < cs._len; rowIndex++) {  // stuff our chunks into priorityQueue
								long absRowIndex = rowIndex + startRow;
								if (!cs.isNA(rowIndex)) { // skip NAN values
										addOneValue(cs, rowIndex, absRowIndex, _sortQueue);
								}
						}
				}

				@Override
				public void reduce(GrabTopNPQ<E> other) {
						this._sortQueue.addAll(other._sortQueue);

						int sizesToReduce = this._sortQueue.size() - _rowSize;
						if (sizesToReduce > 0) {
								for (int index = 0; index < sizesToReduce; index++)
										this._sortQueue.poll();
						}
				}

				@Override
				public void postGlobal() {  // copy the sorted heap into a vector and make a frame out of it.
						Vec[] xvecs = new Vec[2];   // final output frame will have two chunks, original row index, top/bottom values
						long actualRowOutput = min(_rowSize, _sortQueue.size()); // due to NAs, may not have enough rows to return
						for (int index = 0; index < xvecs.length; index++)
								xvecs[index] = Vec.makeZero(actualRowOutput);

						for (int index = 0; index < actualRowOutput; index++) {
								RowValue transport = (RowValue) this._sortQueue.poll();
								xvecs[0].set(index, transport.getRow());
								xvecs[1].set(index, _csLong ? (Long) transport.getValue() : (Double) transport.getValue());
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
								currPair = new RowValue(absRowIndex, a, _increasing);

						} else {                      // other numeric chunk
								double a = cs.atd(rowIndex);
								currPair = new RowValue(absRowIndex, a, _increasing);
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
		public class RowValue<E extends Comparable<E>> implements Comparable<RowValue<E>>, Serializable {
				private Long _rowIndex;
				private E _value;
				boolean _increasing;  // true if grabbing for top N, false for bottom N

				public RowValue(Long rowIndex, E value, boolean increasing) {
						this._rowIndex = rowIndex;
						this._value = value;
						this._increasing = increasing;
				}

				public E getValue() {
						return this._value;
				}

				public Long getRow() {
						return this._rowIndex;
				}

				@Override
				public int compareTo(RowValue<E> other) {
						return (this.getValue().compareTo(other.getValue()) * (this._increasing ? 1 : -1));
				}
		}
}
