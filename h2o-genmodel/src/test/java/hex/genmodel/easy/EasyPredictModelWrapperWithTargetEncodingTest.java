package hex.genmodel.easy;

import hex.ModelCategory;
import hex.genmodel.GenModel;
import org.junit.Test;

import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class EasyPredictModelWrapperWithTargetEncodingTest {

  @Test
  public void transformWithTargetEncoding() {
    MyAutoEncoderModel model = new MyAutoEncoderModel();
    
    model._targetEncodingMap = new HashMap<>();
    HashMap<String, int[]> encodingsForEmbarkingColumn = new HashMap<>();
    int[] encodingComponents = {3, 7};
    encodingsForEmbarkingColumn.put("S", encodingComponents);
    model._targetEncodingMap.put("embarked", encodingsForEmbarkingColumn);
    
    EasyPredictModelWrapper modelWrapper = new EasyPredictModelWrapper(model);

    RowData row = new RowData();
    row.put("embarked", "S");
    row.put("age", 42.0);
    
    modelWrapper.transformWithTargetEncoding(row);
    
    assertEquals((double) row.get("embarked_te"), (double) encodingComponents[0] / encodingComponents[1], 1e-5);
    assertNotEquals((double) row.get("embarked_te"), (double) encodingComponents[0] / encodingComponents[1] + 0.1, 1e-5);
  }

  @Test
  public void targetEncodingIsDisabledWhenEncodingMapIsNotProvided() {
    MyAutoEncoderModel model = new MyAutoEncoderModel();

    model._targetEncodingMap = null;

    EasyPredictModelWrapper modelWrapper = new EasyPredictModelWrapper(model);

    RowData row = new RowData();
    row.put("embarked", "S");
    row.put("age", 42.0);

    // It is expected that there will be no exceptions and encoding will not take place 
    modelWrapper.transformWithTargetEncoding(row);
    assertEquals((String) row.get("embarked"), "S");
  }


  private static class MyAutoEncoderModel extends GenModel {
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

    private MyAutoEncoderModel() {
      super(new String[]{"embarked", "age"}, DOMAINS, null);
    }
  }

}
