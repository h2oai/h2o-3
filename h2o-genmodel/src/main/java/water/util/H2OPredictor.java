package water.util;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import hex.genmodel.GenModel;
import hex.genmodel.easy.exception.PredictException;
import hex.genmodel.easy.prediction.AbstractPrediction;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.MojoModel;

import java.io.*;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by magnus on 5/5/16.
 */
public class H2OPredictor {
  private static final boolean DEBUG = false;

  private static boolean useLabels = false;
  private String[] labels = null;

  private EasyPredictModelWrapper model = null;

  private static final Gson gson = new Gson();
  private final Type MapType = new TypeToken<Map<String, Object>>(){}.getType();

  public H2OPredictor(String ojoFileName, String modelName) {
    if (DEBUG) System.out.printf("init  ojoFileName %s  modelName %s\n", ojoFileName, modelName);
    try {
      if (ojoFileName == null)
        throw new Exception("file name can't be null");
      else if (ojoFileName.endsWith(".jar")) {
        loadPojo(ojoFileName, modelName);
      }
      else if (ojoFileName.endsWith(".zip"))
        loadMojo(ojoFileName);
      else
        throw new Exception("unknown model archive type");
      if (useLabels)
        labels = model.getResponseDomainValues();
    }
    catch (Exception e) {
      e.printStackTrace();
      System.exit(1);

    }
  }

  private GenModel loadClassFromJar(String jarFileName, String modelName) throws Exception {
    if (DEBUG) System.out.println("jar " + jarFileName + " model " + modelName);
    if (!new File(jarFileName).isFile()) {
      throw new FileNotFoundException("Can't read " + jarFileName);
    }
    try {
      URL url = new File(jarFileName).toURI().toURL();
      ClassLoader loader = URLClassLoader.newInstance(
          new URL[]{url},
          getClass().getClassLoader()
      );
      String packagePrefix = "";
      String className = packagePrefix + modelName;
      Class<?> clazz = loader.loadClass(className);
      Class<? extends GenModel> modelClass = clazz.asSubclass(GenModel.class);
      return modelClass.newInstance();
    }
    catch (MalformedURLException e) {
      throw new Exception("Can't use Jar file" + jarFileName);
    }
    catch (ClassNotFoundException e) {
      throw new Exception("Can't find model " + modelName + " in jar file " + jarFileName);
    }
    catch (InstantiationException e) {
      throw new Exception("Can't find model " + modelName + " in jar file " + jarFileName);
    }
    catch (IllegalAccessException e) {
      throw new Exception("Can't find model " + modelName + " in jar file " + jarFileName);
    }
  }

  private void loadPojo(String jarFileName, String modelName)
      throws Exception {
    GenModel rawModel = loadClassFromJar(jarFileName, modelName);
    model = new EasyPredictModelWrapper(rawModel);
  }

  private void loadMojo(String zipFileName)
      throws Exception {
    GenModel rawModel = MojoModel.load(zipFileName);
    model = new EasyPredictModelWrapper(rawModel);
  }

  private RowData jsonToRowData(String json) {
    try {
      return gson.fromJson(json, RowData.class);
    }
    catch (JsonSyntaxException e) {
      throw new JsonSyntaxException("Malformed JSON");
    }
  }

  private RowData[] jsonToRowDataArray(String json) {
    try {
      return gson.fromJson(json, RowData[].class);
    }
    catch (JsonSyntaxException e) {
      throw new JsonSyntaxException("Malformed JSON Array");
    }
  }

  private String predictRow(RowData row) throws PredictException {
    if (model == null)
      throw new PredictException("No model loaded");
    if (gson == null)
      throw new PredictException("Gson not available");
    if (row == null)
      throw new PredictException("No row data");
    AbstractPrediction pr = model.predict(row);
    String json = gson.toJson(pr);
    if (useLabels) {
      Map<String, Object> map = gson.fromJson(json, MapType);
      map.put("responseDomainValues", labels);
      json = gson.toJson(map);
    }
    return json;
  }

