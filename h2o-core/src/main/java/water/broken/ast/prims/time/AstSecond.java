package water.rapids.ast.prims.time;

import org.joda.time.MutableDateTime;

/**
 */
public class AstSecond extends AstTime {
  public String str() {
    return "second";
  }

  public long op(MutableDateTime dt) {
    return dt.getSecondOfMinute();
  }
}
