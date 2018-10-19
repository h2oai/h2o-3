package hex.genmodel.algos.xgboost;

import biz.k11i.xgboost.Predictor;
import biz.k11i.xgboost.gbm.GradBooster;

import hex.genmodel.GenModel;
import hex.genmodel.MojoModel;
import hex.genmodel.algos.tree.SharedTreeGraph;

import ml.dmlc.xgboost4j.java.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;

/**
 * Please note: user is advised to explicitly release the native resources of XGBoost by calling close method on the instance.
 */
public final class XGBoostNativeMojoModel extends XGBoostMojoModel {
  Booster _booster;

  public XGBoostNativeMojoModel(byte[] boosterBytes, String[] columns, String[][] domains, String responseColumn) {
    super(columns, domains, responseColumn);
    _booster = makeBooster(boosterBytes);
  }

  private static Booster makeBooster(byte[] boosterBytes) {
    try (InputStream is = new ByteArrayInputStream(boosterBytes)) {
      return BoosterHelper.loadModel(is);
    } catch (Exception xgBoostError) {
      throw new IllegalStateException("Unable to load XGBooster", xgBoostError);
    }
  }

  public final double[] score0(double[] doubles, double offset, double[] preds) {
    return score0(doubles, offset, preds,
            _booster, _nums, _cats, _catOffsets, _useAllFactorLevels,
            _nclasses, _priorClassDistrib, _defaultThreshold, _sparse);
  }

  public static double[] score0(double[] doubles, double offset, double[] preds,
                                Booster _booster, int _nums, int _cats,
                                int[] _catOffsets, boolean _useAllFactorLevels,
                                int nclasses, double[] _priorClassDistrib,
                                double _defaultThreshold, final boolean _sparse) {
    if (offset != 0) throw new UnsupportedOperationException("Unsupported: offset != 0");

    int cats = _catOffsets == null ? 0 : _catOffsets[_cats];
    // convert dense doubles to expanded floats
    final float[] floats = new float[_nums + cats]; //TODO: use thread-local storage
    GenModel.setInput(doubles, floats, _nums, _cats, _catOffsets, null, null, _useAllFactorLevels, _sparse /*replace NA with 0*/);
    float[] out;
    DMatrix dmat = null;
    try {
      dmat = new DMatrix(floats,1, floats.length, _sparse ? 0 : Float.NaN);
      final DMatrix row = dmat;
      BoosterHelper.BoosterOp<float[]> predictOp = new BoosterHelper.BoosterOp<float[]>() {
        @Override
        public float[] apply(Booster booster) throws XGBoostError {
          return booster.predict(row)[0];
        }
      };
      out = BoosterHelper.doWithLocalRabit(predictOp, _booster);
    } catch (XGBoostError xgBoostError) {
      throw new IllegalStateException("Failed XGBoost prediction.", xgBoostError);
    } finally {
      BoosterHelper.dispose(dmat);
    }

    return toPreds(doubles, out, preds, nclasses, _priorClassDistrib, _defaultThreshold);
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

  @Override
  public SharedTreeGraph convert(final int treeNumber, final String treeClass) {
    GradBooster booster = null;
    try {
      booster = new Predictor(new ByteArrayInputStream(_booster.toByteArray())).getBooster();
    } catch (IOException | XGBoostError e) {
      e.printStackTrace();
    }

    return _computeGraph(booster, treeNumber);
  }
}