  public static String predict3(String ojoFileName, String modelName, String jsonArgs) {
    if (DEBUG)
      System.out.printf("predict3  ojoFileName %s  modelName %s  jsonArgs %s\n", ojoFileName, modelName, jsonArgs);
    try {
      H2OPredictor p = new H2OPredictor(ojoFileName, modelName);
      if (ojoFileName == null)
        throw new Exception("file name can't be null");
      else if (ojoFileName.endsWith(".jar"))
        p.loadPojo(ojoFileName, modelName);
      else if (ojoFileName.endsWith(".zip"))
        p.loadMojo(ojoFileName);
      else
        throw new Exception("unknown model archive type");

      if (jsonArgs == null || jsonArgs.length() == 0)
        throw new Exception("empty json argument");

      // check if argument is a file name or json
      char first = jsonArgs.trim().charAt(0);
      boolean isJson = first == '{' || first == '[';

      if (DEBUG) {
        System.out.println("first " + first);
        System.out.println("isJson " + isJson);
      }

      if (!isJson) {
        // argument is a file name
        byte[] bytes = readFile(jsonArgs);
        jsonArgs = new String(bytes);
        first = jsonArgs.trim().charAt(0);
        isJson = first == '{' || first == '[';
      }

      if (DEBUG) System.out.println("jsonArgs " + jsonArgs);

      String result = "";
      if (first == '[') {
        RowData[] rows = p.jsonToRowDataArray(jsonArgs);
        result += "[ ";
        for (RowData row : rows) {
          if (DEBUG) System.out.println("rowdata\t" + row);
          if (!result.trim().endsWith("["))
            result += ", ";
          result += p.predictRow(row);
        }
        result += " ]";
      }
      else {
        RowData row = p.jsonToRowData(jsonArgs);
        if (DEBUG) System.out.println("rowdata\t" + row);
        result = p.predictRow(row);
      }
      return result;
    }
    catch (final Exception e) {
      Map<String, String> map = new HashMap<String, String>();
      map.put("error", stackTraceToString(e));
      String s = gson.toJson(map);
      return s;
    }
  }

  public String pred(String jsonArgs) {
    try {
      return predictRow(jsonToRowData(jsonArgs));
    }
    catch (Exception e) {
      return "{ \"error\": \"" + stackTraceToString(e) + "\" }";
    }
  }

  public static String predict2(String ojoFileName, String jsonArgs) {
    String modelName = ojoFileName.replace(".zip", "").replace(".jar", "");
    int index = modelName.lastIndexOf(File.separatorChar);
    if (index != -1) modelName = modelName.substring(index + 1);
    return predict3(ojoFileName, modelName, jsonArgs);
  }

  private static byte[] readFile(String filePath) throws IOException {
    StringBuffer fileData = new StringBuffer();
    BufferedReader reader = null;
    try {
      reader = new BufferedReader(new FileReader(filePath));
      char[] buf = new char[1024];
      int numRead = 0;
      while ((numRead = reader.read(buf)) != -1) {
        String readData = String.valueOf(buf, 0, numRead);
        fileData.append(readData);
      }
    }
    finally{
      if (reader != null)
        reader.close();
    }
    return fileData.toString().getBytes();
  }

  private static String stackTraceToString(Throwable e) {
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    PrintStream ps = new PrintStream(os);
    e.printStackTrace(ps);
    String s = os.toString();
    try {
      ps.close();
      os.close();
    }
    catch (IOException e1) {
      return "Can't get stack trace from throwable " + e.getMessage();
    }
    return s;
  }

  public static void main(String[] args) {
    if (DEBUG) System.out.println("args\t" + Arrays.toString(args));
    // -l option means add labels to output
    if (args.length > 0 && args[0].equals("-l")) {
        useLabels = true;
        args = Arrays.copyOfRange(args, 1, args.length);
    }
    String result = "";
    if (args.length == 2)
      result = predict2(args[0], args[1].replaceAll("\\\\", ""));
    else if (args.length == 3)
      result = predict3(args[0], args[1], args[2].replaceAll("\\\\", ""));
    else
      result = "{ \"error\": \"Neeed 2 or 3 args have " + args.length + ", \"usage\": \"mojoFile jsonString  or: jarFile modelName jsonString\" } ";

    System.out.println(result);
  }

}
