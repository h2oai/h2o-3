package water.parser;


import com.google.common.io.Files;

import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumWriter;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import static org.junit.Assert.*;

import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;

/**
 * Test suite for Avro parser.
 */
public class ParseTestAvro extends TestUtil {

  private static double EPSILON = 1e-9;

  @BeforeClass
  static public void setup() { TestUtil.stall_till_cloudsize(5); }

  @Test
  public void testParseSimple() {
    // Tests for basic files which are in smalldata
    FrameAssertion[] assertions = new FrameAssertion[] {
        // sequence100k.avro
        new FrameAssertion("smalldata/parser/avro/sequence100k.avro", TestUtil.ari(1, 100000)) {
          @Override public void check(Frame f) {
            Vec values = f.vec(0);
            for (int i = 0; i < f.numRows(); i++) {
              assertEquals(i, values.at8(i));
            }
          }
        },
        // episodes.avro
        new FrameAssertion("smalldata/parser/avro/episodes.avro", TestUtil.ari(3, 8)) {}
    };

    for (int i = 0; i < assertions.length; ++i) {
      assertFrameAssertion(assertions[i]);
    }
  }

  @Test public void testParsePrimitiveTypes() {
    FrameAssertion[] assertions = new FrameAssertion[]{
        new GenFrameAssertion("supportedPrimTypes.avro", TestUtil.ari(8, 100)) {

          @Override protected File prepareFile() throws IOException { return AvroFileGenerator.generatePrimitiveTypes(file, nrows()); }

          @Override
          public void check(Frame f) {
            assertArrayEquals("Column names need to match!", ar("CString", "CBytes", "CInt", "CLong", "CFloat", "CDouble", "CBoolean", "CNull"), f.names());
            assertArrayEquals("Column types need to match!", ar(Vec.T_STR, Vec.T_STR, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_BAD), f.types());

            int nrows = nrows();
            BufferedString bs = new BufferedString();
            for (int row = 0; row < nrows; row++) {
              assertEquals("Value in column CString", String.valueOf(row), f.vec(0).atStr(bs, row).bytesToString());
              assertEquals("Value in column CBytes", String.valueOf(row), f.vec(1).atStr(bs, row).bytesToString());
              assertEquals("Value in column CInt", row, f.vec(2).at8(row));
              assertEquals("Value in column CLong", row, f.vec(3).at8(row));
              assertEquals("Value in column CFloat", row, f.vec(4).at(row), EPSILON);
              assertEquals("Value in column CDouble", row, f.vec(5).at(row), EPSILON);
              assertEquals("Value in column CBoolean", (row & 1) == 1, (((int) f.vec(5).at(row)) & 1) == 1);
              assertTrue("Value in column CNull", f.vec(7).isNA(row));
            }
          }
        }
    };

    for (int i = 0; i < assertions.length; ++i) {
      assertFrameAssertion(assertions[i]);
    }
  }

  @Test public void testParseUnionTypes() {
    FrameAssertion[] assertions = new FrameAssertion[]{
        new GenFrameAssertion("unionTypes.avro", TestUtil.ari(7, 101)) {

          @Override protected File prepareFile() throws IOException { return AvroFileGenerator.generateUnionTypes(file, nrows()); }

          @Override
          public void check(Frame f) {
            assertArrayEquals("Column names need to match!", ar("CUString", "CUBytes", "CUInt", "CULong", "CUFloat", "CUDouble", "CUBoolean"), f.names());
            assertArrayEquals("Column types need to match!", ar(Vec.T_STR, Vec.T_STR, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM), f.types());
            int nrows = nrows();
            BufferedString bs = new BufferedString();
            // NA in the first row
            for (int col = 0; col < ncols(); col++) {
              assertTrue("NA should be in first row and col " + col, f.vec(col).isNA(0));
            }
            for (int row = 1; row < nrows; row++) {
              assertEquals("Value in column CString", String.valueOf(row), f.vec(0).atStr(bs, row).bytesToString());
              assertEquals("Value in column CBytes", String.valueOf(row), f.vec(1).atStr(bs, row).bytesToString());
              assertEquals("Value in column CInt", row, f.vec(2).at8(row));
              assertEquals("Value in column CLong", row, f.vec(3).at8(row));
              assertEquals("Value in column CFloat", row, f.vec(4).at(row), EPSILON);
              assertEquals("Value in column CDouble", row, f.vec(5).at(row), EPSILON);
              assertEquals("Value in column CBoolean", (row & 1) == 1, (((int) f.vec(5).at(row)) & 1) == 1);
            }
          }
        }
    };

    for (int i = 0; i < assertions.length; ++i) {
      assertFrameAssertion(assertions[i]);
    }
  }

