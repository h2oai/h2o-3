package hex.genmodel.algos.word2vec;

import hex.genmodel.MojoModel;

import java.util.HashMap;

public class Word2VecMojoModel extends MojoModel implements WordEmbeddingModel {

  int _vecSize;
  HashMap<String, float[]> _embeddings;

  Word2VecMojoModel(String[] columns, String[][] domains) {
    super(columns, domains);
  }

  @Override
  public int getVecSize() {
    return _vecSize;
  }

  @Override
  public float[] transform0(String word, float[] output) {
    float[] vec = _embeddings.get(word);
    if (vec == null)
      return null;
    System.arraycopy(vec, 0, output, 0, output.length);
    return output;
  }

  @Override
  public double[] score0(double[] row, double[] preds) {
    throw new UnsupportedOperationException("Word2Vec Model doesn't support scoring using score0() function");
  }

}
