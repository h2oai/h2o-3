package water.parser.parquet;

import org.apache.parquet.format.converter.ParquetMetadataConverter;
import org.apache.parquet.hadoop.metadata.BlockMetaData;
import org.apache.parquet.hadoop.metadata.ParquetMetadata;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.OriginalType;
import org.apache.parquet.schema.Type;
import water.Job;
import water.Key;
import water.exceptions.H2OUnsupportedDataFileException;
import water.fvec.ByteVec;
import water.fvec.Chunk;
import water.fvec.Vec;
import water.parser.*;
import water.util.IcedHashMapGeneric;
import water.util.Log;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.apache.parquet.hadoop.ParquetFileWriter.MAGIC;

/**
 * Parquet parser for H2O distributed parsing subsystem.
 */
public class ParquetParser extends Parser {

  private static final int MAX_PREVIEW_RECORDS = 1000;

  private final byte[] _metadata;

  ParquetParser(ParseSetup setup, Key<Job> jobKey) {
    super(setup, jobKey);
    _metadata = ((ParquetParseSetup) setup).parquetMetadata;
  }

  @Override
  protected final StreamParseWriter sequentialParse(Vec vec, final StreamParseWriter dout) {
    final ParquetMetadata metadata = VecParquetReader.readFooter(_metadata);
    final int nChunks = vec.nChunks();
    final long totalRecs = totalRecords(metadata);
    final long nChunkRecs = ((totalRecs / nChunks) + (totalRecs % nChunks > 0 ? 1 : 0));
    if (nChunkRecs != (int) nChunkRecs) {
      throw new IllegalStateException("Unsupported Parquet file. Too many records (#" + totalRecs + ", nChunks=" + nChunks + ").");
    }

    final WriterDelegate w = new WriterDelegate(dout, _setup.getColumnTypes().length);
    final VecParquetReader reader = new VecParquetReader(vec, metadata, w, _setup.getColumnTypes(), _keepColumns);

    StreamParseWriter nextChunk = dout;
    try {
      long parsedRecs = 0;
      for (int i = 0; i < nChunks; i++) {
        Long recordNumber;
        do {
          recordNumber = reader.read();
          if (recordNumber != null)
            parsedRecs++;
        } while ((recordNumber != null) && (w.lineNum() < nChunkRecs));
        if (_jobKey != null)
          Job.update(vec.length() / nChunks, _jobKey);
        nextChunk.close();
        dout.reduce(nextChunk);
        nextChunk = nextChunk.nextChunk();
        w.setWriter(nextChunk);
      }
      assert parsedRecs == totalRecs;
    } catch (IOException e) {
      throw new RuntimeException("Failed to parse records", e);
    }
    return dout;
  }

  private long totalRecords(ParquetMetadata metadata) {
    long nr = 0;
    for (BlockMetaData meta : metadata.getBlocks()) {
      nr += meta.getRowCount();
    }
    return nr;
  }

  @Override
  protected final ParseWriter parseChunk(int cidx, ParseReader din, ParseWriter dout) {
    if (! (din instanceof FVecParseReader)) {
      // TODO: Should we modify the interface to expose the underlying chunk for non-streaming parsers?
      throw new IllegalStateException("We only accept parser readers backed by a Vec (no streaming support!).");
    }
    Chunk chunk = ((FVecParseReader) din).getChunk();
    Vec vec = chunk.vec();
    // extract metadata, we want to read only the row groups that have centers in this chunk
    ParquetMetadataConverter.MetadataFilter chunkFilter = ParquetMetadataConverter.range(
            chunk.start(), chunk.start() + chunk.len());
    ParquetMetadata metadata = VecParquetReader.readFooter(_metadata, chunkFilter);
    if (metadata.getBlocks().isEmpty()) {
      Log.trace("Chunk #", cidx, " doesn't contain any Parquet block center.");
      return dout;
    }
    Log.info("Processing ", metadata.getBlocks().size(), " blocks of chunk #", cidx);
    VecParquetReader reader = new VecParquetReader(vec, metadata, dout, _setup.getColumnTypes(), _keepColumns, _setup.get_parse_columns_indices().length);
    try {
      Long recordNumber;
      do {
        recordNumber = reader.read();
      } while (recordNumber != null);
    } catch (IOException e) {
      throw new RuntimeException("Failed to parse records", e);
    }
    return dout;
  }

