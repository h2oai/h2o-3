package ml.dmlc.xgboost4j.java;

import biz.k11i.xgboost.Predictor;
import biz.k11i.xgboost.config.PredictorConfiguration;
import biz.k11i.xgboost.tree.RegTree;
import biz.k11i.xgboost.tree.RegTreeFactory;
import biz.k11i.xgboost.util.ModelReader;
import water.util.Log;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;

public class PredictorFactory {

  public static Predictor makePredictor(byte[] boosterBytes) {
    PredictorConfiguration.Builder bldr = PredictorConfiguration.builder();
    if (unsafeTreesSupported()) {
      Log.warn("XGBoost Predictor is using EXPERIMENTAL scoring implementation!");
      bldr.regTreeFactory(UnsafeRegTreeFactory.INSTANCE);
    }
    PredictorConfiguration cfg = bldr.build();
    try (InputStream is = new ByteArrayInputStream(boosterBytes)) {
      return new Predictor(is, cfg);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static boolean unsafeTreesSupported() {
    // XGBoost Predictor uses LE, we can only use our scoring if the system has the same endianness
    return ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN);
  }

  private static class UnsafeRegTreeFactory implements RegTreeFactory {
    private static final UnsafeRegTreeFactory INSTANCE = new UnsafeRegTreeFactory();
    @Override
    public RegTree loadTree(ModelReader reader) throws IOException {
      return new XGBoostRegTree(reader);
    }
  }

}
