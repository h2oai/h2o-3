package hex.genmodel.algos.targetencoder;

import hex.genmodel.ModelMojoReader;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TargetEncoderMojoReader extends ModelMojoReader<TargetEncoderMojoModel> {

  @Override
  public String getModelName() {
    return "TargetEncoder";
  }

  @Override
  protected void readModelData() throws IOException {
    _model._withBlending = readkv("with_blending");
    if(_model._withBlending) {
      _model._inflectionPoint = readkv("inflection_point");
      _model._smoothing = readkv("smoothing");
    }
    _model._targetEncodingMap = parseEncodingMap("feature_engineering/target_encoding/encoding_map.ini");
    _model._teColumnNameToIdx = parseTEColumnNameToIndexMap("feature_engineering/target_encoding/te_column_name_to_idx_map.ini");
  }

  @Override
  protected TargetEncoderMojoModel makeModel(String[] columns, String[][] domains, String responseColumn) {
    return new TargetEncoderMojoModel(columns, domains, responseColumn);
  }
  
  public Map<String, Integer> parseTEColumnNameToIndexMap(String pathToSource) throws IOException {
    Map<String, Integer> teColumnNameToIndexMap = new HashMap<>();
    if(exists(pathToSource)) {
      Iterable<String> parsedFile = readtext(pathToSource);
      for(String line : parsedFile) {
        String[] nameAndIndex = line.split("\\s*=\\s*", 2);
        teColumnNameToIndexMap.put(nameAndIndex[0], Integer.parseInt(nameAndIndex[1]));
      }
    }
    return teColumnNameToIndexMap;
  }
  
    public EncodingMaps parseEncodingMap(String pathToSource) throws IOException {
    Map<String, EncodingMap> encodingMaps = null;

    if(exists(pathToSource)) {
      BufferedReader source = getMojoReaderBackend().getTextFile(pathToSource);

      encodingMaps = new HashMap<>();
      Map<Integer, int[]> encodingsForColumn = null;
      String sectionName = null;
      try {
        String line;

        while (true) {
          line = source.readLine();
          if (line == null) { // EOF
            encodingMaps.put(sectionName, new EncodingMap(encodingsForColumn));
            break;
          }
          line = line.trim();
          if (sectionName == null) {
            sectionName = matchNewSection(line);
            encodingsForColumn = new HashMap<>();
          } else {
            String matchResult = matchNewSection(line);
            if (matchResult != null) {
              encodingMaps.put(sectionName, new EncodingMap(encodingsForColumn));
              encodingsForColumn = new HashMap<>();
              sectionName = matchResult;
              continue;
            }

            String[] res = line.split("\\s*=\\s*", 2);
            int[] numAndDenom = processNumeratorAndDenominator(res[1].split(" "));
            encodingsForColumn.put(Integer.parseInt(res[0]), numAndDenom);
          }
        }
        source.close();
      } finally {
        try {
          source.close();
        } catch (IOException e) { /* ignored */ }
      }
    }
    return new EncodingMaps(encodingMaps);
  }

  private String matchNewSection(String line) {
    Pattern pattern = Pattern.compile("\\[(.*?)\\]");
    Matcher matcher = pattern.matcher(line);
    if (matcher.find()) {
      return matcher.group(1);
    } else return null;
  }

  private int[] processNumeratorAndDenominator(String[] strings) {
    int[] intArray = new int[strings.length];
    int i = 0;
    for (String str : strings) {
      intArray[i] = Integer.parseInt(str);
      i++;
    }
    return intArray;
  }

  @Override public String mojoVersion() {
    return "1.00";
  }

}