  public static ParquetParseSetup guessFormatSetup(ByteVec vec, byte[] bits) {
    if (bits.length < MAGIC.length) {
      return null;
    }
    for (int i = 0; i < MAGIC.length; i++) {
      if (bits[i] != MAGIC[i]) return null;
    }
    // seems like we have a Parquet file
    byte[] metadataBytes = VecParquetReader.readFooterAsBytes(vec);
    ParquetMetadata metadata = VecParquetReader.readFooter(metadataBytes);
    checkCompatibility(metadata);
    return toInitialSetup(metadata.getFileMetaData().getSchema(), metadataBytes);
  }

  private static ParquetParseSetup toInitialSetup(MessageType parquetSchema, byte[] metadataBytes) {
    byte[] roughTypes = roughGuessTypes(parquetSchema);
    String[] columnNames = columnNames(parquetSchema);
    return new ParquetParseSetup(columnNames, roughTypes, null, metadataBytes);
  }

  public static ParquetParseSetup guessDataSetup(ByteVec vec, ParquetParseSetup ps, boolean[] keepcolumns) {
    ParquetPreviewParseWriter ppWriter = readFirstRecords(ps, vec, MAX_PREVIEW_RECORDS, keepcolumns);
    return ppWriter.toParseSetup(ps.parquetMetadata);
  }

  /**
   * Overrides unsupported type conversions/mappings specified by the user.
   * @param vec byte vec holding bin\ary parquet data
   * @param requestedTypes user-specified target types
   * @return corrected types
   */
  public static byte[] correctTypeConversions(ByteVec vec, byte[] requestedTypes) {
    byte[] metadataBytes = VecParquetReader.readFooterAsBytes(vec);
    ParquetMetadata metadata = VecParquetReader.readFooter(metadataBytes, ParquetMetadataConverter.NO_FILTER);
    byte[] roughTypes = roughGuessTypes(metadata.getFileMetaData().getSchema());
    return correctTypeConversions(roughTypes, requestedTypes);
  }

  private static byte[] correctTypeConversions(byte[] roughTypes, byte[] requestedTypes) {
    if (requestedTypes.length != roughTypes.length)
      throw new IllegalArgumentException("Invalid column type specification: number of columns and number of types differ!");
    byte[] resultTypes = new byte[requestedTypes.length];
    for (int i = 0; i < requestedTypes.length; i++) {
      if ((roughTypes[i] == Vec.T_NUM) || (roughTypes[i] == Vec.T_TIME)) {
        // don't convert Parquet numeric/time type to non-numeric type in H2O
        resultTypes[i] = roughTypes[i];
      } else if ((roughTypes[i] == Vec.T_BAD) && (requestedTypes[i] == Vec.T_NUM)) {
        // don't convert Parquet non-numeric type to a numeric type in H2O
        resultTypes[i] = Vec.T_STR;
      } else
        // satisfy the request
        resultTypes[i] = requestedTypes[i];
    }
    return resultTypes; // return types for all columns present.
  }

  private static class ParquetPreviewParseWriter extends PreviewParseWriter {

    private String[] _colNames;
    private byte[] _roughTypes;

    public ParquetPreviewParseWriter() {
      // externalizable class should have a public constructor
      super();
    }

    ParquetPreviewParseWriter(ParquetParseSetup setup) {
      super(setup.getColumnNames().length);
      _colNames = setup.getColumnNames();
      _roughTypes = setup.getColumnTypes();
      setColumnNames(_colNames);
      _nlines = 0;
      _data[0] = new String[_colNames.length];
    }

    @Override
    public byte[] guessTypes() {
      return correctTypeConversions(_roughTypes, super.guessTypes());
    }

    ParquetParseSetup toParseSetup(byte[] parquetMetadata) {
      byte[] types = guessTypes();
      return new ParquetParseSetup(_colNames, types, _data, parquetMetadata);
    }

  }

  public static class ParquetParseSetup extends ParseSetup {
    transient byte[] parquetMetadata;

