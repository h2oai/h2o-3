package hex.genmodel.easy;

import hex.ModelCategory;
import hex.genmodel.algos.targetencoder.EncodingMap;
import hex.genmodel.algos.targetencoder.EncodingMaps;
import hex.genmodel.algos.targetencoder.TargetEncoderMojoModel;
import hex.genmodel.easy.exception.PredictException;
import hex.genmodel.easy.prediction.TargetEncoderPrediction;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static hex.genmodel.utils.SerializationTestHelper.deserialize;
import static hex.genmodel.utils.SerializationTestHelper.serialize;
import static org.junit.Assert.*;

public class EasyPredictModelWrapperWithTargetEncodingTest {

  @Test
  public void targetEncodingIsDisabledWhenEncodingMapIsNotProvided() throws PredictException {
    MyTEModel model = new MyTEModel();

    EasyPredictModelWrapper modelWrapper = new EasyPredictModelWrapper(model);

    RowData row = new RowData();
    row.put("embarked", "S");
    row.put("age", 42.0);

    // It is expected that there will be an exception if encoding map is missing and encoding will not take place 
    try {
      modelWrapper.predictTargetEncoding(row);
      fail();
    } catch (NullPointerException ex) {
      assertEquals((String) row.get("embarked"), "S");
    }
  }

  @Test
  public void serializeWrapperTest() throws Exception {
    TargetEncoderMojoModel teMojoModel = new MyTEModel();

    EasyPredictModelWrapper m = new EasyPredictModelWrapper(new EasyPredictModelWrapper.Config()
            .setModel(teMojoModel));
    
    RowData row = new RowData() {{
      put("embarked", "S");
      put("age", "66");
    }};

    // serialize & deserialize wrapper
    EasyPredictModelWrapper m1deser = (EasyPredictModelWrapper) deserialize(serialize(m));

    // check that the deserialized wrapper can be used to predict
    TargetEncoderPrediction p1 = (TargetEncoderPrediction) m.predict(row);
    TargetEncoderPrediction p1deser = (TargetEncoderPrediction) m1deser.predict(row);
    assertArrayEquals(p1.transformations, p1deser.transformations, 1e-5);
  }


  private static class MyTEModel extends TargetEncoderMojoModel {
    @Override
    public int nfeatures() {
      return 2;
    }

    @Override
    public ModelCategory getModelCategory() { return ModelCategory.TargetEncoder; } 
    @Override
    public String getUUID() { return null; } 

    private static final String[][] DOMAINS = new String[][]{
            new String[]{"S", "Q"},
            null //age
    };

    private MyTEModel() {
      super(new String[]{"embarked", "age"}, DOMAINS, null);
      EncodingMaps encodingMaps = new EncodingMaps();
      EncodingMap encodingMapForEmbarked = new EncodingMap(2);
      encodingMapForEmbarked.add(0, new double[]{3,5});
      encodingMaps.put("embarked", encodingMapForEmbarked);
      
      Map<String, Integer> teColumnNameToIdx = new HashMap<>();
      teColumnNameToIdx.put("embarked", 0);

      _columnNameToIdx.clear();
      _columnNameToIdx.putAll(teColumnNameToIdx);
    }
  }

}
