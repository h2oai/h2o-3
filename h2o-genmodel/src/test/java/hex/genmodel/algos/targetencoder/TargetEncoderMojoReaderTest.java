package hex.genmodel.algos.targetencoder;

import hex.genmodel.MojoReaderBackend;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TargetEncoderMojoReaderTest {
  
  public static class TestMojoReader extends TargetEncoderMojoReader {
    MojoReaderBackend _mojoReaderBackend;

    public TestMojoReader(MojoReaderBackend mojoReaderBackend) {
      _mojoReaderBackend = mojoReaderBackend;
    }

    @Override public String getModelName() { return null; }
    @Override protected void readModelData() throws IOException {
      _model._nclasses = 2;
      _model.setEncodings(parseEncodingMap());
    }
    @Override public String mojoVersion() { return null; }
    
    public void initModel() {
      _model = makeModel(
              new String[]{"embarked", "sex", "foo", "bar", "response"}, 
              new String[][]{
                      new String[] {"n", "y"},
                      new String[] {"f", "m"},
                      null, 
                      null,
                      null
              }, 
              "response"
      );
    }
    
    public TargetEncoderMojoModel getModel() {
      return _model;
    }

    @Override
    public MojoReaderBackend getMojoReaderBackend() {
      return _mojoReaderBackend;
    }
  }

  @Test
  public void parseTargetEncodingMap() throws Exception {

    String test = new StringBuilder()
            .append("[embarked]\n")
            .append("0 = 2 4\n")
            .append("1 = 5 7\n")
            .append("[sex]\n")
            .append("0 = 3 42\n")
            .append("1 = 5 42")
            .toString();

    Reader inputString = new StringReader(test);
    BufferedReader encodingsBR = new BufferedReader(inputString);

    MojoReaderBackend mojoReaderBackendMock = mock(MojoReaderBackend.class);

    when(mojoReaderBackendMock.exists(anyString())).thenReturn(true);
    when(mojoReaderBackendMock.getTextFile(anyString())).thenReturn(encodingsBR);

    TestMojoReader testMojoReader = new TestMojoReader(mojoReaderBackendMock);
    testMojoReader.initModel();
    testMojoReader.readModelData();
    Map<String, EncodingMap> parsedEncodings = testMojoReader.getModel()._encodingsByCol;
    assertArrayEquals(parsedEncodings.get("embarked").getNumDen(0), new double[]{2, 4}, 1e-8);
    assertArrayEquals(parsedEncodings.get("sex").getNumDen(0), new double[]{3, 42}, 1e-8);

  }
}
