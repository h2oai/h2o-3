package water.fvec;

import water.DKV;
import water.Key;
import water.MRTask;

public class ShallowCopyVec extends WrappedVec {
    
    public ShallowCopyVec(Vec masterVec) {
        this(masterVec.group().addVec(), masterVec);
    }
    
    public ShallowCopyVec(Key<Vec> key, Vec masterVec) {
        super(key, masterVec._rowLayout, masterVec.domain(), masterVec._key);
        DKV.put(this);
        new MRTask(){
            @Override public void map(Chunk c){
                Chunk c2 = makeChunkForChunkIdx(c._cidx);
                DKV.put(chunkKey(c.cidx()), c2, _fs);
            }
        }.doAll(masterVec);
    }

    @Override
    public Chunk chunkForChunkIdx(int cidx) {
//        return chunkIdx(cidx).get();
        return makeChunkForChunkIdx(cidx);
    }
    
    private Chunk makeChunkForChunkIdx(int cidx) {
        Chunk masterChunk = masterVec().chunkForChunkIdx(cidx);
        Chunk clone = masterChunk.clone();
        clone.setVec(this);
        clone.reloadFromBytes(masterChunk.asBytes());
        return clone;
    }

}
