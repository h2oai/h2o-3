package water.parser.orc;

// Avro support
import org.apache.avro.Schema;
import org.apache.avro.file.SeekableInput;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.util.Utf8;
import org.apache.commons.io.FileUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.ql.io.orc.OrcFile;
import org.apache.hadoop.hive.ql.io.orc.Reader;
import org.apache.hadoop.hive.ql.io.orc.StripeInformation;
import org.apache.hadoop.hive.serde2.objectinspector.StructField;
import org.apache.hadoop.hive.serde2.objectinspector.StructObjectInspector;
import water.Job;
import water.Key;
import water.parser.*;
import water.util.ArrayUtils;
import water.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import static water.parser.orc.OrcUtil.*;

// Orc support

/**
 * ORC parser for H2O distributed parsing subsystem.
 *
 * Basically, here is the plan:
 * To parse an Orc file, we need to do the following in order to get the followin useful
 * information:
 * 1. Get a Reader rdr.
 * 2. From the reader rdr, we can get the following pieces of information:
 *  a. number of columns, column types and column names.  We only support parsing of primitive types;
 *  b. Lists of StripeInformation that describes how many stripes of data that we will need to read;
 *  c. For each stripe, we will be able to get information like how many rows per stripe, data size
 *    in bytes, offset bytes,
 * 3.  The plan is to read the file in parallel in whole numbers of stripes.
 * 4.  Inside each stripe, we will read data out in batches of VectorizedRowBatch (1024 rows or less).
 */
public class OrcParser extends Parser {
  /** Orc Info */
  private final Reader orcFileReader;              // if I can have this, I can generate all the other fields

  OrcParser(ParseSetup setup, Key<Job> jobKey) {
    super(setup, jobKey);
    this.orcFileReader = ((OrcParser.OrcParseSetup) setup).orcFileReader;

  }

  @Override
  protected final ParseWriter parseChunk(int cidx, ParseReader din, ParseWriter dout) {
//    // We will read GenericRecord and load them based on schema
//    final DatumReader<GenericRecord> datumReader = new GenericDatumReader<>();
//    final H2OSeekableInputAdaptor sbai = new H2OSeekableInputAdaptor(cidx, din);
//    DataFileReader<GenericRecord> dataFileReader = null;
//    int cnt = 0;
//    try {
//      // Reconstruct Avro header
//      DataFileStream.Header
//          fakeHeader = new DataFileReader<>(new SeekableByteArrayInput(this.header), datumReader).getHeader();
//      dataFileReader = DataFileReader.openReader(sbai, datumReader, fakeHeader, true);
//      Schema schema = dataFileReader.getSchema();
//      GenericRecord gr = new GenericData.Record(schema);
//      Schema.Field[] flatSchema = flatSchema(schema);
//      long sync = dataFileReader.previousSync();
//      if (sbai.chunkCnt == 0) { // Find data in first chunk
//        while (dataFileReader.hasNext() && dataFileReader.previousSync() == sync) {
//          gr = dataFileReader.next(gr);
//          // Write values to the output
//          // FIXME: what if user change input names, or ignore an input column?
//          write2frame(gr, _setup.getColumnNames(), flatSchema, _setup.getColumnTypes(), dout);
//          cnt++;
//        }
//      } // else first chunk does not contain synchronization block, so give up and let another reader to use it
//    } catch (Throwable e) {
//      e.printStackTrace();
//    }
//
//    Log.trace(String.format("Orc: ChunkIdx: %d read %d records, start at %d off, block count: %d, block size: %d", cidx, cnt, din.getChunkDataStart(cidx), dataFileReader.getBlockCount(), dataFileReader.getBlockSize()));
//
    return dout;
  }


  /** A simple adaptor for Avro Seekable Input.
   *
   * It implements lazy loading of chunks from ParseReader and track how many chunks
   * were loaded.
   *
   * Warning: This is not designed to be accessed by multiple threads!
   */
  private static class H2OSeekableInputAdaptor implements SeekableInput {

    private final ParseReader din;
    private final int startCidx;

    protected int pos;
    protected int mark;

    private byte[] data;
    // Additional chunks loaded
    protected int chunkCnt;

    public H2OSeekableInputAdaptor(int cidx, ParseReader din) {
      this.din = din;
      this.startCidx = cidx;
      this.data = din.getChunkData(cidx);
      this.chunkCnt = 0;

      this.mark = din.getChunkDataStart(cidx) > 0 ? din.getChunkDataStart(cidx) : 0;
      this.pos = mark;
    }

    @Override
    public void seek(long p) throws IOException {
      this.reset();
      this.skip(p);
    }

    @Override
    public long tell() throws IOException {
      return this.pos;
    }

