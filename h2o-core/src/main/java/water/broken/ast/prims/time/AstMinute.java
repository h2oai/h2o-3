package water.rapids.ast.prims.time;

import org.joda.time.MutableDateTime;

/**
 */
public class AstMinute extends AstTime {
  public String str() {
    return "minute";
  }

  public long op(MutableDateTime dt) {
    return dt.getMinuteOfHour();
  }
}
