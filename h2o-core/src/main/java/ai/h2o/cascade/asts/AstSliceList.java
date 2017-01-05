package ai.h2o.cascade.asts;

import ai.h2o.cascade.core.Scope;
import ai.h2o.cascade.core.SliceList;
import ai.h2o.cascade.core.Val;

import java.util.ArrayList;

/**
 */
public class AstSliceList extends AstNode<AstSliceList> {
  private SliceList sliceList;


  public AstSliceList(ArrayList<Long> bases, ArrayList<Long> counts, ArrayList<Long> strides) {
    sliceList = new SliceList(bases, counts, strides);
  }

  @Override
  public Val exec(Scope scope) {
    return sliceList;
  }

  @Override
  public String str() {
    return sliceList.toString();
  }

}
