package ai.h2o.cascade.core;

/**
 */
public class IdList {
  private String[] ids;     // list of regular ids
  private String varargId;  // (may be absent)

  public IdList(String[] ids, String varargId) {
    this.ids = ids;
    this.varargId = varargId;
  }

  public int numIds() {
    return ids.length;
  }

  public String getId(int i) {
    return ids[i];
  }

  public String getVarargId() {
    return varargId;
  }
}
