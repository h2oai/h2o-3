package water.api;

import water.api.schemas3.FrameChunksV3;
import water.fvec.Frame;

public class FrameChunksHandler extends Handler {
    public FrameChunksV3 fetch(int version, FrameChunksV3 chunks) {
        Frame frame = FramesHandler.getFromDKV("key", chunks.frame_id.key());
        chunks.fillFromFrame(frame);
        return chunks;
    }
}
