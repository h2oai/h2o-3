package water.util;

import water.Freezable;
import water.H2O;
import water.Iced;

import java.io.Serializable;

/**
 * Auto-gen doc support, for JSON and REST API docs
 * @author <a href="mailto:cliffc@h2o.ai"></a>
 */
public abstract class DocGen<T extends DocGen> {

  // --------------------------------------------------------------------------
  // Abstract text generators, for building pretty docs in either HTML or
  // ReStructuredText form.
  public abstract T p( String s);
  public abstract T escape( String s );
  public abstract T bodyHead( );
  public abstract T bodyTail( );
  public abstract T title( String t );
  public abstract T section( String t );
  public abstract T listHead( );
  public abstract T listBullet( String s, String body, int d );
  public abstract T listTail( );
  public abstract T paraHead( );
  public abstract T paraTail( );
  public          T paragraph( String s ) { return (T)paraHead().p(s).paraTail(); }
  public abstract String bold( String s );

  // --------------------------------------------------------------------------
  // HTML flavored help text
  public static class HTML extends DocGen<HTML> {
    private final StringBuilder _sb = new StringBuilder();
    public byte[] getBytes() { return _sb.toString().getBytes(); }
    @Override public HTML p(String s) { _sb.append(s); return this; }
    /* @Override */ public HTML p(Enum e) { _sb.append(e); return this; }
    @Override public String toString() { return _sb.toString(); }
    private HTML p(char c) { _sb.append(c); return this; }
    private HTML p(boolean b) { _sb.append(b); return this; }

    // Weaver shortcuts for placing Java fields nicely.
    private HTML f0(String name) { return p("<dl class='dl-horizontal'><dt>").p(name).p("</dt><dd>"); }
    private HTML f1() { return p("</dd></dl>\n");  }
    private HTML f(String name, String s) { return f0(name).p(s).f1();  }
    // Weaver entry points
    public HTML putSer(String name, Serializable obj) { throw H2O.fail(); }
    public HTML putStr(String name, String  s) { return f(name,s); }
    public HTML putZ  (String name, boolean b) { return f(name,Boolean.toString(b)); }
    public HTML put1  (String name, byte    b) { return f(name,Byte   .toString(b)); }
    public HTML put2  (String name, char    c) { return f(name,Character.toString(c)); }
    public HTML put2  (String name, short   s) { return f(name,Short  .toString(s)); }
    public HTML put4  (String name, int     i) { return f(name,Integer.toString(i)); }
    public HTML put4f (String name, float   f) { return f(name,Float  .toString(f)); }
    public HTML put8  (String name, long    l) { return f(name,Long   .toString(l)); }
    public HTML put8d (String name, double  d) { return f(name,Double .toString(d)); }
    public HTML put   (String name, Freezable f){return f==null?f(name,"null"):f.writeHTML(f0(name)).f1(); }
    public HTML putEnum(String name, Enum   e) { return f(name,e.toString()); }
    public HTML putAEnum(String name, Enum[] es) { return es==null?f(name,"null"):f0(name).array(es).f1(); }

    public HTML putAStr(String name, String [] ss) { return ss==null?f(name,"null"):f0(name).array(ss).f1(); }
    public HTML putA1  (String name, byte   [] bs) { throw H2O.fail(); }
    public HTML putA2  (String name, short  [] ss) { 
      if( ss==null ) return f(name,"null");
      f0(name).arrayHead();
      for( short s : ss ) p("<tr><td>").p(Integer.toString(s)).p("</td></tr>");
      return arrayTail().f1();
    }

    public HTML putAZ (String name, boolean[] b) { return b==null?f(name, "null"):f0(name).array(b).f1(); }

    public HTML putA4  (String name, int    [] is) { return is==null?f(name, "null"):f0(name).array(is).f1(); } //throw H2O.unimpl(); }
    public HTML putA4f (String name, float  [] fs) { return fs==null?f(name, "null"):f0(name).array(fs).f1(); }
    public HTML putA8  (String name, long   [] ls) { 
      if( ls==null ) return f(name,"null");
      f0(name).arrayHead();
      for( long l : ls ) p("<tr><td>").p(Long.toString(l)).p("</td></tr>");
      return arrayTail().f1();
    }
    public HTML putA8d (String name, double [] ds) { 
      if( ds==null ) return f(name,"null");
      f0(name).arrayHead();
      for( double d : ds ) p("<tr><td>").p(Double.toString(d)).p("</td></tr>");
      return arrayTail().f1();
    }
    public HTML putA   (String name, Freezable[]fs){ 
      if( fs==null ) return f(name,"null");
      f0(name).arrayHead();
      for( Freezable f : fs ) {
        p("<tr><td>");
        if( f!=null ) f.writeHTML(this);
        p("</td></tr>");
      }
      return arrayTail().f1();
    }

