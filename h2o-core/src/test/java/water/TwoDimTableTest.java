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
            new String[]{"RowName1", "R2", "R3", "R56"},
            new Object[][]{
                    new Object[]{new Double(1.123), new String("Two"), new Float(3200034.00001f)},
                    new Object[]{new Float(123.34), new String("adfasdfTwo"), new Float(4)},
                    new Object[]{new Double(1.123), new String("Two"), new Float(3200034.00001f)},
                    new Object[]{new Double(3.33420923423423), new String("asdStsdfa"), new Double(3.4f)}});
    String ts = table.toString();
    assertTrue(ts.length() > 0);
  }
}
