package ai.h2o.cascade.core;


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
  public boolean isSlice() {
    return true;
  }

  @Override
  public SliceList getSlice() {
    return sliceList;
  }
}
