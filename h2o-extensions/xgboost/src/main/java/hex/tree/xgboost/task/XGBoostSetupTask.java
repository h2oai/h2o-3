package hex.tree.xgboost.task;

import hex.tree.xgboost.BoosterParms;
import hex.tree.xgboost.matrix.MatrixLoader;
import water.H2O;
import water.Key;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.IcedHashMapGeneric;

import java.util.Map;

/**
 * Initializes XGBoost training (converts Frame to set of node-local DMatrices)
 */
public class XGBoostSetupTask extends XGBoostSaveMatrixTask {

  private final BoosterParms _boosterParms;
  private final byte[] _checkpoint;
  private final IcedHashMapGeneric.IcedHashMapStringString _rabitEnv;

  public XGBoostSetupTask(
      Key modelKey, String saveMatrixDirectory, BoosterParms boosterParms,
      byte[] checkpointToResume, Map<String, String> rabitEnv, boolean[] nodes,
      MatrixLoader loader
  ) {
    super(modelKey, saveMatrixDirectory, nodes, loader);
    _boosterParms = boosterParms;
    _checkpoint = checkpointToResume;
    (_rabitEnv = new IcedHashMapGeneric.IcedHashMapStringString()).putAll(rabitEnv);
  }

  @Override
  protected void execute() {
    super.execute();
    if (matrix == null)
      throw new IllegalStateException("Node " + H2O.SELF + " is supposed to participate in XGB training " +
              "but it doesn't have a DMatrix!");

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
