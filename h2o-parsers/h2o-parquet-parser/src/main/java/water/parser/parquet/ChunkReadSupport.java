package water.parser.parquet;

import org.apache.hadoop.conf.Configuration;
import org.apache.parquet.hadoop.api.InitContext;
import org.apache.parquet.hadoop.api.ReadSupport;
import org.apache.parquet.io.api.RecordMaterializer;
import org.apache.parquet.schema.MessageType;
import water.parser.ParseWriter;

import java.util.Map;

public class ChunkReadSupport extends ReadSupport<Long> {

  private WriterDelegate _writer;
  private byte[] _chunkSchema;

  public ChunkReadSupport(WriterDelegate writer, byte[] chunkSchema) {
    _writer = writer;
    _chunkSchema = chunkSchema;
  }

  @Override
  public ReadContext init(InitContext context) {
    return new ReadContext(context.getFileSchema());
  }

  @Override
  public RecordMaterializer<Long> prepareForRead(Configuration configuration, Map<String, String> keyValueMetaData,
                                                    MessageType fileSchema, ReadContext readContext) {
    return new ChunkRecordMaterializer(fileSchema, _chunkSchema, _writer);
  }

}
