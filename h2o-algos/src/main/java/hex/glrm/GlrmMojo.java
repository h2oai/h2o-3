package hex.glrm;

import hex.ModelMojo;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * MOJO serializer for GLRM model.
 */
public class GlrmMojo extends ModelMojo<GLRMModel, GLRMModel.GLRMParameters, GLRMModel.GLRMOutput> {

  public GlrmMojo(GLRMModel model) {
    super(model);
  }

  @Override
  protected void writeExtraModelInfo() throws IOException {
    GLRM.Archetypes arch = model._output._archetypes_raw;
    writekv("ncolY", arch.nfeatures());
    writekv("nrowY", arch.rank());
    writekv("regularizationX", model._parms._regularization_x);
    writekv("regularizationY", model._parms._regularization_y);
    writekv("gammaX", model._parms._gamma_x);
    writekv("gammaY", model._parms._gamma_y);
  }

  @Override
  protected void writeModelData() throws IOException {
    // Loss functions
    startWritingTextFile("losses.txt");
    for (GlrmLoss loss : model._output._lossFunc) {
      writeln(loss.toString());
    }
    finishWritingTextFile();

    // Archetypes
    GLRM.Archetypes arch = model._output._archetypes_raw;
    int n = arch.rank() * arch.nfeatures();
    ByteBuffer bb = ByteBuffer.wrap(new byte[n * 8]);
    for (double[] row : arch.getY(false))
      for (double val : row)
        bb.putDouble(val);
    writeBinaryFile("archetypes.bin", bb.array());
  }

}
