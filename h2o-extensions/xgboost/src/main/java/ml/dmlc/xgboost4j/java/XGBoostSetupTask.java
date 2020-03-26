package ml.dmlc.xgboost4j.java;

import hex.tree.xgboost.BoosterParms;
import water.H2O;
import water.Key;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.IcedHashMapGeneric;
import water.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * Initializes XGBoost training (converts Frame to set of node-local DMatrices)
 */
public abstract class XGBoostSetupTask extends AbstractXGBoostTask<XGBoostSetupTask> {

  private final String _saveMatrixDirectory;
  private final BoosterParms _boosterParms;
  private final byte[] _checkpoint;
  private final IcedHashMapGeneric.IcedHashMapStringString _rabitEnv;

  public XGBoostSetupTask(
      Key modelKey, String saveMatrixDirectory, BoosterParms boosterParms,
      byte[] checkpointToResume, Map<String, String> rabitEnv, boolean[] nodes
  ) {
    super(modelKey, nodes);
    _saveMatrixDirectory = saveMatrixDirectory;
    _boosterParms = boosterParms;
    _checkpoint = checkpointToResume;
    (_rabitEnv = new IcedHashMapGeneric.IcedHashMapStringString()).putAll(rabitEnv);
  }

  @Override
  protected void execute() {
    final DMatrix matrix;
    try {
      matrix = makeLocalMatrix();
      if (_saveMatrixDirectory != null) {
        File directory = new File(_saveMatrixDirectory);
        if (directory.mkdirs()) {
          Log.debug("Created directory for matrix export: " + directory.getAbsolutePath());
        }
        File path = new File(directory, "matrix.part" + H2O.SELF.index()); 
        Log.info("Saving node-local portion of XGBoost training dataset to " + path.getAbsolutePath() + ".");
        matrix.saveBinary(path.getAbsolutePath());
      }
    } catch (XGBoostError|IOException xgBoostError) {
      throw new IllegalStateException("Failed XGBoost training.", xgBoostError);
    }

    if (matrix == null)
      throw new IllegalStateException("Node " + H2O.SELF + " is supposed to participate in XGB training " +
              "but it doesn't have a DMatrix!");

    _rabitEnv.put("DMLC_TASK_ID", String.valueOf(H2O.SELF.index()));

    XGBoostUpdater thread = XGBoostUpdater.make(_modelKey, matrix, _boosterParms, _checkpoint, _rabitEnv);
    thread.start(); // we do not need to wait for the Updater to init Rabit - subsequent tasks will wait
  }

  protected abstract DMatrix makeLocalMatrix() throws IOException, XGBoostError;

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
    final Frame _fr;
    final boolean[] _nodes;
    final int _numNodes;
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
