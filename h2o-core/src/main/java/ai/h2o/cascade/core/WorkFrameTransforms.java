package ai.h2o.cascade.core;

import water.fvec.Vec;

/**
 * Collection of transforms for the WorkFrame.
 */
public abstract class WorkFrameTransforms {



  public static class CopySingleColumnTransform extends WorkFrameTransform {
    private int i;

    public CopySingleColumnTransform(int colIndex) {
      i = colIndex;
    }

    @Override
    public void transform(double[] row) {
      row[ooffset] = row[ioffset + i];
    }

    @Override public byte[] outputTypes() {
      return new byte[]{Vec.T_NUM};
    }
  }



  public static class CopyColumnSliceTransform extends WorkFrameTransform {
    private SliceList sl;
    private transient SliceList.Iterator iterator;


    public CopyColumnSliceTransform(SliceList indices) {
      sl = indices;
    }

    @Override
    public void setupLocal() {
      iterator = sl.iter();
    }

    @Override
    public void transform(double[] row) {
      iterator.reset();
      int i = 0;
      while (iterator.hasNext()) {
        int next = (int) iterator.nextPrim();
        row[ooffset + (i++)] = row[ioffset + next];
      }
    }

    @Override
    public byte[] outputTypes() {
      int n = (int) sl.size();
      byte[] res = new byte[n];
      for (int i = 0; i < n; ++i)
        res[i] = Vec.T_NUM;
      return res;
    }
  }
}
