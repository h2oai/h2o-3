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
import org.apache.parquet.io.api.Binary;
import org.apache.parquet.schema.MessageType;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import water.*;
import water.fvec.Frame;
import water.fvec.NFSFileVec;
import water.fvec.Vec;
import water.parser.BufferedString;
import water.parser.ParseDataset;
import water.parser.ParseSetup;
import water.util.IcedInt;
import water.util.PrettyPrint;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

import static org.apache.parquet.hadoop.metadata.CompressionCodecName.UNCOMPRESSED;
import static org.apache.parquet.schema.MessageTypeParser.parseMessageType;
import static org.junit.Assert.*;

/**
 * Test suite for Parquet parser.
 */
@RunWith(Parameterized.class)
public class ParseTestParquet extends TestUtil {

  private static double EPSILON = 1e-9;

  @BeforeClass
  static public void setup() { stall_till_cloudsize(1); }

  @Parameterized.Parameters
  public static Object[] data() {
    return new Object[] { false, true };
  }

  @Parameterized.Parameter
  public boolean disableParallelParse;

  public ParseSetupTransformer psTransformer;

  @Before
  public void makeParseSetupTransformer() {
    psTransformer = new ParseSetupTransformer() {
      @Override
      public ParseSetup transformSetup(ParseSetup guessedSetup) {
        guessedSetup.disableParallelParse = disableParallelParse;
        return guessedSetup;
      }
    };
  }

  private Frame parse_parquet(String fname) {
    return parse_test_file(fname, psTransformer);
  }

  @Test
  public void testParseSimple() {
    Frame expected = null, actual = null;
    try {
      expected = parse_test_file("smalldata/airlines/AirlinesTrain.csv.zip");
      actual = parse_parquet("smalldata/parser/parquet/airlines-simple.snappy.parquet");

      assertEquals(Arrays.asList(expected._names), Arrays.asList(actual._names));
      assertEquals(Arrays.asList(expected.typesStr()), Arrays.asList(actual.typesStr()));
      assertEquals(expected.numRows(), actual.numRows());
      assertTrue(isBitIdentical(expected, actual));
    } finally {
      if (expected != null) expected.delete();
      if (actual != null) actual.delete();
    }
  }

  @Test
  public void testParseWithTypeOverride() {
    Frame expected = null, actual = null;
    try {
      NFSFileVec nfs = makeNfsFileVec("smalldata/parser/parquet/airlines-simple.snappy.parquet");
      Key[] keys = new Key[]{nfs._key};
      ParseSetup guessedSetup = ParseSetup.guessSetup(keys, false, ParseSetup.GUESS_HEADER);

      // attempt to override a Enum type to String
      byte[] types = guessedSetup.getColumnTypes();
      types[1] = Vec.T_STR;
      guessedSetup.setColumnTypes(types);
      guessedSetup.disableParallelParse = disableParallelParse;

      // parse the file with the modified setup
      ParseDataset pd = ParseDataset.forkParseDataset(Key.<Frame>make(), keys, guessedSetup, true);
      actual = pd._job.get();

      expected = parse_test_file("smalldata/airlines/AirlinesTrain.csv.zip");
      expected.replace(1, expected.vec(1).toStringVec()).remove();

      // type is String instead of Enum
      assertEquals("String", actual.typesStr()[1]);
      assertEquals(Arrays.asList(expected._names), Arrays.asList(actual._names));
      assertEquals(Arrays.asList(expected.typesStr()), Arrays.asList(actual.typesStr()));
      assertTrue(isBitIdentical(expected, actual));

      // no warnings were generated
      assertNull(pd._job.warns());
    } finally {
      if (expected != null) expected.delete();
      if (actual != null) actual.delete();
    }
  }

  @Test
  public void testParseWithInvalidTypeOverride() {
    Frame expected = null, actual = null;
    try {
      NFSFileVec nfs = makeNfsFileVec("smalldata/parser/parquet/airlines-simple.snappy.parquet");
      Key[] keys = new Key[]{nfs._key};
      ParseSetup guessedSetup = ParseSetup.guessSetup(keys, false, ParseSetup.GUESS_HEADER);

      // attempt to override a Numeric type to String
      byte[] types = guessedSetup.getColumnTypes();
      types[9] = Vec.T_STR;
      guessedSetup.setColumnTypes(types);
      guessedSetup.disableParallelParse = disableParallelParse;

      // parse the file with the modified setup
      ParseDataset pd = ParseDataset.forkParseDataset(Key.<Frame>make(), keys, guessedSetup, true);
      actual = pd._job.get();

      // type stayed the same
      assertEquals("Numeric", actual.typesStr()[9]);
      expected = parse_test_file("smalldata/airlines/AirlinesTrain.csv.zip");
      assertEquals(Arrays.asList(expected._names), Arrays.asList(actual._names));
      assertEquals(Arrays.asList(expected.typesStr()), Arrays.asList(actual.typesStr()));
      assertTrue(isBitIdentical(expected, actual));

      // proper warnings were generated
      assertEquals(1, pd._job.warns().length);
      assertTrue(pd._job.warns()[0].endsWith("error = 'Unsupported type override (Numeric -> String). Column Distance will be parsed as Numeric'"));
    } finally {
      if (expected != null) expected.delete();
      if (actual != null) actual.delete();
    }
  }

