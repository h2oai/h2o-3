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

    // new fields added after version 1.00
    try {
      _model._seed = readkv("seed", 0l);
      _model._reverse_transform = readkv("reverse_transform");
      _model._transposed = readkv("transposed");
      _model._catOffsets = readkv("catOffsets");
      // load in archetypes raw
      if (_model._transposed) {
        _model._archetypes_raw = new double[_model._archetypes[0].length][_model._archetypes.length];
        for (int row = 0; row < _model._archetypes.length; row++) {
          for (int col = 0; col < _model._archetypes[0].length; col++) {
            _model._archetypes_raw[col][row] = _model._archetypes[row][col];
          }
        }
      } else
        _model._archetypes_raw = _model._archetypes;
    } catch (NullPointerException re) { // only expect null pointer exception.
      _model._seed = System.currentTimeMillis();  // randomly initialize seed
      _model._reverse_transform = true;
      _model._transposed = true;
      _model._catOffsets = null;
      _model._archetypes_raw = null;
    }
  }

  @Override
  protected GlrmMojoModel makeModel(String[] columns, String[][] domains, String responseColumn) {
    GlrmMojoModel glrmModel = new GlrmMojoModel(columns, domains, responseColumn);
    glrmModel._allAlphas = GlrmMojoModel.initializeAlphas(glrmModel._numAlphaFactors);  // set _allAlphas array
    return glrmModel;
  }

  @Override public String mojoVersion() {
    return "1.10";
  }

}
