package hex;

import water.Iced;
import water.util.ArrayUtils;
import water.util.DocGen;

import java.util.Arrays;
import java.util.Comparator;

public class VarImp extends Iced {
  /** Variable importance measurement method. */
  public enum VarImpMethod {
    PERMUTATION_IMPORTANCE("Mean decrease accuracy"),
    RELATIVE_IMPORTANCE("Relative importance");
    private final String title;
    VarImpMethod(String title) { this.title = title; }
    @Override public String toString() { return title; }
  }

  public float[]  varimp; // Variable importance of individual variables.
  protected String[] variables; // Names of variables.
  public final VarImpMethod method; // Variable importance measurement method.
  public final int max_var = 100; // Max. number of variables to show.
  public final boolean scaled() { return false; } // Scaled measurements.

  public VarImp(float[] varimp) { this(varimp, null, VarImpMethod.RELATIVE_IMPORTANCE); }
  public VarImp(float[] varimp, String[] variables) { this(varimp, variables, VarImpMethod.RELATIVE_IMPORTANCE); }
  protected VarImp(float[] varimp, String[] variables, VarImpMethod method) {
    this.varimp = varimp;
    this.variables = variables;
    this.method = method;
  }

  public String[] getVariables() { return variables; }
  public void setVariables(String[] variables) { this.variables = variables; }

  /** Generate variable importance HTML code. */
  //public final <T extends Model> StringBuilder toHTML(T model, StringBuilder sb) {
  //  DocGen.HTML.section(sb,"Variable importance of input variables: " + method);
  //  sb.append("<div class=\"alert\">");
  //  //TODO: FIXME
////    sb.append(UIUtils.builderModelLink(model.getClass(), model._dataKey, model.responseName(), "Build a new model using selected variables", "redirectWithCols(this,'vi_chkb')"));
  //  sb.append("</div>");
  //
  //  DocGen.HTML.arrayHead(sb);
  //  // Create a sort order
  //  Integer[] sortOrder = getSortOrder();
  //  // Generate variable labels and raw scores
  //  if (variables != null) DocGen.HTML.tableLine(sb, "Variable", variables, sortOrder, Math.min(max_var, variables.length), true, "vi_chkb");
  //  if (varimp    != null) DocGen.HTML.tableLine(sb, method.toString(), varimp, sortOrder, Math.min(max_var, variables.length));
  //  // Print a specific information
  //  toHTMLAppendMoreTableLines(sb, sortOrder);
  //  DocGen.HTML.arrayTail(sb);
  //  // Generate nice graph ;-)
  //  toHTMLGraph(sb, sortOrder);
  //  // And return the result
  //  return sb;
  //}
  //
  //protected StringBuilder toHTMLAppendMoreTableLines(StringBuilder sb, Integer[] sortOrder) {
  //  return sb;
  //}
  //
  //protected StringBuilder toHTMLGraph(StringBuilder sb, Integer[] sortOrder) {
  //  return toHTMLGraph(sb, variables, varimp, sortOrder, max_var);
  //}
  //
  //static final StringBuilder toHTMLGraph(StringBuilder sb, String[] names, float[] vals, Integer[] sortOrder, int max) {
  //  Integer[] so = vals.length > max ? sortOrder : null;
  //  // Generate a graph
  //  DocGen.HTML.graph(sb, "graphvarimp", "g_varimp",
  //      DocGen.HTML.toJSArray(new StringBuilder(), names, so, Math.min(max, vals.length)),
  //      DocGen.HTML.toJSArray(new StringBuilder(), vals , so, Math.min(max, vals.length))
  //      );
  //  sb.append("<button id=\"sortBars\" class=\"btn btn-primary\">Sort</button>\n");
  //  return sb;
  //}
  ///** By default provides a sort order according to raw scores stored in <code>varimp</code>. */
  //protected Integer[] getSortOrder() {
  //  Integer[] sortOrder = new Integer[varimp.length];
  //  for(int i=0; i<sortOrder.length; i++) sortOrder[i] = i;
  //  Arrays.sort(sortOrder, new Comparator<Integer>() {
  //    @Override public int compare(Integer o1, Integer o2) { float f = varimp[o1]-varimp[o2]; return f<0 ? 1 : (f>0 ? -1 : 0); }
  //  });
  //  return sortOrder;
  //}

