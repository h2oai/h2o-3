package water.init;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import water.util.Log;

/**
 * Utility for processing command
 *
 * Simple command line processing. This class provides functionality for parsing
 * command line arguments that is coded over and over again in main methods. The
 * model is that command line arguments have the form:
 *
 * <pre>
 *  option_args* free_form*
 * </pre>
 *
 * where each element in option_args is an option starting with a '-' character
 * and each element in free_form is a string. Option arguments have the syntax:
 *
 * <pre>
 *  '-'NAME[=VALUE]
 * </pre>
 *
 * where NAME is the option identifier and VALUE is the string argument for that
 * option.
 * <p>
 * An example use of the class is as follows:
 *
 * <pre>
 *  static void main(String[] args) {
 *    Arguments cl = new Arguments();
 *    cl.parse(args);
 *    if (cl.getOption("verbose") != null) ... ;
 *    String file = cl.getArgument(0);
 *    String path = cl.getOption("classpath");
 * </pre>
 *
 * @author Jan Vitek
 */
public class Arguments {

  static public abstract class Arg {
    abstract public String usage();
    abstract public boolean validate();
    @Override public String toString() {
      Field[] fields = getFields(this);
      String r="";
      for( Field field : fields ){
        String name = field.getName();
        Class cl = field.getType();
        try{
          if( cl.isPrimitive() ){
            if( cl == Boolean.TYPE ){
              boolean curval = field.getBoolean(this);
              if( curval ) r += " -"+name;
            }
            else if( cl == Integer.TYPE ) r+=" -"+name+"="+field.getInt(this);
            else if( cl == Float.TYPE )  r+=" -"+name+"="+field.getFloat(this);
            else if( cl == Double.TYPE )  r+=" -"+name+"="+field.getDouble(this);
            else if( cl == Long.TYPE )  r+=" -"+name+"="+field.getLong(this);
            else continue;
          } else if( cl == String.class )
            if (field.get(this)!=null) r+=" -"+name+"="+field.get(this);
        } catch( Exception e ) { Log.err("Argument failed with ",e); }
      }
      return r;
    }
  }

  static public class MissingArgumentError extends Error {
    final String m;
    MissingArgumentError(String s) {  m = s; }
    public String toString() {  return ( m != null ) ? m : super.toString();  }
  }

  /**
   * Optional arguments. The instance fields of this class are treated as
   * optional arguments, if they appear on the command line they will be
   * extracted and the corresponding field will be set to the extracted value.
   * If not found the field is left untouched (the orginal value is not
   * modified).
   */
  static public class Opt extends Arg {
    public String usage() {  return ""; }
    public boolean validate() {  return true;  }
  }

  /**
   * Required arguments. The instance fields of this class are treated as
   * required arguments, arguments with keywords matching each one of the
   * primitive and string fields of the object must appear on the command line.
   * If they all do they will extracted and the corresponding field will be set
   * to the extracted value. If any one of the fields is missing
   */
  static public class Req extends Arg {
    public String usage() { return ""; }
    public boolean validate() { return true; }
  }

  /** Current argument list. The list may grow and shrink as arguments are processed.
   */
  private Entry[] commandLineArgs;

  /** Create a new CommandLine object with an initial argument array.
   * @param args
   *          array of options and argument that will be parsed.
   */
  public Arguments(String[] args) { parse(args);  }


  /** Create a new CommandLine object with no arguments.   */
  public Arguments() { parse(new String[0]); }

  /**
   * Returns the number of remaining command line arguments.
   */
  public int size() { return commandLineArgs.length;  }

  public String get(int i) {  return commandLineArgs[i].val;  }

  /**
   * Add a new argument to this command line. The argument will be parsed and
   * add at the end of the list. Bindings have the following format
   * "-name=value" if value is empty, the binding is treated as an option.
   * Options have the form "-name". All other strings are treated as values.
   *
   * @param str
   *          a string
   */
  public int addArgument(String str, String next) {
    int i = commandLineArgs.length;
    int consumed = 1;
    commandLineArgs = Arrays.copyOf(commandLineArgs, i + 1);
    /*
     * Flags have a null string as val and flag of true; Binding have non-empty
     * name, a non-null val (possibly ""), and a flag of false; Plain strings
     * have an empty name, "", a non-null, non-empty val, and a flag of true;
     */
    if( str.startsWith("-") ){
      int startOffset = (str.startsWith("--"))? 2 : 1;
      String arg = "";
      String opt;
      boolean flag = false;
      int eqPos = str.indexOf("=");
      if( eqPos > 0 ||  (next!=null && !next.startsWith("-"))){
        if( eqPos > 0 ){
          opt = str.substring(startOffset, eqPos);
          arg = str.substring(eqPos + 1);
        }else{
          opt = str.substring(startOffset);
          arg = next;
          consumed = 2;
        }
      }else{
        flag = true;
        opt = str.substring(startOffset);
      }
      commandLineArgs[i] = new Entry(opt, arg, flag, i);
      return consumed;
    }else{
      commandLineArgs[i] = new Entry("", str, true, i);
      return consumed;
    }
  }

