package water.rapids.ast.prims.time;

import org.joda.time.MutableDateTime;

/**
 */
public class AstWeek extends AstTime {
  public String str() {
    return "week";
  }

  public long op(MutableDateTime dt) {
    return dt.getWeekOfWeekyear();
  }
}
