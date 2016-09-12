package water.parser.parquet;

import org.apache.parquet.io.api.GroupConverter;
import org.apache.parquet.io.api.RecordMaterializer;
import org.apache.parquet.schema.MessageType;
import water.parser.ParseWriter;

/**
 * Implementation of Parquet's RecordMaterializer for Chunks
 *
 * This implementation doesn't directly return any records. The rows are written to Chunks
 * indirectly using a ParseWriter and function getCurrentRecord returns the index of the record
 * in the current chunk.
 */
class ChunkRecordMaterializer extends RecordMaterializer<Integer> {

  private ChunkConverter _converter;

  ChunkRecordMaterializer(MessageType parquetSchema, byte[] chunkSchema, ParseWriter writer) {
    _converter = new ChunkConverter(parquetSchema, chunkSchema, writer);
  }

  @Override
  public Integer getCurrentRecord() {
    return _converter.getCurrentRecordIdx();
  }

  @Override
  public GroupConverter getRootConverter() {
    return _converter;
  }

}
