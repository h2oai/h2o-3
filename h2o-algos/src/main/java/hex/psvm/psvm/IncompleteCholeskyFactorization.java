/*
Copyright 2007 Google Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package hex.psvm.psvm;

import hex.DataInfo;
import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.util.Log;

/**
 * Implementation of ICF based on https://static.googleusercontent.com/media/research.google.com/en//pubs/archive/34638.pdf
 *
 * This implementation is based on and takes clues from the reference PSVM implementation in C++: 
 *    https://code.google.com/archive/p/psvm/source/default/source
 *    original code: Copyright 2007 Google Inc., Apache License, Version 2.0   
 */
public class IncompleteCholeskyFactorization {

  public static Frame icf(DataInfo di, Kernel kernel, int n, double threshold) {
    return icf(di._adaptedFrame, di, kernel, n, threshold);
  }
  
  static Frame icf(Frame frame, String response, Kernel kernel, int n, double threshold) {
    Frame adapted = new Frame(frame);
    try {
      adapted.add(response, adapted.remove(response)); // make response to the last column
      adapted.add("two_norm_sq", adapted.anyVec().makeZero()); // (L2 norm)^2; initialized 0 - will be calculated later - treated as second response for the lack of a better place

      DataInfo di = new DataInfo(adapted, null, 2, true, DataInfo.TransformType.NONE, DataInfo.TransformType.NONE, true, false, false, false, false, false, null)
              .disableIntercept();

      return icf(di, kernel, n, threshold);
    } finally {
      Vec tns = adapted.vec("two_norm_sq");
      if (tns != null) {
        tns.remove();
      }
    }
  }

  private static Frame icf(Frame frame, DataInfo di, Kernel kernel, int n, double threshold) {
    Frame icf = new Frame();
    Frame workspace = new Frame();
    try {
      Vec diag1 = new InitICF(di, kernel).doAll(Vec.T_NUM, frame).outputFrame().anyVec(); // diag1: the diagonal part of Q (the kernel matrix diagonal)
      Vec diag2 = frame.anyVec().makeZero(); // diag2: the quadratic sum of a row of the ICF matrix
      Vec pivot_selected = frame.anyVec().makeZero();

      workspace.add("pivot_selected", pivot_selected);
      workspace.add("diag1", diag1);
      workspace.add("diag2", diag2);

      for (int i = 0; i < n; i++) {
        FindPivot fp = new FindPivot(frame, di).doAll(workspace);
        if (fp._trace < threshold) {
          Log.info("ICF finished before full rank was reached in iteration " + i + 
                  ". Trace value = " + fp._trace + " (convergence threshold = " + threshold + ").");
          break;
        }
        Log.info("ICF Iteration " + i + ": trace: " + fp._trace);

        Vec newCol = frame.anyVec().makeZero();
        icf.add("C" + (i + 1), newCol);

        UpdatePivot up = new UpdatePivot(icf, pivot_selected, fp).doOnRemote();

        new CalculateColumn(frame, di, kernel, icf, fp._pivot_sample, up._header_row).doAll(pivot_selected, diag2, newCol);
      }
    } finally {
      workspace.delete();
    }
    return icf;
  }

  /**
   * Calculate a new column of the ICF matrix
   */
  private static class CalculateColumn extends MRTask<CalculateColumn> {
    // IN
    Frame _full_frame;
    DataInfo _dinfo;
    Kernel _kernel;
    double[] _header_row;
    DataInfo.Row _pivot_sample;
    Frame _icf;

    private CalculateColumn(Frame frame, DataInfo dinfo, Kernel kernel, 
                            Frame icf,
                            DataInfo.Row pivotSample, double[] headerRow) {
      _full_frame = frame;
      _dinfo = dinfo;
      _kernel = kernel;
      _icf = icf;
      _pivot_sample = pivotSample;
      _header_row = headerRow;
    }

    @Override
    public void map(Chunk pivot_selected, Chunk diag2, Chunk newColChunk) {
      Chunk[] icf = getLocalChunks(_icf, pivot_selected.start());
      Chunk[] frameChunks = getLocalChunks(_full_frame, pivot_selected.start());

      double[] newColData = MemoryManager.malloc8d(newColChunk._len);
      boolean[] pivotSelected = MemoryManager.mallocZ(newColChunk._len); 
      for (int i = 0; i < newColData.length; i++) {
        pivotSelected[i] = pivot_selected.at8(i) != 0;
        newColData[i] = pivotSelected[i] ? newColChunk.atd(i) : 0;
      }

      for (int k = 0; k < icf.length - 1; k++) {
        for (int i = 0; i < newColChunk._len; i++) {
          if (pivotSelected[i])
            continue;
          newColData[i] -= icf[k].atd(i) * _header_row[k];
        }
      }
      DataInfo.Row row = _dinfo.newDenseRow();
      for (int i = 0; i < newColChunk._len; i++) {
        if (pivotSelected[i])
          continue;
        _dinfo.extractDenseRow(frameChunks, i, row);
        newColData[i] += _kernel.calcKernelWithLabel(row, _pivot_sample);
      }
      for (int i = 0; i < newColChunk._len; i++) {
        if (pivotSelected[i])
          continue;
        newColData[i] /= _header_row[_header_row.length - 1];
      }

      // Updated newCol chunk and calculate diag2
      for (int i = 0; i < newColData.length; i++) {
        double v = newColData[i];
        newColChunk.set(i, v);
        diag2.set(i, diag2.atd(i) + (v * v));
      }
    }
  }
  
