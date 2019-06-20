package hex.genmodel.algos.psvm;

import hex.genmodel.utils.ByteBufferWrapper;

import java.io.Serializable;

public interface SupportVectorScorer extends Serializable {
  double score0(double[] data);
}

class GaussianScorer implements SupportVectorScorer {
  private final double _gamma;
  private final byte[] _svs;

  GaussianScorer(KernelParameters parms, byte[] svs) {
    this(parms._gamma, svs);
  }

  GaussianScorer(double gamma, byte[] svs) {
    _gamma = gamma;
    _svs = svs;
  }

  public double score0(double[] row) {
    double result = 0;
    ByteBufferWrapper bb = new ByteBufferWrapper(_svs);
    while (bb.hasRemaining()) {
      final double alpha = bb.get8d();
      double norm = 0;
      final int cats = bb.get4();
      for (int i = 0; i < cats; i++) {
        norm += (int) row[i] == bb.get4() ? 0 : 2;
      }
      final int nums = bb.get4();
      for (int i = 0; i < nums; i++) {
        double v = row[i + cats] - bb.get8d();
        norm += v * v;
      }
      result += alpha * Math.exp(-_gamma * norm);
    }
    return result;
  }

}
