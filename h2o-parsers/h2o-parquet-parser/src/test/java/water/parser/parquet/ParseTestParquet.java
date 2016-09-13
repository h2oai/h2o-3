package water.parser.parquet;

import com.google.common.io.Files;
import com.google.common.io.Resources;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.GenericRecordBuilder;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.avro.AvroParquetWriter;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

import static org.junit.Assert.*;

import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;
import water.parser.BufferedString;

/**
 * Test suite for Parquet parser.
 */
public class ParseTestParquet extends TestUtil {

  private static double EPSILON = 1e-9;

  @BeforeClass
  static public void setup() { TestUtil.stall_till_cloudsize(5); }

  @Test
  public void testParseSimple() {
    Frame expected = null, actual = null;
    try {
      expected = parse_test_file("smalldata/airlines/AirlinesTrain.csv.zip");
      actual = TestUtil.parse_test_file("smalldata/parser/parquet/airlines-simple.snappy.parquet");

      assertEquals(Arrays.asList(expected._names), Arrays.asList(actual._names));
      assertEquals(Arrays.asList(expected.typesStr()), Arrays.asList(actual.typesStr()));
      assertTrue(isBitIdentical(expected, actual));
    } finally {
      if (expected != null) expected.delete();
      if (actual != null) actual.delete();
    }
  }

  /**
   * Test parsing of Parquet file originally made from Avro records (avro < 1.8, before introduction of logical types)
   */
  @Test
  public void testParseAvroPrimitiveTypes() {
    FrameAssertion assertion = new GenFrameAssertion("avroPrimitiveTypes.parquet", TestUtil.ari(9, 100)) {
      @Override protected File prepareFile() throws IOException { return ParquetFileGenerator.generateAvroPrimitiveTypes(file, nrows(), new Date()); }
      @Override public void check(Frame f) {
        assertArrayEquals("Column names need to match!", ar("myboolean", "myint", "mylong", "myfloat", "mydouble", "mydate", "myuuid", "mystring", "myenum"), f.names());
        assertArrayEquals("Column types need to match!", ar(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_TIME, Vec.T_UUID, Vec.T_STR, Vec.T_CAT), f.types());
        BufferedString bs = new BufferedString();
        for (int row = 0; row < nrows(); row++) {
          assertEquals("Value in column myboolean", 1 - (row % 2), f.vec(0).at8(row));
          assertEquals("Value in column myint", 1 + row, f.vec(1).at8(row));
          assertEquals("Value in column mylong", 2 + row, f.vec(2).at8(row));
          assertEquals("Value in column myfloat", 3.1f + row, f.vec(3).at(row), EPSILON);
          assertEquals("Value in column myfloat", 4.1 + row, f.vec(4).at(row), EPSILON);
          assertEquals("Value in column mystring", "hello world: " + row, f.vec(7).atStr(bs, row).bytesToString());
          assertEquals("Value in column myenum", row % 2 == 0 ? "a" : "b", f.vec(8).factor(f.vec(8).at8(row)));
        }
      }
    };
    assertFrameAssertion(assertion);
  }

}

class ParquetFileGenerator {

  static File generateAvroPrimitiveTypes(String filename, int nrows, Date date) throws IOException {
    File parentDir = Files.createTempDir();
    File f = new File(parentDir, filename);
    Schema schema = new Schema.Parser().parse(Resources.getResource("PrimitiveAvro.avsc").openStream());
    AvroParquetWriter<GenericRecord> writer = new AvroParquetWriter<GenericRecord>(new Path(f.getPath()), schema);
    try {
      DateFormat format = new SimpleDateFormat("yy-MMM-dd:hh.mm.ss.SSS aaa");
      for (int i = 0; i < nrows; i++) {
        GenericData.Record record = new GenericRecordBuilder(schema)
                .set("mynull", null)
                .set("myboolean", i % 2 == 0)
                .set("myint", 1 + i)
                .set("mylong", 2L + i)
                .set("myfloat", 3.1f + i)
                .set("mydouble", 4.1 + i)
                .set("mydate", format.format(new Date(date.getTime() - (i * 1000 * 3600))))
                .set("myuuid", UUID.randomUUID())
                .set("mystring", "hello world: " + i)
                .set("myenum", i % 2 == 0 ? "a" : "b")
                .build();
        writer.write(record);
      }
    } finally {
      writer.close();
    }
    return f;
  }

}