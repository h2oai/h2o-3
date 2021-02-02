package hex.tree.isoforextended;

import hex.ModelBuilder;
import hex.ModelCategory;
import org.apache.log4j.Logger;
import water.*;
import water.fvec.Frame;
import water.util.*;

import java.util.Random;

/**
 * Extended isolation forest implementation. Algorithm comes from https://arxiv.org/pdf/1811.02141.pdf paper.
 *
 * @author Adam Valenta
 */
public class ExtendedIsolationForest extends ModelBuilder<ExtendedIsolationForestModel,
        ExtendedIsolationForestModel.ExtendedIsolationForestParameters,
        ExtendedIsolationForestModel.ExtendedIsolationForestOutput> {

    private static final Logger LOG = Logger.getLogger(ExtendedIsolationForest.class);

    transient IsolationTree[] _iTrees;
    transient Random _rand;

    // Called from an http request
    public ExtendedIsolationForest(ExtendedIsolationForestModel.ExtendedIsolationForestParameters parms) {
        super(parms);
        init(false);
    }

    public ExtendedIsolationForest(ExtendedIsolationForestModel.ExtendedIsolationForestParameters parms, Key<ExtendedIsolationForestModel> key) {
        super(parms, key);
        init(false);
    }

    public ExtendedIsolationForest(ExtendedIsolationForestModel.ExtendedIsolationForestParameters parms, Job job) {
        super(parms, job);
        init(false);
    }

    public ExtendedIsolationForest(boolean startup_once) {
        super(new ExtendedIsolationForestModel.ExtendedIsolationForestParameters(), startup_once);
    }

    @Override
    public void init(boolean expensive) {
        super.init(expensive);
        if (_parms.train() != null) {
            long extensionLevelMax = _parms.train().numCols() - 1;
            if (_parms.extension_level < 0 || _parms.extension_level > extensionLevelMax) {
                throw new IllegalStateException("Parameter extension_level must be in interval [0, "
                        + extensionLevelMax + "] but it is " + _parms.extension_level);
            }
            long sampleSizeMax = _parms.train().numRows();
            if (_parms._sample_size < 0 || _parms._sample_size > sampleSizeMax) {
                throw new IllegalStateException("Parameter sample_size must be in interval [0, "
                        + sampleSizeMax + "] but it is " + _parms._sample_size);
            }
        }
    }

    @Override
    protected Driver trainModelImpl() {
        return new ExtendedIsolationForestDriver();
    }

    @Override
    public ModelCategory[] can_build() {
        return new ModelCategory[]{
                ModelCategory.AnomalyDetection
        };
    }

    @Override
    public boolean isSupervised() {
        return false;
    }

    @Override
    public boolean havePojo() {
        return false;
    }

    @Override
    public boolean haveMojo() {
        return false;
    }

    protected void addCustomInfo(ExtendedIsolationForestModel.ExtendedIsolationForestOutput out) {
        if (_iTrees != null) {
            out.iTrees = _iTrees;
        }
    }

    private class ExtendedIsolationForestDriver extends Driver {

        @Override
        public void computeImpl() {
            buildIsolationTreeEnsemble();
        }

        private void buildIsolationTreeEnsemble() {
            _rand = RandomUtils.getRNG(_parms._seed);
            _iTrees = new IsolationTree[_parms._ntrees];
            ExtendedIsolationForestModel model = new ExtendedIsolationForestModel(dest(), _parms,
                    new ExtendedIsolationForestModel.ExtendedIsolationForestOutput(ExtendedIsolationForest.this));
            model.delete_and_lock(_job); // todo avalenta what is it good for?

            int heightLimit = (int) Math.ceil(MathUtils.log2(_parms._sample_size));

            for (int tid = 0; tid < _parms._ntrees; tid++) {
                int randomUnit = _rand.nextInt();
                Frame subSample = new SubSampleTask(_parms._sample_size, _parms._seed + randomUnit)
                        .doAll(_train.types(), _train.vecs()).outputFrame(null, _train.names(), _train.domains());
                double[][] subSampleArray = FrameUtils.asDoubles(subSample);

                Timer timer = new Timer();
                IsolationTree isolationTree = new IsolationTree(subSampleArray, heightLimit, _parms._seed + _rand.nextInt(), _parms.extension_level, tid);
                isolationTree.buildTreeRecursive();
                _iTrees[tid] = isolationTree;
                _job.update(1);
                LOG.info((tid + 1) + ". tree was built in " + timer.toString() + ". Free memory: " + PrettyPrint.bytes(H2O.CLOUD.free_mem()));
            }

            model.unlock(_job); // todo avalenta what is it good for?
            addCustomInfo(model._output);
        }
    }

}
