package hex.genmodel.easy;

import hex.ModelCategory;
import hex.genmodel.algos.targetencoder.EncodingMap;
import hex.genmodel.algos.targetencoder.EncodingMaps;
import hex.genmodel.algos.targetencoder.TargetEncoderMojoModel;
import hex.genmodel.easy.exception.PredictException;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class EasyPredictModelWrapperWithTargetEncodingTest {

  @Test
  public void transformWithTargetEncoding() throws PredictException{
    MyTEModel model = new MyTEModel();

    EncodingMaps targetEncodingMap = model._targetEncodingMap;
    EncodingMap encodingsForEmbarkingColumn = new EncodingMap();
    int[] encodingComponents = {3, 7};
    encodingsForEmbarkingColumn.put("S", encodingComponents);
    targetEncodingMap.put("embarked", encodingsForEmbarkingColumn);
    
    EasyPredictModelWrapper modelWrapper = new EasyPredictModelWrapper(model);

    RowData row = new RowData();
    row.put("embarked", "S");
    row.put("age", 42.0);
    
    modelWrapper.transformWithTargetEncoding(row);
    
    assertEquals((double) row.get("embarked_te"), (double) encodingComponents[0] / encodingComponents[1], 1e-5);
    assertNotEquals((double) row.get("embarked_te"), (double) encodingComponents[0] / encodingComponents[1] + 0.1, 1e-5);
  }

  @Test
  public void targetEncodingIsDisabledWhenEncodingMapIsNotProvided() throws PredictException {
    MyTEModel model = new MyTEModel();

    model._targetEncodingMap = null;

    EasyPredictModelWrapper modelWrapper = new EasyPredictModelWrapper(model);

    RowData row = new RowData();
    row.put("embarked", "S");
    row.put("age", 42.0);

    // It is expected that there will be an exception if encoding map is missing and encoding will not take place 
    try {
      modelWrapper.transformWithTargetEncoding(row);
      fail();
    } catch (IllegalStateException ex) {
      assertEquals((String) row.get("embarked"), "S");
    }
    
  }


  private static class MyTEModel extends TargetEncoderMojoModel {
    
    @Override
    public ModelCategory getModelCategory() { return null; } 
    @Override
    public String getUUID() { return null; } 
    @Override
    public double[] score0(double[] row, double[] preds) { return new double[0]; }

    private static final String[][] DOMAINS = new String[][]{
            new String[]{"S", "Q"},
            null //age
    };

    private MyTEModel() {
      super(new String[]{"embarked", "age"}, DOMAINS, null);
      _targetEncodingMap = new EncodingMaps();
    }
  }

}
