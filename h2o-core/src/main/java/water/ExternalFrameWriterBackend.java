package water;

import water.fvec.ChunkUtils;
import water.fvec.NewChunk;

import java.io.IOException;
import java.nio.channels.SocketChannel;

import static water.ExternalFrameUtils.*;

/**
 * This class contains methods used on the h2o backend to store incoming data as H2O Frame
 */
final class ExternalFrameWriterBackend {
    /**
     * Internal method use on the h2o backend side to handle writing to the chunk from non-h2o environment
     * @param sock socket channel originating from non-h2o node
     * @param ab {@link AutoBuffer} containing information necessary for preparing backend for writing
     */
    static void handleWriteToChunk(SocketChannel sock, AutoBuffer ab) throws IOException {
        String frameKey = ab.getStr();
        byte[] expectedTypes = ab.getA1();
        assert expectedTypes != null;
        byte[] vecTypes = vecTypesFromExpectedTypes(expectedTypes);
        int expectedNumRows = ab.getInt();
        int currentRowIdx = 0;
        int chunk_id = ab.getInt();
        NewChunk[] nchnk = ChunkUtils.createNewChunks(frameKey, vecTypes, chunk_id);
        assert nchnk != null;
        while (currentRowIdx < expectedNumRows) {
            for(int colIdx = 0; colIdx<expectedTypes.length; colIdx++){
                switch (expectedTypes[colIdx]) {
                    case EXPECTED_BOOL: // fall through to byte since BOOL is internally stored in frame as number (byte)
                    case EXPECTED_BYTE:
                        store(ab, nchnk[colIdx], ab.get1());
                        break;
                    case EXPECTED_CHAR:
                        store(ab, nchnk[colIdx], ab.get2());
                        break;
                    case EXPECTED_SHORT:
                        store(ab, nchnk[colIdx], ab.get2s());
                        break;
                    case EXPECTED_INT:
                        store(ab, nchnk[colIdx], ab.getInt());
                        break;
                    case EXPECTED_TIMESTAMP: // fall through to long since TIMESTAMP is internally stored in frame as long
                    case EXPECTED_LONG:
                        store(ab, nchnk[colIdx], ab.get8());
                        break;
                    case EXPECTED_FLOAT:
                        store(nchnk[colIdx], ab.get4f());
                        break;
                    case EXPECTED_DOUBLE:
                        store(nchnk[colIdx], ab.get8d());
                        break;
                    case EXPECTED_STRING:
                        store(ab, nchnk[colIdx], ab.getStr());
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown expected type: " + expectedTypes[colIdx]);
                }
            }
            currentRowIdx++;
        }
        // close chunks at the end
        ChunkUtils.closeNewChunks(nchnk);

        // Flag informing sender that all work is done and
        // chunks are ready to be finalized.
        //
        // This also needs to be sent because in the sender we have to
        // wait for all chunks to be written to DKV; otherwise we get race during finalizing and
        // it happens that we try to finalize frame with chunks not ready yet
        AutoBuffer outputAb = new AutoBuffer();
        outputAb.put1(ExternalFrameHandler.CONFIRM_WRITING_DONE);
        writeToChannel(outputAb, sock);
    }

    private static void store(AutoBuffer ab, NewChunk chunk, long data){
        if(isNA(ab, data)){
            chunk.addNA();
        }else{
            chunk.addNum(data);
        }
    }

    private static void store(NewChunk chunk, double data){
        if(isNA(data)){
            chunk.addNA();
        }else{
            chunk.addNum(data);
        }
    }

    private static void store(AutoBuffer ab, NewChunk chunk, String data){
        if(isNA(ab, data)){
            chunk.addNA();
        }else{
            chunk.addStr(data);
        }
    }
}
