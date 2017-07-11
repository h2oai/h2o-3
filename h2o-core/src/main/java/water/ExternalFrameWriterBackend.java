package water;

import water.fvec.ChunkUtils;
import water.fvec.NewChunk;

import java.io.IOException;
import java.nio.channels.ByteChannel;

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
    static void handleWriteToChunk(ByteChannel sock, AutoBuffer ab) throws IOException {
        String frameKey = ab.getStr();
        byte[] expectedTypes = ab.getA1();
        if( expectedTypes == null){
          throw new RuntimeException("Expected types can't be null.");
        }
        int[] maxVecSizes = ab.getA4();

        int[] elemSizes = ExternalFrameUtils.getElemSizes(expectedTypes, maxVecSizes);
        int[] startPos = ExternalFrameUtils.getStartPositions(elemSizes);
        byte[] vecTypes = vecTypesFromExpectedTypes(expectedTypes, maxVecSizes);
        int expectedNumRows = ab.getInt();
        int currentRowIdx = 0;
        int chunk_id = ab.getInt();
        NewChunk[] nchnk = ChunkUtils.createNewChunks(frameKey, vecTypes, chunk_id);
        assert nchnk != null;
        while (currentRowIdx < expectedNumRows) {
            for(int typeIdx = 0; typeIdx < expectedTypes.length; typeIdx++){
                switch (expectedTypes[typeIdx]) {
                    case EXPECTED_BOOL: // fall through to byte since BOOL is internally stored in frame as number (byte)
                    case EXPECTED_BYTE:
                        store(ab, nchnk[startPos[typeIdx]], ab.get1());
                        break;
                    case EXPECTED_CHAR:
                        store(ab, nchnk[startPos[typeIdx]], ab.get2());
                        break;
                    case EXPECTED_SHORT:
                        store(ab, nchnk[startPos[typeIdx]], ab.get2s());
                        break;
                    case EXPECTED_INT:
                        store(ab, nchnk[startPos[typeIdx]], ab.getInt());
                        break;
                    case EXPECTED_TIMESTAMP: // fall through to long since TIMESTAMP is internally stored in frame as long
                    case EXPECTED_LONG:
                        store(ab, nchnk[startPos[typeIdx]], ab.get8());
                        break;
                    case EXPECTED_FLOAT:
                        store(nchnk[startPos[typeIdx]], ab.get4f());
                        break;
                    case EXPECTED_DOUBLE:
                        store(nchnk[startPos[typeIdx]], ab.get8d());
                        break;
                    case EXPECTED_STRING:
                        store(ab, nchnk[startPos[typeIdx]], ab.getStr());
                        break;
                    case EXPECTED_VECTOR:
                        storeVector(ab, nchnk, elemSizes[typeIdx], startPos[typeIdx]);
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown expected type: " + expectedTypes[typeIdx]);
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

    private static void storeVector(AutoBuffer ab, NewChunk[] nchnk, int maxVecSize, int startPos){
      boolean isSparse = ab.getZ();
      if(isSparse){
        int[] indices = ab.getA4();
        double[] values = ab.getA8d();

        if(values == null){
          throw new RuntimeException("Values of sparse Vector can't be null!");
        }
        if(indices == null){
          throw new RuntimeException("Indices of sparse Vector can't be null!");
        }

        // store values
        int zeroSectionStart = 0;
        for(int i = 0; i < indices.length; i++){
          for(int zeroIdx = zeroSectionStart; zeroIdx < indices[i]; zeroIdx++ ){
            store(nchnk[startPos + zeroIdx], 0);
          }
          store(nchnk[startPos + indices[i]], values[i]);
          zeroSectionStart = indices[i] + 1;
        }

        // fill remaining zeros
        int lastIdx = indices.length == 0 ? 0 : indices[indices.length - 1];
        for(int j = lastIdx; j< maxVecSize; j++) {
          store(nchnk[startPos + j], 0);
        }
      } else {
        double[] values = ab.getA8d();
        if(values == null){
          throw new RuntimeException("Values of dense Vector can't be null!");
        }
        // fill values
        for(int j = 0; j< values.length; j++){
          store(nchnk[startPos + j], values[j]);
        }

        // fill remaining zeros
        for(int j = values.length; j < maxVecSize; j++){
          store(nchnk[startPos + j], 0);
        }
      }
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