    public ParquetParseSetup() { super(); }
    public ParquetParseSetup(String[] columnNames, byte[] ctypes, String[][] data, byte[] parquetMetadata) {
      super(ParquetParserProvider.PARQUET_INFO, (byte) '|', true, ParseSetup.HAS_HEADER,
              columnNames.length, columnNames, ctypes,
              new String[columnNames.length][] /* domains */, null /* NA strings */, data);
      this.parquetMetadata = parquetMetadata;
    }
  }

  private static void checkCompatibility(ParquetMetadata metadata) {
    // make sure we can map Parquet blocks to Chunks
    for (BlockMetaData block : metadata.getBlocks()) {
      if (block.getRowCount() > Integer.MAX_VALUE) {
        IcedHashMapGeneric.IcedHashMapStringObject dbg = new IcedHashMapGeneric.IcedHashMapStringObject();
        dbg.put("startingPos", block.getStartingPos());
        dbg.put("rowCount", block.getRowCount());
        throw new H2OUnsupportedDataFileException("Unsupported Parquet file (technical limitation).",
                "Current implementation doesn't support Parquet files with blocks larger than " +
                Integer.MAX_VALUE + " rows.", dbg); // because we map each block to a single H2O Chunk
      }
    }
    // check that file doesn't have nested structures
    MessageType schema = metadata.getFileMetaData().getSchema();
    for (String[] path : schema.getPaths())
      if (path.length != 1) {
        throw new H2OUnsupportedDataFileException("Parquet files with nested structures are not supported.",
                "Detected a column with a nested structure " + Arrays.asList(path));
      }
  }

  private static ParquetPreviewParseWriter readFirstRecords(ParquetParseSetup initSetup, ByteVec vec, int cnt,
                                                            boolean[] keepcolumns) {
    ParquetMetadata metadata = VecParquetReader.readFooter(initSetup.parquetMetadata);
    List<BlockMetaData> blockMetaData;
    if (metadata.getBlocks().isEmpty()) {
      blockMetaData = Collections.<BlockMetaData>emptyList();
    } else {
      final BlockMetaData firstBlock = findFirstBlock(metadata);
      blockMetaData = Collections.singletonList(firstBlock);
    }
    ParquetMetadata startMetadata = new ParquetMetadata(metadata.getFileMetaData(), blockMetaData);
    ParquetPreviewParseWriter ppWriter = new ParquetPreviewParseWriter(initSetup);
    VecParquetReader reader = new VecParquetReader(vec, startMetadata, ppWriter, ppWriter._roughTypes, keepcolumns,initSetup.get_parse_columns_indices().length);
    try {
      int recordCnt = 0;
      Long recordNum;
      do {
        recordNum = reader.read();
      } while ((recordNum != null) && (++recordCnt < cnt));
      return ppWriter;
    } catch (IOException e) {
      throw new RuntimeException("Failed to read the first few records", e);
    }
  }

  private static byte[] roughGuessTypes(MessageType messageType) {
    byte[] types = new byte[messageType.getPaths().size()];
    for (int i = 0; i < types.length; i++) {
      Type parquetType = messageType.getType(i);
      assert parquetType.isPrimitive();
      switch (parquetType.asPrimitiveType().getPrimitiveTypeName()) {
        case INT32:
        case BOOLEAN:
        case FLOAT:
        case DOUBLE:
          types[i] = Vec.T_NUM;
          break;
        case INT96:
          types[i] = Vec.T_TIME;
          break;
        case INT64:
          types[i] = OriginalType.TIMESTAMP_MILLIS.equals(parquetType.getOriginalType()) ? Vec.T_TIME : Vec.T_NUM;
          break;
        default:
          types[i] = Vec.T_BAD;
      }
    }
    return types;
  }

  private static String[] columnNames(MessageType messageType) {
    String[] colNames = new String[messageType.getPaths().size()];
    int i = 0;
    for (String[] path : messageType.getPaths()) {
      assert path.length == 1;
      colNames[i++] = path[0];
    }
    return colNames;
  }

  private static BlockMetaData findFirstBlock(ParquetMetadata metadata) {
    BlockMetaData firstBlockMeta = metadata.getBlocks().get(0);
    for (BlockMetaData meta : metadata.getBlocks()) {
      if (meta.getStartingPos() < firstBlockMeta.getStartingPos()) {
        firstBlockMeta = meta;
      }
    }
    return firstBlockMeta;
  }

}
