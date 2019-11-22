package water.api.schemas3;

import water.Iced;
import water.fvec.Frame;
import water.api.API;
import water.fvec.Vec;

public class FrameChunksV3 extends SchemaV3<Iced, FrameChunksV3> {

    @API(help="ID of a given frame", required=true, direction=API.Direction.INOUT)
    public KeyV3.FrameKeyV3 frame_id;

    @API(help="Description of particular chunks", direction=API.Direction.OUTPUT)
    public FrameChunkV3[] chunks;
    
    public static class FrameChunkV3 extends SchemaV3<Iced, FrameChunkV3> {

        @API(help="An identifier unique in scope of a given frame", direction=API.Direction.OUTPUT)
        public int chunk_id;

        @API(help="Number of rows represented byt the chunk", direction=API.Direction.OUTPUT)
        public int row_count;
        
        @API(help="Index of H2O node where the chunk is located in", direction=API.Direction.OUTPUT)
        public int node_idx;

        public FrameChunkV3() {}
        
        public FrameChunkV3(int id, Vec vector) {
            this.chunk_id = id;
            this.row_count = vector.chunkLen(id);
            this.node_idx = vector.chunkKey(id).home_node().index();
        }
    }

    public FrameChunksV3 fillFromFrame(Frame frame) {
        this.frame_id = new KeyV3.FrameKeyV3(frame._key);
        Vec vector = frame.anyVec();
        this.chunks = new FrameChunkV3[vector.nChunks()];
        for(int i = 0; i < vector.nChunks(); i++) {
            this.chunks[i] = new FrameChunkV3(i, vector);
        }
        return this;
    }
}