    @Override
    public long length() throws IOException {
      return -1;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      if (b == null) {
        throw new NullPointerException();
      } else if (off < 0 || len < 0 || len > b.length - off) {
        throw new IndexOutOfBoundsException();
      }

      needData(len);

      if (pos >= count()) {
        return -1;
      }

      int avail = count() - pos;
      if (len > avail) {
        len = avail;
      }
      if (len <= 0) {
        return 0;
      }

      // FIXME drop read data
      System.arraycopy(data, pos, b, off, len);
      pos += len;
      return len;
    }

    @Override
    public void close() throws IOException {
      data = null;
    }

    public void reset() {
      pos = 0;
    }

    public long skip(long n) {
      long remain = 0;
      while ((remain = count() - pos) < n && loadNextData()) ;
      if (n < remain) {
        remain = n < 0 ? 0 : n;
      }
      pos += remain;
      return remain;
    }

    private int count() {
      return data.length;
    }

    private boolean needData(int len) {
      boolean loaded = false;
      while ((count() - pos) < len && (loaded = loadNextData())) ;
      return loaded;
    }

    private boolean loadNextData() {
      // FIXME: just replace data
      byte[] nextChunk = this.din.getChunkData(this.startCidx + chunkCnt + 1);
      if (nextChunk != null && nextChunk.length > 0) {
        this.data = ArrayUtils.append(this.data, nextChunk);
        this.chunkCnt++;
        Log.trace(String.format("Avro stream wrapper - loading another chunk: StartChunkIdx: %d, LoadedChunkCnt: %d",  startCidx, chunkCnt));
        return true;
      } else {
        return false;
      }
    }
  }

  /**
   * The main method transforming Avro record into a row in H2O frame.
   *
   * @param gr  Avro generic record
   * @param columnNames Column names prepared by parser setup
   * @param inSchema  Flattenized Avro schema which corresponds to passed column names
   * @param columnTypes  Target H2O types
   * @param dout  Parser writer
   */
  private static void write2frame(GenericRecord gr, String[] columnNames, Schema.Field[] inSchema, byte[] columnTypes, ParseWriter dout) {
    assert inSchema.length == columnTypes.length : "AVRO field flatenized schema has to match to parser setup";
    BufferedString bs = new BufferedString();
    for (int cIdx = 0; cIdx < columnNames.length; cIdx++) {
      int inputFieldIdx = inSchema[cIdx].pos();
      Schema.Type inputType = toPrimitiveType(inSchema[cIdx].schema());
      byte targetType = columnTypes[cIdx]; // FIXME: support target conversions
      Object value = gr.get(inputFieldIdx);
      if (value == null) {
        dout.addInvalidCol(cIdx);
      } else {
        switch (inputType) {
          case BOOLEAN:
            dout.addNumCol(cIdx, ((Boolean) value) ? 1 : 0);
            break;
          case INT:
            dout.addNumCol(cIdx, ((Integer) value), 0);
            break;
          case LONG:
            dout.addNumCol(cIdx, ((Long) value), 0);
            break;
          case FLOAT:
            dout.addNumCol(cIdx, (Float) value);
            break;
          case DOUBLE:
            dout.addNumCol(cIdx, (Double) value);
            break;
          case ENUM:
            // Note: this code expects ordering of categoricals provided by Avro remain same
            // as in H2O!!!
            GenericData.EnumSymbol es = (GenericData.EnumSymbol) value;
            dout.addNumCol(cIdx, es.getSchema().getEnumOrdinal(es.toString()));
            break;
          case BYTES:
            dout.addStrCol(cIdx, bs.set(((ByteBuffer) value).array()));
            break;
          case STRING:
            dout.addStrCol(cIdx, bs.set(((Utf8) value).getBytes()));
            break;
          case NULL:
            dout.addInvalidCol(cIdx);
            break;
        }
      }
    }
  }

  public static class OrcParseSetup extends ParseSetup {
    // expand to include Orc specific fields
    final Reader orcFileReader;
    final long maxStripeBlockSize;   // stripe size, max of all stripe sizes
    final long minStripeBlockSize;   // if all stripes are the same size, equal to maxStripeBlockSize

    public OrcParseSetup(int ncols,
                         String[] columnNames,
                         byte[] ctypes,
                         String[][] domains,
                         String[][] naStrings,
                         String[][] data,
                         Reader orcReader,
                         long maxStripeSize,
                         long minStripeSize) {
      super(OrcParserProvider.ORC_INFO, (byte) '|', true, HAS_HEADER ,
              ncols, columnNames, ctypes, domains, naStrings, data);
      this.orcFileReader = orcReader;
      this.maxStripeBlockSize = maxStripeSize;
      this.minStripeBlockSize = minStripeSize;
      this.setChunkSize((int) maxStripeSize);
    }

    public OrcParseSetup(ParseSetup ps, Reader reader, long maxStripeSize, long minStripeSize) {
      super(ps);
      this.orcFileReader = reader;
      this.maxStripeBlockSize = maxStripeSize;
      this.minStripeBlockSize = minStripeSize;
      this.setChunkSize((int) maxStripeSize);
    }

