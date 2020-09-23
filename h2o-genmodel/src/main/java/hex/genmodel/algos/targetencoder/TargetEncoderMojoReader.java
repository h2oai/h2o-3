package hex.genmodel.algos.targetencoder;

import hex.genmodel.ModelMojoReader;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TargetEncoderMojoReader extends ModelMojoReader<TargetEncoderMojoModel> {
  
  public static final String ENCODING_MAP_PATH = "feature_engineering/target_encoding/encoding_map.ini";
  public static final String MISSING_VALUES_PRESENCE_MAP_PATH = "feature_engineering/target_encoding/te_column_name_to_missing_values_presence.ini";

  @Override
  public String getModelName() {
    return "TargetEncoder";
  }

  @Override
  protected void readModelData() throws IOException {
    _model._keepOriginalCategoricalColumns = readkv("keep_original_categorical_columns", false); // defaults to false for legacy TE Mojos
    _model._withBlending = readkv("with_blending");
    if(_model._withBlending) {
      _model._inflectionPoint = readkv("inflection_point");
      _model._smoothing = readkv("smoothing");
    }
    _model._nonPredictors = Arrays.asList((readkv("non_predictors", "")).split(";"));
    _model.setEncodings(parseEncodingMap());
    _model._teColumn2HasNAs = parseTEColumnsToHasNAs();
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
  
  protected EncodingMaps parseEncodingMap() throws IOException {
    if (!exists(ENCODING_MAP_PATH)) {
      return null;
    }
    Map<String, EncodingMap> encodingMaps = new HashMap<>();
    try (BufferedReader source = getMojoReaderBackend().getTextFile(ENCODING_MAP_PATH)) {
      EncodingMap colEncodingMap = new EncodingMap(_model.nclasses());
      String sectionName = null;
      String line;

      while (true) {
        line = source.readLine();
        if (line == null) { // EOF
          encodingMaps.put(sectionName, colEncodingMap);
          break;
        }
        line = line.trim();
        String matchSection = matchNewSection(line);
        if (sectionName == null || matchSection != null) {
          if (sectionName != null) encodingMaps.put(sectionName, colEncodingMap); // section completed
          sectionName = matchSection;
          colEncodingMap = new EncodingMap(_model.nclasses());
        } else {
          String[] res = line.split("\\s*=\\s*", 2);
          double[] components = processEncodingsComponents(res[1].split(" "));
          colEncodingMap.add(Integer.parseInt(res[0]), components);
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

  private double[] processEncodingsComponents(String[] componentsStr) {
    // note that there may be additional entries in those arrays outside the numerator and denominator.
    // for multiclass problems, the last entry correspond to the target class associated with the num/den values.
    double[] numDen = new double[componentsStr.length];
    int i = 0;
    for (String str : componentsStr) {
      numDen[i] = Double.parseDouble(str);
      i++;
    }
    return numDen;
  }

  @Override public String mojoVersion() {
    return "1.00";
  }

}
