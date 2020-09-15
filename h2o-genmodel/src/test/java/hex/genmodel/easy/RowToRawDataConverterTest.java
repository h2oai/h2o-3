package hex.genmodel.easy;

import hex.genmodel.easy.error.VoidErrorConsumer;
import hex.genmodel.easy.exception.PredictException;
import hex.genmodel.easy.stub.TestMojoModel;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class RowToRawDataConverterTest {

  @Test
  public void convert() throws PredictException {

    RowData rowToPredictFor = new RowData();
    String columnName1 = "embarked";
    rowToPredictFor.put(columnName1, "S");
    String columnName2 = "sex";
    rowToPredictFor.put(columnName2, "female");
    String columnName3 = "age";
    rowToPredictFor.put(columnName3, "42.0");

    VoidErrorConsumer errorConsumer = new VoidErrorConsumer();
    
    // Indices for this map are based on `m.getNames()` method
    HashMap<String, Integer> modelColumnNameToIndexMap = new HashMap<>();
    modelColumnNameToIndexMap.put(columnName2, 2);
    modelColumnNameToIndexMap.put(columnName1, 0);
    modelColumnNameToIndexMap.put(columnName3, 1);

    TestMojoModel testMojoModel = new TestMojoModel();

    // Indices for this map are based on `m.getDomainValues()` method
    Map<Integer, CategoricalEncoder> domainMap = new EnumEncoderDomainMapConstructor(testMojoModel, modelColumnNameToIndexMap).create();
    RowToRawDataConverter rowToRawDataConverter = new DefaultRowToRawDataConverter(modelColumnNameToIndexMap, domainMap, errorConsumer, new EasyPredictModelWrapper.Config());

    double[] rawData = new double[rowToPredictFor.size()];
    rowToRawDataConverter.convert(rowToPredictFor, rawData);

    
    // assumption that is being checked: Raw array should be filled up with respect to column indices in the `modelColumnNameToIndexMap`
    // Note, that `modelColumnNameToIndexMap` and `domainMap` are constructed based on `_names` and `_domains` respectively and that they must have same order of columns.
    // Expected order is: {embarked, age, sex}
    assertArrayEquals(new double[]{0, 42, 1}, rawData, 1e-5);
  }
}
