package water.parser.parquet;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.parquet.column.ParquetProperties;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.example.data.simple.SimpleGroupFactory;
import org.apache.parquet.hadoop.ParquetFileWriter;
import org.apache.parquet.hadoop.api.WriteSupport;
import org.apache.parquet.hadoop.example.GroupWriteSupport;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.schema.MessageType;
import water.H2O;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;

import static org.apache.parquet.hadoop.metadata.CompressionCodecName.GZIP;
import static org.apache.parquet.hadoop.metadata.CompressionCodecName.UNCOMPRESSED;
import static org.apache.parquet.schema.MessageTypeParser.parseMessageType;
import static water.fvec.Vec.*;
import static water.parser.parquet.TypeUtils.getTimestampAdjustmentFromUtcToLocalInMillis;

import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.hadoop.fs.Path;
import water.parser.BufferedString;
import water.persist.PersistHdfs;

import java.io.File;
import java.io.IOException;

public class FrameParquetExporter  {

    public void export(H2O.H2OCountedCompleter<?> completer, String path, Frame frame, boolean force, String compression, boolean writeChecksum, boolean tzAdjustFromLocal) {
        File f = new File(path);
        new FrameParquetExporter.PartExportParquetTask(
                completer, 
                f.getPath(), 
                generateMessageTypeString(frame), 
                frame.names(), 
                frame.types(), 
                frame.domains(), 
                force, 
                compression,
                writeChecksum,
                tzAdjustFromLocal
        ).dfork(frame);
    }

    private static class PartExportParquetTask extends MRTask<PartExportParquetTask> {
        final String _path;
        final CompressionCodecName _compressionCodecName;
        final String _messageTypeString;
        final String[] _colNames;
        final byte[] _colTypes;
        final String[][] _domains;
        final boolean _force;
        final boolean _writeChecksum;
        final boolean _tzAdjustFromLocal;

        PartExportParquetTask(H2O.H2OCountedCompleter<?> completer, String path, String messageTypeString,
                              String[] colNames, byte[] colTypes, String[][] domains, 
                              boolean force, String compression, boolean writeChecksum, boolean tzAdjustFromLocal) {
            super(completer);
            _path = path;
            _compressionCodecName = getCompressionCodecName(compression);
            _messageTypeString = messageTypeString;
            _colNames = colNames;
            _colTypes = colTypes;
            _domains = domains;
            _force = force;
            _writeChecksum = writeChecksum;
            _tzAdjustFromLocal = tzAdjustFromLocal;
        }

        CompressionCodecName getCompressionCodecName(String compression) {
            if (compression == null)
                return UNCOMPRESSED;

            switch (compression.toLowerCase()) {
                case "gzip":
                    return GZIP;
                case "lzo":
                    return CompressionCodecName.LZO;
                case "snappy":
                    return CompressionCodecName.SNAPPY;
                default:
                    throw new RuntimeException("Compression " + compression + "is not supported for parquet export.");
            }

        }

        ParquetFileWriter.Mode getMode(boolean force) {
            return force ? ParquetFileWriter.Mode.OVERWRITE : ParquetFileWriter.Mode.CREATE;
        }

        @Override
        public void map(Chunk[] cs) {
            Chunk anyChunk = cs[0];
            int partIdx = anyChunk.cidx();
            String partPath = _path + "/part-m-" + String.valueOf(100000 + partIdx).substring(1);

            SimpleGroupFactory fact = new SimpleGroupFactory(parseMessageType(_messageTypeString));
            try (ParquetWriter<Group> writer = buildWriter(new Path(partPath), _compressionCodecName, PersistHdfs.CONF, parseMessageType(_messageTypeString), getMode(_force), _writeChecksum)) {
                String currColName;
                byte currColType;
                long timeStampAdjustment = _tzAdjustFromLocal ? getTimestampAdjustmentFromUtcToLocalInMillis() : 0L;
                for (int i = 0; i < anyChunk._len; i++) {
                    Group group = fact.newGroup();
                    for (int j = 0; j < cs.length; j++) {
                        currColName = _colNames[j];
                        currColType = _colTypes[j];
                        switch (currColType) {
                            case (T_UUID):
                            case (T_TIME):
                                long timestamp = cs[j].at8(i);
                                long adjustedTimestamp = timestamp - timeStampAdjustment;
                                group = group.append(currColName, adjustedTimestamp);
                                break;
                            case (T_STR):
                                if (!cs[j].isNA(i)) {
                                    group = group.append(currColName, cs[j].atStr(new BufferedString(), i).toString());
                                }
                                break;
                            case (T_CAT):
                                if (cs[j].isNA(i)) {
                                    group = group.append(currColName, "");
                                } else {
                                    group = group.append(currColName, _domains[j][(int) cs[j].at8(i)]);
                                }
                                break;
                            case (T_NUM):
                            case (T_BAD):
                            default:
                                group = group.append(currColName, cs[j].atd(i));
                                break;
                        }
                    }
                    writer.write(group);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static String generateMessageTypeString(Frame frame) {
        StringBuilder mb = new StringBuilder("message export_type { ");
        String currName;
        for (int i = 0; i < frame.numCols(); i++) {
            currName = frame._names[i];
            switch (frame.types()[i]) {
                case (T_TIME):
                    mb.append("optional int64 ").append(currName).append(" (TIMESTAMP_MILLIS);");
                    break;
                case (T_NUM):
                case (T_BAD):
                    mb.append("optional double ").append(currName).append("; ");
                    break;
                case (T_STR):
                case (T_CAT):
                    mb.append("optional BINARY ").append(currName).append(" (UTF8); ");
                    break;
                case (T_UUID):
                    mb.append("optional fixed_len_byte_array(16) ").append(currName).append(" (UUID); ");
                    break;
            }
        }
        mb.append("} ");
        return mb.toString();
    }

    private static ParquetWriter<Group> buildWriter(Path path, CompressionCodecName compressionCodecName, Configuration configuration, MessageType schema, ParquetFileWriter.Mode mode, boolean writeChecksum) throws IOException {
        GroupWriteSupport.setSchema(schema, configuration);

        // The filesystem is cached for a given path and configuration, 
        // therefore the following modification on the fs is a bit hacky as another process could use the same instance.
        // However, given the current use case and the fact that the changes impacts only the way files are written, it should be on the safe side.
        FileSystem fs = path.getFileSystem(configuration);
        fs.setWriteChecksum(writeChecksum);
        return new ParquetWriter.Builder(path) {
            @Override
            protected ParquetWriter.Builder self() {
                return this;
            }

            @Override
            protected WriteSupport<Group> getWriteSupport(Configuration conf) {
                return new GroupWriteSupport();
            }
        }
                .self()
                .withCompressionCodec(compressionCodecName)
                .withConf(configuration)
                .withWriteMode(mode)
                .build();
    }
}
