package hex.tree.isoforextended;

import hex.ModelBuilder;
import hex.ModelCategory;
import hex.tree.isoforextended.isolationtree.CompressedIsolationTree;
import hex.tree.isoforextended.isolationtree.IsolationTree;
import org.apache.log4j.Logger;
import water.H2O;
import water.Job;
import water.Key;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Frame;
import water.util.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
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
            if(_parms._ntrees < 0 || _parms._ntrees > MAX_NTREES)
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
            init(true);
            if(error_count() > 0)
                throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(ExtendedIsolationForest.this);
            buildIsolationTreeEnsemble();
        }

        private void buildIsolationTreeEnsemble() {
            _rand = RandomUtils.getRNG(_parms._seed);
            ExtendedIsolationForestModel model = new ExtendedIsolationForestModel(dest(), _parms,
                    new ExtendedIsolationForestModel.ExtendedIsolationForestOutput(ExtendedIsolationForest.this));
            model.delete_and_lock(_job); // todo valenad what is it good for?
            model._output._iTrees = new CompressedIsolationTree[_parms._ntrees];

            int heightLimit = (int) Math.ceil(MathUtils.log2(_parms._sample_size));

            long modelsize = 0;
            long treeSize = 0;
            long startsWithMemory = H2O.CLOUD.free_mem();
            for (int tid = 0; tid < _parms._ntrees; tid++) {
                Timer timer = new Timer();
                int randomUnit = _rand.nextInt();
                Frame subSample = SamplingUtils.sampleOfFixedSize(_train, _parms._sample_size, _parms._seed + randomUnit);
                double[][] subSampleArray = FrameUtils.asDoubles(subSample);

                IsolationTree isolationTree = new IsolationTree(subSampleArray, heightLimit, _parms._seed + _rand.nextInt(), _parms._extension_level, tid);
                model._output._iTrees[tid] = isolationTree.buildTree();
                int treeSizeL = convertToBytes(isolationTree).length;
                LOG.info("Tree size: " + PrettyPrint.bytes(treeSizeL));
                _job.update(1);
                modelsize += convertToBytes(model._output._iTrees[tid]).length;
                treeSize += treeSizeL;
                isolationTree.nodesTotalSize();
                if (startsWithMemory < H2O.CLOUD.free_mem()) {
                    startsWithMemory = H2O.CLOUD.free_mem();
                }
                LOG.info((tid + 1) + ". tree was built in " + timer.toString() + ". Free memory: " + PrettyPrint.bytes(H2O.CLOUD.free_mem()));
            }

            LOG.info("Model size: " + PrettyPrint.bytes(modelsize));
            LOG.info("Trees size average: " + PrettyPrint.bytes(treeSize/_parms._ntrees));
            LOG.info("Trees total size: " + PrettyPrint.bytes(treeSize));
            LOG.info("Starts with mem: " + PrettyPrint.bytes(startsWithMemory) + " Ends with mem: " + PrettyPrint.bytes(H2O.CLOUD.free_mem()) + " Real memory usage: " + PrettyPrint.bytes(startsWithMemory - H2O.CLOUD.free_mem()));
            LOG.info("Estimation memory usage: " + PrettyPrint.bytes(treeSize));
            
            model.unlock(_job); // todo valenad what is it good for?
            model._output._model_summary = createModelSummaryTable();
        }
    }

    public TwoDimTable createModelSummaryTable() {
        List<String> colHeaders = new ArrayList<>();
        List<String> colTypes = new ArrayList<>();
        List<String> colFormat = new ArrayList<>();

        colHeaders.add("Number of Trees"); colTypes.add("int"); colFormat.add("%d");
        colHeaders.add("Size of Subsample"); colTypes.add("int"); colFormat.add("%d");
        colHeaders.add("Extension Level"); colTypes.add("int"); colFormat.add("%d");

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

    private byte[] convertToBytes(Object object){
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(object);
            return bos.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new byte[0];
    }
    
}
