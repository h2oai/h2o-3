package hex.tree.isoforextended;

import hex.ModelBuilder;
import hex.ModelCategory;
import org.apache.log4j.Logger;
import water.H2O;
import water.Job;
import water.Key;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Frame;
import water.util.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Extended isolation forest implementation. Algorithm comes from https://arxiv.org/pdf/1811.02141.pdf paper.
 *
 * @author Adam Valenta
 */
public class ExtendedIsolationForest extends ModelBuilder<ExtendedIsolationForestModel,
        ExtendedIsolationForestModel.ExtendedIsolationForestParameters,
        ExtendedIsolationForestModel.ExtendedIsolationForestOutput> {

    transient private static final Logger LOG = Logger.getLogger(ExtendedIsolationForest.class);
    public static final int MAX_NTREES = 100000; // todo valenad consult the size

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
    protected void checkMemoryFootPrint_impl() {
        // TODO valenad implement memory check
    }

    @Override
    public void init(boolean expensive) {
        super.init(expensive);
        if (_parms.train() != null) {
            long extensionLevelMax = _parms.train().numCols() - 1;
            if (_parms._extension_level < 0 || _parms._extension_level > extensionLevelMax) {
                error("extension_level", "Parameter extension_level must be in interval [0, "
                        + extensionLevelMax + "] but it is " + _parms._extension_level);
            }
            long sampleSizeMax = _parms.train().numRows();
            if (_parms._sample_size < 0 || _parms._sample_size > sampleSizeMax) {
                error("sample_size","Parameter sample_size must be in interval [0, "
                        + sampleSizeMax + "] but it is " + _parms._sample_size);
            }
            if( _parms._ntrees < 0 || _parms._ntrees > MAX_NTREES)
                error("ntrees", "Parameter ntrees must be in interval [1, "
                        + MAX_NTREES + "] but it is " + _parms._ntrees);
        }
        checkMemoryFootPrint();
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

    private class ExtendedIsolationForestDriver extends Driver {

        @Override
        public void computeImpl() {
            init(true);
            if( error_count() > 0 )
                throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(ExtendedIsolationForest.this);
            buildIsolationTreeEnsemble();
        }

        private void buildIsolationTreeEnsemble() {
            _rand = RandomUtils.getRNG(_parms._seed);
            ExtendedIsolationForestModel model = new ExtendedIsolationForestModel(dest(), _parms,
                    new ExtendedIsolationForestModel.ExtendedIsolationForestOutput(ExtendedIsolationForest.this));
            model.delete_and_lock(_job); // todo valenad what is it good for?
            model._output._iTrees = new IsolationTree[_parms._ntrees];

            int heightLimit = (int) Math.ceil(MathUtils.log2(_parms._sample_size));

            for (int tid = 0; tid < _parms._ntrees; tid++) {
                int randomUnit = _rand.nextInt();
                Frame subSample = new SubSampleTask(_parms._sample_size, _parms._seed + randomUnit)
                        .doAll(_train.types(), _train.vecs()).outputFrame(null, _train.names(), _train.domains());
                double[][] subSampleArray = FrameUtils.asDoubles(subSample);

                Timer timer = new Timer();
                IsolationTree isolationTree = new IsolationTree(subSampleArray, heightLimit, _parms._seed + _rand.nextInt(), _parms._extension_level, tid);
                isolationTree.buildTree();
                model._output._iTrees[tid] = isolationTree;
                _job.update(1);
                LOG.info((tid + 1) + ". tree was built in " + timer.toString() + ". Free memory: " + PrettyPrint.bytes(H2O.CLOUD.free_mem()));
            }

            model.unlock(_job); // todo valenad what is it good for?
            model._output._model_summary = createModelSummaryTable();
        }
    }

    public TwoDimTable createModelSummaryTable() {
        List<String> colHeaders = new ArrayList<>();
        List<String> colTypes = new ArrayList<>();
        List<String> colFormat = new ArrayList<>();

        colHeaders.add("Number of Trees"); colTypes.add("long"); colFormat.add("%d");
        colHeaders.add("Size of Subsample"); colTypes.add("long"); colFormat.add("%d");
        colHeaders.add("Extension Level"); colTypes.add("long"); colFormat.add("%d");

        final int rows = 1;
        TwoDimTable table = new TwoDimTable(
                "Model Summary", null,
                new String[rows],
                colHeaders.toArray(new String[0]),
                colTypes.toArray(new String[0]),
                colFormat.toArray(new String[0]),
                "");
        int row = 0;
        int col = 0;
        table.set(row, col++, _parms._ntrees);
        table.set(row, col++, _parms._sample_size);
        table.set(row, col, _parms._extension_level);
        return table;
    }

}