    @Override
    protected Parser parser(Key jobKey) {
      return new OrcParser(this, jobKey);
    }
  }

  public static ParseSetup guessSetup(byte[] bits) {
    try {
      return runOnPreview(bits, new OrcPreviewProcessor<ParseSetup>() {
        @Override
        public ParseSetup process(Reader orcFileReader, StructObjectInspector inspector) {
          return deriveParseSetup(orcFileReader, inspector);
        }
      });
    } catch (IOException e) {
      throw new RuntimeException("Orc format was not recognized", e);
    }

  }

  /** Just like derivedOrcInfo
   */
  static OrcInfo extractOrcInfo(byte[] bits, final ParseSetup requiredSetup) throws IOException {
    return runOnPreview(bits, new OrcPreviewProcessor<OrcInfo>() {
      @Override
      public OrcInfo process(Reader orcReader, StructObjectInspector inspector) {
        OrcParseSetup ps = (OrcParseSetup) deriveParseSetup(orcReader, inspector);

        return new OrcInfo(ps.orcFileReader, ps.maxStripeBlockSize, ps.minStripeBlockSize);
      }
    });
  }

  /**
   * This method basically grab the reader, the inspector of an orc file.  However, it will
   * return null if an exception was found.
   * @param bits
   * @param processor
   * @param <T>
   * @return
   * @throws IOException
     */
  static <T> T runOnPreview(byte[] bits, OrcPreviewProcessor<T> processor) throws IOException {
    try {
      String tempFile = "tempFile";
      Configuration conf = new Configuration();
      FileUtils.writeByteArrayToFile(new File(tempFile), bits);

      Path p = new Path(tempFile);
      Reader orcFileReader = OrcFile.createReader(p, OrcFile.readerOptions(conf));;     // orc reader
      StructObjectInspector insp = (StructObjectInspector) orcFileReader.getObjectInspector();;

      return processor.process(orcFileReader, insp);
    } catch (IOException safeToIgnore) {
      return null;
    }
  }

  /*
   * This function will derive information like column names, types and number from
   * the inspector.
   */
  private static ParseSetup deriveParseSetup(Reader orcFileReader, StructObjectInspector insp) {

    List<StructField> allColumns = (List<StructField>) insp.getAllStructFieldRefs();  // grab column info
    List<StripeInformation> allStripes = orcFileReader.getStripes();  // grab stripe information

    int supportedFieldCnt = 0 ;
    for (StructField oneField:allColumns) {
      if (isSupportedSchema(oneField.getFieldObjectInspector().getTypeName())) supportedFieldCnt++;
    }

    String[] names = new String[supportedFieldCnt];
    byte[] types = new byte[supportedFieldCnt];
    String[][] domains = new String[supportedFieldCnt][];
    String[] dataPreview = new String[supportedFieldCnt];

    // go through all column information
    int columnIndex = 0;

    for (StructField oneField : allColumns) {
      String columnType = oneField.getFieldObjectInspector().getTypeName();
      if (isSupportedSchema(columnType)) {
        names[columnIndex] = oneField.getFieldName();
        types[columnIndex] = schemaToColumnType(columnType);
//          if (types[columnIndex] == Vec.T_CAT) {  // Orc does not support ENUM/CATEGORICAL
//            domains[columnIndex] = getDomain(schema);
//          }

        columnIndex++;
      } else {
        Log.warn("Skipping field: " + oneField.getFieldName() + " because of unsupported type: " + columnType);
      }
    }

    // go through all stripe and get stripe size information
    long minStripeSize = (long) Double.POSITIVE_INFINITY;
    long maxStripeSize = (long) Double.POSITIVE_INFINITY * (-1);
    for (StripeInformation oneStripe : allStripes) {
      long stripeSize = oneStripe.getDataLength();

      if (stripeSize < minStripeSize)
        minStripeSize = stripeSize;
      else if (stripeSize > maxStripeSize)
        maxStripeSize = stripeSize;
    }

    OrcParseSetup ps = new OrcParseSetup(
            supportedFieldCnt,
            names,
            types,
            domains,
            null,
            new String[][] { dataPreview },
            orcFileReader,
            maxStripeSize,
            minStripeSize
    );
    return ps;
  }

  /** Helper to represent Orc Info
   */
  static class OrcInfo {

    public OrcInfo(Reader orcReader, long maxStripeBlockSize, long minStripeBlockSize) {
      this.orcFileReader = orcReader;
      this.maxStripeBlockSize = maxStripeBlockSize;
      this.minStripeBlockSize = minStripeBlockSize;

    }

    Reader orcFileReader;   // can derive all other fields from here
    long maxStripeBlockSize;   // stripe size, max of all stripe sizes
    long minStripeBlockSize;   // if all stripes are the same size, equal to maxStripeBlockSize
  }

  private interface OrcPreviewProcessor<R> {
    R process(Reader orcFileReader, StructObjectInspector inspector);
  }

}
