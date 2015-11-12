package water.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import water.exceptions.JCodeSB;

/** Tight/tiny StringBuilder wrapper.
 *  Short short names on purpose; so they don't obscure the printing.
 *  Can't believe this wasn't done long long ago. */
public final class SB implements JCodeSB<SB> {
  public final StringBuilder _sb;
  int _indent = 0;
  public SB(        ) { _sb = new StringBuilder( ); }
  public SB(String s) { _sb = new StringBuilder(s); }
  public SB ps( String s ) { _sb.append("\""); pj(s); _sb.append("\""); return this;  }
  public SB p( String s ) { _sb.append(s); return this; }
  public SB p( float  s ) {
    if( Float.isNaN(s) )
      _sb.append( "Float.NaN");
    else if( Float.isInfinite(s) ) {
      _sb.append(s > 0 ? "Float.POSITIVE_INFINITY" : "Float.NEGATIVE_INFINITY");
    } else _sb.append(s);
    return this;
  }
  public SB p( double s ) {
    if( Double.isNaN(s) )
      _sb.append("Double.NaN");
    else if( Double.isInfinite(s) ) {
      _sb.append(s > 0 ? "Double.POSITIVE_INFINITY" : "Double.NEGATIVE_INFINITY");
    } else _sb.append(s);
    return this;
  }
  public SB p( char   s ) { _sb.append(s); return this; }
  public SB p( int    s ) { _sb.append(s); return this; }
  public SB p( long   s ) { _sb.append(s); return this; }
  public SB p( boolean s) { _sb.append(s); return this; }
  // Not spelled "p" on purpose: too easy to accidentally say "p(1.0)" and
  // suddenly call the the autoboxed version.
  public SB pobj( Object s ) { _sb.append(s.toString()); return this; }
  public SB i( int d ) { for( int i=0; i<d+_indent; i++ ) p("  "); return this; }
  public SB i( ) { return i(0); }
  public SB ip(String s) { return i().p(s); }
  public SB s() { _sb.append(' '); return this; }
  // Java specific append of double
  public SB pj( double  s ) {
    if (Double.isInfinite(s))
      _sb.append("Double.").append(s>0? "POSITIVE_INFINITY" : "NEGATIVE_INFINITY");
    else if (Double.isNaN(s))
      _sb.append("Double.NaN");
    else
      _sb.append(s);
    return this;
  }
  // Java specific append of float
  public SB pj( float  s ) {
    if (Float.isInfinite(s))
      _sb.append("Float.").append(s>0? "POSITIVE_INFINITY" : "NEGATIVE_INFINITY");
    else if (Float.isNaN(s))
      _sb.append("Float.NaN");
    else
      _sb.append(s).append('f');
    return this;
  }
  /* Append Java string - escape all " and \ */
  public SB pj( String s ) { _sb.append(escapeJava(s)); return this; }

  @Override
  public SB pj(String objectName, String fieldName) {
    _sb.append(objectName).append('.').append(fieldName);
    return this;
  }

  public SB p( IcedBitSet ibs ) { return ibs.toString(this); }
  // Increase indentation
  public SB ii( int i) { _indent += i; return this; }
  // Decrease indentation
  public SB di( int i) { _indent -= i; return this; }

  @Override
  public SB ci(JCodeSB sb) {
    _indent = sb.getIndent();
    return this;
  }

  // Copy indent from given string buffer
  public SB nl( ) { return p('\n'); }
  // Convert a String[] into a valid Java String initializer
  public SB toJavaStringInit( String[] ss ) {
    if (ss==null) return p("null");
    p('{');
    for( int i=0; i<ss.length-1; i++ )  p('"').pj(ss[i]).p("\",");
    if( ss.length > 0 ) p('"').pj(ss[ss.length-1]).p('"');
    return p('}');
  }
  public SB toJavaStringInit( float[] ss ) {
    if (ss==null) return p("null");
    p('{');
    for( int i=0; i<ss.length-1; i++ ) pj(ss[i]).p(',');
    if( ss.length > 0 ) pj(ss[ss.length-1]);
    return p('}');
  }
  public SB toJavaStringInit( double[] ss ) {
    if (ss==null) return p("null");
    p('{');
    for( int i=0; i<ss.length-1; i++ ) pj(ss[i]).p(',');
    if( ss.length > 0 ) pj(ss[ss.length-1]);
    return p('}');
  }
  public SB toJavaStringInit( double[][] ss ) {
    if (ss==null) return p("null");
    p('{');
    for( int i=0; i<ss.length-1; i++ ) toJavaStringInit(ss[i]).p(',');
    if( ss.length > 0 ) toJavaStringInit(ss[ss.length-1]);
    return p('}');
  }
  public SB toJavaStringInit( double[][][] ss ) {
    if (ss==null) return p("null");
    p('{');
    for( int i=0; i<ss.length-1; i++ ) toJavaStringInit(ss[i]).p(',');
    if( ss.length > 0 ) toJavaStringInit(ss[ss.length-1]);
    return p('}');
  }
  public SB toJSArray(float[] nums) {
    p('[');
    for (int i=0; i<nums.length; i++) {
      if (i>0) p(',');
      p(nums[i]);
    }
    return p(']');
  }
  public SB toJSArray(String[] ss) {
    p('[');
    for (int i=0; i<ss.length; i++) {
      if (i>0) p(',');
      p('"').p(ss[i]).p('"');
    }
    return p(']');
  }

  @Override
  public int getIndent() {
    return _indent;
  }

  // Mostly a fail, since we should just dump into the same SB.
  public SB p(JCodeSB sb) {
    _sb.append(sb.getContent());
    return this;
  }
  @Override public String toString() { return _sb.toString(); }

  /** Java-string illegal characters which need to be escaped */
  public static final Pattern[] ILLEGAL_CHARACTERS = new Pattern[] { Pattern.compile("\\",Pattern.LITERAL), Pattern.compile("\"",Pattern.LITERAL) };
  public static final String[]  REPLACEMENTS       = new String [] { "\\\\\\\\", "\\\\\"" };

  /** Escape all " and \ characters to provide a proper Java-like string
   * Does not escape unicode characters.
   */
  public static String escapeJava(String s) {
    assert ILLEGAL_CHARACTERS.length == REPLACEMENTS.length;
    for (int i=0; i<ILLEGAL_CHARACTERS.length; i++ ) {
      Matcher m = ILLEGAL_CHARACTERS[i].matcher(s);
      s = m.replaceAll(REPLACEMENTS[i]);
    }
    return s;
  }

  @Override
  public String getContent() {
    return _sb.toString();
  }
}
