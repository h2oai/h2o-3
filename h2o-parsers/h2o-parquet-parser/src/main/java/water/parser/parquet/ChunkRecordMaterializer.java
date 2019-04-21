package water.parser.parquet;

import org.apache.parquet.io.api.GroupConverter;
import org.apache.parquet.io.api.RecordMaterializer;
import org.apache.parquet.schema.MessageType;

/**
 * Implementation of Parquet's RecordMaterializer for Chunks
 *
 * This implementation doesn't directly return any records. The rows are written to Chunks
 * indirectly using a ParseWriter and function getCurrentRecord returns the index of the record
 * in the current chunk.
 */
class ChunkRecordMaterializer extends RecordMaterializer<Long> {

  private ChunkConverter _converter;

  ChunkRecordMaterializer(MessageType parquetSchema, byte[] chunkSchema, WriterDelegate writer, boolean[] keepColumns) {
    _converter = new ChunkConverter(parquetSchema, chunkSchema, writer, keepColumns);
  }

  @Override
  public Long getCurrentRecord() {
    return _converter.getCurrentRecordIdx();
  }

  @Override
  public GroupConverter getRootConverter() {
    return _converter;
  }

}