    public HTML putAAStr(String name, String [][] sss) { return sss==null?f(name,"null"):f0(name).array(sss).f1(); }
    public HTML putAA4  (String name, int    [][] iss) { return iss==null?f(name,"null"):f0(name).array(iss).f1(); }
    public HTML putAA8  (String name, long   [][] lss) { return lss==null?f(name,"null"):f0(name).array(lss).f1(); }
    public HTML putAA4f (String name, float  [][] fss) { return fss==null?f(name,"null"):f0(name).array(fss).f1(); }
    public HTML putAA8d (String name, double [][] dss) { return dss==null?f(name,"null"):f0(name).array(dss).f1(); }
    public HTML putAA   (String name, Freezable[][]fss){ throw H2O.fail(); }
    public HTML putAAA  (String name, Freezable[][][]fss){ throw H2O.fail(); }

    public HTML putAAA4 (String name,  int    [][][]isss) { return isss==null?f(name,"null"):f0(name).array(isss).f1(); }
    public HTML putAAA8 (String name,  long   [][][]lsss) { return lsss==null?f(name,"null"):f0(name).array(lsss).f1(); }
    public HTML putAAA8d(String name,  double [][][]dsss) { return dsss==null?f(name,"null"):f0(name).array(dsss).f1(); }
    public HTML putAAASer(String name, Object  [][][]dsss) { throw H2O.fail();}


    public HTML href( String name, String text, String link ) {
      return f0(name).p("<a href='").p(link).p("'>").p(text).p("</a>").f1();
    }

    @SuppressWarnings("unused")
    @Override public HTML escape( String s ) {
      int len=s.length();
      for( int i=0; i<len; i++ ) {
        char c = s.charAt(i);
        if( c=='<' ) p("&lt;");
        else if( c=='>' ) p("&gt;");
        else if( c=='&' ) p("&amp;");
        else if( c=='"' ) p("&quot;");
        else p(c);
      }
      return this;
    }
    @Override public HTML bodyHead( ) {
      return p("<div class='container'>"+
               "<div class='row-fluid'>"+
               "<div class='span12'>");
    }
    @Override public HTML bodyTail(  ) { return p("</div></div></div>"); }

    @Override public HTML title   ( String t ) { return p("<h3>").p(t).p("</h3>\n"); }
    @Override public HTML section ( String t ) { return p("<h4>").p(t).p("</h4>\n"); }
    @Override public HTML paraHead(  ) { return p("<p>"); }
    @Override public HTML paraTail(  ) { return p("</p>\n"); }
    @Override public HTML listHead(  ) { return p("<ul>"); }
    @Override public HTML listBullet( String s, String body, int d ) { return p("<li>").p(s).p("</li>").p(body).p('\n'); }
    @Override public HTML listTail( ) { return p("</ul>\n"); }
    @Override public String bold( String s ) { return "<b>"+s+"</b>"; }

