package hex.genmodel;

import hex.genmodel.attributes.ModelAttributes;
import org.mockito.Mockito;

import java.io.IOException;

public class MockMojoReader extends ModelMojoReader {

  private ModelMojoReader delegate = Mockito.mock(ModelMojoReader.class); 
  
  @Override
  public String getModelName() {
    return "mock";
  }

  @Override
  protected String getModelMojoReaderClassName() {
    return null;
  }

  @Override
  protected ModelAttributes readModelSpecificAttributes() {
    return delegate.readModelSpecificAttributes();
  }

  @Override
  protected void readModelData(final boolean readModelMetadata) throws IOException {
    delegate.readModelData(readModelMetadata);
  }

  @Override
  protected MojoModel makeModel(String[] columns, String[][] domains, String responseColumn) {
    return new MockMojoModel(columns, domains, responseColumn);
  }

  @Override
  public String mojoVersion() {
    return "1.00";
  }

  public class MockMojoModel extends MojoModel {

    private MockMojoModel(String[] columns, String[][] domains, String responseColumn) {
      super(columns, domains, responseColumn);
    }

    @Override
    public double[] score0(double[] row, double[] preds) {
      throw new UnsupportedOperationException();
    }

    ModelMojoReader getReader() {
      return MockMojoReader.this.delegate;
    }

  } 
  
}
