package hex.genmodel;

import org.junit.Test;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ModelMojoReaderTest {

  public static class TestMojoReader extends ModelMojoReader {
    MojoReaderBackend _mojoReaderBackend;
    
    public TestMojoReader(MojoReaderBackend mojoReaderBackend) {
      _mojoReaderBackend = mojoReaderBackend;
    }

    @Override public String getModelName() { return null; } 
    @Override protected void readModelData() throws IOException { } 
    @Override protected MojoModel makeModel(String[] columns, String[][] domains, String responseColumn) { return null; } 
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
            .append("C = 2 4\n")
            .append("D = 5 7\n")
            .append("[sex]\n")
            .append("Male = 3 42\n")
            .append("Female = 5 42")
            .toString();

    Reader inputString = new StringReader(test);
    BufferedReader encodingsBR = new BufferedReader(inputString);

    MojoReaderBackend mojoReaderBackendMock = mock(MojoReaderBackend.class);

    when(mojoReaderBackendMock.exists(anyString())).thenReturn(true);
    when(mojoReaderBackendMock.getTextFile(anyString())).thenReturn(encodingsBR);
    
    TestMojoReader testMojoReader = new TestMojoReader(mojoReaderBackendMock);

    Map<String, Map<String, int[]>> parsedEncodings = testMojoReader.parseTargetEncodingMap("pathToFileWithEncodings");
    assertArrayEquals(parsedEncodings.get("embarked").get("C"), new int[]{2, 4});
    assertArrayEquals(parsedEncodings.get("sex").get("Male"), new int[]{3, 42});

  }
}
