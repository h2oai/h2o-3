package water.parser;

import com.google.common.io.Files;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumWriter;
import org.junit.Ignore;
import water.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

@Ignore
public class AvroFileGenerator {

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
        gr.put("CBytes", ByteBuffer.wrap(StringUtils.toBytes(i)));
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
        gr.put("CUBytes", i == 0 ? null : ByteBuffer.wrap(StringUtils.toBytes(i)));
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
