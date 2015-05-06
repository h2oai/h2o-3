package water.rapids;

import water.*;
import water.fvec.*;

import java.util.ArrayList;

/**
* R's `apply`
*/
public class ASTApply extends ASTOp {
  protected static int _margin;  // 1 => work on rows; 2 => work on columns
  protected static String _fun;  // the function to run on the frame
  protected static AST[] _fun_args; // any additional args to _fun
  static final String VARS[] = new String[]{ "", "ary", "MARGIN", "FUN", "..."};
  public ASTApply( ) { super(VARS); }
  @Override String opStr(){ return "apply";}
  @Override ASTOp make() {return new ASTApply();}
  @Override ASTApply parse_impl(Exec E) {
    AST ary = E.parse();    // parse the array
    AST a = E.parse();      // parse the margin, must be 1 or 2
    if( a instanceof ASTNum ) _margin=(int)((ASTNum)a)._d;
    else throw new IllegalArgumentException("`MARGIN` must be either 1 or 2, it cannot be both.");
    _fun = ((ASTId)E.parse())._id;  // parse the function

    // parse any additional arguments
    ArrayList<AST> fun_args = new ArrayList<>();
    while( !E.isEnd() )fun_args.add(E.parse());
    E.eatEnd();

    ASTApply res = (ASTApply)clone();
    res._asts = new AST[]{ary};
    if (fun_args.size() > 0) _fun_args = fun_args.toArray(new AST[fun_args.size()]);
    else                     _fun_args = null;
    return res;
  }
  @Override void apply(Env env) {
    String err="Result of function produced more than a single column!";
    final ASTOp FUN = ASTOp.get(_fun);   // function never goes on the stack... distinctly not 1st class...

    // PEEK everything from the stack (do not POP)
    Frame fr2 = null;  // results Frame
    Frame fr = env.popAry();

    // apply FUN to each column;
    // assume work is independent of Vec order => otherwise asking for trouble anyways.
    // Types of results:
    //   A single row: Each col produces a single number result.
    //   A new array: Each column produces a new column
    //   If a new array, columns must be align'able.
    if( _margin == 2) {
      double[] row_result;
      Vec[] vecs_result;

      boolean isRow = false;
      ParallelVecApply t;
      H2O.submitTask(t=new ParallelVecApply(FUN,fr,_fun_args,env)).join();
      if( t._applyTasks[0]._vec_result==null ) isRow = true;
      // Create the results frame.
      if (isRow) {
        Futures fs = new Futures();
        Key key = Vec.VectorGroup.VG_LEN1.addVecs(1)[0];
        AppendableVec v = new AppendableVec(key);
        NewChunk chunk = new NewChunk(v, 0);
        row_result=new double[t._applyTasks.length];
        for(int i=0;i<row_result.length;++i) row_result[i] = t._applyTasks[i]._row_result;
        for (double aRow_result : row_result) chunk.addNum(aRow_result);
        chunk.close(0, fs);
        Vec vec = v.close(fs);
        fs.blockForPending();
        fr2 = new Frame(vec);
      } else {
        vecs_result=new Vec[t._applyTasks.length];
        for(int i=0; i<vecs_result.length;++i) vecs_result[i]=t._applyTasks[i]._vec_result;
        fr2 = new Frame(fr.names(), vecs_result);
      }
    }
    if( _margin == 1) {      // Work on rows
      // apply on rows is essentially a map function
      // find out return type
      double[] rowin = new double[fr.vecs().length];
      for (int c = 0; c < rowin.length; c++) rowin[c] = fr.vecs()[c].at(0);
      final int outlen = FUN.map(env,rowin,null, _fun_args).length;
      final Env env0 = env;
      MRTask mrt = new MRTask() {
        @Override public void map(Chunk[] cs, NewChunk[] ncs) {
          double rowin [] = new double[cs.length];
          double rowout[] = new double[outlen];
          for (int row = 0; row < cs[0]._len; row++) {
            for (int c = 0; c < cs.length; c++) rowin[c] = cs[c].atd(row);
            rowout = FUN.map(env0, rowin, rowout, _fun_args);
            for (int c = 0; c < ncs.length; c++) ncs[c].addNum(rowout[c]);
          }
        }
      };
      String[] names = new String[outlen];
      for (int i = 0; i < names.length; i++) names[i] = "C"+(i+1);
      fr2 = mrt.doAll(outlen,fr).outputFrame(names, null);
    }
    else if (_margin != 1 && _margin != 2) throw new IllegalArgumentException("MARGIN limited to 1 (rows) or 2 (cols)");
    env.pushAry(fr2);
  }

