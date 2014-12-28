package water;

import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;
import water.util.Log;
import water.util.TwoDimTable;
import static water.util.TwoDimTable.emptyDouble;

public class TwoDimTableTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  @Test
  public void run1() {
    TwoDimTable table = new TwoDimTable(
            "My foo bar table",
            new String[]{"First row", "R2", "Row #3", "Last row is here:"},
            new String[]{"DoubleValue", "S2", "My Terrible Percent Value"},
            new String[]{"double", "string", "double"},
            new String[]{"%5.8e", "%s", "%5.8g %%"},
            new String[][]{
                    new String[]{null,             "One",    null        },
                    new String[]{null,             null,     null        },
                    new String[]{null,             "Three",  null        },
                    new String[]{null,             "FooBar", null        }
            },
            new double[][]{
                    new double[]{1.123,            emptyDouble,    3200034.00001},
                    new double[]{123.34,           emptyDouble,    1.0          },
                    new double[]{emptyDouble,      emptyDouble,    3234.00001   },
                    new double[]{3.33420923423423, emptyDouble,    3.40234234   }
            }
    );
    String ts = table.toString();
    assertTrue(ts.length() > 0);
    Log.info(ts);

    String json = new String(table.writeJSON(new AutoBuffer()).buf());
    assertTrue(json.equals("{\"tableHeader\":\"My foo bar table\",\"rowHeaders\":[\"First row\",\"R2\",\"Row #3\",\"Last row is here:\"],\"colHeaders\":[\"DoubleValue\",\"S2\",\"My Terrible Percent Value\"],\"colTypes\":[\"double\",\"string\",\"double\"],\"colFormats\":[\"%5.8e\",\"%s\",\"%5.8g %%\"],\"cellValues\":[[\"0x1.1f7ced916872bp0\",\"One\",\"0x1.86a11000053e3p21\"],[\"0x1.ed5c28f5c28f6p6\",\"\",\"0x1.0p0\"],[\"\",\"Three\",\"0x1.94400014f8b59p11\"],[\"0x1.aac75e4187531p1\",\"FooBar\",\"0x1.b37ff42c0c4d7p1\"]]}"));
    Log.info(json);
  }

  @Test(expected=IllegalArgumentException.class)
  public void run2() {
    TwoDimTable table = new TwoDimTable(
            "My foo bar table",
            new String[]{"First row", "R2", "Row #3", "Last row is here:"},
            new String[]{"DoubleValue", "S2", "My Terrible Percent Value"},
            new String[]{"double", "string", "double"},
            new String[]{"%5.8e", "%s", "%5.8g %%"},
            new String[][]{
                    new String[]{null,             "One",    null        },
                    new String[]{null,             null,     null        },
                    new String[]{null,             "Three",  "extra"     },
                    new String[]{null,             "FooBar", null        }
            },
            new double[][]{
                    new double[]{1.123,            emptyDouble,    3200034.00001},
                    new double[]{123.34,           emptyDouble,    1.0          },
                    new double[]{emptyDouble,      emptyDouble,    3234.00001   },
                    new double[]{3.33420923423423, emptyDouble,    3.40234234   }
            }
    );
  }

  @Test
  public void run3() {
    TwoDimTable table = new TwoDimTable(
            "My foo bar table",
            new String[]{"First row", "R2", "Row #3", "Last row is here:"},
            new String[]{"DoubleValue", "S2", "My Terrible Percent Value"},
            new String[]{"double", "string", "double"},
            new String[]{"%f", "%s", "%f"},
            new String[][]{
                    new String[]{null,             "One",    null        },
                    new String[]{null,             null,     null        },
                    new String[]{null,             "Three",  null        },
                    new String[]{null,             "FooBar", null        }
            },
            new double[][]{
                    new double[]{1.123,            emptyDouble,    3200034.00001},
                    new double[]{123.34,           emptyDouble,    1.0          },
                    new double[]{emptyDouble,      emptyDouble,    3234.00001   },
                    new double[]{3.33420923423423, emptyDouble,    3.40234234   }
            }
    );
    String ts = table.toString();
    assertTrue(ts.length() > 0);
    Log.info(ts);

    String json = new String(table.writeJSON(new AutoBuffer()).buf());
    assertTrue(json.equals("{\"tableHeader\":\"My foo bar table\",\"rowHeaders\":[\"First row\",\"R2\",\"Row #3\",\"Last row is here:\"],\"colHeaders\":[\"DoubleValue\",\"S2\",\"My Terrible Percent Value\"],\"colTypes\":[\"double\",\"string\",\"double\"],\"colFormats\":[\"%f\",\"%s\",\"%f\"],\"cellValues\":[[\"0x1.1f7ced916872bp0\",\"One\",\"0x1.86a11000053e3p21\"],[\"0x1.ed5c28f5c28f6p6\",\"\",\"0x1.0p0\"],[\"\",\"Three\",\"0x1.94400014f8b59p11\"],[\"0x1.aac75e4187531p1\",\"FooBar\",\"0x1.b37ff42c0c4d7p1\"]]}"));
    Log.info(json);
  }

  @Test
  public void run4() {
    TwoDimTable table = new TwoDimTable(
            "All numbers",
            new String[]{"R1", "R2", "R3", "R4"},
            new String[]{"Num1", "Num2", "Num3"},
            new String[]{"double", "double", "double"},
            new String[]{"%f", "%f", "%f"},
            new String[4][],
            new double[][]{
                    new double[]{1.123,            3.42,          3200034.00001},
                    new double[]{123.34,           emptyDouble,   1.0          },
                    new double[]{emptyDouble,      emptyDouble,   3234.00001   },
                    new double[]{3.33420923423423, 83.32,         3.40234234   }
            }
    );
    String ts = table.toString();
    assertTrue(ts.length() > 0);
    Log.info(ts);

    String json = new String(table.writeJSON(new AutoBuffer()).buf());
    assertTrue(json.equals("{\"tableHeader\":\"All numbers\",\"rowHeaders\":[\"R1\",\"R2\",\"R3\",\"R4\"],\"colHeaders\":[\"Num1\",\"Num2\",\"Num3\"],\"colTypes\":[\"double\",\"double\",\"double\"],\"colFormats\":[\"%f\",\"%f\",\"%f\"],\"cellValues\":[[\"0x1.1f7ced916872bp0\",\"0x1.b5c28f5c28f5cp1\",\"0x1.86a11000053e3p21\"],[\"0x1.ed5c28f5c28f6p6\",\"\",\"0x1.0p0\"],[\"\",\"\",\"0x1.94400014f8b59p11\"],[\"0x1.aac75e4187531p1\",\"0x1.4d47ae147ae14p6\",\"0x1.b37ff42c0c4d7p1\"]]}"));
    Log.info(json);
  }

  @Test
  public void run5() {
    TwoDimTable table = new TwoDimTable(
            "All strings",
            new String[]{"R1", "R2", "R3", "R4"},
            new String[]{"S1", "S2", "S3", "S4"},
            new String[]{"string", "string", "string", "string"},
            new String[]{"%s", "%s", "%s", "%s"},
            new String[][]{
                    new String[]{"a", "b", "c", "d"},
                    new String[]{"a", "b", "c", "d"},
                    new String[]{"a", null, "c", "d"},
                    new String[]{"a", "b", "c", null},
            },
            new double[4][]
    );
    String ts = table.toString();
    assertTrue(ts.length() > 0);
    Log.info(ts);

    String json = new String(table.writeJSON(new AutoBuffer()).buf());
    assertTrue(json.equals("{\"tableHeader\":\"All strings\",\"rowHeaders\":[\"R1\",\"R2\",\"R3\",\"R4\"],\"colHeaders\":[\"S1\",\"S2\",\"S3\",\"S4\"],\"colTypes\":[\"string\",\"string\",\"string\",\"string\"],\"colFormats\":[\"%s\",\"%s\",\"%s\",\"%s\"],\"cellValues\":[[\"a\",\"b\",\"c\",\"d\"],[\"a\",\"b\",\"c\",\"d\"],[\"a\",\"\",\"c\",\"d\"],[\"a\",\"b\",\"c\",\"\"]]}"));
    Log.info(json);
  }

  @Test
  public void run6() {
    TwoDimTable table = new TwoDimTable(
            "Mixed",
            new String[]{"R0", "R1", "R2", "R3"},
            new String[]{"C0", "C1", "C2", "C3"},
            new String[]{"string", "string", "string", "string"},
            new String[]{"%s", "%s", "%s", "%s"}
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
    assertTrue(table.get(1, 2).equals("1.2"));
    assertTrue(table.get(1, 3) == null);

    String json = new String(table.writeJSON(new AutoBuffer()).buf());
    assertTrue(json.equals("{\"tableHeader\":\"Mixed\",\"rowHeaders\":[\"R0\",\"R1\",\"R2\",\"R3\"],\"colHeaders\":[\"C0\",\"C1\",\"C2\",\"C3\"],\"colTypes\":[\"string\",\"string\",\"string\",\"string\"],\"colFormats\":[\"%s\",\"%s\",\"%s\",\"%s\"],\"cellValues\":[[\"\",\"a01\",\"a02\",\"\"],[\"\",\"\",\"1.2\",\"\"],[\"\",\"\",\"\",\"\"],[\"a30\",\"\",\"\",\"a33\"]]}"));
    Log.info(json);

  }
}
