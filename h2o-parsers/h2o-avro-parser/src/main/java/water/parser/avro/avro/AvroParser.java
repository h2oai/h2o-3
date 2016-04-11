package water.parser.avro.avro;

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

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import water.H2O;
import water.Job;
import water.Key;
import water.parser.BufferedString;
import water.parser.ParseReader;
import water.parser.ParseSetup;
import water.parser.ParseWriter;
import water.parser.Parser;
import water.parser.ParserType;
import water.util.ArrayUtils;
import water.util.Log;

import static water.parser.avro.AvroUtil.*;

/**
 * AVRO parser for H2O distributed parsing subsystem.
 */
public class AvroParser extends Parser {

  /** Avro header */
  private final byte[] header;

  AvroParser(ParseSetup setup, Key<Job> jobKey) {
    super(setup, jobKey);
    this.header = ((AvroParser.AvroParseSetup) setup).header;
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
          fakeHeader = new DataFileReader<GenericRecord>(new SeekableByteArrayInput(this.header), datumReader).getHeader();
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

    System.out.println(String.format("Cidx: %d read %d records, start at %d off, block count: %d, block size: %d", cidx, cnt, din.getChunkDataStart(cidx), dataFileReader.getBlockCount(), dataFileReader.getBlockSize()));

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
      //System.err.println("Mark: " + mark);
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
        System.err.println(String.format("StartCIdx: %d, chunkCnt: %d",  startCidx, chunkCnt));
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
          case DOUBLE:
            dout.addNumCol(cIdx, (Double) value);
            break;
          case ENUM:
            throw H2O.unimpl();
            //break;
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

  public static class AvroParseSetup extends ParseSetup {
    public byte[] header; //FIXME no public
    long blockSize;

    public AvroParseSetup(int ncols,
                          String[] columnNames, byte[] ctypes,
                          String[][] domains,
                          String[][] naStrings,
                          String[][] data) {
      super(ParserType.OTHER, (byte) '|', true, HAS_HEADER , ncols, columnNames, ctypes, domains, naStrings, data);
    }

    public AvroParseSetup(ParseSetup ps, byte[] header) {
      super(ps);
      this.header = header;
    }

    @Override
    protected Parser parser(Key jobKey) {
      return new AvroParser(this, jobKey);
    }
  }

  public static ParseSetup guessSetup(byte[] bits) {
    DatumReader<GenericRecord> datumReader = new GenericDatumReader<GenericRecord>();
    SeekableByteArrayInput sbai = new SeekableByteArrayInput(bits);
    DataFileReader<GenericRecord> dataFileReader = null;

    try {
      dataFileReader = new DataFileReader<>(sbai, datumReader);
      // FIXME: first process schema then create preview
      // Extract header bytes
      int headerLen = (int) dataFileReader.previousSync();
      byte[] header = Arrays.copyOf(bits, headerLen);
      // Read an entry to create a preview
      if (dataFileReader.hasNext()) {
        GenericRecord gr = dataFileReader.next();
        return deriveParseSetup(header, gr, dataFileReader.getBlockCount(), dataFileReader.getBlockSize());
      } else {
        throw new RuntimeException("FIXME: Empty file!");
      }
    } catch (IOException e) {
      if (dataFileReader != null)
        try {
          dataFileReader.close();
        } catch (IOException ignored) {
          /* ignore */
        }
    }

    throw new RuntimeException("Cannot derive parser setup!");
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
    String[] dataPreview = new String[supportedFieldCnt];
    int i = 0;
    for (Schema.Field f : fields) {
      Schema schema = f.schema();
      if (isSupportedSchema(schema)) {
        names[i] = f.name();
        types[i] = schemaToColumnType(schema);
        dataPreview[i] = gr.get(f.name()) != null ? gr.get(f.name()).toString() : "null";
        i++;
      } else {
        Log.warn("Skipping field: " + f.name() + " because of unsupported type: " + schema.getType() + " schema: " + schema);
      }
    }

    AvroParseSetup ps = new AvroParseSetup(
        supportedFieldCnt,
        names,
        types,
        null,
        null,
        new String[][] { dataPreview }
    );
    ps.header = header;
    ps.blockSize = blockSize;
    ps.setChunkSize((int) blockSize);
    return ps;
  }

}
