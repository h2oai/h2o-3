package ai.h2o.cascade.vals;

/**
 */
public class IdList extends Val {
  private String[] ids;     // list of regular ids
  private String varargId;  // (may be absent)


  public IdList(String[] ids, String varargId) {
    this.ids = ids;
    this.varargId = varargId;
  }


  /**
   * Return the number of regular ids in the list (i.e. excluding the vararg)
   */
  public int numIds() {
    return ids.length;
  }


  /**
   * Get the {@code i}-th id from the list. The caller must ensure that the
   * index is within the valid range {@code 0 .. numIds()-1}.
   */
  public String getId(int i) {
    return ids[i];
  }


  /**
   * Return the vararg id if there is one; otherwise return {@code null}.
   */
  public String getVarargId() {
    return varargId;
  }


  //--------------------------------------------------------------------------------------------------------------------
  // Val interface
  //--------------------------------------------------------------------------------------------------------------------

  @Override public Type type() {
    return Type.IDS;
  }

  @Override public boolean isIds() {
    return true;
  }

  @Override public IdList getIds() {
    return this;
  }

}
