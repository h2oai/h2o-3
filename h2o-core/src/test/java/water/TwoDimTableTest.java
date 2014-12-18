package water;

import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;
import water.util.Log;
import water.util.TwoDimTable;

public class TwoDimTableTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  @Test
  public void run1() {
    TwoDimTable table = new TwoDimTable(
            "My foo bar table",
            new String[]{"DoubleValue", "S2", "My Terrible Percent Value"},
            new String[]{"%5.8e", "%s", "%5.8g %%"},
            new String[]{"First row", "R2", "Row #3", "Last row is here:"},
            new String[][]{
                    new String[]{null,             "One",    null        },
                    new String[]{null,             null,     null        },
                    new String[]{null,             "Three",  null        },
                    new String[]{null,             "FooBar", null        }
            },
            new Double[][]{
                    new Double[]{1.123,            null,    3200034.00001},
                    new Double[]{123.34,           null,    1.0          },
                    new Double[]{null,             null,    3234.00001   },
                    new Double[]{3.33420923423423, null,    3.40234234   }
            }
    );
    String ts = table.toString();
    assertTrue(ts.length() > 0);
    Log.info(ts);

    String json = new String(table.writeJSON(new AutoBuffer()).buf());
    assertTrue(json.equals("{\"description\":\"My foo bar table\",\"colNames\":[\"DoubleValue\",\"S2\",\"My Terrible Percent Value\"],\"colFormatStrings\":[\"%5.8e\",\"%s\",\"%5.8g %%\"],\"rowHeaders\":[\"First row\",\"R2\",\"Row #3\",\"Last row is here:\"],\"strings\":[[null,\"One\",null],[null,null,null],[null,\"Three\",null],[null,\"FooBar\",null]],\"doubles\":[[1.123,null,3200034.00001],[123.34,null,1.0],[null,null,3234.00001],[3.33420923423423,null,3.40234234]]}"));
    Log.info(json);
  }

  @Test(expected=IllegalArgumentException.class)
  public void run2() {
    TwoDimTable table = new TwoDimTable(
            "My foo bar table",
            new String[]{"DoubleValue", "S2", "My Terrible Percent Value"},
            new String[]{"%5.8e", "%s", "%5.8g %%"},
            new String[]{"First row", "R2", "Row #3", "Last row is here:"},
            new String[][]{
                    new String[]{null,             "One",    null        },
                    new String[]{null,             null,     null        },
                    new String[]{null,             "Three",  "extra"     },
                    new String[]{null,             "FooBar", null        }
            },
            new Double[][]{
                    new Double[]{1.123,            null,    3200034.00001},
                    new Double[]{123.34,           null,    1.0          },
                    new Double[]{null,             null,    3234.00001   },
                    new Double[]{3.33420923423423, null,    3.40234234   }
            }
    );
  }

  @Test
  public void run3() {
    TwoDimTable table = new TwoDimTable(
            "My foo bar table",
            new String[]{"DoubleValue", "S2", "My Terrible Percent Value"},
            new String[]{"%f", "%s", "%f"},
            new String[]{"First row", "R2", "Row #3", "Last row is here:"},
            new String[][]{
                    new String[]{null,             "One",    null        },
                    new String[]{null,             null,     null        },
                    new String[]{null,             "Three",  null        },
                    new String[]{null,             "FooBar", null        }
            },
            new Double[][]{
                    new Double[]{1.123,            null,    3200034.00001},
                    new Double[]{123.34,           null,    1.0          },
                    new Double[]{null,             null,    3234.00001   },
                    new Double[]{3.33420923423423, null,    3.40234234   }
            }
    );
    String ts = table.toString();
    assertTrue(ts.length() > 0);
    Log.info(ts);

    String json = new String(table.writeJSON(new AutoBuffer()).buf());
    assertTrue(json.equals("{\"description\":\"My foo bar table\",\"colNames\":[\"DoubleValue\",\"S2\",\"My Terrible Percent Value\"],\"colFormatStrings\":[\"%f\",\"%s\",\"%f\"],\"rowHeaders\":[\"First row\",\"R2\",\"Row #3\",\"Last row is here:\"],\"strings\":[[null,\"One\",null],[null,null,null],[null,\"Three\",null],[null,\"FooBar\",null]],\"doubles\":[[1.123,null,3200034.00001],[123.34,null,1.0],[null,null,3234.00001],[3.33420923423423,null,3.40234234]]}"));
    Log.info(json);
  }

  @Test
  public void run4() {
    TwoDimTable table = new TwoDimTable(
            "All numbers",
            new String[]{"Num1", "Num2", "Num3"},
            new String[]{"%f", "%f", "%f"},
            new String[]{"R1", "R2", "R3", "R4"},
            new String[4][],
            new Double[][]{
                    new Double[]{1.123,            3.42,    3200034.00001},
                    new Double[]{123.34,           null,    1.0          },
                    new Double[]{null,             null,    3234.00001   },
                    new Double[]{3.33420923423423, 83.32,   3.40234234   }
            }
    );
    String ts = table.toString();
    assertTrue(ts.length() > 0);
    Log.info(ts);

    String json = new String(table.writeJSON(new AutoBuffer()).buf());
    assertTrue(json.equals("{\"description\":\"All numbers\",\"colNames\":[\"Num1\",\"Num2\",\"Num3\"],\"colFormatStrings\":[\"%f\",\"%f\",\"%f\"],\"rowHeaders\":[\"R1\",\"R2\",\"R3\",\"R4\"],\"strings\":[null,null,null,null],\"doubles\":[[1.123,3.42,3200034.00001],[123.34,null,1.0],[null,null,3234.00001],[3.33420923423423,83.32,3.40234234]]}"));
    Log.info(json);
  }

  @Test
  public void run5() {
    TwoDimTable table = new TwoDimTable(
            "All strings",
            new String[]{"S1", "S2", "S3", "S4"},
            new String[]{"%s", "%s", "%s", "%s"},
            new String[]{"R1", "R2", "R3", "R4"},
            new String[][]{
                    new String[]{"a", "b", "c", "d"},
                    new String[]{"a", "b", "c", "d"},
                    new String[]{"a", null, "c", "d"},
                    new String[]{"a", "b", "c", null},
            },
            new Double[4][]
    );
    String ts = table.toString();
    assertTrue(ts.length() > 0);
    Log.info(ts);

    String json = new String(table.writeJSON(new AutoBuffer()).buf());
    assertTrue(json.equals("{\"description\":\"All strings\",\"colNames\":[\"S1\",\"S2\",\"S3\",\"S4\"],\"colFormatStrings\":[\"%s\",\"%s\",\"%s\",\"%s\"],\"rowHeaders\":[\"R1\",\"R2\",\"R3\",\"R4\"],\"strings\":[[\"a\",\"b\",\"c\",\"d\"],[\"a\",\"b\",\"c\",\"d\"],[\"a\",null,\"c\",\"d\"],[\"a\",\"b\",\"c\",null]],\"doubles\":[null,null,null,null]}"));
    Log.info(json);
  }

  @Test
  public void run6() {
    TwoDimTable table = new TwoDimTable(
            "All strings",
            new String[]{"C0", "C1", "C2", "C3"},
            new String[]{"%s", "%s", "%s", "%s"},
            new String[]{"R0", "R1", "R2", "R3"},
            new String[4][4],
            new Double[4][4]
    );
    table.set(3, 3, "a33");
    table.set(0, 1, "a01");
    table.set(1, 2, 1.2);
    table.set(0, 2, "a02");
    table.set(3, 0, "a30");

    String ts = table.toString();
    assertTrue(ts.length() > 0);
    Log.info(ts);

    assertTrue(table.get(3, 0).equals("a30"));
    assertTrue(table.get(1, 2).equals(1.2));
    assertTrue(table.get(1, 3) == null);

    String json = new String(table.writeJSON(new AutoBuffer()).buf());
    assertTrue(json.equals("{\"description\":\"All strings\",\"colNames\":[\"C0\",\"C1\",\"C2\",\"C3\"],\"colFormatStrings\":[\"%s\",\"%s\",\"%s\",\"%s\"],\"rowHeaders\":[\"R0\",\"R1\",\"R2\",\"R3\"],\"strings\":[[null,\"a01\",\"a02\",null],[null,null,null,null],[null,null,null,null],[\"a30\",null,null,\"a33\"]],\"doubles\":[[null,null,null,null],[null,null,1.2,null],[null,null,null,null],[null,null,null,null]]}"));
    Log.info(json);

  }
}
