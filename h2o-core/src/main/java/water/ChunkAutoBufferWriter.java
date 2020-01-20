package water;

import water.fvec.Chunk;
import water.fvec.ChunkUtils;
import water.fvec.Frame;
import water.parser.BufferedString;

import java.io.Closeable;
import java.io.OutputStream;
import java.util.UUID;

import static water.ExternalFrameUtils.*;
import static water.ExternalFrameUtils.EXPECTED_STRING;

public final class ChunkAutoBufferWriter implements Closeable  {
    
    private final AutoBuffer buffer;
    
    public ChunkAutoBufferWriter(OutputStream outputStream) {
        this.buffer = new AutoBuffer(outputStream, false);
    }

    public void writeChunk(String frameName, int chunkId, byte[] expectedTypes, int[] selectedColumnIndices) {
        final Frame frame = DKV.getGet(frameName);
        final Chunk[] chunks = ChunkUtils.getChunks(frame, chunkId);
        final int numberOfRows = chunks[0]._len;
        ExternalFrameUtils.sendInt(buffer, numberOfRows);

        // buffered string to be reused for strings to avoid multiple allocation in the loop
        final BufferedString valStr = new BufferedString();
        for (int rowIdx = 0; rowIdx < numberOfRows; rowIdx++) {
            for(int i = 0; i < selectedColumnIndices.length; i++){
                if (chunks[selectedColumnIndices[i]].isNA(rowIdx)) {
                    ExternalFrameUtils.sendNA(buffer, expectedTypes[i]);
                } else {
                    final Chunk chnk = chunks[selectedColumnIndices[i]];
                    switch (expectedTypes[i]) {
                        case EXPECTED_BOOL:
                            ExternalFrameUtils.sendBoolean(buffer, (byte)chnk.at8(rowIdx));
                            break;
                        case EXPECTED_BYTE:
                            ExternalFrameUtils.sendByte(buffer, (byte)chnk.at8(rowIdx));
                            break;
                        case EXPECTED_CHAR:
                            ExternalFrameUtils.sendChar(buffer, (char)chnk.at8(rowIdx));
                            break;
                        case EXPECTED_SHORT:
                            ExternalFrameUtils.sendShort(buffer, (short)chnk.at8(rowIdx));
                            break;
                        case EXPECTED_INT:
                            ExternalFrameUtils.sendInt(buffer, (int)chnk.at8(rowIdx));
                            break;
                        case EXPECTED_FLOAT:
                            ExternalFrameUtils.sendFloat(buffer, (float)chnk.atd(rowIdx));
                            break;
                        case EXPECTED_LONG:
                            ExternalFrameUtils.sendLong(buffer, chnk.at8(rowIdx));
                            break;
                        case EXPECTED_DOUBLE:
                            ExternalFrameUtils.sendDouble(buffer, chnk.atd(rowIdx));
                            break;
                        case EXPECTED_TIMESTAMP:
                            ExternalFrameUtils.sendTimestamp(buffer, chnk.at8(rowIdx));
                            break;
                        case EXPECTED_STRING:
                            String string = null;
                            if (chnk.vec().isCategorical()) {
                                string = chnk.vec().domain()[(int) chnk.at8(rowIdx)];
                            } else if (chnk.vec().isString()) {
                                string = chnk.atStr(valStr, rowIdx).toString();
                            } else if (chnk.vec().isUUID()) {
                                string = new UUID(chnk.at16h(rowIdx), chnk.at16l(rowIdx)).toString();
                            } else {
                                assert false : "Can never be here";
                            }
                            ExternalFrameUtils.sendString(buffer, string);
                            break;
                    }
                }
            }
        }
    }

    @Override
    public void close() {
        buffer.close();
    }
}
