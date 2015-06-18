package water.rapids;

import water.Futures;
import water.H2O;
import water.Key;
import water.fvec.AppendableVec;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;

import java.util.Arrays;

/**
 *  The ASTFunc Object
 *
 *  An ASTFunc pulls the function ast produced by the front-end and creates a reference to this function.
 *
 *  A function has a body (which may be empty), and a body is a list of statements.
 *
 *  Statements that are possible:
 *
 *  if statements
 *  else statements
 *  for statements
 *  while statements
 *  switch statement
 *  declarative statements
 *  operative statements
 *  return statements
 *
 *  The last statement of a function will return the result of that statement.
 *
 *  Some Rules:
 *  -----------
 *
 *  Every function defines a new Environment that inherits from the current one. Put another way, the calling scope
 *  provides the context for the function to be executed in. Environments can be captured with the `capture` call.
 *
 *  No function shall modify state in any of its parent environments (which includes the DKV store). A function may only
 *  return values to a parent scope.
 */


/**
 * Functions will always have all of their arguments fully specified (even if that means they are filled with null).
 * This means `nargs` arguments are always parsed.
 */
public class ASTFunc extends ASTFuncDef {
  public ASTFunc() { super(); }
  public ASTFunc(String name, String[] arg_names, Env.SymbolTable table, ASTStatement body) {
    _name = name; _arg_names = arg_names; _table = table; _body = body;
  }
  AST[] _args;

  // (name args)
  @Override ASTFunc parse_impl(Exec E) {
    int nargs = _arg_names.length;
    AST[] args = new AST[nargs];
    for (int i = 0; i < nargs; ++i) args[i] = E.parse();
    E.eatEnd();
    ASTFunc res = (ASTFunc)clone();
    res._args = args;
    res._asts = _asts;
    return res;
  }

  @Override String opStr() { return _name; }
  @Override ASTOp make() {
    ASTFunc res = (ASTFunc)clone();
    res._name = _name;
    res._arg_names = _arg_names==null?null:Arrays.copyOf(_arg_names, _arg_names.length);
    res._args = _args==null?null:Arrays.copyOf(_args, _args.length);
    res._body = new ASTStatement();
    res._body._asts = _body==null?null:Arrays.copyOf(_body._asts, _body._asts.length);
    return res;
  }
  @Override void apply(Env e) {
    Env captured = e.capture();

    // for each arg in _args, want to fill in the appropriate value into the captured Env's symbol table
    for (int i = 0; i < _args.length; ++i) {
      if( _args[i] instanceof ASTId     ) _args[i] = e.lookup((ASTId)_args[i]);   // may have to do an initial lookup...

      // arg is an AST, must eval it...
      if( !(_args[i] instanceof ASTNum || _args[i] instanceof ASTString || _args[i] instanceof ASTFrame || _args[i] instanceof ASTNull) ) {
        _args[i].treeWalk(e);
        _args[i] = e.pop2AST();
      }

      if( _args[i] instanceof ASTNum         ) captured.put(_arg_names[i], Env.NUM, _args[i].value()); // put a #
      else if( _args[i] instanceof ASTString ) captured.put(_arg_names[i], Env.STR, _args[i].value()); // put a string
      else if( _args[i] instanceof ASTNull   ) captured.put(_arg_names[i], Env.NULL,"()");             // put a null
      else if( _args[i] instanceof ASTFrame  ) captured.put(_arg_names[i], ((ASTFrame)_args[i])._fr);  // put a frame
      else throw new IllegalArgumentException("Argument of type "+ _args[i].getClass()+" unsupported. Argument must be a String, number, Frame, or null.");
    }
    _body.exec(captured);    // execute the fcn body
    e.push(captured.peek()); // get the result from the captured scope without popping, sticks references into parent scope.
    captured.popScope();     // pop the captured scope
  }

  // used by methods that pass their args to FUN (e.g. apply, sapply, ddply); i.e. args are not parsed here.
  @Override void exec(Env e, AST... args) {
    _args = args;
    apply(e);
  }