  @Test public void testParseEnumTypes() {
    FrameAssertion[] assertions = new FrameAssertion[]{
        new GenFrameAssertion("enumTypes.avro", TestUtil.ari(2, 100)) {
          String[][] categories = AvroFileGenerator.generateSymbols(ar("CAT_A_", "CAT_B_"), ari(7, 13)); // Generated categories
          @Override protected File prepareFile() throws IOException {
            return AvroFileGenerator.generateEnumTypes(file, nrows(), categories);
          }

          @Override
          public void check(Frame f) {
            assertArrayEquals("Column names need to match!", ar("CEnum", "CUEnum"), f.names());
            assertArrayEquals("Column types need to match!", ar(Vec.T_CAT, Vec.T_CAT), f.types());
            assertArrayEquals("Category names need to match in CEnum!", categories[0], f.vec("CEnum").domain());
            assertArrayEquals("Category names need to match in CUEnum!", categories[1], f.vec("CUEnum").domain());

            int numOfCategories1 = categories[0].length;
            int numOfCategories2 = categories[1].length;
            int nrows = nrows();
            for (int row = 0; row < nrows; row++) {
              assertEquals("Value in column CEnum", row % numOfCategories1, (int) f.vec("CEnum").at(row));
              if (row % (numOfCategories2+1) == 0) assertTrue("NA should be in row " + row + " and col CUEnum", f.vec("CUEnum").isNA(row));
              else assertEquals("Value in column CUEnum", row % numOfCategories2, (int) f.vec("CUEnum").at(row));
            }
          }
        }
    };

    for (int i = 0; i < assertions.length; ++i) {
      assertFrameAssertion(assertions[i]);
    }
  }

}

/* A test file generator.
  Use it offline, upload file into smalldata S3 bucket.
*/
class AvroFileGenerator {

  public static void main(String[] args) throws IOException {
    generatePrimitiveTypes("/tmp/h2o-avro-tests/primitiveTypes.avro", 100);
  }

  public static File generatePrimitiveTypes(String filename, int nrows) throws IOException {
    File parentDir = Files.createTempDir();
    File f  = new File(parentDir, filename);
    // Write output records
    DatumWriter<GenericRecord> w = new GenericDatumWriter<GenericRecord>();
    DataFileWriter<GenericRecord> dw = new DataFileWriter<GenericRecord>(w);
    Schema
        schema = SchemaBuilder.builder()
          .record("test_primitive_types").fields()
            .name("CString").type("string").noDefault()
            .name("CBytes").type("bytes").noDefault()
            .name("CInt").type("int").noDefault()
            .name("CLong").type("long").noDefault()
            .name("CFloat").type("float").noDefault()
            .name("CDouble").type("double").noDefault()
            .name("CBoolean").type("boolean").noDefault()
            .name("CNull").type("null").noDefault()
          .endRecord();
    try {
      dw.create(schema, f);
      for (int i = 0; i < nrows; i++) {
        GenericRecord gr = new GenericData.Record(schema);
        gr.put("CString", String.valueOf(i));
        gr.put("CBytes", ByteBuffer.wrap(String.valueOf(i).getBytes()));
        gr.put("CInt", i);
        gr.put("CLong", Long.valueOf(i));
        gr.put("CFloat", Float.valueOf(i));
        gr.put("CDouble", Double.valueOf(i));
        gr.put("CBoolean", (i & 1) == 1);
        gr.put("CNull", null);
        dw.append(gr);
      }
      return f;
    } finally {
      dw.close();
    }
  }

