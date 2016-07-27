package water.test.util;

import hex.tree.gbm.GBMModel;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Iced;
import water.TestUtil;
import water.api.SchemaServer;
import water.api.schemas3.FrameV3;
import water.api.schemas3.SchemaV3;
import water.fvec.Frame;
import water.util.PojoUtils;

public class PojoUtilsTest extends TestUtil {
  @BeforeClass()
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void testGetFieldValue() {
    GBMModel.GBMParameters o = new GBMModel.GBMParameters();
    Assert.assertEquals(50, PojoUtils.getFieldValue(o, "_ntrees", PojoUtils.FieldNaming.CONSISTENT));
  }

  static public class TestIcedPojo extends Iced {
    Frame.VecSpecifier column;
    Frame.VecSpecifier[] column_array;
  }

  static public class TestSchemaPojo extends SchemaV3<TestIcedPojo, TestSchemaPojo> {
    FrameV3.ColSpecifierV3 column;
    FrameV3.ColSpecifierV3[] column_array;
  }

  @Test
  public void testVecSpecifierToColSpecifier() {
    SchemaServer.registerAllSchemasIfNecessary();

    TestIcedPojo fromIced = new TestIcedPojo();
    TestSchemaPojo toSchema = new TestSchemaPojo();

    fromIced.column = new Frame.VecSpecifier();
    fromIced.column._column_name = ("see one");

    fromIced.column_array = new Frame.VecSpecifier[2];

    fromIced.column_array[0] = new Frame.VecSpecifier();
    fromIced.column_array[1] = new Frame.VecSpecifier();

    fromIced.column_array[0]._column_name = ("C1");
    fromIced.column_array[1]._column_name = ("C2");

    PojoUtils.copyProperties(toSchema, fromIced, PojoUtils.FieldNaming.CONSISTENT);
    Assert.assertEquals(fromIced.column._column_name.toString(), toSchema.column.column_name);

    for (int i = 0; i < fromIced.column_array.length; i++)
      Assert.assertEquals(fromIced.column_array[i]._column_name.toString(), toSchema.column_array[i].column_name);
  }

  @Test
  public void testColSpecifierToVecSpecifier() {
    SchemaServer.registerAllSchemasIfNecessary();

    TestSchemaPojo fromSchema = new TestSchemaPojo();
    TestIcedPojo toIced = new TestIcedPojo();

    fromSchema.column = new FrameV3.ColSpecifierV3();
    fromSchema.column.column_name = ("see one");

    fromSchema.column_array = new FrameV3.ColSpecifierV3[2];

    fromSchema.column_array[0] = new FrameV3.ColSpecifierV3();
    fromSchema.column_array[1] = new FrameV3.ColSpecifierV3();

    fromSchema.column_array[0].column_name = ("C1");
    fromSchema.column_array[1].column_name = ("C2");

    PojoUtils.copyProperties(toIced, fromSchema, PojoUtils.FieldNaming.CONSISTENT);
    Assert.assertEquals(fromSchema.column.column_name, toIced.column._column_name.toString());

    for (int i = 0; i < fromSchema.column_array.length; i++)
      Assert.assertEquals(fromSchema.column_array[i].column_name, toIced.column_array[i]._column_name.toString());
  }

}
