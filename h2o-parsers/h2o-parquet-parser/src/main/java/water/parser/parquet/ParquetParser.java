package water.parser.parquet;

import static org.apache.parquet.hadoop.ParquetFileWriter.MAGIC;

import org.apache.parquet.format.converter.ParquetMetadataConverter;
import org.apache.parquet.hadoop.VecParquetReader;
import org.apache.parquet.hadoop.metadata.BlockMetaData;
import org.apache.parquet.hadoop.metadata.ParquetMetadata;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.Type;
import water.Job;
import water.Key;

import water.fvec.ByteVec;
import water.fvec.Chunk;
import water.fvec.Vec;
import water.parser.*;
import water.util.Log;

import java.io.IOException;
import java.util.Collections;

/**
 * Parquet parser for H2O distributed parsing subsystem.
 *
 * The implementation relies on Avro compatibility layer. We use Parquet's support to read data as Avro's
 * GenericRecords and re-use the existing Avro parser implementation to store the data to Chunks.
 */
public class ParquetParser extends Parser {

  private static final int MAX_PREVIEW_RECORDS = 1000;

  ParquetParser(ParseSetup setup, Key<Job> jobKey) {
    super(setup, jobKey);
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
    ParquetMetadata metadata = VecParquetReader.readFooter(vec, chunkFilter);
    if (metadata.getBlocks().isEmpty()) {
      Log.trace("Chunk #", cidx, " doesn't contain any Parquet block center.");
      return dout;
    }
    Log.info("Processing ", metadata.getBlocks().size(), " blocks of chunk #", cidx);
    VecParquetReader reader = new VecParquetReader(vec, metadata, dout, _setup.getColumnTypes());
    try {
      Integer recordNumber;
      do {
        recordNumber = reader.read();
      } while (recordNumber != null);
    } catch (IOException e) {
      throw new RuntimeException("Failed to parse records", e);
    }
    return dout;
  }

  public static ParseSetup guessSetup(ByteVec vec, byte[] bits) {
    if (bits.length < MAGIC.length) {
      return null;
    }
    for (int i = 0; i < MAGIC.length; i++) {
      if (bits[i] != MAGIC[i]) return null;
    }
    // seems like we have a Parquet file
    ParquetMetadata metadata = VecParquetReader.readFooter(vec, ParquetMetadataConverter.NO_FILTER);
    checkCompatibility(metadata);
    ParquetPreviewParseWriter ppWriter = readFirstRecords(metadata, vec, MAX_PREVIEW_RECORDS);
    return ppWriter.toParseSetup();
  }

  private static class ParquetPreviewParseWriter extends PreviewParseWriter {

    private String[] _colNames;
    private byte[] _roughTypes;

    public ParquetPreviewParseWriter() {
      // externalizable class should have a public constructor
      super();
    }

    ParquetPreviewParseWriter(MessageType parquetSchema) {
      super(parquetSchema.getPaths().size());
      _colNames = columnNames(parquetSchema);
      _roughTypes = roughGuessTypes(parquetSchema);
      setColumnNames(_colNames);
      _nlines = 0;
      _data[0] = new String[_colNames.length];
    }

    @Override
    public byte[] guessTypes() {
      byte[] types = super.guessTypes();
      for (int i = 0; i < types.length; i++) {
        if (_roughTypes[i] == Vec.T_NUM) {
          // don't convert Parquet numeric type to non-numeric type in H2O
          types[i] = Vec.T_NUM;
        } else if ((_roughTypes[i] == Vec.T_BAD) && (types[i] == Vec.T_NUM)) {
          // don't convert Parquet non-numeric type to a numeric type in H2O
          types[i] = Vec.T_STR;
        }
      }
      return types;
    }

    ParseSetup toParseSetup() {
      byte[] types = guessTypes();
      return new ParseSetup(
              ParquetParserProvider.PARQUET_INFO, (byte) '|', true, ParseSetup.HAS_HEADER,
              _colNames.length, _colNames, types, new String[_colNames.length][] /* domains */, null /* NA strings */,
              _data);
    }

  }

  private static void checkCompatibility(ParquetMetadata metadata) {
    for (BlockMetaData block : metadata.getBlocks()) {
      if (block.getRowCount() > Integer.MAX_VALUE) {
        throw new RuntimeException("Current implementation doesn't support Parquet files with blocks larger than " +
                Integer.MAX_VALUE + " rows."); // because we map each block to a single H2O Chunk
      }
    }
  }

  private static ParquetPreviewParseWriter readFirstRecords(ParquetMetadata metadata, ByteVec vec, int cnt) {
    ParquetMetadata startMetadata = new ParquetMetadata(metadata.getFileMetaData(), Collections.singletonList(findFirstBlock(metadata)));
    ParquetPreviewParseWriter ppWriter = new ParquetPreviewParseWriter(metadata.getFileMetaData().getSchema());
    VecParquetReader reader = new VecParquetReader(vec, startMetadata, ppWriter, ppWriter._roughTypes);
    try {
      int recordCnt = 0;
      Integer recordNum;
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
        case INT64:
        case INT32:
        case BOOLEAN:
        case FLOAT:
        case DOUBLE:
          types[i] = Vec.T_NUM;
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
      if (firstBlockMeta.getStartingPos() < firstBlockMeta.getStartingPos()) {
        firstBlockMeta = meta;
      }
    }
    return firstBlockMeta;
  }

}
