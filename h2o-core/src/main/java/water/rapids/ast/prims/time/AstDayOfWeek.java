package water.rapids.ast.prims.time;

import org.joda.time.MutableDateTime;

/**
 */
public class AstDayOfWeek extends AstTime {
  static private final String[][] FACTORS = new String[][]{{"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"}}; // Order comes from Joda

  @Override
  protected String[][] factors() {
    return FACTORS;
  }

  @Override
  public String str() {
    return "dayOfWeek";
  }

  @Override
  public long op(MutableDateTime dt) {
    return dt.getDayOfWeek() - 1;
  }
}
