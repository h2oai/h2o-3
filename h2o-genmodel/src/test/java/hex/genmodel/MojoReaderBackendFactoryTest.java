package hex.genmodel;

import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.prediction.RegressionModelPrediction;
import org.junit.Test;

import java.io.Closeable;
import java.io.File;
import java.net.URL;

import static org.junit.Assert.*;
import static hex.genmodel.MojoReaderBackendFactory.CachingStrategy;

public class MojoReaderBackendFactoryTest {

  @Test
  public void testCreateReaderBackend_URL_Memory() throws Exception {
    URL dumjo = MojoReaderBackendFactoryTest.class.getResource("dumjo.zip");
    assertNotNull(dumjo);
    MojoReaderBackend r = MojoReaderBackendFactory.createReaderBackend(dumjo, CachingStrategy.MEMORY);
    assertTrue(r instanceof InMemoryMojoReaderBackend);
    try {
      assertTrue(r.exists("binary-file"));
      assertTrue(r.exists("text-file"));
      assertEquals("line1", r.getTextFile("text-file").readLine());
    } finally {
      ((Closeable) r).close();
    }
  }

  @Test
  public void testCreateReaderBackend_URL_Disk() throws Exception {
    URL dumjo = MojoReaderBackendFactoryTest.class.getResource("dumjo.zip");
    assertNotNull(dumjo);
    MojoReaderBackend r = MojoReaderBackendFactory.createReaderBackend(dumjo, CachingStrategy.DISK);
    assertTrue(r instanceof TmpMojoReaderBackend);
    File tempFile = ((TmpMojoReaderBackend) r)._tempZipFile;
    assertTrue(tempFile.exists());
    try {
      assertTrue(r.exists("binary-file"));
      assertTrue(r.exists("text-file"));
      assertEquals("line1", r.getTextFile("text-file").readLine());
    } finally {
      ((Closeable) r).close();
    }
    assertFalse(tempFile.exists());
  }

  @Test
  public void testMojoE2E_Memory() throws Exception {
    testMojoE2E(CachingStrategy.MEMORY);
  }

  @Test
  public void testMojoE2E_Disk() throws Exception {
    testMojoE2E(CachingStrategy.DISK);
  }

  private void testMojoE2E(CachingStrategy cachingStrategy) throws Exception {
    URL mojoSource = MojoReaderBackendFactoryTest.class.getResource("mojo.zip");
    assertNotNull(mojoSource);
    MojoReaderBackend reader = MojoReaderBackendFactory.createReaderBackend(mojoSource, cachingStrategy);
    MojoModel model = ModelMojoReader.readFrom(reader);
    EasyPredictModelWrapper modelWrapper = new EasyPredictModelWrapper(model);
    RowData testRow = makeTestRow();
    RegressionModelPrediction prediction = (RegressionModelPrediction) modelWrapper.predict(testRow);
    assertEquals(71.085d, prediction.value, 0.001d);
  }

  private static RowData makeTestRow() {
    RowData testRow = new RowData();
    String[] row = ("75,0,190,80,91,193,371,174,121,-16,13,64,-2,0,63,0,52,44,0,0,32,0,0,0,0,0,0,0,44,20,36,0,28,0,0,0,0," +
            "0,0,52,40,0,0,0,60,0,0,0,0,0,0,52,0,0,0,0,0,0,0,0,0,0,0,0,56,36,0,0,32,0,0,0,0,0,0,48,32,0,0,0,56,0,0,0,0,0," +
            "0,80,0,0,0,0,0,0,0,0,0,0,0,0,40,52,0,0,28,0,0,0,0,0,0,0,48,48,0,0,32,0,0,0,0,0,0,0,52,52,0,0,36,0,0,0,0,0,0," +
            "0,52,48,0,0,32,0,0,0,0,0,0,0,56,44,0,0,32,0,0,0,0,0,0,-0.2,0.0,6.1,-1.0,0.0,0.0,0.6,2.1,13.6,30.8,0.0,0.0,1.7," +
            "-1.0,0.6,0.0,1.3,1.5,3.7,14.5,0.1,-5.2,1.4,0.0,0.0,0.0,0.8,-0.6,-10.7,-15.6,0.4,-3.9,0.0,0.0,0.0,0.0,-0.8,-1.7," +
            "-10.1,-22.0,0.0,0.0,5.7,-1.0,0.0,0.0,-0.1,1.2,14.1,22.5,0.0,-2.5,0.8,0.0,0.0,0.0,1.0,0.4,-4.8,-2.7,0.1,-6.0,0.0" +
            ",0.0,0.0,0.0,-0.8,-0.6,-24.0,-29.7,0.0,0.0,2.0,-6.4,0.0,0.0,0.2,2.9,-12.6,15.2,-0.1,0.0,8.4,-10.0,0.0,0.0,0.6,5.9," +
            "-3.9,52.7,-0.3,0.0,15.2,-8.4,0.0,0.0,0.9,5.1,17.7,70.7,-0.4,0.0,13.5,-4.0,0.0,0.0,0.9,3.9,25.5,62.9,-0.3,0.0,9.0," +
            "-0.9,0.0,0.0,0.9,2.9,23.3,49.4,8")
            .split(",");
    for (int i = 0; i < row.length; i++)
      testRow.put("C" + (i+1), Double.valueOf(row[i]));
    return testRow;
  }

}