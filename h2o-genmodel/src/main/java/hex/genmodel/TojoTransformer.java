package hex.genmodel;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// TODO It should not represent only TE Transformer, For POC it is ok.
public class TojoTransformer {

  public Map<String, Map<String, int[]>> encodingMap;

  public TojoTransformer(Map<String, Map<String, int[]>> encodingMap) {
    this.encodingMap = encodingMap;
  }

  public static TojoTransformer loadTransformer(String file) throws IOException {
    File f = new File(file);
    if (!f.exists())
      throw new FileNotFoundException("File " + file + " cannot be found.");
    MojoReaderBackend cr = f.isDirectory()? new FolderMojoReaderBackend(file)
            : new ZipfileMojoReaderBackend(file);
    return parseTargetEncodingTransformer(cr, "feature_engineering/target_encoding.ini");
  }

  static TojoTransformer parseTargetEncodingTransformer(MojoReaderBackend cr, String pathToSource) throws IOException {
    Map<String, Map<String, int[]>> encodingMap = null;

    if(cr.exists(pathToSource)) {
      BufferedReader source = cr.getTextFile("feature_engineering/target_encoding.ini");

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
    return new TojoTransformer(encodingMap);
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