  @Test
  public void testParseMulti() {
    final int nFiles = 10;
    FrameAssertion assertion = new GenFrameAssertion("testParseMulti-$.parquet", TestUtil.ari(9, 100), psTransformer) {
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
    FrameAssertion assertion = new GenFrameAssertion("avroPrimitiveTypes.parquet", TestUtil.ari(9, 100), psTransformer) {
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
          assertEquals("Value in column mystring", "hello world: " + row, f.vec(7).atStr(bs, row).toSanitizedString());
          assertEquals("Value in column myenum", row % 2 == 0 ? "a" : "b", f.vec(8).factor(f.vec(8).at8(row)));
        }
      }
    };
    assertFrameAssertion(assertion);
  }

  @Test
  public void testParseTimestamps() {
    final Date date = new Date();
    FrameAssertion assertion = new GenFrameAssertion("avroPrimitiveTypes.parquet", TestUtil.ari(5, 100), psTransformer) {
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

  @Test
  public void testParseSingleEmpty() {
    FrameAssertion assertion = new GenFrameAssertion("empty.parquet", TestUtil.ari(5, 0), psTransformer) {
      @Override
      protected File prepareFile() throws IOException {
        return ParquetFileGenerator.generateEmptyWithSchema(Files.createTempDir(), file);
      }

      @Override
      public void check(Frame f) {
        assertArrayEquals("Column names need to match!", ar("int32_field", "int64_field", "float_field", "double_field", "timestamp_field"), f.names());
        assertArrayEquals("Column types need to match!", ar(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_TIME), f.types());
      }
    };
    assertFrameAssertion(assertion);
  }

  @Test
  public void testParseStringOverflow() {
    FrameAssertion assertion = new GenFrameAssertion("large.parquet", TestUtil.ari(1, 1), psTransformer) {
      @Override
      protected File prepareFile() throws IOException {
        return ParquetFileGenerator.generateStringParquet(Files.createTempDir(), file);
      }

      @Override
      public Frame prepare() {
        try {
          File f = super.generatedFile = prepareFile();
          System.out.println("File generated into: " + f.getCanonicalPath());
            return parse_test_file(f.getCanonicalPath(), null, ParseSetup.HAS_HEADER, new byte[]{Vec.T_STR}, psTransformer);
        } catch (IOException e) {
          throw new RuntimeException("Cannot prepare test frame from file: " + file, e);
        }
      }

      @Override
      public void check(Frame f) {
        assertArrayEquals("Column names need to match!", ar("string_field"), f.names());
        assertArrayEquals("Column types need to match!", ar(Vec.T_STR), f.types());
        Assert.assertEquals(1, f.naCount());
        Assert.assertEquals(1, f.numCols());
        Assert.assertEquals(1, f.numRows());
      }
    };

    Key<?> cfgKey = Key.make(WriterDelegate.class.getCanonicalName() + "_maxStringSize");
    try {
      DKV.put(cfgKey, new IcedInt(6));
      assertFrameAssertion(assertion);
    } finally {
      DKV.remove(cfgKey);
    }
  }

  @Test
  public void testParseMultiWithEmpty() {
    final int nFiles = 10;
    FrameAssertion assertion = new GenFrameAssertion("testParseMultiEmpty-$.parquet", TestUtil.ari(5, 90), psTransformer) {
      @Override
      protected File prepareFile() throws IOException {
        File dir = Files.createTempDir();
        for (int i = 0; i < nFiles - 1; i++) {
          final String fName = file.replace("$", String.valueOf(i));
          final File f = ParquetFileGenerator.generateParquetFile(dir, fName, nrows() / (nFiles - 1), new Date());
          final File crcF = new File(f.getCanonicalPath().replace(fName, "." + fName + ".crc"));
          if (crcF.exists() && (!crcF.delete()))
            throw new IllegalStateException("Unable to delete Parquet CRC for file: " + f);
        }
        final String emptyFileName = file.replace("$", String.valueOf(nFiles - 1));
        File emptyFile = ParquetFileGenerator.generateEmptyWithSchema(dir, emptyFileName);
        final File crcEmptyFile = new File(emptyFile.getCanonicalPath().replace(emptyFileName, "." + emptyFileName + ".crc"));
        if (crcEmptyFile.exists() && (!crcEmptyFile.delete()))
          throw new IllegalStateException("Unable to delete Parquet CRC for file: " +emptyFileName);
        return dir;
      }

      @Override
      public void check(Frame f) {
        assertArrayEquals("Column names need to match!", ar("int32_field", "int64_field", "float_field", "double_field", "timestamp_field"), f.names());
        assertArrayEquals("Column types need to match!", ar(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_TIME), f.types());
      }
    };
    assertFrameAssertion(assertion);
  }

  @Test
  public void testParseSparseColumns() {
    FrameAssertion assertion = new GenFrameAssertion("sparseColumns.parquet", TestUtil.ari(4, 100), psTransformer) {
      @Override protected File prepareFile() throws IOException { return ParquetFileGenerator.generateSparseParquetFile(Files.createTempDir(), file, nrows()); }
      @Override public void check(Frame f) {
        assertArrayEquals("Column names need to match!", ar("int32_field", "string_field", "row", "int32_field2"), f.names());
        assertArrayEquals("Column types need to match!", ar(Vec.T_NUM, Vec.T_CAT, Vec.T_NUM, Vec.T_NUM), f.types());
        for (int row = 0; row < nrows(); row++) {
          if (row % 10 == 0) {
            assertEquals("Value in column int32_field", row, f.vec(0).at8(row));
            assertEquals("Value in column string_field", "CAT_" + (row % 10), f.vec(1).factor(f.vec(1).at8(row)));
            assertEquals("Value in column int32_field2", row, f.vec(3).at8(row));
          } else {
            assertTrue(f.vec(0).isNA(row));
            assertTrue(f.vec(1).isNA(row));
            assertTrue(f.vec(3).isNA(row));
          }
          assertEquals("Value in column row", row, f.vec(2).at8(row));
        }
      }
    };
    assertFrameAssertion(assertion);
  }

  @Test
  public void testParseCategoricalsWithZeroCharacters() {
    FrameAssertion assertion = new GenFrameAssertion("nullCharacters.parquet", TestUtil.ari(1, 100), psTransformer) {
      @Override protected File prepareFile() throws IOException { return ParquetFileGenerator.generateParquetFileWithNullCharacters(Files.createTempDir(), file, nrows()); }
      @Override public void check(Frame f) {
        assertArrayEquals("Column names need to match!", ar("cat_field"), f.names());
        assertArrayEquals("Column types need to match!", ar(Vec.T_CAT), f.types());
        for (int row = 0; row < nrows(); row++) {
          String catValue = row == 66 ? "CAT_0_weird\0" : "CAT_" + (row % 10);
          assertEquals("Value in column string_field", catValue, f.vec(0).factor(f.vec(0).at8(row))
          );
        }
      }
    };
    assertFrameAssertion(assertion);
  }

  @Test
  public void testParseDecimals() {
    FrameAssertion assertion = new GenFrameAssertion("decimals.parquet", TestUtil.ari(2, 18), psTransformer) {
      @Override protected File prepareFile() throws IOException { return ParquetFileGenerator.generateParquetFileDecimals(Files.createTempDir(), file, nrows()); }
      @Override public void check(Frame f) {
        assertArrayEquals("Column names need to match!", ar("decimal32", "decimal64"), f.names());
        assertArrayEquals("Column types need to match!", ar(Vec.T_NUM, Vec.T_NUM), f.types());
        for (int row = 0; row < nrows(); row++) {
          double expected32 = (1 + PrettyPrint.pow10(1, row % 9)) / 1e5;
          assertEquals("Value in column decimal32", expected32, f.vec(0).at(row), 0);
          double expected64 = (1 + PrettyPrint.pow10(1, row % 18)) / 1e10;
          assertEquals("Value in column decimal64", expected64, f.vec(1).at(row), 0);
        }
      }
    };
    assertFrameAssertion(assertion);
  }

  @Test
  public void testPubdev5673() {
    Frame actual = null;
    try {
      actual = parse_parquet("smalldata/jira/pubdev-5673.parquet");
      double actualVal = actual.vec(0).at(0);
      assertEquals(98776543211.99876, actualVal, 0);
    } finally {
      if (actual != null) actual.delete();
    }
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

  static File generateStringParquet(File parentDir, String filename) throws IOException {
    File f = new File(parentDir, filename);

    Configuration conf = new Configuration();
    MessageType schema = parseMessageType(
        "message test { "
            + "required BINARY string_field; "
            + "} ");
    GroupWriteSupport.setSchema(schema, conf);
    SimpleGroupFactory fact = new SimpleGroupFactory(schema);
    ParquetWriter<Group> writer = new ParquetWriter<Group>(new Path(f.getPath()), new GroupWriteSupport(),
        UNCOMPRESSED,
        262144, 1024, 512, true, false, ParquetProperties.WriterVersion.PARQUET_2_0, conf);
    try {
      //This test may fail on Java 9, as it will use 1 byte per char and sometimes 2 bytes
      Binary binary = Binary.fromString(fillString(12, 'c'));
      writer.write(fact.newGroup()
            .append("string_field", binary)
        );
    } finally {
      writer.close();
    }
    return f;
  }

  public static String fillString(int count,char c) {
    StringBuilder sb = new StringBuilder( count );
    for( int i=0; i<count; i++ ) {
      sb.append( c );
    }
    return sb.toString();
  }

  static File generateEmptyWithSchema(File parentDir, String filename) throws IOException {
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
        UNCOMPRESSED, 1024, 1024, 512, false, false, ParquetProperties.WriterVersion.PARQUET_2_0, conf);
    writer.close();

    return f;
  }

  static File generateSparseParquetFile(File parentDir, String filename, int nrows) throws IOException {
    File f = new File(parentDir, filename);

    Configuration conf = new Configuration();
    MessageType schema = parseMessageType(
            "message test { optional int32 int32_field; optional binary string_field (UTF8); required int32 row; optional int32 int32_field2; } ");
    GroupWriteSupport.setSchema(schema, conf);
    SimpleGroupFactory fact = new SimpleGroupFactory(schema);
    ParquetWriter<Group> writer = new ParquetWriter<Group>(new Path(f.getPath()), new GroupWriteSupport(),
            UNCOMPRESSED, 1024, 1024, 512, true, false, ParquetProperties.WriterVersion.PARQUET_2_0, conf);
    try {
      for (int i = 0; i < nrows; i++) {
        Group g = fact.newGroup();
        if (i % 10 == 0) { g = g.append("int32_field", i); }
        if (i % 10 == 0) { g = g.append("string_field", "CAT_" + (i % 10)); }
        if (i % 10 == 0) { g = g.append("int32_field2", i); }
        writer.write(g.append("row", i));
      }
    } finally {
      writer.close();
    }
    return f;
  }

  static File generateParquetFileWithNullCharacters(File parentDir, String filename, int nrows) throws IOException {
    File f = new File(parentDir, filename);

    Configuration conf = new Configuration();
    MessageType schema = parseMessageType(
            "message test { optional binary cat_field (UTF8); } ");
    GroupWriteSupport.setSchema(schema, conf);
    SimpleGroupFactory fact = new SimpleGroupFactory(schema);
    ParquetWriter<Group> writer = new ParquetWriter<Group>(new Path(f.getPath()), new GroupWriteSupport(),
            UNCOMPRESSED, 1024, 1024, 512, true, false, ParquetProperties.WriterVersion.PARQUET_2_0, conf);
    try {
      for (int i = 0; i < nrows; i++) {
        Group g = fact.newGroup();
        String value = i == 66 ? "CAT_0_weird\0" : "CAT_" + (i % 10);
        writer.write(g.append("cat_field", value));
      }
    } finally {
      writer.close();
    }
    return f;
  }

  static File generateParquetFileDecimals(File parentDir, String filename, int nrows) throws IOException {
    File f = new File(parentDir, filename);

    Configuration conf = new Configuration();
    MessageType schema = parseMessageType(
            "message test { required int32 decimal32 (DECIMAL(9, 5)); required int64 decimal64 (DECIMAL(18, 10)); } ");
    GroupWriteSupport.setSchema(schema, conf);
    SimpleGroupFactory fact = new SimpleGroupFactory(schema);
    ParquetWriter<Group> writer = new ParquetWriter<Group>(new Path(f.getPath()), new GroupWriteSupport(),
            UNCOMPRESSED, 1024, 1024, 512, true, false, ParquetProperties.WriterVersion.PARQUET_2_0, conf);
    try {
      for (int i = 0; i < nrows; i++) {
        Group g = fact.newGroup();
        int dec32 = 1 + (int) PrettyPrint.pow10(1, i % 9);
        g.append("decimal32", dec32);
        long dec64 = 1 + (long) PrettyPrint.pow10(1, i % 18);
        g.append("decimal64", dec64);
        writer.write(g);
      }
    } finally {
      writer.close();
    }
    return f;
  }

}
