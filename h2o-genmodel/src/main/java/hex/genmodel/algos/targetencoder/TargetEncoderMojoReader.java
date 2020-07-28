package hex.genmodel.algos.targetencoder;

import hex.genmodel.ModelMojoReader;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TargetEncoderMojoReader extends ModelMojoReader<TargetEncoderMojoModel> {
  
  private static final String ENCODING_MAP_PATH = "feature_engineering/target_encoding/encoding_map.ini";
  
  private static final String MISSING_VALUES_PRESENCE_MAP_PATH = "feature_engineering/target_encoding/te_column_name_to_missing_values_presence.ini";

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
    _model._targetEncodingMap = parseEncodingMap();
    _model._teColumn2HasNAs = parseTEColumnsToHasNAs();
    _model._priorMean = readkv("priorMean");
  }

  @Override
  protected TargetEncoderMojoModel makeModel(String[] columns, String[][] domains, String responseColumn) {
    return new TargetEncoderMojoModel(columns, domains, responseColumn);
  }
  
  private Map<String, Boolean> parseTEColumnsToHasNAs() throws IOException {
    Map<String, Boolean> cols2HasNAs = new HashMap<>();
    if (exists(MISSING_VALUES_PRESENCE_MAP_PATH)) {
      Iterable<String> parsedFile = readtext(MISSING_VALUES_PRESENCE_MAP_PATH);
      for (String line : parsedFile) {
        String[] indexAndPresence = line.split("\\s*=\\s*", 2);
        cols2HasNAs.put(indexAndPresence[0], Integer.parseInt(indexAndPresence[1]) == 1);
      }
    }
    return cols2HasNAs;
  }
  
  public EncodingMaps parseEncodingMap() throws IOException {
    if (!exists(ENCODING_MAP_PATH)) {
      return null;
    }
    Map<String, EncodingMap> encodingMaps;
    try (BufferedReader source = getMojoReaderBackend().getTextFile(ENCODING_MAP_PATH)) {
      encodingMaps = new HashMap<>();
      Map<Integer, double[]> encodingsForColumn = null;
      String sectionName = null;
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
          double[] numDen = processNumeratorAndDenominator(res[1].split(" "));
          encodingsForColumn.put(Integer.parseInt(res[0]), numDen);
        }
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

  private double[] processNumeratorAndDenominator(String[] numDenStr) {
    double[] numDen = new double[numDenStr.length];
    int i = 0;
    for (String str : numDenStr) {
      numDen[i] = Double.parseDouble(str);
      i++;
    }
    return numDen;
  }

  @Override public String mojoVersion() {
    return "1.00";
  }

}
