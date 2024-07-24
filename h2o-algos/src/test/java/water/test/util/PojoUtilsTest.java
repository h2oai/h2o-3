package water.test.util;

import hex.glm.GLMModel;
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

import java.lang.reflect.Field;

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

  static public class TestNestedFillFromJson extends Iced {
    public int meaning = 42;
    public GBMModel.GBMParameters parameters = null;
    public double double_meaning = 84.0;
  }

  @Test
  public void TestFillFromJson() {
    // Fill only one level, scalar:
    GBMModel.GBMParameters o = new GBMModel.GBMParameters();
    Assert.assertEquals(50, o._ntrees);
    Assert.assertEquals(5, o._max_depth);

    PojoUtils.fillFromJson(o, "{\"_ntrees\": 17}");
    Assert.assertEquals(17, o._ntrees);
    Assert.assertEquals(5, o._max_depth);

    // Fill with a nested object:
    TestNestedFillFromJson nested = new TestNestedFillFromJson();
    nested.parameters = new GBMModel.GBMParameters();
    Assert.assertEquals(50, nested.parameters._ntrees);
    Assert.assertEquals(5, nested.parameters._max_depth);

    PojoUtils.fillFromJson(nested, "{\"double_meaning\": 96, \"parameters\": {\"_ntrees\": 17}}");
    Assert.assertEquals(96, nested.double_meaning, 0.00001);
    Assert.assertEquals(42, nested.meaning);
    Assert.assertEquals(17, nested.parameters._ntrees);
    Assert.assertEquals(5, nested.parameters._max_depth);
  }

  @Test
  public void TestEquals() {
    GLMModel.GLMParameters params1 = new GLMModel.GLMParameters();
    GLMModel.GLMParameters params2 = new GLMModel.GLMParameters();

    Field field1, field2 = null;
    try {
      field1 = params1.getClass().getDeclaredField("_alpha");
      field2 = params2.getClass().getDeclaredField("_alpha");
    } catch (NoSuchFieldException e) {
      throw new IllegalArgumentException("Field _alpha not found!", e);
    }

    // check null
    assert PojoUtils.equals(params1,  field1, params2,  field2);

    // check double[] array:
    params1._alpha = new double[] {1.2, 1.3, 1.4};
    params2._alpha = new double[] {1.2, 1.3, 1.4};
    assert PojoUtils.equals(params1,  field1, params2,  field2);

    params1._alpha = new double[] {1.2, 1.3, 1.4};
    params2._alpha = new double[] {1.2, 1.5, 1.4};
    assert PojoUtils.equals(params1,  field1, params2,  field2) == false;
    
    field1 = params1.getClass().getFields()[3];
    field2 = params2.getClass().getFields()[3];

    assert PojoUtils.equals(params1,  field1, params2,  field2);
  }
}
