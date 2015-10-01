package hex.genmodel;

import hex.genmodel.easy.RowData;

import java.io.Serializable;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GenMunger implements Serializable {
  protected Step[] _steps;
  String[] inTypes() { return _steps[0].types(); }
  String[] inNames() { return _steps[0].names(); }
  public abstract class Step<T> {
    private final String[] _names;
    private final String[] _types;
    protected HashMap<String, String[]> _params;
    public abstract RowData transform(RowData row);
    public Step(String[] inNames, String[] inTypes) {_names=inNames; _types=inTypes; _params = new HashMap<>(); }
    public String[] names() { return _names; }
    public String[] types() { return _types; }
    public HashMap<String,String[]> params() { return _params; }
  }
  public RowData fit(RowData row) {
    for(Step s: _steps)
      row = s.transform(row);
    return row;
  }

  // currents/transforms/GenMunger utilities
  public static void scaleInPlace(final double[] means, final double[] mults, double[] in) {
    for(int i=0; i<in.length; ++i)
      in[i] = (in[i]-means[i])*mults[i];
  }
  public static double cos(double r, HashMap<String,String[]> parameters) { return Math.cos(r); }
  public static double sin(double r, HashMap<String,String[]> parameters) { return Math.sin(r); }
  public static double countmatches(String s, HashMap<String,String[]> parameters) {
    String[] patterns = parameters.get("pattern");
    return countMatches(s, patterns);
  }
  private static int countMatches(String s, String[] pattern) {
    int cnt=0;
    for(String pat: pattern) {
      Pattern p = Pattern.compile(pat);
      Matcher m = p.matcher(s);
      while(m.find()) cnt++;
    }
    return cnt;
  }
  public static double add(double d, HashMap<String, String[]> parameters) {
    String[] leftArg = parameters.get("leftArg");
    String[] riteArg = parameters.get("rightArg");
    if( leftArg==null ) return d + Double.valueOf(riteArg[0]);
    return Double.valueOf(leftArg[0]) + d;
  }
  public static double minus(double d, HashMap<String, String[]> parameters) {
    String[] leftArg = parameters.get("leftArg");
    String[] riteArg = parameters.get("rightArg");
    if( leftArg==null ) return d - Double.valueOf(riteArg[0]);
    return Double.valueOf(leftArg[0]) - d;
  }
  public static double multiply(double d, HashMap<String,String[]> parameters) {
    String[] leftArg = parameters.get("leftArg");
    String[] riteArg = parameters.get("rightArg");
    if( leftArg==null ) return d * Double.valueOf(riteArg[0]);
    return Double.valueOf(leftArg[0]) * d;
  }
  public static double divide(double d, HashMap<String,String[]> parameters) {
    String[] leftArg = parameters.get("leftArg");
    String[] riteArg = parameters.get("rightArg");
    if( leftArg==null ) return d / Double.valueOf(riteArg[0]);
    return Double.valueOf(leftArg[0]) / d;
  }
}