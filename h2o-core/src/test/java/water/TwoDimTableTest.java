package water;

import static org.junit.Assert.assertFalse;
import org.junit.BeforeClass;
import org.junit.Test;
import water.api.schemas3.TwoDimTableV3;
import water.fvec.Frame;
import water.parser.ParseDataset;
import water.parser.ParserTest;
import water.util.Log;
import water.util.TwoDimTable;

import static org.junit.Assert.assertTrue;
import static water.util.TwoDimTable.emptyDouble;

public class TwoDimTableTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  @Test
  public void run0() {
    TwoDimTable table = new TwoDimTable(
            "My foo bar table", null,
            new String[4],
            new String[]{"DoubleValue", "S2", "My Terrible Percent Value"},
            new String[]{"double", "string", "double"},
            new String[]{"%5.8e", "%s", "%5.8g %%"}, "",
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
            });
    String ts = table.toString();
    assertTrue(ts.length() > 0);
    Log.info(ts);

    String json = new TwoDimTableV3().fillFromImpl(table).toJsonString();
    Log.info(json);
  }

  @Test
  public void run1() {
    TwoDimTable table = new TwoDimTable(
            "My foo bar table", "Corner value",
            new String[]{"First row", "R2", "Row #3", "Last row is here:"},
            new String[]{"DoubleValue", "S2", "My Terrible Percent Value"},
            new String[]{"double", "string", "double"},
            new String[]{"%5.8e", "%s", "%5.8g %%"}, "",
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
            });
    String ts = table.toString();
    assertTrue(ts.length() > 0);
    Log.info(ts);

    String json = new TwoDimTableV3().fillFromImpl(table).toJsonString();
    Log.info(json);
  }

  @Test(expected=IllegalArgumentException.class)
  public void run2() {
    TwoDimTable table = new TwoDimTable(
            "My foo bar table", null,
            new String[]{"First row", "R2", "Row #3", "Last row is here:"},
            new String[]{"DoubleValue", "S2", "My Terrible Percent Value"},
            new String[]{"double", "string", "double"},
            new String[]{"%5.8e", "%s", "%5.8g %%"}, "",
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
            });
  }

  @Test
  public void run3() {
    TwoDimTable table = new TwoDimTable(
            "My foo bar table", "desc",
            new String[]{"First row", "R2", "Row #3", "Last row is here:"},
            new String[]{"DoubleValue", "S2", "My Terrible Percent Value"},
            new String[]{"double", "string", "double"},
            new String[]{"%f", "%s", "%f"}, "",
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
                    new double[]{3.33420923423423, emptyDouble,    3.40234234}
            });
    String ts = table.toString();
    assertTrue(ts.length() > 0);
    Log.info(ts);

    String json = new TwoDimTableV3().fillFromImpl(table).toJsonString();
    Log.info(json);
  }

  @Test
  public void run4() {
    TwoDimTable table = new TwoDimTable(
            "All numbers", "yada",
            new String[]{"R1", "R2", "R3", "R4"},
            new String[]{"Num1", "Num2", "Num3"},
            new String[]{"double", "double", "double"},
            new String[]{"%f", "%f", "%f"}, "",
            new String[4][],
            new double[][]{
                    new double[]{1.123,            3.42,          3200034.00001},
                    new double[]{123.34,           emptyDouble,   1.0          },
                    new double[]{emptyDouble,      emptyDouble,   3234.00001   },
                    new double[]{3.33420923423423, 83.32,         3.40234234   }
            });
    String ts = table.toString();
    assertTrue(ts.length() > 0);
    Log.info(ts);

    String json = new TwoDimTableV3().fillFromImpl(table).toJsonString();
    Log.info(json);
  }

  @Test
  public void run5() {
    TwoDimTable table = new TwoDimTable(
            "All strings", null,
            new String[]{"R1", "R2", "R3", "R4"},
            new String[]{"S1", "S2", "S3", "S4"},
            new String[]{"string", "string", "string", "string"},
            new String[]{"%s", "%s", "%s", "%s"}, "",
            new String[][]{
                    new String[]{"a", "b", "c", "d"},
                    new String[]{"a", "b", "c", "d"},
                    new String[]{"a", null, "c", "d"},
                    new String[]{"a", "b", "c", null},
            },
            new double[4][]);
    String ts = table.toString();
    assertTrue(ts.length() > 0);
    Log.info(ts);

    String json = new TwoDimTableV3().fillFromImpl(table).toJsonString();
    Log.info(json);
  }

  @Test
  public void run6() {
    TwoDimTable table = new TwoDimTable(
            "Mixed", "stuff",
            new String[]{"R0", "R1", "R2", "R3"},
            new String[]{"C0", "C1", "C2", "C3"},
            new String[]{"string", "string", "string", "string"},
            new String[]{"%s", "%s", "%s", "%s"},
            "");
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

    String json = new TwoDimTableV3().fillFromImpl(table).toJsonString();
    Log.info(json);

  }

  @Test
  public void run7() {
    TwoDimTable table = new TwoDimTable(
            "Mixed", "description",
            new String[]{"R0", "R1", "R2", "R3"},
            new String[]{"C0", "C1", "C2", "C3"},
            new String[]{"double", "float", "int", "long"},
            new String[]{"%f", "%f", "%d", "%d"},
            "");
    table.set(0, 0, Double.NEGATIVE_INFINITY);
    table.set(1, 0, Double.POSITIVE_INFINITY);
    table.set(2, 0, Double.NaN);
    table.set(3, 0, Math.PI);
    table.set(0, 1, Float.NEGATIVE_INFINITY);
    table.set(1, 1, Float.POSITIVE_INFINITY);
    table.set(2, 1, Float.NaN);
    table.set(3, 1, Float.MIN_VALUE);
    table.set(0, 2, Integer.MAX_VALUE);
    table.set(1, 2, Integer.MIN_VALUE);
    table.set(2, 2, Double.NaN);
    table.set(3, 2, 0);
    table.set(0, 3, Long.MAX_VALUE);
    table.set(1, 3, Long.MIN_VALUE);
    table.set(2, 3, Double.NaN);
    table.set(3, 3, null);

    String ts = table.toString();
    assertTrue(ts.length() > 0);
    Log.info(ts);

    assertTrue(table.get(0, 0).equals(Double.NEGATIVE_INFINITY));
    assertTrue(table.get(1, 0).equals(Double.POSITIVE_INFINITY));
    assertTrue(table.get(2, 0).equals(Double.NaN));
    assertTrue(table.get(3, 0).equals(Math.PI));
    assertTrue(table.get(0, 1).equals(Float.NEGATIVE_INFINITY));
    assertTrue(table.get(1, 1).equals(Float.POSITIVE_INFINITY));
    assertTrue(table.get(2, 1).equals(Float.NaN));
    assertTrue(table.get(3, 1).equals(Float.MIN_VALUE));
    assertTrue(table.get(0, 2).equals(Integer.MAX_VALUE));
    assertTrue(table.get(1, 2).equals(Integer.MIN_VALUE));
    assertTrue(table.get(2, 2).equals(Double.NaN));
    assertTrue(table.get(3, 2).equals(0));
    assertTrue(table.get(0, 3).equals(Long.MAX_VALUE));
    assertTrue(table.get(1, 3).equals(Long.MIN_VALUE));
    assertTrue(table.get(2, 3).equals(Double.NaN));
    assertTrue(table.get(3, 3) == null);

    String json = new TwoDimTableV3().fillFromImpl(table).toJsonString();
    Log.info(json);

  }

  @Test public void run8() {
    TwoDimTable table = new TwoDimTable(
            "Mixed", "description",
            new String[1000],
            new String[]{"C0", "C1", "C2", "C3"},
            new String[]{"double", "float", "int", "long"},
            new String[]{"%f", "%f", "%d", "%d"},
            "");
    for (int i=0; i<1000; ++i) {
      table.set(i, 0, Double.NEGATIVE_INFINITY);
      table.set(i, 1, Double.POSITIVE_INFINITY);
      table.set(i, 2, i);
      table.set(i, 3, -234234);
    }

    String ts = table.toString(1,false);
    assertTrue(ts.length() > 0);
    Log.info(ts);

    String json = new TwoDimTableV3().fillFromImpl(table).toJsonString();
    Log.info(json);

  }

  @Test public void run9() {
    Frame fr = null;
    try {
      int OFFSET = 5;
      int firstVal = 1;
      String data = "1\nNA\n";
      Key k1 = ParserTest.makeByteVec(data);
      Key r1 = Key.make();
      fr = ParseDataset.parse(r1, k1);
      assertTrue(fr.numRows() == 2);
      assertTrue(fr.hasNAs());
      System.out.println(fr);
      TwoDimTable table = fr.toTwoDimTable(0,2);
      assertTrue(table.getColTypes()[0]=="long");
      assertTrue((long) table.get(0 + OFFSET,0) == firstVal);
      try {
        long invalid = (long) table.get(1 + OFFSET,0); // NaN can't be cast to a long
        assertFalse(true);
      } catch(ClassCastException ex) {}
      assertTrue(Double.isNaN((double) table.get(1 + OFFSET, 0)));
    } finally {
      if (fr != null) fr.delete();
    }
  }

  @Test public void run10() {
    Frame fr = null;
    try {
      int OFFSET = 5;
      String data = "1\n3\n4\nNA\n";
      Key k1 = ParserTest.makeByteVec(data);
      Key r1 = Key.make();
      fr = ParseDataset.parse(r1, k1);
      assertTrue(fr.numRows() == 4);
      System.out.println(fr);
      TwoDimTable table = fr.toTwoDimTable(0,4);
      assertTrue(table.getColTypes()[0]=="long");
      assertTrue((long)table.get(0,0) == 1); //min
      assertTrue((double)table.get(1,0) > 2.66); //mean
      assertTrue((double)table.get(1,0) < 2.67); //mean
      assertTrue((double)table.get(2,0) > 1.52); //sd
      assertTrue((double)table.get(2,0) < 1.53); //sd
      assertTrue((long)table.get(3,0) == 4); //max
      assertTrue((long)table.get(4,0) == 1); //missing
      assertTrue((long)table.get(0 + OFFSET,0) == 1);
      assertTrue((long)table.get(1 + OFFSET,0) == 3);
      assertTrue((long)table.get(2 + OFFSET,0) == 4);
      assertTrue(Double.isNaN((double)table.get(3 + OFFSET,0)));
    } finally {
      if (fr != null) fr.delete();
    }
  }
}
