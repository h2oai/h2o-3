package water.rapids.ast.prims.time;

import org.joda.time.MutableDateTime;

/**
 */
public class AstYear extends AstTime {
  public String str() {
    return "year";
  }

  public long op(MutableDateTime dt) {
    return dt.getYear();
  }
}
