package ai.h2o.cascade.vals;


import ai.h2o.cascade.core.SliceList;

/**
 */
public class ValSlice extends Val {
  private SliceList sliceList;


  public ValSlice(SliceList sl) {
    sliceList = sl;
  }

  @Override
  public Type type() {
    return Type.SLICE;
  }

  @Override
  public boolean maybeSlice() {
    return true;
  }

  @Override
  public SliceList getSlice() {
    return sliceList;
  }
}