    public HTML arrayHead( ) { return arrayHead(null); }
    public HTML arrayHead( String[] headers ) {
      p("<span style='display: block;'>");
      p("<table class='table table-striped table-bordered'>\n");
      if( headers != null ) {
        p("<tr>");
        for( String s : headers ) p("<th>").p(s).p("</th>");
        p("</tr>\n");
      }
      return this;
    }
    public HTML arrayTail( ) { return p("</table></span>\n"); }
    public HTML arrayRow( String[] ss ) {
      p("<tr>");
      for( String s : ss ) cell(s);
      return p("</tr>");
    }
    public HTML array( String[] ss ) {
      arrayHead();
      if( ss != null ) for( String s : ss ) p("<tr>").cell(s).p("</tr>");
      return arrayTail();
    }
    public HTML array(boolean[] ss) {
      arrayHead();
      if (ss != null ) for (boolean b : ss) p("<tr>").cell(b).p("</tr>");
      return arrayTail();
    }
    public HTML array( int[] ds    ) {
      arrayHead();
      if( ds != null ) for( double d : ds ) p("<tr>").cell(d).p("</tr>");
      return arrayTail();
    }
    public HTML array( double[] ds ) {
      arrayHead();
      if( ds != null ) for( double d : ds ) p("<tr>").cell(d).p("</tr>");
      return arrayTail();
    }
    public HTML array( float[] ds ) {
      arrayHead();
      if( ds != null ) for( float d : ds ) p("<tr>").cell(d).p("</tr>");
      return arrayTail();
    }
    public HTML array( Enum[] es ) {
      arrayHead();
      if( es != null ) for( Enum e : es ) p("<tr>").cell(e).p("</tr>");
      return arrayTail();
    }
    public HTML array( String[][] sss ) {
      arrayHead();
      for( String[] ss : sss ) {
        p("<tr>");
        if( ss != null ) for( String s : ss ) cell(s);
        p("</tr>");
      }
      return arrayTail();
    }
    public HTML array( double[][] dss ) {
      arrayHead();
      for( double[] ds : dss ) { 
        p("<tr>");
        if( ds != null ) for( double d : ds ) cell(d);
        p("</tr>");
      }
      return arrayTail();
    }
    public HTML array( float[][] fss ) {
      arrayHead();
      for( float[] fs : fss ) {
        p("<tr>");
        if( fs != null ) for( float f : fs ) cell(f);
        p("</tr>");
      }
      return arrayTail();
    }
    public HTML array( int[][] iss ) {
      arrayHead();
      for( int[] is : iss ) {
        p("<tr>");
        if( is != null ) for( int i : is ) cell(i);
        p("</tr>");
      }
      return arrayTail();
    }
    public HTML array( long[][] lss ) {
      arrayHead();
      for( long[] ls : lss ) {
        p("<tr>");
        if( ls != null ) for( long l : ls ) cell(l);
        p("</tr>");
      }
      return arrayTail();
    }
    public HTML array( int[][][] isss ) {
      arrayHead();
      for( int i=0; i<isss.length; ++i ) {
        p("<div>");
        if( isss[i] != null ) array(isss[i]);
        p("</div>");
      }
      return arrayTail();
    }
    public HTML array( long[][][] lsss ) {
      arrayHead();
      for( int i=0; i<lsss.length; ++i ) {
        p("<div>");
        if( lsss[i] != null ) array(lsss[i]);
        p("</div>");
      }
      return arrayTail();
    }
    public HTML array( double[][][] dsss ) {
      arrayHead();
      for( int i=0; i<dsss.length; ++i ) {
        p("<div>");
        if( dsss[i] != null ) array(dsss[i]);
        p("</div>");
      }
      return arrayTail();
    }
    public HTML cell( Enum e ) { return p("<td>").p(e).p("</td>"); }
    public HTML cell( String s ) { return p("<td>").p(s).p("</td>"); }
    public HTML cell( long l )   { return cell(Long.toString(l)); }
    public HTML cell( double d ) { return cell(Double.toString(d)); }
    public HTML cell( boolean b ) { return p("<td>").p(b).p("</td>"); }
    public HTML cell( String[] ss ) { return p("<td>").array(ss).p("</td>"); }
    public HTML cell( double[] ds ) { return p("<td>").array(ds).p("</td>"); }

