package ml.dmlc.xgboost4j.java;

import hex.tree.xgboost.XGBoostExtension;
import hex.tree.xgboost.XGBoostModel;
import hex.tree.xgboost.XGBoostUtils;
import water.ExtensionManager;
import water.H2O;
import water.MRTask;
import water.util.IcedHashMapGeneric;

/**
 * Initializes XGBoost training (converts Frame to set of node-local DMatrices)
 */
public class XGBoostSetupTask extends MRTask<XGBoostSetupTask> {

  private final XGBoostModelInfo _sharedModel;
  private final XGBoostModel.XGBoostParameters _parms;
  private boolean _sparse;

  // OUT
  IcedHashMapGeneric.IcedHashMapStringObject _nodeToMatrixWrapper;

  public XGBoostSetupTask(XGBoostModelInfo inputModel, XGBoostModel.XGBoostParameters parms, boolean sparse) {
    _sharedModel = inputModel;
    _parms = parms;
    _sparse = sparse;
  }

  @Override
  protected void setupLocal() {
    if (H2O.ARGS.client) {
      return;
    }

    // We need to verify that the xgboost is available on the remote node
    if (!ExtensionManager.getInstance().isCoreExtensionEnabled(XGBoostExtension.NAME)) {
      throw new IllegalStateException("XGBoost is not available on the node " + H2O.SELF);
    }

    final PersistentDMatrix matrix;
    try {
      matrix = makeLocalMatrix();
    } catch (XGBoostError xgBoostError) {
      throw new IllegalStateException("Failed XGBoost training.", xgBoostError);
    }

    if (matrix == null)
      return;

    _nodeToMatrixWrapper = new IcedHashMapGeneric.IcedHashMapStringObject();
    _nodeToMatrixWrapper.put(H2O.SELF.toString(), matrix.wrap());
  }

  private PersistentDMatrix makeLocalMatrix() throws XGBoostError {
      return PersistentDMatrix.persist(XGBoostUtils.convertFrameToDMatrix(
              _sharedModel._dataInfoKey,
              _fr,
              true,
              _parms._response_column,
              _parms._weights_column,
              _parms._fold_column,
              _sparse));
  }

  @Override
  public void reduce(XGBoostSetupTask mrt) {
    if (mrt._nodeToMatrixWrapper == null)
      return;
    if (_nodeToMatrixWrapper == null)
      _nodeToMatrixWrapper = mrt._nodeToMatrixWrapper;
    else
      _nodeToMatrixWrapper.putAll(mrt._nodeToMatrixWrapper);
  }

}
