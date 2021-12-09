package hex.glrm;

import hex.ModelMetrics;
import hex.ModelMojoWriter;
import hex.genmodel.algos.glrm.GlrmLoss;

import java.io.IOException;
import java.nio.ByteBuffer;


/**
 * MOJO serializer for GLRM model.
 */
public class GlrmMojoWriter extends ModelMojoWriter<GLRMModel, GLRMModel.GLRMParameters, GLRMModel.GLRMOutput> {

  @SuppressWarnings("unused")  // Called through reflection in ModelBuildersHandler
  public GlrmMojoWriter() {}

  public GlrmMojoWriter(GLRMModel model) {
    super(model);
  }

  @Override public String mojoVersion() {
    return "1.10";
  }

  @Override
  protected void writeModelData() throws IOException {
    writekv("initialization", model._parms._init);
    writekv("regularizationX", model._parms._regularization_x);
    writekv("regularizationY", model._parms._regularization_y);
    writekv("gammaX", model._parms._gamma_x);
    writekv("gammaY", model._parms._gamma_y);
    writekv("ncolX", model._parms._k);
    writekv("seed", model._parms._seed);  // store seed for later use
    writekv("reverse_transform", model._parms._impute_original);

    // DataInfo mapping
    writekv("cols_permutation", model._output._permutation);
    writekv("num_categories", model._output._ncats);
    writekv("num_numeric", model._output._nnums);
    writekv("norm_sub", model._output._normSub);
    writekv("norm_mul", model._output._normMul);
    writekv("transposed", model._output._archetypes_raw._transposed);

    // Loss functions
    writekv("ncolA", model._output._lossFunc.length);
    startWritingTextFile("losses");
    for (GlrmLoss loss : model._output._lossFunc) {
      writeln(loss.toString());
    }
    finishWritingTextFile();

    // Archetypes
    GLRM.Archetypes arch = model._output._archetypes_raw;
    writekv("ncolY", arch.nfeatures());
    writekv("nrowY", arch.rank());
    writekv("num_levels_per_category", arch._numLevels);
    writekv("catOffsets", arch._catOffsets);
    int n = arch.rank() * arch.nfeatures();
    ByteBuffer bb = ByteBuffer.wrap(new byte[n * 8]);
    for (double[] row : arch.getY(false))
      for (double val : row)
        bb.putDouble(val);
    writeblob("archetypes", bb.array());
  }

  @Override
  public ModelMetrics.MetricBuilderFactory getModelBuilderFactory() {
    return new ModelMetricsGLRM.GLRMMetricBuilderFactory();
  }

}
