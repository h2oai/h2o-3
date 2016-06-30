package water.rapids.ast.prims.time;

import org.joda.time.MutableDateTime;

/**
 */
public class AstMillis extends AstTime {
  public String str() {
    return "millis";
  }

  public long op(MutableDateTime dt) {
    return dt.getMillisOfSecond();
  }
}
