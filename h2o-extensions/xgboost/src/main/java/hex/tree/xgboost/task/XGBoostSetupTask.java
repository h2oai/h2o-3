package hex.tree.xgboost.task;

import hex.tree.xgboost.BoosterParms;
import hex.tree.xgboost.matrix.MatrixLoader;
import ai.h2o.xgboost4j.java.DMatrix;
import ai.h2o.xgboost4j.java.XGBoostError;
import org.apache.log4j.Logger;
import water.H2O;
import water.Key;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.IcedHashMapGeneric;

import java.io.File;
import java.util.Map;

/**
 * Initializes XGBoost training (converts Frame to set of node-local DMatrices)
 */
public class XGBoostSetupTask extends AbstractXGBoostTask<XGBoostSetupTask> {

  private static final Logger LOG = Logger.getLogger(XGBoostSetupTask.class);

  private final BoosterParms _boosterParms;
  private final byte[] _checkpoint;
  private final IcedHashMapGeneric.IcedHashMapStringString _rabitEnv;
  private final MatrixLoader _matrixLoader;
  private final String _saveMatrixDirectory;

  public XGBoostSetupTask(
      Key modelKey, String saveMatrixDirectory, BoosterParms boosterParms,
      byte[] checkpointToResume, Map<String, String> rabitEnv, boolean[] nodes,
      MatrixLoader matrixLoader
  ) {
    super(modelKey, nodes);
    _boosterParms = boosterParms;
    _checkpoint = checkpointToResume;
    _matrixLoader = matrixLoader;
    _saveMatrixDirectory = saveMatrixDirectory;
    (_rabitEnv = new IcedHashMapGeneric.IcedHashMapStringString()).putAll(rabitEnv);
  }

  @Override
  protected void execute() {
    DMatrix matrix;
    try {
      matrix = _matrixLoader.makeLocalMatrix().get();
    } catch (XGBoostError e) {
      throw new IllegalStateException("Failed to create XGBoost DMatrix", e);
    }
    if (_saveMatrixDirectory != null) {
      File directory = new File(_saveMatrixDirectory);
      if (directory.mkdirs()) {
        LOG.debug("Created directory for matrix export: " + directory.getAbsolutePath());
      }
      File path = new File(directory, "matrix.part" + H2O.SELF.index());
      LOG.info("Saving node-local portion of XGBoost training dataset to " + path.getAbsolutePath() + ".");
      matrix.saveBinary(path.getAbsolutePath());
    }
    _rabitEnv.put("DMLC_TASK_ID", String.valueOf(H2O.SELF.index()));

    XGBoostUpdater thread = XGBoostUpdater.make(_modelKey, matrix, _boosterParms, _checkpoint, _rabitEnv);
    thread.start(); // we do not need to wait for the Updater to init Rabit - subsequent tasks will wait
  }

  /**
   * Finds what nodes actually do carry some of data of a given Frame
   * @param fr frame to find nodes for
   * @return FrameNodes
   */
  public static FrameNodes findFrameNodes(Frame fr) {
    // Count on how many nodes the data resides
    boolean[] nodesHoldingFrame = new boolean[H2O.CLOUD.size()];
    Vec vec = fr.anyVec();
    for(int chunkNr = 0; chunkNr < vec.nChunks(); chunkNr++) {
      int home = vec.chunkKey(chunkNr).home_node().index();
      if (! nodesHoldingFrame[home])
        nodesHoldingFrame[home] = true;
    }
    return new FrameNodes(fr, nodesHoldingFrame);
  }

  public static class FrameNodes {
    public final Frame _fr;
    public final boolean[] _nodes;
    public final int _numNodes;
    private FrameNodes(Frame fr, boolean[] nodes) {
      _fr = fr;
      _nodes = nodes;
      int n = 0;
      for (boolean f : _nodes)
        if (f) n++;
      _numNodes = n;
    }
    public int getNumNodes() { return _numNodes; }
  }

}
