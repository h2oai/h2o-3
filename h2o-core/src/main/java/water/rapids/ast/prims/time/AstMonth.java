package water.rapids.ast.prims.time;

import org.joda.time.MutableDateTime;

/**
 */
public class AstMonth extends AstTime {
  public String str() {
    return "month";
  }

  public long op(MutableDateTime dt) {
    return dt.getMonthOfYear();
  }
}
