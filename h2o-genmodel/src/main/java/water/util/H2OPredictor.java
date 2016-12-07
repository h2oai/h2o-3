package water.util;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import hex.genmodel.GenModel;
import hex.genmodel.easy.exception.PredictException;
import hex.genmodel.easy.prediction.AbstractPrediction;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.MojoModel;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * Created by magnus on 5/5/16.
 */
public class H2OPredictor {
  private static boolean DEBUG = false;

  private EasyPredictModelWrapper model = null;

  private Gson gson = new Gson();

    public H2OPredictor(String ojoFileName, String modelName) {
    try {
      if (ojoFileName == null)
        throw new Exception("file name can't be null");
      else if (ojoFileName.endsWith(".jar")) {
	  //        String modelName = ojoFileName.replace(".zip", "").replace(".jar", "");
        loadPojo(ojoFileName, modelName);
      }
      else if (ojoFileName.endsWith(".zip"))
        loadMojo(ojoFileName);
      else
        throw new Exception("unknown model archive type");
      if (DEBUG) System.out.println("loaded " + ojoFileName);
    }
    catch (Exception e) {
      System.out.println("{ \"error\": \"" + e.getMessage() + "\" }");
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
    return gson.toJson(pr);
  }

  public static String predict3(String ojoFileName, String modelName, String jsonArgs) {
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
	  byte[] bytes = Files.readAllBytes(Paths.get(jsonArgs));
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
    catch (Exception e) {
      return "{ \"error\": \"" + e.getMessage() + "\" }";
    }
  }

  public String pred(String jsonArgs) {
    try {
      return predictRow(jsonToRowData(jsonArgs));
    }
    catch (Exception e) {
      return "{ \"error\": \"" + e.getMessage() + "\" }";
    }
  }

  public static String predict2(String ojoFileName, String jsonArgs) {
      String modelName = ojoFileName.replace(".zip", "").replace(".jar", "");
      if (DEBUG) System.out.println("predict2 modelName\t" + modelName);
      return predict3(ojoFileName, modelName, jsonArgs);
  }

  public static void main(String[] args) {
    String result = "";

    if (DEBUG) System.out.println("args\t" + Arrays.toString(args));

    if (args.length == 2)
	result = predict2(args[0], args[1].replaceAll("\\\\", ""));
    else if (args.length == 3)
      result = predict3(args[0], args[1], args[2].replaceAll("\\\\", ""));
    else
      result = "{ \"error\": \"Neeed 2 or 3 args have " + args.length + ", \"usage\": \"mojoFile jsonString  or: jarFile modelName jsonString\" } ";

    System.out.println(result);
  }

}