  public static File generateUnionTypes(String filename, int nrows) throws IOException {
    File parentDir = Files.createTempDir();
    File f  = new File(parentDir, filename);
    DatumWriter<GenericRecord> w = new GenericDatumWriter<GenericRecord>();
    DataFileWriter<GenericRecord> dw = new DataFileWriter<GenericRecord>(w);

    // Based on SchemaBuilder javadoc:
    // * The below two field declarations are equivalent:
    // * <pre>
    // *  .name("f").type().unionOf().nullType().and().longType().endUnion().nullDefault()
    // *  .name("f").type().optional().longType()
    // * </pre>
    Schema
        schema = SchemaBuilder.builder()
        .record("test_union_types").fields()
          .name("CUString").type().optional().stringType()
          .name("CUBytes").type().optional().bytesType()
          .name("CUInt").type().optional().intType()
          .name("CULong").type().optional().longType()
          .name("CUFloat").type().optional().floatType()
          .name("CUDouble").type().optional().doubleType()
          .name("CUBoolean").type().optional().booleanType()
        .endRecord();
    try {
      dw.create(schema, f);
      for (int i = 0; i < nrows; i++) {
        GenericRecord gr = new GenericData.Record(schema);
        gr.put("CUString", i == 0 ? null : String.valueOf(i));
        gr.put("CUBytes", i == 0 ? null : ByteBuffer.wrap(String.valueOf(i).getBytes()));
        gr.put("CUInt", i == 0 ? null : i);
        gr.put("CULong", i == 0 ? null : Long.valueOf(i));
        gr.put("CUFloat", i == 0 ? null : Float.valueOf(i));
        gr.put("CUDouble", i == 0 ? null : Double.valueOf(i));
        gr.put("CUBoolean", i == 0 ? null : (i & 1) == 1);
        dw.append(gr);
      }
      return f;
    } finally {
      dw.close();;
    }
  }

  public static File generateEnumTypes(String filename, int nrows, String[][] categories) throws IOException {
    assert categories.length == 2 : "Needs only 2 columns";
    File parentDir = Files.createTempDir();
    File f  = new File(parentDir, filename);
    DatumWriter<GenericRecord> w = new GenericDatumWriter<GenericRecord>();
    DataFileWriter<GenericRecord> dw = new DataFileWriter<GenericRecord>(w);

    Schema enumSchema1 = SchemaBuilder.enumeration("CEnum1").symbols(categories[0]);
    Schema enumSchema2 = SchemaBuilder.enumeration("CEnum2").symbols(categories[1]);
    Schema
        schema = SchemaBuilder.builder()
        .record("test_enum_types").fields()
          .name("CEnum").type(enumSchema1).noDefault()
          .name("CUEnum").type().optional().type(enumSchema2)
        .endRecord();

    System.out.println(schema);
    int numOfCategories1 = categories[0].length;
    int numOfCategories2 = categories[1].length;
    try {
      dw.create(schema, f);
      for (int i = 0; i < nrows; i++) {
        GenericRecord gr = new GenericData.Record(schema);
        gr.put("CEnum", new GenericData.EnumSymbol(enumSchema1, categories[0][i % numOfCategories1]));
        gr.put("CUEnum", i % (numOfCategories2+1) == 0 ? null : new GenericData.EnumSymbol(enumSchema2, categories[1][i % numOfCategories2]));
        dw.append(gr);
      }
      return f;
    } finally {
      dw.close();;
    }
  }

  public static String[][] generateSymbols(String[] prefix, int[] num) {
    assert prefix.length == num.length;
    String[][] symbols = new String[prefix.length][];
    for (int i = 0; i < prefix.length; i++) symbols[i] = generateSymbols(prefix[i], num[i]);
    return symbols;
  }

  public static String[] generateSymbols(String prefix, int num) {
    String[] symbols = new String[num];
    for (int i = 0; i < num; i++) symbols[i] = prefix + i;
    return symbols;
  }
}
