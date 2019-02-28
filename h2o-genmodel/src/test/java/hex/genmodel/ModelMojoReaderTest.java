package hex.genmodel;

import org.junit.Test;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Map;

import static org.junit.Assert.*;

public class ModelMojoReaderTest {

  public static class TestMojoReader extends ModelMojoReader {
    @Override public String getModelName() { return null; } 
    @Override protected void readModelData() throws IOException { } 
    @Override protected MojoModel makeModel(String[] columns, String[][] domains, String responseColumn) { return null; } 
    @Override public String mojoVersion() { return null; }
  }
  
  @Test
  public void parseTargetEncodingMap() {
    
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

    try {

      TestMojoReader testMojoReader = new TestMojoReader();
//      TestMojoReader spyMojoReader = PowerMockito.spy(testMojoReader);
//      PowerMockito.doNothing().when(spyMojoReader, "readAll");

      Map<String, Map<String, int[]>> parsedEncodings = testMojoReader.parseTargetEncodingMap(encodingsBR);
      assertArrayEquals(parsedEncodings.get("embarked").get("C"), new int[] {2,4});

    } catch (Exception ex) {
      System.out.println(ex.getMessage());
    }
  }
}
