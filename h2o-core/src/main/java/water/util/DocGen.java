package water.util;
import java.io.*;

/**
 * Auto-gen doc support, for JSON and REST API docs
 * @author <a href="mailto:cliffc@0xdata.com"></a>
 */
public abstract class DocGen {
  public static final HTML HTML = new HTML();
  public static final ReST ReST = new ReST();

  public static void createFile (String fileName, String content) {
    try {
      FileWriter fstream = new FileWriter(fileName, false); //true tells to append data.
      BufferedWriter out = new BufferedWriter(fstream);
      out.write(content);
      out.close();
    } catch( Throwable e ) {
      System.err.println("Error: " + e.getMessage());
    }
  }

//  public static void createReSTFilesInCwd() {
//    // ImportFiles2 is spitting out a bunch of HTML junk, which is buggy.  Disable for now.
//    // createFile("ImportFiles2.rst", new ImportFiles2().ReSTHelp());
//
//    createFile("Parse2.rst", new Parse2().ReSTHelp());
//    createFile("GBM.rst", new GBM().ReSTHelp());
//    createFile("DRF2.rst", new DRF().ReSTHelp());
//    createFile("GLM2.rst", new GLM2().ReSTHelp());
//    createFile("KMeans2.rst", new KMeans2().ReSTHelp());
//    // createFile("Summary2.rst", new Summary2().ReSTHelp());
//  }

