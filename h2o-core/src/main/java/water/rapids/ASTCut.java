package water.rapids;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.util.MathUtils;

import java.util.Arrays;

public class ASTCut extends ASTPrim {
  @Override
  public String[] args() { return new String[]{"ary", "breaks", "labels", "include_lowest", "right", "digits"}; }
  @Override
  public String str() { return "cut"; }
  @Override int nargs() { return 1+6; } // (cut x breaks labels include_lowest right digits)
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    double[] cuts  = check(asts[2]);
    Arrays.sort(cuts);
    String[] labels= check2(asts[3]);
    final boolean lowest = asts[4].exec(env).getNum()==1;
    final boolean rite   = asts[5].exec(env).getNum()==1;
    final int     digits = Math.min((int) asts[6].exec(env).getNum(), 12); // cap at 12

    if(fr.vecs().length != 1 || fr.vecs()[0].isCategorical())
      throw new IllegalArgumentException("First argument must be a numeric column vector");

    double fmin = fr.anyVec().min();
    double fmax = fr.anyVec().max();

    int nbins = cuts.length - 1;  // c(0,10,100) -> 2 bins (0,10] U (10, 100]
    double width;
    if (nbins == 0) {
      if (cuts[0] < 2) throw new IllegalArgumentException("The number of cuts must be >= 2. Got: "+cuts[0]);
      // in this case, cut the vec into _cuts[0] many pieces of equal length
      nbins = (int) Math.floor(cuts[0]);
      width = (fmax - fmin)/nbins;
      cuts = new double[nbins];
      cuts[0] = fmin - 0.001*(fmax - fmin);
      for (int i = 1; i < cuts.length; ++i) cuts[i] = (i == cuts.length-1) ? (fmax + 0.001*(fmax-fmin))  : (fmin + i*width);
    }
    // width = (fmax - fmin)/nbins;
    // if(width == 0) throw new IllegalArgumentException("Data vector is constant!");
    if (labels != null && labels.length != nbins) throw new IllegalArgumentException("`labels` vector does not match the number of cuts.");

    // Construct domain names from _labels or bin intervals if _labels is null
    final double cutz[] = cuts;

    // first round _cuts to dig.lab decimals: example floor(2.676*100 + 0.5) / 100
    for (int i = 0; i < cuts.length; ++i) cuts[i] = Math.floor(cuts[i] * Math.pow(10,digits) + 0.5) / Math.pow(10,digits);

    String[][] domains = new String[1][nbins];
    if (labels == null) {
      domains[0][0] = (lowest ? "[" : left(rite)) + cuts[0] + "," + cuts[1] + rite(rite);
      for (int i = 1; i < (cuts.length - 1); ++i)  domains[0][i] = left(rite) + cuts[i] + "," + cuts[i+1] + rite(rite);
    } else domains[0] = labels;

    Frame fr2 = new MRTask() {
      @Override public void map(Chunk c, NewChunk nc) {
        int rows = c._len;
        for (int r = 0; r < rows; ++r) {
          double x = c.atd(r);
          if (Double.isNaN(x) || (lowest && x <  cutz[0])
                  || (!lowest && (x < cutz[0] || MathUtils.equalsWithinOneSmallUlp(x,cutz[0])) )
                  || (rite    && x >  cutz[cutz.length-1])
                  || (!rite   && (x > cutz[cutz.length-1] || MathUtils.equalsWithinOneSmallUlp(x,cutz[cutz.length-1]) )) ) nc.addNum(Double.NaN);
          else {
            for (int i = 1; i < cutz.length; ++i) {
              if (rite) {
                if (x <= cutz[i]) {
                  nc.addNum(i - 1);
                  break;
                }
              } else if (x < cutz[i]) { nc.addNum(i-1); break; }
            }
          }
        }
      }
    }.doAll(1, Vec.T_NUM, fr).outputFrame(fr.names(), domains);
    return new ValFrame(fr2);
  }

  private String left(boolean rite) { return rite ? "(" : "["; }
  private String rite(boolean rite) { return rite ? "]" : ")"; }

  private double[] check(AST ast) {
    double[] n;
    if( ast instanceof ASTNumList  ) n = ((ASTNumList)ast).expand();
    else if( ast instanceof ASTNum ) n = new double[]{((ASTNum)ast)._v.getNum()};  // this is the number of breaks wanted...
    else throw new IllegalArgumentException("Requires a number-list, but found a "+ast.getClass());
    return n;
  }
  private String[] check2(AST ast) {
    String[] s=null;
    if( ast instanceof ASTStrList ) s = ((ASTStrList)ast)._strs;
    else if( ast instanceof ASTStr) s = new String[]{ast.str()};
    return s;
  }
}
