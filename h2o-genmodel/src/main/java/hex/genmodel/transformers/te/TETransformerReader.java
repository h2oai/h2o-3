package hex.genmodel.transformers.te;

import hex.genmodel.MojoReaderBackend;
import hex.genmodel.TETransformer;
import hex.genmodel.TransformerMojoReader;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 */
public class TETransformerReader extends TransformerMojoReader<TETransformer> {
  @Override
  public String getTransformerName() {
    return "TE transformer";
  }

  @Override public String mojoVersion() {
    return "1.00";
  }

  @Override
  public void readTransformerSpecific() throws IOException {
    _transformer.encodingMap = parseTargetEncodingMap("feature_engineering/target_encoding.ini");
  
  }

  private Map<String, Map<String, int[]>> parseTargetEncodingMap(String pathToSource) throws IOException {
    Map<String, Map<String, int[]>> encodingMap = null;

    if(_reader.exists(pathToSource)) {
      BufferedReader source = _reader.getTextFile("feature_engineering/target_encoding.ini");

      encodingMap = new HashMap<>();
      Map<String, int[]> encodingsForColumn = null;
      String sectionName = null;
      try {
        String line;

        while (true) {
          line = source.readLine();
          if (line == null) { // EOF
            encodingMap.put(sectionName, encodingsForColumn);
            break;
          }
          line = line.trim();
          if (sectionName == null) {
            sectionName = matchNewSection(line);
            encodingsForColumn = new HashMap<>();
          } else {
            String matchResult = matchNewSection(line);
            if (matchResult != null) {
              encodingMap.put(sectionName, encodingsForColumn);
              encodingsForColumn = new HashMap<>();
              sectionName = matchResult;
              continue;
            }

            String[] res = line.split("\\s*=\\s*", 2);
            int[] numAndDenom = processNumeratorAndDenominator(res[1].split(" "));
            encodingsForColumn.put(res[0], numAndDenom);
          }
        }
        source.close();
      } finally {
        try {
          source.close();
        } catch (IOException e) { /* ignored */ }
      }
    }
    return encodingMap;
  }

  static private String matchNewSection(String line) {
    Pattern pattern = Pattern.compile("\\[(.*?)\\]");
    Matcher matcher = pattern.matcher(line);
    if (matcher.find()) {
      return matcher.group(1);
    } else return null;
  }

  static private int[] processNumeratorAndDenominator(String[] strings) {
    int[] intArray = new int[strings.length];
    int i = 0;
    for (String str : strings) {
      intArray[i] = Integer.parseInt(str);
      i++;
    }
    return intArray;
  }
}
