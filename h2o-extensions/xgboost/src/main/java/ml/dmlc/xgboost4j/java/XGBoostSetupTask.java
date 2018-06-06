package ml.dmlc.xgboost4j.java;

import hex.tree.xgboost.BoosterParms;
import hex.tree.xgboost.XGBoostModel;
import hex.tree.xgboost.XGBoostUtils;
import water.*;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.IcedHashMapGeneric;

import java.util.Map;

/**
 * Initializes XGBoost training (converts Frame to set of node-local DMatrices)
 */
public class XGBoostSetupTask extends AbstractXGBoostTask<XGBoostSetupTask> {

  private final XGBoostModelInfo _sharedModel;
  private final XGBoostModel.XGBoostParameters _parms;
  private final boolean _sparse;
  private final BoosterParms _boosterParms;
  private final IcedHashMapGeneric.IcedHashMapStringString _rabitEnv;
  private final Frame _trainFrame;

  public XGBoostSetupTask(XGBoostModel model, XGBoostModel.XGBoostParameters parms, BoosterParms boosterParms,
                          Map<String, String> rabitEnv, FrameNodes trainFrame) {
    super(model._key, trainFrame._nodes);
    _sharedModel = model.model_info();
    _parms = parms;
    _sparse = model._output._sparse;
    _boosterParms = boosterParms;
    (_rabitEnv = new IcedHashMapGeneric.IcedHashMapStringString()).putAll(rabitEnv);
    _trainFrame = trainFrame._fr;
  }

  @Override
  protected void execute() {
    final DMatrix matrix;
    try {
      matrix = makeLocalMatrix();
    } catch (XGBoostError xgBoostError) {
      throw new IllegalStateException("Failed XGBoost training.", xgBoostError);
    }

    if (matrix == null)
      throw new IllegalStateException("Node " + H2O.SELF + " is supposed to participate in XGB training " +
              "but it doesn't have a DMatrix!");

    _rabitEnv.put("DMLC_TASK_ID", String.valueOf(H2O.SELF.index()));

    XGBoostUpdater thread = XGBoostUpdater.make(_modelKey, matrix, _boosterParms, _rabitEnv);
    thread.start(); // we do not need to wait for the Updater to init Rabit - subsequent tasks will wait
  }

  private DMatrix makeLocalMatrix() throws XGBoostError {
      return XGBoostUtils.convertFrameToDMatrix(
              _sharedModel._dataInfoKey,
              _trainFrame,
              true,
              _parms._response_column,
              _parms._weights_column,
              _parms._fold_column,
              _sparse);
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