  double[] map(Env env, double[] in, double[] out, AST[] args) {
    Futures fs = new Futures();
    Vec[] vecs = new Vec[in.length];
    Key keys[] = Vec.VectorGroup.VG_LEN1.addVecs(vecs.length);
    for( int c = 0; c < vecs.length; c++ ) {
      AppendableVec vec = new AppendableVec(keys[c]);
      NewChunk chunk = new NewChunk(vec, 0);
      chunk.addNum(in[c]);
      chunk.close(0, fs);
      vecs[c] = vec.close(fs);
    }
    fs.blockForPending();
    Key local_key = Key.make();
    Frame fr = new Frame(local_key, null, vecs);
    env.addRef(fr);
//    if( !env.isGlobal() ) {
//      if( env._local==null ) env._local = env.newTable();
//    }
//    env.put(local_key.toString(), fr); // push fr, since not in DKV, into the _local_frames -> must trash this frame at some point ... during popScope()
    AST[] as = new AST[args==null?1:args.length+1];
    as[0]=new ASTFrame(fr);
    if( args!=null )
      System.arraycopy(args, 0, as, 1, args.length);
    // execute the function on the row
    exec(env, as);

    // cleanup results and return
    if (env.isNum()) {
      if (out==null || out.length<1) out= new double[1];
      out[0] = env.popDbl();
    } else if (env.isAry()) {
      fr = env.popAry();
      if (fr.numCols() > 1 && fr.numRows() != 1) throw H2O.unimpl("Number of rows returned is > 1");
//      if (fr.numRows() > 1<<8) throw H2O.unimpl("Too many rows!");
      if (fr.numCols() > 1) {
        out = new double[fr.numCols()];
        for (int v = 0; v < fr.vecs().length; ++v) out[v] = fr.vecs()[v].at(0);
      } else {
        Vec vec = fr.anyVec();
        if (out == null || out.length < vec.length()) out = new double[(int) vec.length()];
        for (long i = 0; i < vec.length(); i++) out[(int) i] = vec.at(i);
      }
    } else {
      throw H2O.unimpl();
    }
//    env.cleanup(fr);
    return out;
  }

  @Override public StringBuilder toString( StringBuilder sb, int d ) {
    indent(sb,d).append(this).append(") {\n");
    _body.toString(sb,d+1).append("\n");
    return indent(sb,d).append("}");
  }
}

class ASTFuncDef extends ASTOp {
  String _name;
  String[] _arg_names;
  Env.SymbolTable _table;
  ASTStatement _body;
  public ASTFuncDef() { super(null); }   // super(null) => _vars[] = null

  void parse_func(Exec E) {
    String l;
    if( E.isSpecial(E.peek()) ) l = E.nextStr();
    else                        l = E.parseID();
    _name=l;

    // parse the function args: these are just arg names -> will do _local.put(name, Env.NULL, null) (local ST put)
    Env.SymbolTable table = E._env.newTable(); // grab a new SymbolTable
    AST arggs = E.parse();
    if( arggs instanceof ASTStringList ) _arg_names = ((ASTStringList)arggs)._s;
    else if( arggs instanceof ASTString) _arg_names = new String[]{((ASTString)arggs)._s};
    else throw new IllegalArgumentException("Expected args to be either a slist or a string (for a single argument). Got: " + arggs.getClass());

    if (_arg_names == null) table.put(null, Env.NULL, null);
    else for (String arg : _arg_names) table.put(arg, Env.NULL, null);
    _table = table;

    // parse the function body
    _body = new ASTStatement().parse_impl(E);
    E.eatEnd();
    ASTFunc res = new ASTFunc(_name, _arg_names, _table, _body);
    res._asts = null;
    putUDF(res, _name);  // not all nodes get this...
  }

  @Override String opStr() { return "def"; }
  @Override ASTOp make() { return new ASTFuncDef(); }
  @Override void apply(Env e) { throw H2O.unimpl("No `apply` call for ASTFuncDef"); }
}