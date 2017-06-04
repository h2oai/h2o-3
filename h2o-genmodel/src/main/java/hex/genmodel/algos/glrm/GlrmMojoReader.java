package hex.genmodel.algos.glrm;

import hex.genmodel.ModelMojoReader;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 */
public class GlrmMojoReader extends ModelMojoReader<GlrmMojoModel> {

  @Override
  public String getModelName() {
    return "Generalized Low Rank Model";
  }

  @Override
  protected void readModelData() throws IOException {
    _model._ncolA = readkv("ncolA");
    _model._ncolY = readkv("ncolY");
    _model._nrowY = readkv("nrowY");
    _model._ncolX = readkv("ncolX");
    _model._regx = GlrmRegularizer.valueOf((String) readkv("regularizationX"));
    _model._gammax = readkv("gammaX");
    _model._init = GlrmInitialization.valueOf((String) readkv("initialization"));

    _model._ncats = readkv("num_categories");
    _model._nnums = readkv("num_numeric");
    _model._normSub = readkv("norm_sub");
    _model._normMul = readkv("norm_mul");
    _model._permutation = readkv("cols_permutation");

    // loss functions
    _model._losses = new GlrmLoss[_model._ncolA];
    int li = 0;
    for (String line : readtext("losses")) {
      _model._losses[li++] = GlrmLoss.valueOf(line);
    }

    // archetypes
    _model._numLevels = readkv("num_levels_per_category");
    _model._archetypes = new double[_model._nrowY][];
    ByteBuffer bb = ByteBuffer.wrap(readblob("archetypes"));
    for (int i = 0; i < _model._nrowY; i++) {
      double[] row = new double[_model._ncolY];
      _model._archetypes[i] = row;
      for (int j = 0; j < _model._ncolY; j++)
        row[j] = bb.getDouble();
    }
  }

  @Override
  protected GlrmMojoModel makeModel(String[] columns, String[][] domains) {
    return new GlrmMojoModel(columns, domains);
  }

}
