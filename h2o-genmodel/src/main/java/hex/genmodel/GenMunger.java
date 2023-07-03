package hex.genmodel;

import hex.genmodel.easy.RowData;

import java.io.Serializable;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GenMunger implements Serializable {
  public Step[] _steps;
  public String[] inTypes() { return _steps[0].types(); }
  public String[] inNames() { return _steps[0].names(); }
  public String[] outNames() { return _steps[_steps.length-1].outNames(); }
  public abstract class Step<T> implements Serializable {
    private final String[] _names;
    private final String[] _types;
    private final String[] _outNames;
    protected HashMap<String, String[]> _params;
    public abstract RowData transform(RowData row);
    public Step(String[] inNames, String[] inTypes, String[] outNames) {_names=inNames; _types=inTypes; _outNames=outNames; _params = new HashMap<>(); }
    public String[] outNames() { return _outNames; }
    public String[] names() { return _names; }
    public String[] types() { return _types; }
    public HashMap<String,String[]> params() { return _params; }
  }
  public RowData fit(RowData row) {
    if( row==null ) return null;
    for(Step s: _steps)
      row = s.transform(row);
    return row;
  }

  public RowData fillDefault(String[] vals) {
    RowData row = new RowData();
    String[] types = inTypes();
    String[] names = inNames();
    for(int i=0;i<types.length;++i)
      row.put(names[i],vals==null?null:valueOf(types[i],vals[i]));
    return row;
  }

  private static Double parseNum(String n){
    if(n==null || n.equals("") || n.isEmpty()) return Double.NaN;
    return Double.valueOf(n);
  }

  private static Object valueOf(String type, String val) {
    val = val.replaceAll("^\"|\"$", ""); // strip any bounding quotes
    return type.equals("Numeric")
            ? parseNum(val)
            : val;
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
    if( riteArg!=null ) return d + parseNum(riteArg[0]);
    return parseNum(leftArg[0]) + d;
  }
  public static double minus(double d, HashMap<String, String[]> parameters) {
    String[] leftArg = parameters.get("leftArg");
    String[] riteArg = parameters.get("rightArg");
    if( riteArg!=null ) return d - parseNum(riteArg[0]);
    return parseNum(leftArg[0]) - d;
  }
  public static double multiply(double d, HashMap<String,String[]> parameters) {
    String[] leftArg = parameters.get("leftArg");
    String[] riteArg = parameters.get("rightArg");
    if( riteArg!=null ) return d * parseNum(riteArg[0]);
    return parseNum(leftArg[0]) * d;
  }
  public static double divide(double d, HashMap<String,String[]> parameters) {
    String[] leftArg = parameters.get("leftArg");
    String[] rightArg = parameters.get("rightArg");
    if( rightArg!=null ) return d / parseNum(rightArg[0]);
    return parseNum(leftArg[0]) / d;
  }
  public static double mod(double d, HashMap<String,String[]> parameters) {
    String leftArg = parameters.get("leftArg")[0];
    String rightArg = parameters.get("rightArg")[0];
    if( rightArg!=null ) return d % parseNum(rightArg);
    return parseNum(leftArg) % d;
  }
  public static double pow(double d, HashMap<String, String[]> parameters) {
    String leftArg = parameters.get("leftArg")[0];
    String rightArg = parameters.get("rightArg")[0];
    if( rightArg!=null ) return Math.pow(d,parseNum(rightArg));
    return Math.pow(parseNum(leftArg),d);
  }

  private static double and(double l, double r) {
    return (l == 0 || r == 0) ? 0 : (Double.isNaN(l) || Double.isNaN(r) ? Double.NaN : 1);
  }
  
  public static double and(double d, HashMap<String, String[]> parameters) {
    String leftArg = parameters.get("leftArg")[0];
    String rightArg = parameters.get("rightArg")[0];
    if( rightArg!=null ) return and(d, parseNum(rightArg));
    return and(parseNum(leftArg), d);
  }

  private static double or(double l, double r) {
    return (l == 1 || r == 1) ? 1 : (Double.isNaN(l) || Double.isNaN(r) ? Double.NaN : 0);
  }
  public static double or(double d, HashMap<String, String[]> parameters) {
    String leftArg = parameters.get("leftArg")[0];
    String rightArg = parameters.get("rightArg")[0];
    if( rightArg!=null ) return or(d, parseNum(rightArg));
    return or(parseNum(leftArg), d);
  }
  
  private static double intDiv(double l, double r) {
    return (((int) r) == 0) ? Double.NaN : (int) l / (int) r;
  }
  
  public static double intDiv(double d, HashMap<String, String[]> parameters) {
    String leftArg = parameters.get("leftArg")[0];
    String rightArg = parameters.get("rightArg")[0];
    if( rightArg!=null ) return intDiv(d, parseNum(rightArg));
    return intDiv(parseNum(leftArg), d);
  }
  
  public static String[] strsplit(String s, HashMap<String,String[]> parameters) {
    String pattern = parameters.get("split")[0];
    return s.split(pattern);
  }
  public static double asnumeric(String s, HashMap<String, String[]> parameters) {
    return parseNum(s);
  }
  public static String trim(String s, HashMap<String, String[]> parameters) {
    return s.trim();
  }
  public static String replaceall(String s, HashMap<String, String[]> parameters) {
    String pattern = parameters.get("pattern")[0];
    String replacement = parameters.get("replacement")[0];
    boolean ignoreCase = parameters.get("ignore_case")[0].equals("TRUE");
    return ignoreCase
            ? s.replaceAll("(?i)"+Pattern.quote(pattern),replacement)
            : s.replaceAll(pattern,replacement);
  }
  public static String toupper(String s, HashMap<String, String[]> parameters) {
    return s.toUpperCase();
  }
  public static String tolower(String s, HashMap<String, String[]> parameters) {
    return s.toLowerCase();
  }
  public static String cut(double d, HashMap<String, String[]> parameters) {
    String[] breaks = parameters.get("breaks");
    String[] labels = parameters.get("labels");
    boolean lowest = parameters.get("include_lowest")[0].equals("TRUE");
    boolean rite = parameters.get("right")[0].equals("TRUE");
    if( Double.isNaN(d) || (lowest && d < parseNum(breaks[0]))
            || (!lowest && d <= parseNum(breaks[0]))
            || (rite    && d >  parseNum(breaks[breaks.length-1]))
            || (!rite   && d >= parseNum(breaks[breaks.length-1]))) return "";
    else {
      for(int i=1;i<breaks.length;++i) {
        if( rite )
          if( d <= parseNum(breaks[i]) ) return labels[i-1];
        else if( d < parseNum(breaks[i]) ) return labels[i-1];
      }
    }
    return "";
  }
  public static double nchar(String s, HashMap<String, String[]> parameters) {
    return s.length();
  }
}
