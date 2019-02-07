package hex.genmodel.easy;

import hex.ModelCategory;
import hex.genmodel.GenModel;
import hex.genmodel.MojoModel;
import hex.genmodel.algos.word2vec.WordEmbeddingModel;
import hex.genmodel.easy.error.CountingErrorConsumer;
import hex.genmodel.easy.error.VoidErrorConsumer;
import hex.genmodel.easy.exception.PredictUnknownCategoricalLevelException;
import hex.genmodel.easy.prediction.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

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
    
    Assert.assertEquals((double) row.get("embarked_te"), (double) encodingComponents[0] / encodingComponents[1], 1e-5);
    Assert.assertNotEquals((double) row.get("embarked_te"), (double) encodingComponents[0] / encodingComponents[1] + 0.1, 1e-5);
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