  /** The main method launched in the H2O environment and
   * generating documentation.
   */
//  public static void main(String[] args) throws Exception {
//    // Boot invoke by default mainClass water.H2O and then call runClass
//    H2O.waitForCloudSize(1);
//    createReSTFilesInCwd();
//    H2O.exit(0);
//  }
//
//  // Class describing meta-info about H2O queries and results.
//  public static class FieldDoc {
//    final String _name;           // Field name
//    final String _help;           // Some descriptive text
//    final int _since, _until; // Min/Max supported-version numbers
//    final Class _clazz; // Java type: subtypes of Argument are inputs, otherwise outputs
//    final boolean _input, _required;
//    final ParamImportance _importance;
//    RequestArguments.Argument _arg; // Lazily filled in, as docs are asked for.
//    public FieldDoc( String name, String help, int min, int max, Class C, boolean input, boolean required, ParamImportance importance ) {
//      _name = name; _help = help; _since = min; _until = max; _clazz = C; _input = input; _required = required; _importance = importance;
//    }
//    @Override public String toString() {
//      return "{"+_name+", from "+_since+" to "+_until+", "+_clazz.getSimpleName()+", "+_help+"}";
//    }
//
//    private final String version() {
//      return "Since version "+_since+
//        (_until==Integer.MAX_VALUE?"":", deprecated on version "+_until);
//    }
//
//    public final boolean isInput () {
//      return _input;
//    }
//    public final boolean isJSON() { return !isInput(); }
//
//    public final ParamImportance importance() { return _importance; }
//
//    public final String name() { return _name; }
//
//    // Specific accessors for input arguments.  Not valid for JSON output fields.
//    private RequestArguments.Argument arg(Request R) {
//      if( _arg != null ) return _arg;
//      Class clzz = R.getClass();
//      // An amazing crazy API from the JDK again.  Cannot search for protected
//      // fields without either (1) throwing NoSuchFieldException if you ask in
//      // a subclass, or (2) sorting through the list of ALL fields and EACH
//      // level of the hierarchy.  Sadly, I catch NSFE & loop.
//      while( true ) {
//        try {
//          Field field = clzz.getDeclaredField(_name);
//          field.setAccessible(true);
//          Object o = field.get(R);
//          return _arg=((RequestArguments.Argument)o);
//        }
//        catch(   NoSuchFieldException ie ) { clzz = clzz.getSuperclass(); }
//        catch( IllegalAccessException ie ) { break; }
//        catch( ClassCastException ie ) { break; }
//      }
//      return null;
//    }
//  }
//
//  // --------------------------------------------------------------------------
//  // Abstract text generators, for building pretty docs in either HTML or
//  // ReStructuredText form.
//  public abstract StringBuilder escape( StringBuilder sb, String s );
//  public abstract StringBuilder bodyHead( StringBuilder sb );
//  public abstract StringBuilder bodyTail( StringBuilder sb );
//  public abstract StringBuilder title( StringBuilder sb, String t );
//  public abstract StringBuilder section( StringBuilder sb, String t );
//  public abstract StringBuilder listHead( StringBuilder sb );
//  public abstract StringBuilder listBullet( StringBuilder sb, String s, String body, int d );
//  public abstract StringBuilder listTail( StringBuilder sb );
//  public abstract String bold( String s );
//  public abstract StringBuilder paraHead( StringBuilder sb );
//  public abstract StringBuilder paraTail( StringBuilder sb );
//  public StringBuilder paragraph( StringBuilder sb, String s ) {
//    return paraTail(paraHead(sb).append(s));
//  }
//
//  public String genHelp(Request R) {
//    final String name = R.getClass().getSimpleName();
//    final FieldDoc docs[] = R.toDocField();
//    final StringBuilder sb = new StringBuilder();
//    bodyHead(sb);
//    title(sb,name);
//    paragraph(sb,"");
//
//    section(sb,"Supported HTTP methods and descriptions");
//    String gs = R.toDocGET();
//    if( gs != null ) {
//      paragraph(sb,"GET");
//      paragraph(sb,gs);
//    }
//
//    section(sb,"URL");
//    paraTail(escape(paraHead(sb),"http://<h2oHost>:<h2oApiPort>/"+name+".json"));
//
//    // Escape out for not-yet-converted auto-doc Requests
//    if( docs == null ) return bodyTail(sb).toString();
//
//    section(sb,"Input parameters");
//    listHead(sb);
//    for( FieldDoc doc : docs ) {
//      if( doc.isInput() ) {
//        Argument arg = doc.arg(R); // Legacy
//        String help = doc._help;
//        boolean required = doc._required;
//        ParamImportance importance = doc.importance();
//        String[] errors = null;
//        if(arg != null) {
//          String description = arg.queryDescription();
//          if(description != null && description.length() != 0)
//            help = description;
//          required |= arg._required;
//          errors = arg.errors();
//        }
//        listBullet(sb,
//                   bold(doc._name)+", a "+doc._clazz.getSimpleName()+", <i>"+importance.title+"</i>",
//                   help+".  "+doc.version(), 0);
//        if( errors != null || required ) {
//          paragraph(sb,"");
//          paragraph(sb,bold("Possible JSON error field returns:"));
//          listHead(sb);
//          String argErr = "Argument '"+doc._name+"' error: ";
//          if( errors != null )
//            for( String err : errors )
//              listBullet(sb,argErr+err,"",1);
//          if( required )
//            listBullet(sb,argErr+"Argument '"+doc._name+"' is required, but not specified","",1);
//          listTail(sb);
//        }
//      }
//    }
//    listTail(sb);
//
//    section(sb,"Output JSON elements");
//    listJSONFields(sb,docs);
//
//    section(sb,"HTTP response codes");
//    paragraph(sb,"200 OK");
//    paragraph(sb,"Success and error responses are identical.");
//
//    String s[] = R.DocExampleSucc();
//    if( s != null ) {
//      section(sb,"Success Example");
//      paraHead(sb);
//      url(sb,name,s);
//      paraTail(sb);
//      paragraph(sb,serve(name,s));
//    }
//
//    String f[] = R.DocExampleFail();
//    if( f != null ) {
//      section(sb,"Error Example");
//      paraHead(sb);
//      url(sb,name,f);
//      paraTail(sb);
//      paragraph(sb,serve(name,f));
//    }
//
//    bodyTail(sb);
//    return sb.toString();
//  }
//
//  private void listJSONFields( StringBuilder sb, FieldDoc[] docs ) {
//    listHead(sb);
//    for( FieldDoc doc : docs )
//      if( doc.isJSON() ) {
//        listBullet(sb,
//                   bold(doc._name)+", a "+doc._clazz.getSimpleName(),
//                   doc._help+".  "+doc.version()+", "+doc.importance().title,0);
//        Class c = doc._clazz.getComponentType();
//        if( c==null ) c = doc._clazz;
//        if( Iced.class.isAssignableFrom(c) ) {
//          try {
//            FieldDoc[] nested = ((Iced)c.newInstance()).toDocField();
//            if( nested != null ) // Can be empty, e.g. for Key
//              listJSONFields(sb,nested);
//          }
//          catch( InstantiationException ie ) { water.util.Log.errRTExcept(ie); }
//          catch( IllegalAccessException ie ) { water.util.Log.errRTExcept(ie); }
//        }
//      }
//    listTail(sb);
//  }
//
//  private static StringBuilder url( StringBuilder sb, String name, String[] parms ) {
//    sb.append("curl -s ").append(name).append(".json");
//    boolean first = true;
//    for( int i=0; i<parms.length; i+= 2 ) {
//      if( first ) { first = false; sb.append("?"); }
//      else        {                sb.append("&"); }
//      sb.append(parms[i]).append('=').append(parms[i+1]);
//    }
//    return sb.append('\n');
//  }
//
//  private static String serve( String name, String[] parms ) {
//    Properties p = new Properties();
//    for( int i=0; i<parms.length; i+= 2 )
//      p.setProperty(parms[i],parms[i+1]);
//    NanoHTTPD.Response r = RequestServer.SERVER.serve(name+".json",null,null,p);
//    try {
//      int l = r.data.available();
//      byte[] b = new byte[l];
//      r.data.read(b);
//      return new String(b);
//    } catch( IOException ioe ) {
//      Log.err(ioe);
//      return null;
//    }
//  }

