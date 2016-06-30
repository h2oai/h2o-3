package water.rapids.ast.prims.time;

import org.joda.time.MutableDateTime;

/**
 */
public class AstHour extends AstTime {
  public String str() {
    return "hour";
  }

  public long op(MutableDateTime dt) {
    return dt.getHourOfDay();
  }
}
