package hex.tree.isoforextended;

import hex.ModelBuilder;
import hex.ModelCategory;
import hex.tree.isoforextended.isolationtree.CompressedIsolationTree;
import hex.tree.isoforextended.isolationtree.IsolationTree;
import hex.tree.isoforextended.isolationtree.IsolationTreeStats;
import org.apache.log4j.Logger;
import water.DKV;
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
    public static final int MAX_NTREES = 100_000;
    public static final int MAX_SAMPLE_SIZE = 100_000;

    private ExtendedIsolationForestModel _model;
    transient Random _rand;
    transient IsolationTreeStats isolationTreeStats;

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
        int heightLimit = (int) Math.ceil(MathUtils.log2(_parms._sample_size));
        double numInnerNodes = Math.pow(2, heightLimit) - 1;
        double numLeafNodes = Math.pow(2, heightLimit);
        double sizeOfInnerNode = 2 * _train.numCols() * Double.BYTES;
        double sizeOfLeafNode = Integer.BYTES;
        long maxMem = H2O.SELF._heartbeat.get_free_mem();

        // IsolationTree is sparse for large data, count only with 25% of the full tree
        double oneTree = 0.25 * numInnerNodes * sizeOfInnerNode + numLeafNodes * sizeOfLeafNode;
        long estimatedMemory = (long) (_parms._ntrees * oneTree);
        long estimatedComputingMemory = 5 * estimatedMemory;
        if (estimatedComputingMemory > H2O.SELF._heartbeat.get_free_mem() || estimatedComputingMemory < 0 /* long overflow **/) {
            String msg = "Extended Isolation Forest computation won't fit in the driver node's memory ("
                    + PrettyPrint.bytes(estimatedComputingMemory) + " > " + PrettyPrint.bytes(maxMem)
                    + ") - try reducing the number of columns and/or the number of trees and/or the sample_size parameter. "
                    + "You can disable memory check by setting the attribute " + H2O.OptArgs.SYSTEM_PROP_PREFIX + "debug.noMemoryCheck.";
            error("_train", msg);
        }
    }

    @Override
    public void init(boolean expensive) {
        super.init(expensive);
        if (_parms.train() != null) {
            if (expensive) { // because e.g. OneHotExplicit categorical encoding can change the dimension
                long extensionLevelMax = _train.numCols() - 1;
                if (_parms._extension_level < 0 || _parms._extension_level > extensionLevelMax) {
                    error("extension_level", "Parameter extension_level must be in interval [0, "
                            + extensionLevelMax + "] but it is " + _parms._extension_level);
                }
            }
            long sampleSizeMax = _parms.train().numRows();
            if (_parms._sample_size < 2 || _parms._sample_size > MAX_SAMPLE_SIZE || _parms._sample_size > sampleSizeMax) {
                error("sample_size","Parameter sample_size must be in interval [2, "
                        + MAX_SAMPLE_SIZE + "] but it is " + _parms._sample_size);
            }
            if(_parms._ntrees < 1 || _parms._ntrees > MAX_NTREES)
                error("ntrees", "Parameter ntrees must be in interval [1, "
                        + MAX_NTREES + "] but it is " + _parms._ntrees);
        }
        if (expensive && error_count() == 0) checkMemoryFootPrint();
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
            _model = null;
            try {
                init(true);
                if(error_count() > 0) {
                    throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(ExtendedIsolationForest.this);
                }
                _rand = RandomUtils.getRNG(_parms._seed);
                isolationTreeStats = new IsolationTreeStats();
                _model = new ExtendedIsolationForestModel(dest(), _parms,
                        new ExtendedIsolationForestModel.ExtendedIsolationForestOutput(ExtendedIsolationForest.this));
                _model.delete_and_lock(_job);
                buildIsolationTreeEnsemble();
                _model._output._model_summary = createModelSummaryTable();
                LOG.info(_model.toString());
            } finally {
                if(_model != null)
                    _model.unlock(_job);
            }
        }

        private void buildIsolationTreeEnsemble() {
            _model._output._iTreeKeys = new Key[_parms._ntrees];

            int heightLimit = (int) Math.ceil(MathUtils.log2(_parms._sample_size));

            IsolationTree isolationTree = new IsolationTree(heightLimit, _parms._extension_level);
            for (int tid = 0; tid < _parms._ntrees; tid++) {
                Timer timer = new Timer();
                Frame subSample = _train.deepSlice(_rand.longs(0, _train.numRows()).distinct().limit(_parms._sample_size).toArray(), null);
                double[][] subSampleArray = FrameUtils.asDoubles(subSample);
                CompressedIsolationTree compressedIsolationTree = isolationTree.buildTree(subSampleArray, _parms._seed + _rand.nextInt(), tid);
                if (LOG.isDebugEnabled()) {
                    isolationTree.logNodesNumRows();
                    isolationTree.logNodesHeight();
                }
                _model._output._iTreeKeys[tid] = compressedIsolationTree._key;
                DKV.put(compressedIsolationTree);
                _job.update(1);
                _model.update(_job);
                LOG.info((tid + 1) + ". tree was built in " + timer.toString());
                isolationTreeStats.updateBy(isolationTree);
            }
        }
    }

    public TwoDimTable createModelSummaryTable() {
        List<String> colHeaders = new ArrayList<>();
        List<String> colTypes = new ArrayList<>();
        List<String> colFormat = new ArrayList<>();

        colHeaders.add("Number of Trees"); colTypes.add("int"); colFormat.add("%d");
        colHeaders.add("Size of Subsample"); colTypes.add("int"); colFormat.add("%d");
        colHeaders.add("Extension Level"); colTypes.add("int"); colFormat.add("%d");
        colHeaders.add("Seed"); colTypes.add("long"); colFormat.add("%d");

        colHeaders.add("Number of trained trees"); colTypes.add("long"); colFormat.add("%d");
        colHeaders.add("Min. Depth"); colTypes.add("long"); colFormat.add("%d");
        colHeaders.add("Max. Depth"); colTypes.add("long"); colFormat.add("%d");
        colHeaders.add("Mean Depth"); colTypes.add("float"); colFormat.add("%d");
        colHeaders.add("Min. Leaves"); colTypes.add("long"); colFormat.add("%d");
        colHeaders.add("Max. Leaves"); colTypes.add("long"); colFormat.add("%d");
        colHeaders.add("Mean Leaves"); colTypes.add("float"); colFormat.add("%d");
        colHeaders.add("Min. Isolated Point"); colTypes.add("long"); colFormat.add("%d");
        colHeaders.add("Max. Isolated Point"); colTypes.add("long"); colFormat.add("%d");
        colHeaders.add("Mean Isolated Point"); colTypes.add("float"); colFormat.add("%d");
        colHeaders.add("Min. Not Isolated Point"); colTypes.add("long"); colFormat.add("%d");
        colHeaders.add("Max. Not Isolated Point"); colTypes.add("long"); colFormat.add("%d");
        colHeaders.add("Mean Not Isolated Point"); colTypes.add("float"); colFormat.add("%d");
        colHeaders.add("Min. Zero Splits"); colTypes.add("long"); colFormat.add("%d");
        colHeaders.add("Max. Zero Splits"); colTypes.add("long"); colFormat.add("%d");
        colHeaders.add("Mean Zero Splits"); colTypes.add("float"); colFormat.add("%d");

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
        table.set(row, col++, _parms._extension_level);
        table.set(row, col++, _parms._seed);
        table.set(row, col++, isolationTreeStats._numTrees);
        table.set(row, col++, isolationTreeStats._minDepth);
        table.set(row, col++, isolationTreeStats._maxDepth);
        table.set(row, col++, isolationTreeStats._meanDepth);
        table.set(row, col++, isolationTreeStats._minLeaves);
        table.set(row, col++, isolationTreeStats._maxLeaves);
        table.set(row, col++, isolationTreeStats._meanLeaves);
        table.set(row, col++, isolationTreeStats._minIsolated);
        table.set(row, col++, isolationTreeStats._maxIsolated);
        table.set(row, col++, isolationTreeStats._meanIsolated);
        table.set(row, col++, isolationTreeStats._minNotIsolated);
        table.set(row, col++, isolationTreeStats._maxNotIsolated);
        table.set(row, col++, isolationTreeStats._meanNotIsolated);
        table.set(row, col++, isolationTreeStats._minZeroSplits);
        table.set(row, col++, isolationTreeStats._maxZeroSplits);
        table.set(row, col, isolationTreeStats._meanZeroSplits);
        return table;
    }

}
