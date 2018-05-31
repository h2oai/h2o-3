package hex.genmodel.algos.xgboost;

import hex.genmodel.GenModel;
import hex.genmodel.MojoModel;
import ml.dmlc.xgboost4j.java.*;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Please note: user is advised to explicitly release the native resources of XGBoost by calling close method on the instance.
 */
public final class XGBoostNativeMojoModel extends XGBoostMojoModel {
  Booster _booster;

  public XGBoostNativeMojoModel(String[] columns, String[][] domains, String responseColumn) {
    super(columns, domains, responseColumn);
  }

  public final double[] score0(double[] doubles, double offset, double[] preds) {
    return score0(doubles, offset, preds,
            _booster, _nums, _cats, _catOffsets, _useAllFactorLevels,
            _nclasses, _priorClassDistrib, _defaultThreshold, _sparse);
  }

  public static double[][] bulkScore0(double[][] doubles, double[] offsets, double[][] preds,
                                      Booster _booster, int _nums, int _cats,
                                      int[] _catOffsets, boolean _useAllFactorLevels,
                                      int nclasses, double[] _priorClassDistrib,
                                      double _defaultThreshold, boolean _sparse) {
    if (offsets != null) throw new UnsupportedOperationException("Unsupported: offset != null or only 0s");
    float[][] floats;
    int cats = _catOffsets == null ? 0 : _catOffsets[_cats];
    // convert dense doubles to expanded floats
    floats = new float[doubles.length][_nums + cats]; //TODO: use thread-local storage
    for(int i = 0; i < doubles.length; i++) {
      GenModel.setInput(doubles[i], floats[i], _nums, _cats, _catOffsets, null, null, _useAllFactorLevels, _sparse /*replace NA with 0*/);
    }
    float[][] out = null;
    DMatrix dmat = null;
    try {
      Map<String, String> rabitEnv = new HashMap<>();
      rabitEnv.put("DMLC_TASK_ID", "0");
      Rabit.init(rabitEnv);
      dmat = new DMatrix(floats,doubles.length,floats[0].length, _sparse ? 0 : Float.NaN);
//      dmat.setWeight(new float[]{(float)weight});
      out = _booster.predict(dmat);
      Rabit.shutdown();
    } catch (XGBoostError xgBoostError) {
      throw new IllegalStateException("Failed XGBoost prediction.", xgBoostError);
    } finally {
      BoosterHelper.dispose(dmat);
    }

    for(int r = 0; r < out.length; r++) {
      if (nclasses > 2) {
        for (int i = 0; i < out[0].length; ++i)
          preds[r][1 + i] = out[r][i];
//      if (_balanceClasses)
//        GenModel.correctProbabilities(preds, _priorClassDistrib, _modelClassDistrib);
        preds[r][0] = GenModel.getPrediction(preds[r], _priorClassDistrib, doubles[r], _defaultThreshold);
      } else if (nclasses == 2) {
        preds[r][1] = 1 - out[r][0];
        preds[r][2] = out[r][0];
        preds[r][0] = GenModel.getPrediction(preds[r], _priorClassDistrib, doubles[r], _defaultThreshold);
      } else {
        preds[r][0] = out[r][0];
      }
    }
    return preds;
  }

  public static double[] score0(double[] doubles, double offset, double[] preds,
                                Booster _booster, int _nums, int _cats,
                                int[] _catOffsets, boolean _useAllFactorLevels,
                                int nclasses, double[] _priorClassDistrib,
                                double _defaultThreshold, boolean _sparse) {
    if (offset != 0) throw new UnsupportedOperationException("Unsupported: offset != 0");
    float[] floats;
    int cats = _catOffsets == null ? 0 : _catOffsets[_cats];
    // convert dense doubles to expanded floats
    floats = new float[_nums + cats]; //TODO: use thread-local storage
    GenModel.setInput(doubles, floats, _nums, _cats, _catOffsets, null, null, _useAllFactorLevels, _sparse /*replace NA with 0*/);
    float[][] out;
    DMatrix dmat = null;
    try {
      Map<String, String> rabitEnv = new HashMap<>();
      rabitEnv.put("DMLC_TASK_ID", "0");
      Rabit.init(rabitEnv);
      dmat = new DMatrix(floats,1,floats.length, _sparse ? 0 : Float.NaN);
      out = _booster.predict(dmat);
      Rabit.shutdown();
    } catch (XGBoostError xgBoostError) {
      throw new IllegalStateException("Failed XGBoost prediction.", xgBoostError);
    } finally {
      BoosterHelper.dispose(dmat);
    }

    return toPreds(doubles, out[0], preds, nclasses, _priorClassDistrib, _defaultThreshold);
  }

  @Override
  public void close() {
    BoosterHelper.dispose(_booster);
  }

  public String[] getBoosterDump(final boolean withStats, final String format) {
    final Path featureMapFile;
    if (_featureMap != null && ! _featureMap.isEmpty())
      try {
        featureMapFile = Files.createTempFile("featureMap", ".txt");
      } catch (IOException e) {
        throw new IllegalStateException("Unable to write a temporary file with featureMap");
      }
    else
      featureMapFile = null;

    try {
      if (featureMapFile != null) {
        Files.write(featureMapFile, Collections.singletonList(_featureMap), Charset.defaultCharset(), StandardOpenOption.WRITE);
      }

      BoosterHelper.BoosterOp<String[]> dumpOp = new BoosterHelper.BoosterOp<String[]>() {
        @Override
        public String[] apply(Booster booster) throws XGBoostError {
          String featureMap = featureMapFile != null ? featureMapFile.toFile().getAbsolutePath() : null;
          return booster.getModelDump(featureMap, withStats, format);
        }
      };

      return BoosterHelper.doWithLocalRabit(dumpOp, _booster);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to write feature map file", e);
    } catch (XGBoostError e) {
      throw new IllegalStateException("Failed to dump model", e);
    } finally {
      if (featureMapFile != null) {
        try {
          Files.deleteIfExists(featureMapFile);
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }

  public static void main(String[] args) throws IOException {
    if (args.length < 2 || ! "--dump".equals(args[0])) {
      usage();
      System.exit(1);
    }
    String mojoFile = args[1];
    boolean withStats = args.length > 2 ? Boolean.valueOf(args[2]) : false;
    String format = args.length > 3 ? args[3] : "text";

    XGBoostNativeMojoModel mojoModel = (XGBoostNativeMojoModel) MojoModel.load(mojoFile);
    for (String dumpLine : mojoModel.getBoosterDump(withStats, format))
      System.out.println(dumpLine);
  }

  private static void usage() {
    System.out.println("java -cp h2o-genmodel.jar " + XGBoostNativeMojoModel.class.getCanonicalName() + " --dump <mojo> [withStats?] [format]");
  }

}
