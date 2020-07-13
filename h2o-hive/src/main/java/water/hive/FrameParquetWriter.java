package water.hive;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.GenericRecordBuilder;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import water.H2O;
import water.Key;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.parser.BufferedString;
import water.persist.PersistHdfs;
import water.util.PrettyPrint;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FrameParquetWriter {

    public void write(Frame frame, String fileName) throws IOException {
        Schema schema = makeSchema(frame);
        try (ParquetWriter<GenericRecord> writer = openWriter(fileName, schema)) {
            Chunk[] chunks = new Chunk[frame.numCols()];
            BufferedString tmpStr = new BufferedString();
            for (int cidx = 0; cidx < frame.anyVec().nChunks(); cidx++) {
                for (int col = 0; col < frame.numCols(); col++) {
                    chunks[col] = frame.vec(col).chunkForChunkIdx(cidx);
                }
                for (int crow = 0; crow < chunks[0].len(); crow++) {
                    GenericRecordBuilder builder = new GenericRecordBuilder(schema);
                    for (int col = 0; col < frame.numCols(); col++) {
                        builder.set(frame.name(col), getValue(chunks[col], crow, tmpStr));
                    }
                    writer.write(builder.build());
                }
                for (int col = 0; col < frame.numCols(); col++) {
                    Key chunkKey = chunks[col].vec().chunkKey(cidx);
                    if (!chunkKey.home()) {
                        H2O.raw_remove(chunkKey);
                    }
                }
            }
        }
    }

    private Object getValue(Chunk chunk, int crow, BufferedString tmpStr) {
        Vec v = chunk.vec();
        if (!chunk.isNA(crow)) {
            if (v.isCategorical()) {
                return chunk.vec().domain()[(int) chunk.at8(crow)];
            } else if (v.isUUID()) {
                return PrettyPrint.UUID(chunk.at16l(crow), chunk.at16h(crow));
            } else if (v.isInt()) {
                return chunk.at8(crow);
            } else if (v.isString()) {
                return chunk.atStr(tmpStr, crow).toString();
            } else {
                return chunk.atd(crow);
            }
        } else {
            return null;
        }
    }

    private ParquetWriter<GenericRecord> openWriter(String fileName, Schema schema) throws IOException {
        return AvroParquetWriter.<GenericRecord>builder(new Path(fileName))
            .withSchema(schema)
            .withConf(PersistHdfs.CONF)
            .build();
    }

    private Schema makeSchema(Frame frame) {
        List<Schema.Field> fields = new ArrayList<>();
        for (int cidx = 0; cidx < frame.numCols(); cidx++) {
            fields.add(new Schema.Field(
                frame.name(cidx),
                getColumnType(frame.vec(cidx)),
                null,
                null
            ));
        }
        Schema schema = Schema.createRecord("h2o_frame", null, null, false);
        schema.setFields(fields);
        return schema;
    }

    private Schema getColumnType(Vec v) {
        Schema type;
        if (v.isCategorical() || v.isUUID() || v.isString()) {
            type = Schema.create(Schema.Type.STRING);
        } else if (v.isInt()) {
            type = Schema.create(Schema.Type.LONG);
        } else {
            type = Schema.create(Schema.Type.DOUBLE);
        }
        return Schema.createUnion(Arrays.asList(type, Schema.create(Schema.Type.NULL)));
    }

}
