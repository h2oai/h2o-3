package water.fvec.persist;

import jsr166y.CountedCompleter;
import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.FileUtils;

import java.net.URI;

import static water.fvec.persist.PersistUtils.*;

public class FramePersist {
    
    static {
        // make sure FrameMeta is registered in TypeMap
        TypeMap.onIce(FrameMeta.class.getName());
    }

    private final Frame frame;

    public FramePersist(Frame frame) {
        this.frame = frame;
    }

    private static class FrameMeta extends Iced<FrameMeta> {
        Key<Frame> key;
        String[] names;
        Vec[] vecs;
        long[] espc;
        int numNodes;

        FrameMeta(Frame f) {
            key = f._key;
            names = f.names();
            vecs = f.vecs();
            espc = f.anyVec().espc();
            numNodes = H2O.CLOUD.size();
        }
    }

    private static URI getMetaUri(Key key, String dest) {
        return FileUtils.getURI(dest + "/" + key);
    }

    private static URI getDataUri(String metaUri, int cidx) {
        return FileUtils.getURI(metaUri + "_n" + H2O.SELF.index() + "_c" + cidx);
    }

    public Job<Frame> saveTo(String uri, boolean overwrite) {
        URI metaUri = getMetaUri(frame._key, sanitizeUri(uri));
        if (exists(metaUri) && !overwrite) {
            throw new IllegalArgumentException("File already exists at " + metaUri);
        }
        FrameMeta frameMeta = new FrameMeta(frame);
        write(metaUri, ab -> {
            ab.put(frameMeta);
        });
        Job<Frame> job = new Job<>(frame._key, "water.fvec.Frame", "Save frame");
        return job.start(new SaveFrameDriver(job, frame, metaUri), frame.anyVec().nChunks());
    }

    public static class SaveFrameDriver extends H2O.H2OCountedCompleter<LoadFrameDriver> {

        private final Job<Frame> job;
        private final Frame frame;
        private final URI metaUri;

        public SaveFrameDriver(
            Job<Frame> job, 
            Frame frame,
            URI metaUri
        ) {
            this.job = job;
            this.frame = frame;
            this.metaUri = metaUri;
        }

        @Override
        public void compute2() {
            frame.read_lock(job._key);
            new SaveChunksTask(job, metaUri.toString()).doAll(frame).join();
            tryComplete();
        }

        @Override
        public void onCompletion(CountedCompleter caller) {
            frame.unlock(job);
        }

        @Override
        public boolean onExceptionalCompletion(Throwable t, CountedCompleter caller) {
            frame.unlock(job);
            return super.onExceptionalCompletion(t, caller);
        }
    }

    static class SaveChunksTask extends MRTask<SaveChunksTask> {

        private final Job<Frame> job;
        private final String metaUri;

        SaveChunksTask(Job<Frame> job, String metaUri) {
            this.job = job;
            this.metaUri = metaUri;
        }

        @Override
        public void map(Chunk[] cs) {
            PersistUtils.write(getDataUri(metaUri, cs[0].cidx()), ab -> writeChunks(ab, cs));
            job.update(1);
        }

        private void writeChunks(AutoBuffer autoBuffer, Chunk[] chunks) {
            for (Chunk c : chunks) {
                autoBuffer.put(c);
            }
        }
    }

    public static Job<Frame> loadFrom(Key<Frame> key, String uri) {
        URI metaUri = getMetaUri(key, sanitizeUri(uri));
        FrameMeta meta = read(metaUri, AutoBuffer::get);
        if (meta.numNodes != H2O.CLOUD.size()) {
            throw new IllegalArgumentException("To load this frame a cluster with " + meta.numNodes + " nodes is needed.");
        }
        Job<Frame> job = new Job<>(meta.key, "water.fvec.Frame", "Load frame");
        return job.start(new LoadFrameDriver(job, metaUri.toString(), meta), meta.espc.length-1);
    }

    public static class LoadFrameDriver extends H2O.H2OCountedCompleter<LoadFrameDriver> {

        private final Job<Frame> job;
        private final String metaUri;
        private final FrameMeta meta;

        public LoadFrameDriver(
            Job<Frame> job,
            String metaUri, 
            FrameMeta meta
        ) {
            this.job = job;
            this.metaUri = metaUri;
            this.meta = meta;
        }

        @Override
        public void compute2() {
            Vec con = null;
            try {
                long nrow = meta.espc[meta.espc.length-1];
                int nchunk = meta.espc.length-1;
                con = Vec.makeConN(nrow, nchunk);
                new LoadChunksTask(job, metaUri, meta.vecs).doAll(con).join();
            } finally {
                if (con != null) con.remove();
            }
            Futures fs = new Futures();
            int rowLayout = Vec.ESPC.rowLayout(meta.vecs[0]._key, meta.espc);
            for (Vec v : meta.vecs) {
                v._rowLayout = rowLayout;
                DKV.put(v, fs);
            }
            fs.blockForPending();
            Frame frame = new Frame(meta.key, meta.names, meta.vecs);
            DKV.put(frame);
            tryComplete();
        }

    }

    static class LoadChunksTask extends MRTask<LoadChunksTask> {

        private final Job<Frame> job;
        private final String metaUri;
        private final Vec[] vecs;

        LoadChunksTask(Job<Frame> job, String metaUri, Vec[] vecs) {
            this.job = job;
            this.metaUri = metaUri;
            this.vecs = vecs;
        }

        @Override
        public void map(Chunk c) {
            PersistUtils.read(getDataUri(metaUri, c.cidx()), ab -> readChunks(ab, c.cidx()));
            job.update(1);
            
        }

        private int readChunks(AutoBuffer autoBuffer, int cidx) {
            for (Vec v : vecs) {
                Key chunkKey = v.chunkKey(cidx);
                Chunk chunk = autoBuffer.get();
                DKV.put(chunkKey, new Value(chunkKey, chunk));
            }
            return vecs.length;
        }

    }

}
