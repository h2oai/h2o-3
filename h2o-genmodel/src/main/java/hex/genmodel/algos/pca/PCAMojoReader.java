package hex.genmodel.algos.pca;

import hex.genmodel.ModelMojoReader;

import java.io.IOException;
import java.nio.ByteBuffer;

public class PCAMojoReader extends ModelMojoReader<PCAMojoModel>{

  @Override
  public String getModelName() {
    return "Principal Component Analysis";
  }

  @Override
  protected String getModelMojoReaderClassName() { return "hex.pca.PCAMojoWriter"; }

  @Override
  protected void readModelData(final boolean readModelMetadata) throws IOException {
    _model._use_all_factor_levels = readkv("use_all_factor_levels");
    _model._pca_method = readkv("pca_methods");
    _model._pca_impl = readkv("pca_impl");
    _model._k = readkv("k");
    _model._permutation = readkv("permutation");
    _model._ncats = readkv("ncats");
    _model._nnums = readkv("nnums");
    if (_model._nnums==0) {
      _model._normMul = new double[0];
      _model._normSub = new double[0];
    } else {
      _model._normSub = readkv("normSub");
      _model._normMul = readkv("normMul");
    }
    _model._catOffsets = readkv("catOffsets");
    _model._eigenVectorSize = readkv("eigenvector_size");
    _model._eigenvectors_raw = new double[_model._eigenVectorSize][];
    ByteBuffer bb = ByteBuffer.wrap(readblob("eigenvectors_raw"));
    for (int i = 0; i < _model._eigenVectorSize; i++) {
      double[] row = new double[_model._k];
      _model._eigenvectors_raw[i] = row;
      for (int j = 0; j < _model._k; j++)
        row[j] = bb.getDouble();
    }
  }

  @Override
  protected PCAMojoModel makeModel(String[] columns, String[][] domains, String responseColumn) {
      return new PCAMojoModel(columns, domains, responseColumn);
  }


  @Override public String mojoVersion() {
    return "1.00";
  }
}
