package hex.psvm;

import hex.genmodel.algos.psvm.KernelParameters;
import hex.genmodel.utils.ByteBufferWrapper;
import org.apache.commons.math3.util.FastMath;
import water.fvec.Chunk;

import java.io.Serializable;

public interface BulkSupportVectorScorer extends Serializable {
  double[] bulkScore0(Chunk[] cs);
}

class GaussianScorerRawBytes implements BulkSupportVectorScorer {
  private final double _gamma;
  private final byte[] _svs;

  GaussianScorerRawBytes(KernelParameters parms, byte[] svs) {
    this(parms._gamma, svs);
  }

  private GaussianScorerRawBytes(double gamma, byte[] svs) {
    _gamma = gamma;
    _svs = svs;
  }

  public double[] bulkScore0(Chunk[] cs) {
    double[] result = new double[cs[0]._len];
    double[] norms = new double[cs[0]._len];

    ByteBufferWrapper bb = new ByteBufferWrapper(_svs);
    while (bb.hasRemaining()) {
      final double alpha = bb.get8d();
      final int cats = bb.get4();
      for (int i = 0; i < cats; i++) {
        int svCat = bb.get4();
        for (int j = 0; j < norms.length; j++) {
          norms[j] += (int) cs[i].at8(j) == svCat ? 0 : 2;
        }
      }
      final int nums = bb.get4();
      for (int i = 0; i < nums; i++) {
        double svNum = bb.get8d();
        for (int j = 0; j < norms.length; j++) {
          double v = cs[i + cats].atd(j) - svNum;
          norms[j] += v * v;
        }
      }
      for (int j = 0; j < result.length; j++) {
        result[j] += alpha * FastMath.exp(-_gamma * norms[j]);
        norms[j] = 0;
      }
    }
    return result;
  }

}

class GaussianScorerParsed implements BulkSupportVectorScorer {
  private final double _gamma;
  private final double[] _alphas;
  private final double[][] _nums;
  private final int[][] _cats;

  GaussianScorerParsed(KernelParameters parms, byte[] svs, int svsCount) {
    this(parms._gamma, svs, svsCount);
  }

  private GaussianScorerParsed(double gamma, byte[] svs, int svsCount) {
    _gamma = gamma;
    _alphas = new double[svsCount];
    _nums = new double[svsCount][];
    _cats = new int[svsCount][];
    ByteBufferWrapper bb = new ByteBufferWrapper(svs);
    for (int i = 0; i < svsCount; i++) {
      _alphas[i] = bb.get8d();
      _cats[i] = new int[bb.get4()];
      for (int j = 0; j < _cats[i].length; j++) {
        _cats[i][j] = bb.get4();
      }
      _nums[i] = new double[bb.get4()];
      for (int j = 0; j < _nums[i].length; j++) {
        _nums[i][j] = bb.get8d();
      }
    }
  }

  public double[] bulkScore0(Chunk[] cs) {
    double[] result = new double[cs[0]._len];
    double[] norms = new double[cs[0]._len];

    for (int s = 0; s < _alphas.length; s++) {
      for (int i = 0; i < _cats[s].length; i++) {
        int svCat = _cats[s][i];
        for (int j = 0; j < norms.length; j++) {
          norms[j] += (int) cs[i].at8(j) == svCat ? 0 : 2;
        }
      }
      for (int i = 0; i < _nums[s].length; i++) {
        double svNum = _nums[s][i];
        for (int j = 0; j < norms.length; j++) {
          double v = cs[i + _cats[s].length].atd(j) - svNum;
          norms[j] += v * v;
        }
      }
      for (int j = 0; j < result.length; j++) {
        result[j] += _alphas[s] * FastMath.exp(-_gamma * norms[j]);
        norms[j] = 0;
      }
    }
    return result;
  }

}
