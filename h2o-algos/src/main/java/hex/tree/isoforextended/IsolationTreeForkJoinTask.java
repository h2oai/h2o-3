package hex.tree.isoforextended;

import jsr166y.CountedCompleter;
import org.apache.log4j.Logger;
import water.H2O;
import water.Key;
import water.Scope;
import water.fvec.Frame;
import water.util.MathUtils;

class IsolationTreeForkJoinTask extends H2O.H2OCountedCompleter<IsolationTreeForkJoinTask> {
    private static final Logger LOG = Logger.getLogger(IsolationTreeForkJoinTask.class);

    private final ExtendedIsolationForest eif;
    private final Frame train;
    private IsolationTree iTree;
    private final int treeNum;

    public IsolationTreeForkJoinTask(ExtendedIsolationForest extendedIsolationForest, Frame train, int treeNum) {
        super();
        this.eif = extendedIsolationForest;
        this.train = train;
        this.treeNum = treeNum;
    }

    @Override
    public void compute2() {
        try {
            Scope.enter();
            int heightLimit = (int) Math.ceil(MathUtils.log2(eif._parms._sample_size));
            int randomUnit = eif._rand.nextInt();

            Frame subSample = new SubSampleTask(eif._parms._sample_size, eif._parms._seed + randomUnit)
                    .doAll(train.types(), train.vecs()).outputFrame(Key.make(), train.names(), train.domains());
            Scope.track(subSample);

//            iTree = new IsolationTree(subSample._key, heightLimit, eif._parms._seed + randomUnit, eif._parms.extension_level, treeNum);
//            iTree.buildTree();
//            if (LOG.isDebugEnabled()) {
//                iTree.logNodesNumRows();
//                iTree.logNodesHeight();
//            }
            tryComplete();
        } finally {
            Scope.exit();
        }
    }

    /**
     * Blocking call to obtain a result of computation.
     */
    public IsolationTree getResult() {
        join();
        return this.iTree;
    }

    @Override
    public void onCompletion(CountedCompleter caller) {
        eif._job.update(1);
        LOG.info("Tree " + treeNum + " is done.");
    }

    @Override
    public boolean onExceptionalCompletion(Throwable ex, CountedCompleter caller) {
        LOG.error(ex);
        return true;
    }
}
