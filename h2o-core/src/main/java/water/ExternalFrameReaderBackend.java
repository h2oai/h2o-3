package water;

import water.fvec.Chunk;
import water.fvec.ChunkUtils;
import water.fvec.Frame;
import water.parser.BufferedString;

import java.io.IOException;
import java.nio.channels.ByteChannel;
import java.util.UUID;

import static water.ExternalFrameUtils.*;

/**
 * This class contains methods used on the h2o backend to read data from H2O Frame.
 */
final class ExternalFrameReaderBackend {

    /**
     * Internal method use on the h2o backend side to handle reading from the chunk from non-h2o environment
     * @param channel socket channel originating from non-h2o node
     * @param initAb {@link AutoBuffer} containing information necessary for preparing backend for reading
     */
    static void readFromChunk(ByteChannel channel, AutoBuffer initAb) throws IOException {
        // receive required information
        String frameKey = initAb.getStr();
        int chunkIdx = initAb.getInt();
        byte[] expectedTypes = initAb.getA1();
        assert expectedTypes != null : "Expected types can't be null";
        int[] selectedColumnIndices = initAb.getA4();
        assert selectedColumnIndices != null : "Selected column indices can't be null";
        Frame fr = DKV.getGet(frameKey);
        Chunk[] chunks = ChunkUtils.getChunks(fr, chunkIdx);

        // write number of rows
        AutoBuffer ab = new AutoBuffer();
        ab.putInt(chunks[0]._len);
        writeToChannel(ab, channel);

        // buffered string to be reused for strings to avoid multiple allocation in the loop
        BufferedString valStr = new BufferedString();
        for (int rowIdx = 0; rowIdx < chunks[0]._len; rowIdx++) {
            for(int i = 0; i < selectedColumnIndices.length; i++){
                if (chunks[selectedColumnIndices[i]].isNA(rowIdx)) {
                    ExternalFrameUtils.sendNA(ab, expectedTypes[i]);
                } else {
                    final Chunk chnk = chunks[selectedColumnIndices[i]];
                    switch (expectedTypes[i]) {
                        case EXPECTED_BOOL:
                            ExternalFrameUtils.sendBoolean(ab, (byte)chnk.at8(rowIdx));
                            break;
                        case EXPECTED_BYTE:
                            ExternalFrameUtils.sendByte(ab, (byte)chnk.at8(rowIdx));
                            break;
                        case EXPECTED_CHAR:
                            ExternalFrameUtils.sendChar(ab, (char)chnk.at8(rowIdx));
                            break;
                        case EXPECTED_SHORT:
                            ExternalFrameUtils.sendShort(ab, (short)chnk.at8(rowIdx));
                            break;
                        case EXPECTED_INT:
                            ExternalFrameUtils.sendInt(ab, (int)chnk.at8(rowIdx));
                            break;
                        case EXPECTED_FLOAT:
                            ExternalFrameUtils.sendFloat(ab, (float)chnk.atd(rowIdx));
                            break;
                        case EXPECTED_LONG:
                            ExternalFrameUtils.sendLong(ab, chnk.at8(rowIdx));
                            break;
                        case EXPECTED_DOUBLE:
                            ExternalFrameUtils.sendDouble(ab, chnk.atd(rowIdx));
                            break;
                        case EXPECTED_TIMESTAMP:
                            ExternalFrameUtils.sendTimestamp(ab, chnk.at8(rowIdx));
                            break;
                        case EXPECTED_STRING:
                            if (chnk.vec().isCategorical()) {
                                ExternalFrameUtils.sendString(ab, chnk.vec().domain()[(int) chnk.at8(rowIdx)]);
                            } else if (chnk.vec().isString()) {
                                ExternalFrameUtils.sendString(ab, chnk.atStr(valStr, rowIdx).toString());
                            } else if (chnk.vec().isUUID()) {
                                UUID uuid = new UUID(chnk.at16h(rowIdx), chnk.at16l(rowIdx));
                                ExternalFrameUtils.sendString(ab, uuid.toString());
                            } else {
                                assert false : "Can never be here";
                            }
                            break;
                    }
                }
            }
        }
        ExternalFrameUtils.writeToChannel(ab, channel);
    }
}
