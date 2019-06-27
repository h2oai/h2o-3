package hex.genmodel.algos.targetencoder;

import hex.genmodel.MojoReaderBackend;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

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
    @Override protected void readModelData() throws IOException { }
    @Override public String mojoVersion() { return null; }

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

    EncodingMaps parsedEncodings = testMojoReader.parseEncodingMap("pathToFileWithEncodings");
    assertArrayEquals(parsedEncodings.get("embarked").get(0), new int[]{2, 4});
    assertArrayEquals(parsedEncodings.get("sex").get(0), new int[]{3, 42});

  }
}
