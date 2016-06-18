package water.parser.orc;

import org.apache.avro.Schema;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.DataFileStream;
import org.apache.avro.file.SeekableByteArrayInput;
import org.apache.avro.file.SeekableInput;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;
import org.apache.avro.util.Utf8;
import water.Job;
import water.Key;
import water.fvec.Vec;
import water.parser.*;
import water.util.ArrayUtils;
import water.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import static water.parser.orc.OrcUtil.*;

/**
 * AVRO parser for H2O distributed parsing subsystem.
 */
public class OrcParser extends Parser {

  /** Avro header */
  private final byte[] header;

  OrcParser(ParseSetup setup, Key<Job> jobKey) {
    super(setup, jobKey);
    this.header = ((OrcParser.OrcParseSetup) setup).header;
  }

  @Override
  protected final ParseWriter parseChunk(int cidx, ParseReader din, ParseWriter dout) {
    // We will read GenericRecord and load them based on schema
    final DatumReader<GenericRecord> datumReader = new GenericDatumReader<>();
    final H2OSeekableInputAdaptor sbai = new H2OSeekableInputAdaptor(cidx, din);
    DataFileReader<GenericRecord> dataFileReader = null;
    int cnt = 0;
    try {
      // Reconstruct Avro header
      DataFileStream.Header
          fakeHeader = new DataFileReader<>(new SeekableByteArrayInput(this.header), datumReader).getHeader();
      dataFileReader = DataFileReader.openReader(sbai, datumReader, fakeHeader, true);
      Schema schema = dataFileReader.getSchema();
      GenericRecord gr = new GenericData.Record(schema);
      Schema.Field[] flatSchema = flatSchema(schema);
      long sync = dataFileReader.previousSync();
      if (sbai.chunkCnt == 0) { // Find data in first chunk
        while (dataFileReader.hasNext() && dataFileReader.previousSync() == sync) {
          gr = dataFileReader.next(gr);
          // Write values to the output
          // FIXME: what if user change input names, or ignore an input column?
          write2frame(gr, _setup.getColumnNames(), flatSchema, _setup.getColumnTypes(), dout);
          cnt++;
        }
      } // else first chunk does not contain synchronization block, so give up and let another reader to use it
    } catch (Throwable e) {
      e.printStackTrace();
    }

    Log.trace(String.format("Orc: ChunkIdx: %d read %d records, start at %d off, block count: %d, block size: %d", cidx, cnt, din.getChunkDataStart(cidx), dataFileReader.getBlockCount(), dataFileReader.getBlockSize()));

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
    final byte[] header;
    final long blockSize;

    public OrcParseSetup(int ncols,
                         String[] columnNames,
                         byte[] ctypes,
                         String[][] domains,
                         String[][] naStrings,
                         String[][] data,
                         byte[] header,
                         long blockSize) {
      super(OrcParserProvider.ORC_INFO, (byte) '|', true, HAS_HEADER , ncols, columnNames, ctypes, domains, naStrings, data);
      this.header = header;
      this.blockSize = blockSize;
      this.setChunkSize((int) blockSize);
    }

    public OrcParseSetup(ParseSetup ps, byte[] header, long blockSize, String[][] domains) {
      super(ps);
      this.header = header;
      this.blockSize = blockSize;
      this.setDomains(domains);
      this.setChunkSize((int) blockSize);
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
        public ParseSetup process(byte[] header, GenericRecord gr, long blockCount,
                                  long blockSize) {
          return deriveParseSetup(header, gr, blockCount, blockSize);
        }
      });
    } catch (IOException e) {
      throw new RuntimeException("Avro format was not recognized", e);
    }
  }

  static OrcInfo extractOrcInfo(byte[] bits, final ParseSetup requiredSetup) throws IOException {
    return runOnPreview(bits, new OrcPreviewProcessor<OrcInfo>() {
      @Override
      public OrcInfo process(byte[] header, GenericRecord gr, long blockCount,
                              long blockSize) {
        Schema recordSchema = gr.getSchema();
        List<Schema.Field> fields = recordSchema.getFields();
        int supportedFieldCnt = 0 ;
        for (Schema.Field f : fields) if (isSupportedSchema(f.schema())) supportedFieldCnt++;
        assert supportedFieldCnt == requiredSetup.getColumnNames().length : "User-driven changes are not not supported in Avro format";
        String[][] domains = new String[supportedFieldCnt][];
        int i = 0;
        for (Schema.Field f : fields) {
          Schema schema = f.schema();
          if (isSupportedSchema(schema)) {
            byte type = schemaToColumnType(schema);
            if (type == Vec.T_CAT) {
              domains[i] = getDomain(schema);
            }
            i++;
          }
        }
        return new OrcInfo(header, blockCount, blockSize, domains);
      }
    });
  }

  static <T> T runOnPreview(byte[] bits, OrcPreviewProcessor<T> processor) throws IOException {
    DatumReader<GenericRecord> datumReader = new GenericDatumReader<GenericRecord>();
    SeekableByteArrayInput sbai = new SeekableByteArrayInput(bits);
    DataFileReader<GenericRecord> dataFileReader = null;

    try {
      dataFileReader = new DataFileReader<>(sbai, datumReader);
      int headerLen = (int) dataFileReader.previousSync();
      byte[] header = Arrays.copyOf(bits, headerLen);
      if (dataFileReader.hasNext()) {
        GenericRecord gr = dataFileReader.next();
        return processor.process(header, gr, dataFileReader.getBlockCount(), dataFileReader.getBlockSize());
      } else {
        throw new RuntimeException("Empty Avro file - cannot run preview! ");
      }
    } finally {
      try { dataFileReader.close(); } catch (IOException safeToIgnore) {}
    }
  }

  private static ParseSetup deriveParseSetup(byte[] header, GenericRecord gr,
                                             long blockCount, long blockSize) {
    // Expect flat structure
    Schema recordSchema = gr.getSchema();
    List<Schema.Field> fields = recordSchema.getFields();
    int supportedFieldCnt = 0 ;
    for (Schema.Field f : fields) if (isSupportedSchema(f.schema())) supportedFieldCnt++;
    String[] names = new String[supportedFieldCnt];
    byte[] types = new byte[supportedFieldCnt];
    String[][] domains = new String[supportedFieldCnt][];
    String[] dataPreview = new String[supportedFieldCnt];
    int i = 0;
    for (Schema.Field f : fields) {
      Schema schema = f.schema();
      if (isSupportedSchema(schema)) {
        names[i] = f.name();
        types[i] = schemaToColumnType(schema);
        if (types[i] == Vec.T_CAT) {
          domains[i] = getDomain(schema);
        }
        dataPreview[i] = gr.get(f.name()) != null ? gr.get(f.name()).toString() : "null";
        i++;
      } else {
        Log.warn("Skipping field: " + f.name() + " because of unsupported type: " + schema.getType() + " schema: " + schema);
      }
    }

    OrcParseSetup ps = new OrcParseSetup(
        supportedFieldCnt,
        names,
        types,
        domains,
        null,
        new String[][] { dataPreview },
        header,
        blockSize
    );
    return ps;
  }

  /** Helper to represent Avro header
   * and size of 1st block of data.
   */
  static class OrcInfo {

    public OrcInfo(byte[] header, long firstBlockCount, long firstBlockSize, String[][] domains) {
      this.header = header;
      this.firstBlockCount = firstBlockCount;
      this.firstBlockSize = firstBlockSize;
      this.domains = domains;
    }

    byte[] header;
    long firstBlockCount;
    long firstBlockSize;
    String[][] domains;
  }

  private interface OrcPreviewProcessor<R> {
    R process(byte[] header, GenericRecord gr, long blockCount, long blockSize);
  }

}
