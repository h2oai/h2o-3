package water;

import static org.junit.Assert.assertTrue;
import org.junit.Test;
import water.util.TwoDimTable;

public class TwoDimTableTest {

  @Test
  public void run() {
    TwoDimTable table = new TwoDimTable(
            "My foo bar table",
            new String[]{"DoubleValue", "S2", "My Terrible Percent Value"},
            new String[]{"%5.8e", "%s", "%5.8g %%"},
            new String[]{"First row", "R2", "Row #3", "Last row is here:"},
            new Object[][]{
                    new Object[]{new Double(1.123),            new String("One"),    new Float(3200034.00001f)},
                    new Object[]{new Float(123.34),            new String("Two"),    new Float(1.0f)},
                    new Object[]{new Double(5.123),            new String("Three"),  new Float(3234.00001f)},
                    new Object[]{new Double(3.33420923423423), new String("FooBar"), new Double(3.40234234)}
            });
    String ts = table.toString();
    assertTrue(ts.length() > 0);
  }
}
