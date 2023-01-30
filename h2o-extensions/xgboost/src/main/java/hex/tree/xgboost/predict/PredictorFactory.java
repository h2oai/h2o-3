package hex.tree.xgboost.predict;

import biz.k11i.xgboost.Predictor;
import biz.k11i.xgboost.config.PredictorConfiguration;
import biz.k11i.xgboost.tree.DefaultRegTreeFactory;
import biz.k11i.xgboost.tree.RegTree;
import biz.k11i.xgboost.tree.RegTreeFactory;
import biz.k11i.xgboost.util.ModelReader;
import hex.genmodel.algos.xgboost.XGBoostJavaMojoModel;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;

public class PredictorFactory {
  
  public static Predictor makePredictor(byte[] boosterBytes) {
    return makePredictor(boosterBytes, null, true, 0);
  }

  public static Predictor makePredictor(byte[] boosterBytes, byte[] auxNodeWeightBytes,
                                        boolean scoringOnly, int skipTrees) {
    PredictorConfiguration.Builder bldr = PredictorConfiguration.builder();
    RegTreeFactory regTreeFactory = scoringOnly && unsafeTreesSupported() ?
      UnsafeRegTreeFactory.INSTANCE : DefaultRegTreeFactory.INSTANCE;
    if (skipTrees > 0) {
      regTreeFactory = new TreeSkippingFactory(skipTrees, regTreeFactory);
    }
    bldr.regTreeFactory(regTreeFactory);
    PredictorConfiguration cfg = bldr.build();
    try (InputStream is = new ByteArrayInputStream(boosterBytes)) {
      Predictor p = new Predictor(is, cfg);
      if (!scoringOnly && auxNodeWeightBytes != null)
        XGBoostJavaMojoModel.updateNodeWeights(p, auxNodeWeightBytes);
      return p;
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  static boolean unsafeTreesSupported() {
    // XGBoost Predictor uses LE, we can only use our scoring if the system has the same endianness
    return ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN);
  }

  private static class UnsafeRegTreeFactory implements RegTreeFactory {
    private static final UnsafeRegTreeFactory INSTANCE = new UnsafeRegTreeFactory();
    @Override
    public RegTree loadTree(int treeId, ModelReader reader) throws IOException {
      return new XGBoostRegTree(reader);
    }
  }

  private static class TreeSkippingFactory implements RegTreeFactory {
    private final int _nSkippedTrees;
    private final RegTreeFactory _actualFactory;

    private TreeSkippingFactory(int nSkippedTrees, RegTreeFactory actualFactory) {
      _nSkippedTrees = nSkippedTrees;
      _actualFactory = actualFactory;
    }

    @Override
    public RegTree loadTree(int treeId, ModelReader reader) throws IOException {
      return treeId < _nSkippedTrees ? new SkippedRegTree(reader) : _actualFactory.loadTree(treeId, reader);
    }
  }

}
