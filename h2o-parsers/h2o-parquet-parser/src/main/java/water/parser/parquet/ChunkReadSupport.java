package water.parser.parquet;

import org.apache.hadoop.conf.Configuration;
import org.apache.parquet.hadoop.api.InitContext;
import org.apache.parquet.hadoop.api.ReadSupport;
import org.apache.parquet.io.api.RecordMaterializer;
import org.apache.parquet.schema.MessageType;

import java.util.Map;

public class ChunkReadSupport extends ReadSupport<Long> {

  private WriterDelegate _writer;
  private byte[] _chunkSchema;
  private boolean[] _keepColumns;

  public ChunkReadSupport(WriterDelegate writer, byte[] chunkSchema, boolean[] keepcolumns) {
    _writer = writer;
    _chunkSchema = chunkSchema;
    _keepColumns = keepcolumns;
  }

  @Override
  public ReadContext init(InitContext context) {
    return new ReadContext(context.getFileSchema());
  }

  @Override
  public RecordMaterializer<Long> prepareForRead(Configuration configuration, Map<String, String> keyValueMetaData,
                                                    MessageType fileSchema, ReadContext readContext) {
    return new ChunkRecordMaterializer(fileSchema, _chunkSchema, _writer, _keepColumns);
  }

}