  private static class ParallelVecTask extends H2O.H2OCountedCompleter<ParallelVecTask> {
    // IN
    private final ASTOp _FUN;
    private final Frame _f;
    private final int _i;
    private final AST[] _funArgs;
    private final Env _env;

    // OUT
    private double _row_result;
    private Vec    _vec_result;

    ParallelVecTask(H2O.H2OCountedCompleter cc, ASTOp FUN, Frame fr, int i, AST[] funArgs, Env env) { super(cc); _FUN = FUN; _f=fr; _i=i; _funArgs=funArgs; _env=env; }
    @Override protected void compute2() {
      // combine all function args:
      ASTFrame f;
      AST[] args= new AST[_funArgs==null?1:_funArgs.length+1];
      args[0]=f=new ASTFrame(_f.vec(_i)._key.toString());
      if( _funArgs!=null )
        System.arraycopy(_funArgs, 0, args, 1, _funArgs.length);
      _FUN.exec(_env,args);
      if( _env.isNum() ) _row_result=_env.popDbl();
      else              {_vec_result=_env.popAry().anyVec(); _env.addRef(_vec_result); }
      DKV.remove(f._fr._key);
      tryComplete();
    }
  }

  private static class ParallelVecApply extends H2O.H2OCountedCompleter<ParallelVecApply> {
    // IN
    private final Frame _fr;
    private final ASTOp _FUN;
    private final AST[] _funArgs;
    private final Env   _env;

    // OUT
    ParallelVecTask[] _applyTasks;

    ParallelVecApply(ASTOp FUN, Frame fr, AST[] funArgs, Env env) { _FUN=FUN; _fr=fr; _funArgs=funArgs; _env=env; }
    @Override protected void compute2() {
      int nTasks;
      addToPendingCount((nTasks=_fr.numCols())-1);
      _applyTasks = new ParallelVecTask[nTasks];
      for(int i=0;i<nTasks;++i)
        (_applyTasks[i] = new ParallelVecTask(this, _FUN, _fr, i, _funArgs,_env)).compute2();
    }
  }
}

// --------------------------------------------------------------------------
// Same as "apply" but defaults to columns.
class ASTSApply extends ASTApply {
//  static final String VARS[] = new String[]{ "", "ary", "fcn", "..."};
  public ASTSApply( ) { super(); }
  @Override String opStr(){ return "sapply";}
  @Override ASTOp make() {return new ASTSApply();}
  @Override ASTSApply parse_impl(Exec E) {
    AST ary = E.parse();
    if (ary instanceof ASTId) ary = Env.staticLookup((ASTId)ary);
    _margin = 2;
    _fun = ((ASTId)E.skipWS().parse())._id;
    ArrayList<AST> fun_args = new ArrayList<>();
    while(E.skipWS().hasNext()) {
      fun_args.add(E.parse());
    }
    ASTSApply res = (ASTSApply)clone();
    res._asts = new AST[]{ary};
    if (fun_args.size() > 0) _fun_args = fun_args.toArray(new AST[fun_args.size()]);
    return res;
  }
  @Override void apply(Env env) {
    super.apply(env);
  }
}

// --------------------------------------------------------------------------
// unique(ary)
// Returns only the unique rows

//class ASTUnique extends ASTddply {
//  static final String VARS[] = new String[]{ "", "ary"};
//  ASTUnique( ) { super(VARS, new Type[]{Type.ARY, Type.ARY}); }
//  @Override String opStr(){ return "unique";}
//  @Override ASTOp make() {return new ASTUnique();}
//  @Override void apply(Env env, int argcnt, ASTApply apply) {
//    Thread cThr = Thread.currentThread();
//    Frame fr = env.peekAry();
//    int cols[] = new int[fr.numCols()];
//    for( int i=0; i<cols.length; i++ ) cols[i]=i;
//    ddplyPass1 p1 = new ddplyPass1( false, cols ).doAll(fr);
//    double dss[][] = new double[p1._groups.size()][];
//    int i=0;
//    for( Group g : p1._groups.keySet() )
//      dss[i++] = g._ds;
//    Frame res = FrameUtils.frame(fr._names,dss);
//    env.poppush(2,res,null);
//  }
//}
