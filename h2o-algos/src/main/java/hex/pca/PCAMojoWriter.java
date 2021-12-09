package hex.pca;

import hex.ModelMetrics;
import hex.ModelMojoWriter;
import water.MemoryManager;

import java.io.IOException;
import java.nio.ByteBuffer;

public class PCAMojoWriter extends ModelMojoWriter<PCAModel, PCAModel.PCAParameters, PCAModel.PCAOutput> {
  @SuppressWarnings("unused")  // Called through reflection in ModelBuildersHandler
  public PCAMojoWriter() {}

  public PCAMojoWriter(PCAModel model) {
    super(model);
  }

  @Override public String mojoVersion() {
    return "1.00";
  }

  @Override
  protected void writeModelData() throws IOException {
    writekv("pcaMethod", model._parms._pca_method.toString()); // for reference
    writekv("pca_impl", model._parms._pca_implementation.toString());
    writekv("k", model._parms._k);
    writekv("use_all_factor_levels", model._parms._use_all_factor_levels);
    writekv("permutation", model._output._permutation);
    writekv("ncats", model._output._ncats);
    writekv("nnums", model._output._nnums);
    writekv("normSub", model._output._normSub);
    writekv("normMul", model._output._normMul);
    writekv("catOffsets", model._output._catOffsets);
    int n = model._output._eigenvectors_raw.length*model._output._eigenvectors_raw[0].length;
    writekv("eigenvector_size", model._output._eigenvectors_raw.length);
    ByteBuffer bb = ByteBuffer.wrap(MemoryManager.malloc1(n * 8));
    for (double[] row : model._output._eigenvectors_raw)
      for (double val : row)
        bb.putDouble(val);
    writeblob("eigenvectors_raw", bb.array());
  }

  @Override
  public ModelMetrics.MetricBuilderFactory getModelBuilderFactory() {
    return new ModelMetricsPCA.PCAMetricBuilderFactory();
  }
}