  // --------------------------------------------------------------------------
  // HTML flavored help text
  public static class HTML extends DocGen {
    @SuppressWarnings("unused")
//    @Override
    public StringBuilder escape(StringBuilder sb, String s ) {
      int len=s.length();
      for( int i=0; i<len; i++ ) {
        char c = s.charAt(i);
        if( c=='<' ) sb.append("&lt;");
        else if( c=='>' ) sb.append("&gt;");
        else if( c=='&' ) sb.append("&amp;");
        else if( c=='"' ) sb.append("&quot;");
        else sb.append(c);
      }
      return sb;
    }
    public String escape2(String s) {
      StringBuilder sb = new StringBuilder(s.length());
      escape(sb, s);
      return sb.toString();
    }
//    @Override
    public StringBuilder bodyHead( StringBuilder sb ) {
      return sb.append("<div class='container'>"+
                       "<div class='row-fluid'>"+
                       "<div class='span12'>");
    }
//    @Override
    public StringBuilder bodyTail( StringBuilder sb ) { return sb.append("</div></div></div>"); }
//    @Override
    public StringBuilder title  ( StringBuilder sb, String t ) { return sb.append("<h3>").append(t).append("</h3>\n"); }
//    @Override
    public StringBuilder section( StringBuilder sb, String t ) { return sb.append("<h4>").append(t).append("</h4>\n"); }
//    @Override
    public StringBuilder paraHead( StringBuilder sb ) { return sb.append("<p>"); }
//    @Override
    public StringBuilder paraTail( StringBuilder sb ) { return sb.append("</p>\n"); }

    public StringBuilder paragraph( StringBuilder sb, String s ) {
      return paraTail(paraHead(sb).append(s));
    }

//    @Override
    public StringBuilder listHead( StringBuilder sb ) { return sb.append("<ul>"); }
//    @Override
    public StringBuilder listBullet( StringBuilder sb, String s, String body, int d ) {
      return paragraph(sb.append("<li>").append(s).append("</li>"),body).append('\n');
    }
//    @Override
    public StringBuilder listTail( StringBuilder sb ) { return sb.append("</ul>\n"); }
//    @Override
    public String bold( String s ) { return "<b>"+s+"</b>"; }

    public StringBuilder arrayHead( StringBuilder sb ) { return arrayHead(sb,null); }

    public StringBuilder progress(float value, StringBuilder sb){
      int    pct  = (int) (value * 100);
      String type = "progress-stripped active";
      if (pct==-100) { // task is done
        pct = 100;
        type = "progress-success";
      } else if (pct==-200) {
        pct = 100;
        type = "progress-warning";
      }
      // @formatter:off
      sb.append
          ("<div style='margin-bottom:0px;padding-bottom:0xp;margin-top:8px;height:5px;width:180px' class='progress "+type+"'>").append //
          ("<div class='bar' style='width:" + pct + "%;'>").append //
          ("</div>").append //
          ("</div>");
      // @formatter:on
      return sb;
    }
    public StringBuilder arrayHead( StringBuilder sb, String[] headers ) {
      sb.append("<span style='display: inline-block;'>");
      sb.append("<table class='table table-striped table-bordered'>\n");
      if( headers != null ) {
        sb.append("<tr>");
        for( String s : headers ) sb.append("<th>").append(s).append("</th>");
        sb.append("</tr>\n");
      }
      return sb;
    }
    public StringBuilder arrayTail( StringBuilder sb ) { return sb.append("</table></span>\n"); }
    public StringBuilder array( StringBuilder sb, String[] ss ) {
      arrayHead(sb);
      for( String s : ss ) sb.append("<tr><td>").append(s).append("</td></tr>");
      return arrayTail(sb);
    }
    public StringBuilder toJSArray(StringBuilder sb, float[] nums) { return toJSArray(sb, nums, null, nums.length); }
    public StringBuilder toJSArray(StringBuilder sb, float[] nums, Integer[] sortOrder, int maxValues) {
      sb.append('[');
      for (int i=0; i<maxValues; i++) {
        if (i>0) sb.append(',');
        sb.append(nums[sortOrder!=null ? sortOrder[i] : i]);
      }
      sb.append(']');
      return sb;
    }
    public StringBuilder toJSArray(StringBuilder sb, String[] ss) { return toJSArray(sb, ss, null, ss.length); }
    public StringBuilder toJSArray(StringBuilder sb, String[] ss, Integer[] sortOrder, int maxValues) {
      sb.append('[');
      for (int i=0; i<maxValues; i++) {
        if (i>0) sb.append(',');
        sb.append('"').append(ss[sortOrder!=null ? sortOrder[i] : i]).append('"');
      }
      sb.append(']');
      return sb;
    }