  public <TArg extends Arg> TArg extract(TArg arg) throws MissingArgumentError {
    Field[] fields = getFields(arg);
    int count = extract(arg, fields);
    if( arg instanceof Req && count != fields.length )
      throw new MissingArgumentError(arg.usage());
    return arg;
  }

  /**
   * Extracts bindings and options; and sets appropriate fields in the
   * CommandLineArgument object.
   */
  private int extract(Arg arg, Field[] fields) {
    int count = 0;
    for( Field field : fields ){
      String name = field.getName();
      Class cl = field.getType();
      String opt = getValue(name); // optional value
      try{
        if( cl.isPrimitive() ){
          if( cl == Boolean.TYPE ){
            boolean curval = field.getBoolean(arg);
            boolean xval = curval;
            if( opt != null ) xval = !curval;
            if( "1".equals(opt) || "true" .equals(opt) ) xval = true;
            if( "0".equals(opt) || "false".equals(opt) ) xval = false;
            if( opt != null ) field.setBoolean(arg, xval);
          }else if( opt == null || opt.length()==0 ) continue;
          else if( cl == Integer.TYPE ) field.setInt(arg, Integer.parseInt(opt));
          else if( cl == Float.TYPE ) field.setFloat(arg, Float.parseFloat(opt));
          else if( cl == Double.TYPE ) field.setDouble(arg, Double.parseDouble(opt));
          else if( cl == Long.TYPE ) field.setLong(arg, Long.parseLong(opt));
          else continue;
          count++;
        }else if( cl == String.class ){
          if( opt != null ){
            field.set(arg, opt);
            count++;
          }
        }
      } catch( Exception e ) { Log.err("Argument failed with ",e); }
    }
    Arrays.sort(commandLineArgs);
    for( int i = 0; i < commandLineArgs.length; i++ )
      commandLineArgs[i].position = i;
    return count;
  }


  /**
   * Return the value of a binding (e.g. "value" for "-name=value") and the
   * empty string "" for an option ("-name" or "-name="). A null value is
   * returned if no binding or option is found.
   *
   * @param name string name of the option or binding
   */
  public String getValue(String name) {
    for( Entry e : commandLineArgs )
      if( name.equals(e.name) ) return e.val;
    return System.getProperty("h2o.arg."+name);
  }

  /**
   * Parse the command line arguments and extracts options. The current
   * implementation allows the same command line instance to parse several
   * argument lists, the results will be merged.
   *
   * @param s the array of arguments to be parsed
   */
  private void parse(String[] s) {
    commandLineArgs = new Entry[0];
    for( int i = 0; i < s.length; ) {
      String next = (i+1<s.length)? s[i+1]: null;
      i += addArgument(s[i],next);
    }
  }

  public String toString() {
    String[] ss = toStringArray();
    String result = "";
    for( String s : ss )  result += s+" ";
    return result;
  }

  public String[] toStringArray() {
    String[] result = new String[commandLineArgs.length];
    for( int i = 0; i < commandLineArgs.length; i++ )
      result[i] = commandLineArgs[i].toString();
    return result;
  }

  /**
   * Keep only the fields which are either primitive or strings.
   */
  static private Field[] getFields(Arg arg) {
    Class target_ = arg.getClass();
    Field[] fields = new Field[0];
    while( target_ != null ){
      int flen = fields.length;
      Field[] f2 = target_.getDeclaredFields();
      fields = Arrays.copyOf(fields,flen+f2.length);
      System.arraycopy(f2,0,fields,flen,f2.length);
      target_ = target_.getSuperclass();
    }
    Field[] keep = new Field[fields.length];
    int num = 0;
    for( Field field : fields ){
      field.setAccessible(true);
      if( Modifier.isStatic(field.getModifiers()) ) continue;
      if( field.getType().isPrimitive() || field.getType() == String.class ) keep[num++] = field;
    }
    Field[] res = new Field[num];
    for( int i = 0; i < num; i++ )
      res[i] = keep[i];
    return res;
  }

  /**
   * Private class for holding arguments. There are three cases: a flag, a
   * binding, or a plain string. - Flags have a null string as val and flag of
   * true; - Binding have non-empty name, a non-null val (possibly ""), and a
   * flag of false; - Plain strings have an empty name, "", a non-null,
   * non-empty val, and a flag of true;
   */
  private static class Entry implements Comparable {
    //true if this is a flag, i.e. ("-name" or "-name=")
    boolean flag;
    // option name, -name=value
    String name;
     // position in the argument list
    int position;
    // option value, -name=value
    String val;

    Entry(String _name, String _val, boolean _flag, int _position) {
      assert !_name.startsWith("-") && !_name.contains("=");
      name = _name;  val = _val;  flag = _flag;    position = _position;
    }

    public int compareTo(Object o) { return position - ((Entry) o).position;  }

    public String toString() {
      String result = " ";
      if( !name.equals("") )   result += "-";
      result += name;
      if( !flag )  result += "=";
      if( val != null ) result += val;
      return result;
    }
  }
}