  /** Variable importance measured as relative influence.
   * It provides raw values, scaled values, and summary.
   * Motivate by R's GBM package. */
  public static class VarImpRI extends VarImp {
//    static final int API_WEAVER = 1; // This file has auto-gen'd doc & json fields
//    static public DocGen.FieldDoc[] DOC_FIELDS; // Initialized from Auto-Gen code.

    public VarImpRI(float[] varimp) {
      super(varimp);
    }

//    @API(help = "Scaled values of raw scores with respect to maximal value (GBM call - relative.influnce(model, scale=T)).")
    public float[] scaled_values() {
      float[] scaled = new float[varimp.length];
      int maxVar = 0;
      for (int i=0; i<varimp.length; i++)
        if (varimp[i] > varimp[maxVar]) maxVar = i;
      float maxVal = varimp[maxVar];
      for (int var=0; var<varimp.length; var++)
        scaled[var] = varimp[var] / maxVal;
      return scaled;
    }

//    @API(help = "Summary of values in percent (the same as produced by summary.gbm).")
    public float[] summary() {
      float[] summary = new float[varimp.length];
      float sum = ArrayUtils.sum(varimp);
      for (int var=0; var<varimp.length; var++)
        summary[var] = 100*varimp[var] / sum;
      return summary;
    }

    //@Override protected StringBuilder toHTMLAppendMoreTableLines(StringBuilder sb, Integer[] sortOrder ) {
    //  StringBuilder ssb = super.toHTMLAppendMoreTableLines(sb, sortOrder);
    //  DocGen.HTML.tableLine(sb, "Scaled values",  scaled_values(), sortOrder, Math.min(max_var, varimp.length));
    //  DocGen.HTML.tableLine(sb, "Influence in %", summary(), sortOrder, Math.min(max_var, varimp.length));
    //  return ssb;
    //}
    //@Override protected StringBuilder toHTMLGraph(StringBuilder sb, Integer[] sortOrder) {
    //  return toHTMLGraph(sb, variables, scaled_values(), sortOrder, max_var );
    //}
  }

  /** Variable importance measured as mean decrease in accuracy.
   * It provides raw variable importance measures, SD and z-scores. */
  public static class VarImpMDA extends VarImp {
//    static final int API_WEAVER = 1; // This file has auto-gen'd doc & json fields
//    static public DocGen.FieldDoc[] DOC_FIELDS; // Initialized from Auto-Gen code.

//    @API(help="Variable importance SD for individual variables.")
    public final float[]  varimpSD;

    /** Number of trees participating for producing variable importance measurements */
    private final int ntrees;

    public VarImpMDA(float[] varimp, float[] varimpSD, int ntrees) {
      super(varimp,null,VarImpMethod.PERMUTATION_IMPORTANCE);
      this.varimpSD = varimpSD;
      this.ntrees = ntrees;
    }

//    @API(help = "Z-score for individual variables")
    public float[] z_score() {
      float[] zscores = new float[varimp.length];
      double rnt = Math.sqrt(ntrees);
      for(int v = 0; v < varimp.length ; v++) zscores[v] = (float) (varimp[v] / (varimpSD[v] / rnt));
      return zscores;
    }

    //@Override protected StringBuilder toHTMLAppendMoreTableLines(StringBuilder sb, Integer[] sortOrder ) {
    //  StringBuilder ssb = super.toHTMLAppendMoreTableLines(sb, sortOrder);
    //  if (varimpSD!=null) {
    //    DocGen.HTML.tableLine(sb, "SD", varimpSD, sortOrder, Math.min(max_var, varimp.length));
    //    float[] zscores = z_score();
    //    DocGen.HTML.tableLine(sb, "Z-scores", zscores, sortOrder, Math.min(max_var, varimp.length));
    //  }
    //  return ssb;
    //}
  }
}