  private static class InitICF extends MRTask<InitICF> {
    // IN
    DataInfo _dinfo;
    Kernel _kernel;

    InitICF(DataInfo dinfo, Kernel kernel) {
      _dinfo = dinfo;
      _kernel = kernel;
    }

    @Override
    public void map(Chunk[] cs, NewChunk nc) {
      DataInfo.Row row = _dinfo.newDenseRow();
      Chunk two_norm_sq = cs[cs.length - 1];

      for (int r = 0; r < cs[0]._len; r++) {
        _dinfo.extractDenseRow(cs, r, row);
        final double tns = row.twoNormSq();
        row.response[1] = tns;
        two_norm_sq.set(r, tns);
        double diag1val = _kernel.calcKernel(row, row);
        nc.addNum(diag1val);
      }
    }

  }

  /**
   * Find new pivot and calculate a trace of Q
   */
  private static class FindPivot extends MRTask<FindPivot> {
    // IN
    Frame _full_frame;
    DataInfo _dinfo;

    // OUT
    long _index = -1;
    double _value;
    DataInfo.Row _pivot_sample;
    double _trace;

    FindPivot(Frame frame, DataInfo dinfo) {
      _full_frame = frame;
      _dinfo = dinfo;
    }

    @Override
    public void map(Chunk pivot_selected, Chunk diag1, Chunk diag2) {
      if (diag1._len == 0)
        return;
      int idx = -1;
      _value = -Double.MAX_VALUE;
      _trace = 0;
      for (int i = 0; i < diag1._len; i++) {
        if (pivot_selected.at8(i) != 0)
          continue;
        double diff = diag1.atd(i) - diag2.atd(i);
        _trace += diff;
        if (diff > _value) {
          _value = diff;
          idx = i;
        }
      }
      if (idx != -1) {
        _index = diag1.start() + idx;
        _pivot_sample = extractLocalRow(_index);
      }
    }

    @Override
    public void reduce(FindPivot mrt) {
      _trace += mrt._trace;
      if ((_index == -1) || ((mrt._index != -1) && (mrt._value > _value))) {
        _index = mrt._index;
        _value = mrt._value;
        _pivot_sample = mrt._pivot_sample;
      }
    }

    private DataInfo.Row extractLocalRow(long idx) {
      Chunk[] chks = getLocalChunks(_full_frame, idx);
      DataInfo.Row row = _dinfo.newDenseRow();
      int rid = (int) (idx - chks[0].start());
      _dinfo.extractDenseRow(chks, rid, row);
      return row;
    }

  }

  /**
   * Update pivot on remote node and return the corresponding row of the ICF matrix
   */
  private static class UpdatePivot extends DTask<UpdatePivot> {
    // IN
    Frame _icf;
    Vec _pivot_selected;
    long _index;
    double _value;

    // OUT
    double[] _header_row;

    UpdatePivot(Frame icf, Vec pivotSelected, FindPivot fp) {
      _icf = icf;
      _pivot_selected = pivotSelected;
      _index = fp._index;
      _value = Math.sqrt(fp._value);
    }

    @Override
    public void compute2() {
      // using global Vec API on the home node of the underlying chunks - will be fast 
      _icf.vecs()[_icf.numCols() - 1].set(_index, _value);
      _pivot_selected.set(_index, 1);

      Chunk[] chks = getLocalChunks(_icf, _index);
      int row = (int) (_index - chks[0].start());
      _header_row = new double[chks.length];
      for (int i = 0; i < chks.length; i++) {
        _header_row[i] = chks[i].atd(row);
      }
      tryComplete();
    }

    UpdatePivot doOnRemote() {
      Vec newCol = _icf.lastVec();
      assert newCol.isConst();

      H2ONode node = newCol.chunkKey(newCol.elem2ChunkIdx(_index)).home_node();
      return new RPC<>(node, this).call().get();
    }

  }

  private static Chunk[] getLocalChunks(Frame f, long rowId) {
    if (f.numCols() == 0)
      return new Chunk[0];
    Vec[] vecs = f.vecs();
    Chunk[] chks = new Chunk[vecs.length];
    int cidx = vecs[0].elem2ChunkIdx(rowId);
    for (int i = 0; i < chks.length; i++) {
      assert vecs[i].chunkKey(cidx).home();
      chks[i] = vecs[i].chunkForChunkIdx(cidx);
    }
    return chks;
  }

}