    //public StringBuilder progress(float value, StringBuilder sb){
    //  int    pct  = (int) (value * 100);
    //  String type = "progress-stripped active";
    //  if (pct==-100) { // task is done
    //    pct = 100;
    //    type = "progress-success";
    //  } else if (pct==-200) {
    //    pct = 100;
    //    type = "progress-warning";
    //  }
    //  // @formatter:off
    //  p
    //      ("<div style='margin-bottom:0px;padding-bottom:0xp;margin-top:8px;height:5px;width:180px' class='progress "+type+"'>").p //
    //      ("<div class='bar' style='width:" + pct + "%;'>").p //
    //      ("</div>").p //
    //      ("</div>");
    //  // @formatter:on
    //  return sb;
    //}
    //public StringBuilder toJSArray(StringBuilder sb, float[] nums) { return toJSArray(sb, nums, null, nums.length); }
    //public StringBuilder toJSArray(StringBuilder sb, float[] nums, Integer[] sortOrder, int maxValues) {
    //  p('[');
    //  for (int i=0; i<maxValues; i++) {
    //    if (i>0) p(',');
    //    p(nums[sortOrder!=null ? sortOrder[i] : i]);
    //  }
    //  p(']');
    //  return sb;
    //}
    //public StringBuilder toJSArray(StringBuilder sb, String[] ss) { return toJSArray(sb, ss, null, ss.length); }
    //public StringBuilder toJSArray(StringBuilder sb, String[] ss, Integer[] sortOrder, int maxValues) {
    //  p('[');
    //  for (int i=0; i<maxValues; i++) {
    //    if (i>0) p(',');
    //    p('"').p(ss[sortOrder!=null ? sortOrder[i] : i]).p('"');
    //  }
    //  p(']');
    //  return sb;
    //}
    //
    //public <T> StringBuilder tableLine(StringBuilder sb, String title, T[] values, Integer[] sortOrder) {
    //  return tableLine(sb, title, values, sortOrder, values.length);
    //}
    //public <T> StringBuilder tableLine(StringBuilder sb, String title, T[] values, Integer[] sortOrder, int maxValues) {
    //  return tableLine(sb, title, values, sortOrder, maxValues, false, null);
    //
    //}
    //public <T> StringBuilder tableLine(StringBuilder sb, String title, T[] values, Integer[] sortOrder, int maxValues, boolean checkBoxes, String idName) {
    //  assert sortOrder == null || values.length == sortOrder.length;
    //  p("<tr><th>").p(title).p("</th>");
    //  for( int i=0; i<maxValues; i++ ) {
    //    p("<td>");
    //    T val = values[sortOrder!=null ? sortOrder[i] : i];
    //    if (checkBoxes) p("<input type=\"checkbox\" name=\"").p(idName).p("\" value=\"").p(val).p("\" checked />&nbsp;");
    //    p(val);
    //    p("</td>");
    //  }
    //  p("</tr>");
    //  return sb;
    //}
    //public StringBuilder tableLine(StringBuilder sb, String title, float[] values, Integer[] sortOrder) {
    //  return tableLine(sb, title, values, sortOrder, values.length);
    //}
    //public StringBuilder tableLine(StringBuilder sb, String title, float[] values, Integer[] sortOrder, int maxValues) {
    //  assert sortOrder == null || values.length == sortOrder.length;
    //  p("<tr><th>").p(title).p("</th>");
    //  for( int i=0; i<maxValues; i++ )
    //    p(String.format("<td>%5.4f</td>",values[sortOrder!=null ? sortOrder[i] : i]));
    //  p("</tr>");
    //  return sb;
    //}
    //
    //public StringBuilder graph(StringBuilder sb, String gid, String gname, StringBuilder ...gparams) {
    //  p("<style scoped>@import url('/h2o/css/graphs.css')</style>");
    //  p("<script type=\"text/javascript\" training_frame='/h2o/js/d3.v3.min.js'></script>");
    //  p("<script training_frame='/h2o/js/graphs.js'></script>");
    //  p("<div id='").p(gid).p("'>")
    //    .p("  <script>")
    //    .p(gname).p("('").p(gid).p("'");
    //  for (int i=0; i<gparams.length; i++) p(", ").p(gparams[i]);
    //  p(");");
    //  p("  </script>")
    //    .p("</div>");
    //  return sb;
    //}
  }

  // --------------------------------------------------------------------------
  // ReST flavored help text
  //static class ReST extends DocGen { // Restructured text
  //  private StringBuilder cr(StringBuilder sb) { return sb.append('\n'); }
  //  private StringBuilder underLine( StringBuilder sb, String s, char c ) {
  //    cr(cr(sb).append(s));
  //    int len = s.length();
  //    for( int i=0; i<len; i++ ) sb.append(c);
  //    return cr(cr(sb));
  //  }
  //  @Override public StringBuilder escape(StringBuilder sb, String s ) { return sb.append(s); }
  //  @Override public StringBuilder bodyHead( StringBuilder sb ) { return sb; }
  //  @Override public StringBuilder bodyTail( StringBuilder sb ) { return sb; }
  //  @Override public StringBuilder title  ( StringBuilder sb, String t ) { return underLine(sb,t,'='); }
  //  @Override public StringBuilder section( StringBuilder sb, String t ) { return underLine(sb,t,'-'); }
  //  @Override public StringBuilder listHead( StringBuilder sb ) { return cr(sb); }
  //  @Override public StringBuilder listBullet( StringBuilder sb, String s, String body, int d ) {
  //    if( d > 0 ) sb.append("  ");
  //    cr(sb.append("*  ").append(s));
  //    if( body.length() > 0 )
  //      cr(cr(cr(sb).append("   ").append(body)));
  //    return sb;
  //  }
  //  @Override public StringBuilder listTail( StringBuilder sb ) { return cr(sb); }
  //  @Override public String bold( String s ) { return "**"+s+"**"; }
  //  @Override public StringBuilder paraHead( StringBuilder sb ) { return sb.append("  "); }
  //  @Override public StringBuilder paraTail( StringBuilder sb ) { return cr(sb); }
  //}
}
