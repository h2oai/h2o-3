package water.rapids.ast.prims.time;

import org.joda.time.MutableDateTime;

/**
 */
public class AstDay extends AstTime {
  public String str() {
    return "day";
  }

  public long op(MutableDateTime dt) {
    return dt.getDayOfMonth();
  }
}
