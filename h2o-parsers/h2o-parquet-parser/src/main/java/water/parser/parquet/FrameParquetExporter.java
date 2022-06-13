package water.parser.parquet;

import org.apache.hadoop.conf.Configuration;
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

import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.hadoop.fs.Path;
import water.parser.BufferedString;
import water.persist.PersistHdfs;

import java.io.File;
import java.io.IOException;

public class FrameParquetExporter  {

    public void export(H2O.H2OCountedCompleter<?> completer, String path, Frame frame, boolean force, String compression) {
        File f = new File(path);
        new FrameParquetExporter.PartExportParquetTask(completer, f.getPath(), generateMessageTypeString(frame), frame.names(), frame.types(), frame.domains(), force, compression).dfork(frame);
    }

    private static class PartExportParquetTask extends MRTask<PartExportParquetTask> {
        final String _path;
        final CompressionCodecName _compressionCodecName;
        final String _messageTypeString;
        final String[] _colNames;
        final byte[] _colTypes;
        final String[][] _domains;
        final boolean _force;

        PartExportParquetTask(H2O.H2OCountedCompleter<?> completer, String path, String messageTypeString,
                              String[] colNames, byte[] colTypes, String[][] domains, boolean force, String compression) {
            super(completer);
            _path = path;
            _compressionCodecName = getCompressionCodecName(compression);
            _messageTypeString = messageTypeString;
            _colNames = colNames;
            _colTypes = colTypes;
            _domains = domains;
            _force = force;
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
            try (ParquetWriter<Group> writer = buildWriter(new Path(partPath), _compressionCodecName, PersistHdfs.CONF, parseMessageType(_messageTypeString), getMode(_force))) {
                String currColName;
                byte currColType;

                for (int i = 0; i < anyChunk._len; i++) {
                    Group group = fact.newGroup();
                    for (int j = 0; j < cs.length; j++) {
                        currColName = _colNames[j];
                        currColType = _colTypes[j];
                        switch (currColType) {
                            case (T_UUID):
                            case (T_TIME):
                                group = group.append(currColName, cs[j].at8(i));
                                break;
                            case (T_STR):
                                group = group.append(currColName, cs[j].atStr(new BufferedString(), i).toString());
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
        String message_txt = "message test { ";
        String currName;
        for (int i = 0; i < frame.numCols(); i++) {
            currName = frame._names[i];
            switch (frame.types()[i]) {
                case (T_TIME):
                    message_txt = message_txt.concat("optional int64 ").concat(currName).concat(" (TIMESTAMP_MILLIS);");
                    break;
                case (T_NUM):
                case (T_BAD):
                    message_txt = message_txt.concat("optional double ").concat(currName).concat("; ");
                    break;
                case (T_STR):
                case (T_CAT):
                    message_txt = message_txt.concat("optional BINARY ").concat(currName).concat(" (UTF8); ");
                    break;
                case (T_UUID):
                    message_txt = message_txt.concat("optional fixed_len_byte_array(16) ").concat(currName).concat(" (UUID); ");
                    break;
            }
        }
        message_txt = message_txt.concat("} ");
        return message_txt;
    }

    private static ParquetWriter<Group> buildWriter(Path file, CompressionCodecName compressionCodecName, Configuration configuration, MessageType _schema, ParquetFileWriter.Mode mode) throws IOException {
        GroupWriteSupport.setSchema(_schema, configuration);
        return new ParquetWriter.Builder(file) {
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