    public <T> StringBuilder tableLine(StringBuilder sb, String title, T[] values, Integer[] sortOrder) {
      return tableLine(sb, title, values, sortOrder, values.length);
    }
    public <T> StringBuilder tableLine(StringBuilder sb, String title, T[] values, Integer[] sortOrder, int maxValues) {
      return tableLine(sb, title, values, sortOrder, maxValues, false, null);

    }
    public <T> StringBuilder tableLine(StringBuilder sb, String title, T[] values, Integer[] sortOrder, int maxValues, boolean checkBoxes, String idName) {
      assert sortOrder == null || values.length == sortOrder.length;
      sb.append("<tr><th>").append(title).append("</th>");
      for( int i=0; i<maxValues; i++ ) {
        sb.append("<td>");
        T val = values[sortOrder!=null ? sortOrder[i] : i];
        if (checkBoxes) sb.append("<input type=\"checkbox\" name=\"").append(idName).append("\" value=\"").append(val).append("\" checked />&nbsp;");
        sb.append(val);
        sb.append("</td>");
      }
      sb.append("</tr>");
      return sb;
    }
    public StringBuilder tableLine(StringBuilder sb, String title, float[] values, Integer[] sortOrder) {
      return tableLine(sb, title, values, sortOrder, values.length);
    }
    public StringBuilder tableLine(StringBuilder sb, String title, float[] values, Integer[] sortOrder, int maxValues) {
      assert sortOrder == null || values.length == sortOrder.length;
      sb.append("<tr><th>").append(title).append("</th>");
      for( int i=0; i<maxValues; i++ )
        sb.append(String.format("<td>%5.4f</td>",values[sortOrder!=null ? sortOrder[i] : i]));
      sb.append("</tr>");
      return sb;
    }

    public StringBuilder graph(StringBuilder sb, String gid, String gname, StringBuilder ...gparams) {
      sb.append("<style scoped>@import url('/h2o/css/graphs.css')</style>");
      sb.append("<script type=\"text/javascript\" src='/h2o/js/d3.v3.min.js'></script>");
      sb.append("<script src='/h2o/js/graphs.js'></script>");
      sb.append("<div id='").append(gid).append("'>")
        .append("  <script>")
        .append(gname).append("('").append(gid).append("'");
      for (int i=0; i<gparams.length; i++) sb.append(", ").append(gparams[i]);
      sb.append(");");
      sb.append("  </script>")
        .append("</div>");
      return sb;
    }
  }

  // --------------------------------------------------------------------------
  // ReST flavored help text
  static class ReST extends DocGen { // Restructured text
    private StringBuilder cr(StringBuilder sb) { return sb.append('\n'); }
    private StringBuilder underLine( StringBuilder sb, String s, char c ) {
      cr(cr(sb).append(s));
      int len = s.length();
      for( int i=0; i<len; i++ ) sb.append(c);
      return cr(cr(sb));
    }
//    @Override
    public StringBuilder escape(StringBuilder sb, String s ) { return sb.append(s); }
//    @Override
    public StringBuilder bodyHead( StringBuilder sb ) { return sb; }
//    @Override
    public StringBuilder bodyTail( StringBuilder sb ) { return sb; }
//    @Override
    public StringBuilder title  ( StringBuilder sb, String t ) { return underLine(sb,t,'='); }
//    @Override
    public StringBuilder section( StringBuilder sb, String t ) { return underLine(sb,t,'-'); }
//    @Override
    public StringBuilder listHead( StringBuilder sb ) { return cr(sb); }
//    @Override
    public StringBuilder listBullet( StringBuilder sb, String s, String body, int d ) {
      if( d > 0 ) sb.append("  ");
      cr(sb.append("*  ").append(s));
      if( body.length() > 0 )
        cr(cr(cr(sb).append("   ").append(body)));
      return sb;
    }
//    @Override
    public StringBuilder listTail( StringBuilder sb ) { return cr(sb); }
//    @Override
    public String bold( String s ) { return "**"+s+"**"; }
//    @Override
    public StringBuilder paraHead( StringBuilder sb ) { return sb.append("  "); }
//    @Override
    public StringBuilder paraTail( StringBuilder sb ) { return cr(sb); }
  }
}
