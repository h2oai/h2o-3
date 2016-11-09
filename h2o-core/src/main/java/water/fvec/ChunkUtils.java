package water.fvec;


import water.DKV;
import water.Key;

/**
 * Simple helper class which publishes some frame and chunk package private methods as public
 */
public class ChunkUtils {

    public static NewChunk[] createNewChunks(String name, byte[] vecTypes, int chunkId){
        return Frame.createNewChunks(name, vecTypes, chunkId);
    }

    public static void closeNewChunks(NewChunk[] nchks){
        Frame.closeNewChunks(nchks);
    }

    public static Chunk[] getChunks(Frame fr, int cidx) {
        Chunk[] chunks = new Chunk[fr.vecs().length];
        for(int i=0; i<fr.vecs().length; i++){
            chunks[i] = fr.vec(i).chunkForChunkIdx(cidx);
        }
       return chunks;
    }

    public static void initFrame(String keyName, String[] names) {
        Frame fr = new water.fvec.Frame(Key.<Frame>make(keyName));
        fr.preparePartialFrame(names);
        // Save it directly to DKV
        fr.update();
    }

    public static Frame finalizeFrame(String keyName, long[] rowsPerChunk, byte[] colTypes, String[][] colDomains){
        Frame fr = DKV.getGet(keyName);
        fr.finalizePartialFrame(rowsPerChunk, colDomains, colTypes);
        return fr;
    }

}
