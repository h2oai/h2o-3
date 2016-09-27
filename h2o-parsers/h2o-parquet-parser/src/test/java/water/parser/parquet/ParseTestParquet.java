package water.parser.parquet;

import com.google.common.io.Files;
import com.google.common.io.Resources;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.GenericRecordBuilder;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.column.ParquetProperties;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.example.data.simple.SimpleGroupFactory;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.example.GroupWriteSupport;
import org.apache.parquet.schema.MessageType;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.apache.parquet.hadoop.metadata.CompressionCodecName.UNCOMPRESSED;
import static org.apache.parquet.schema.MessageTypeParser.parseMessageType;
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

  @Test
  public void testParseMulti() {
    final int nFiles = 10;
    FrameAssertion assertion = new GenFrameAssertion("testParseMulti-$.parquet", TestUtil.ari(9, 100)) {
      @Override protected File prepareFile() throws IOException {
        File dir = Files.createTempDir();
        for (int i = 0; i < nFiles; i++) {
          String fName = file.replace("$", String.valueOf(i));
          File f = ParquetFileGenerator.generateAvroPrimitiveTypes(dir, fName, nrows() / nFiles, new Date());
          File crcF = new File(f.getCanonicalPath().replace(fName, "." + fName + ".crc"));
          if (crcF.exists() && (! crcF.delete())) throw new IllegalStateException("Unable to delete Parquet CRC for file: " + f);
        }
        return dir;
      }
      @Override public void check(Frame f) {
        assertArrayEquals("Column names need to match!", ar("myboolean", "myint", "mylong", "myfloat", "mydouble", "mydate", "myuuid", "mystring", "myenum"), f.names());
        assertArrayEquals("Column types need to match!", ar(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_TIME, Vec.T_UUID, Vec.T_STR, Vec.T_CAT), f.types());
      }
    };
    assertFrameAssertion(assertion);
  }

  /**
   * Test parsing of Parquet file originally made from Avro records (avro < 1.8, before introduction of logical types)
   */
  @Test
  public void testParseAvroPrimitiveTypes() {
    FrameAssertion assertion = new GenFrameAssertion("avroPrimitiveTypes.parquet", TestUtil.ari(9, 100)) {
      @Override protected File prepareFile() throws IOException { return ParquetFileGenerator.generateAvroPrimitiveTypes(Files.createTempDir(), file, nrows(), new Date()); }
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

  @Test
  public void testParseTimestamps() {
    final Date date = new Date();
    FrameAssertion assertion = new GenFrameAssertion("avroPrimitiveTypes.parquet", TestUtil.ari(5, 100)) {
      @Override protected File prepareFile() throws IOException { return ParquetFileGenerator.generateParquetFile(Files.createTempDir(), file, nrows(), date); }
      @Override public void check(Frame f) {
        assertArrayEquals("Column names need to match!", ar("int32_field", "int64_field", "float_field", "double_field", "timestamp_field"), f.names());
        assertArrayEquals("Column types need to match!", ar(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_TIME), f.types());
        for (int row = 0; row < nrows(); row++) {
          assertEquals("Value in column int32_field", 32 + row, f.vec(0).at8(row));
          assertEquals("Value in column timestamp_field", date.getTime() + (row * 117), f.vec(4).at8(row));
        }
      }
    };
    assertFrameAssertion(assertion);
  }

}

class ParquetFileGenerator {

  static File generateAvroPrimitiveTypes(File parentDir, String filename, int nrows, Date date) throws IOException {
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

  static File generateParquetFile(File parentDir, String filename, int nrows, Date date) throws IOException {
    File f = new File(parentDir, filename);

    Configuration conf = new Configuration();
    MessageType schema = parseMessageType(
            "message test { "
                    + "required int32 int32_field; "
                    + "required int64 int64_field; "
                    + "required float float_field; "
                    + "required double double_field; "
                    + "required int64 timestamp_field (TIMESTAMP_MILLIS);"
                    + "} ");
    GroupWriteSupport.setSchema(schema, conf);
    SimpleGroupFactory fact = new SimpleGroupFactory(schema);
    ParquetWriter<Group> writer = new ParquetWriter<Group>(new Path(f.getPath()), new GroupWriteSupport(),
            UNCOMPRESSED, 1024, 1024, 512, true, false, ParquetProperties.WriterVersion.PARQUET_2_0, conf);
    try {
      for (int i = 0; i < nrows; i++) {
        writer.write(fact.newGroup()
                .append("int32_field", 32 + i)
                .append("int64_field", 64L + i)
                .append("float_field", 1.0f + i)
                .append("double_field", 2.0d + i)
                .append("timestamp_field", date.getTime() + (i * 117))
        );
      }
    } finally {
      writer.close();
    }
    return f;
  }

}