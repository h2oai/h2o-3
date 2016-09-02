package water.parser.parquet;

import static org.apache.parquet.hadoop.ParquetFileWriter.MAGIC;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.parquet.format.converter.ParquetMetadataConverter;
import org.apache.parquet.hadoop.VecParquetReader;
import org.apache.parquet.hadoop.metadata.BlockMetaData;
import org.apache.parquet.hadoop.metadata.ParquetMetadata;
import water.Job;
import water.Key;

import water.fvec.ByteVec;
import water.fvec.Chunk;
import water.fvec.Vec;
import water.parser.*;
import water.parser.parquet.compat.AvroUtil;
import water.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parquet parser for H2O distributed parsing subsystem.
 *
 * The implementation relies on Avro compatibility layer. We use Parquet's support to read data as Avro's
 * GenericRecords and re-use the existing Avro parser implementation to store the data to Chunks.
 */
public class ParquetParser extends Parser {

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
    VecParquetReader reader = new VecParquetReader(vec, metadata);
    try {
      GenericRecord record;
      while ((record = reader.read()) != null) {
        write2frame(record, AvroUtil.flatSchema(record.getSchema()), dout);
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to parse records", e);
    }
    return dout;
  }

  // TODO: Extracted from Avro parser with minor modifications, we need custom Parquet parser
  private static void write2frame(GenericRecord gr, Schema.Field[] inSchema, ParseWriter dout) {
    BufferedString bs = new BufferedString();
    for (int cIdx = 0; cIdx < inSchema.length; cIdx++) {
      int inputFieldIdx = inSchema[cIdx].pos();
      Schema.Type inputType = AvroUtil.toPrimitiveType(inSchema[cIdx].schema());
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
            dout.addStrCol(cIdx, bs.set(((String) value).getBytes()));
            break;
          case NULL:
            dout.addInvalidCol(cIdx);
            break;
        }
      }
    }
  }

  public static ParseSetup guessSetup(ByteVec vec, byte[] bits) {
    if (bits.length < MAGIC.length) {
      return null;
    }
    for (int i = 0; i < MAGIC.length; i++) {
      if (bits[i] != MAGIC[i]) return null;
    }
    // seems like we have a Parquet file
    List<GenericRecord> records = readFirstRecords(vec, 1);
    if (records.isEmpty()) {
      throw new RuntimeException("File is empty, unable to guess setup.");
    }
    GenericRecord record = records.get(0);
    Schema.Field[] fields = AvroUtil.flatSchema(record.getSchema());
    String[] names = new String[fields.length];
    byte[] types = new byte[fields.length];
    String[] example = new String[fields.length];
    for (int i = 0; i < fields.length; i++) {
      names[i] = fields[i].name();
      types[i] = AvroUtil.schemaToColumnType(fields[i].schema());
      example[i] = String.valueOf(record.get(fields[i].name()));
    }
    return new ParseSetup(
            ParquetParserProvider.PARQUET_INFO, (byte) '|', true, ParseSetup.HAS_HEADER,
            names.length, names, types, new String[names.length][], null, new String[][] { example }
    );
  }

  private static List<GenericRecord> readFirstRecords(ByteVec vec, int cnt) {
    ParquetMetadata metadata = VecParquetReader.readFooter(vec, ParquetMetadataConverter.NO_FILTER);
    ParquetMetadata startMetadata = new ParquetMetadata(metadata.getFileMetaData(), Collections.singletonList(findFirstBlock(metadata)));
    VecParquetReader reader = new VecParquetReader(vec, startMetadata);
    List<GenericRecord> records = new ArrayList<>(cnt);
    try {
      GenericRecord record;
      while ((records.size() < cnt) && ((record = reader.read()) != null)) {
        records.add(record);
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to read the first few records", e);
    }
    return records;
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
