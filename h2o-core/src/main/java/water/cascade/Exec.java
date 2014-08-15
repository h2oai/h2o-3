package water.cascade;


import water.*;
import water.fvec.Frame;
import water.util.Log;
import java.util.ArrayList;

/**
 * An interpreter.
 *
 * Parse an AST and then execute the AST by walking it.
 *
 * Parsing:
 *
 *   Trees are defined in the following way:
 *     '(' begins a tree node
 *     ')' ends   a tree node
 *     '#' signals the parser to parse a double
 *     's' signals the parser to parse a string
 *     '$' signals a named column selection
 *     '[' signals a column slice by index  -> R shalt replace column names with their indexes!
 *     '=' signals the parser to assign the RHS to the LHS. (So does '<-')
 *     'f' signals the parser to a parse a UDF.
 */

public class Exec extends Iced {

  //parser
  final byte[] _ast;
  int _x;

  //global env
  final Env _env;

  public Exec(String ast, Env env) {
    _ast = ast == null ? null : ast.getBytes();
    _env = env;

//    _display = new ArrayList<>();
//    _ast2ir = new AST2IR(ast); _ast2ir.make();
//    _display.add(new Env(_ast2ir.getLocked()));
  }

  public static Env exec( String str ) throws IllegalArgumentException {
//    cluster_init();
    // Preload the global environment from existing Frames
    ArrayList<Key> locked = new ArrayList<>();
    Env env = new Env(locked);

    //TODO: frame keys snapshot
//    final Key [] frameKeys = H2O.KeySnapshot.globalSnapshot().filter(new H2O.KVFilter() {
//      @Override public boolean filter(H2O.KeyInfo k) { return k._type == TypeMap.FRAME; }
//    }).keys();
//    for( Key k : frameKeys ) {      // Convert all VAs to Frames
//      Value val = DKV.get(k);
//      if( val == null || !val.isFrame()) continue;
//      // Bad if it's already locked by 'null', because lock by 'null' is removed when you leave Exec.
//      // Before was adding all frames with read-shared lock here.
//      // Should be illegal to add any keys locked by "null' to exec? (is it only unparsed keys?)
//      // undoing. this doesn't always work (gets stack trace)
//      Frame fr = val.get();
//      String kstr = k.toString();
//      try {
//        env.push(fr,kstr);
//        global.add(new ASTId(Type.ARY,kstr,0,global.size()));
//        fr.read_lock(null);
//        locked.add(fr._key);
//      } catch( Exception e ) {
//        Log.err("Exception while adding frame " + k + " to Exec env");
//      }
//    }

    // Some global constants
    env.put("TRUE", "double", "1");   env.put("T", "double", "1");
    env.put("FALSE", "doudble", "0"); env.put("F", "double", "0");
    env.put("NA", "double", Double.toString(Double.NaN));
    env.put("Inf", "double", Double.toString(Double.POSITIVE_INFINITY));

    // Parse.  Type-errors get caught here and throw IAE
    try {
      Exec ex = new Exec(str, env);
      AST ast = ex.parse();

      env = ast.treeWalk(env);
//      env.postWrite();
    } catch( RuntimeException t ) {
      env.remove_and_unlock();
      throw t;
    }
    return env;
  }

  protected AST parse() {
    // take a '('
    String tok = xpeek('(').parseID();
    //lookup of the token
    AST ast = AST.SYMBOLS.get(tok); // hash table declared here!
    assert ast != null : "Failed lookup on token: "+tok;
    return ast.parse_impl(this);
  }

  String parseID() {
    StringBuilder sb = new StringBuilder();
    while(_ast[_x] != ' ' && _ast[_x] != ')') { // isWhiteSpace...
      sb.append((char)_ast[_x++]);
    }
    _x++;
    return sb.toString();
  }

  Exec xpeek(char c) {
    assert _ast[_x] == c : "Expected '"+c+"'. Got: '"+(char)_ast[_x]+"'. unparsed: "+ unparsed() + " ; _x = "+_x;
    _x++; return this;
  }

  char peek() {
    return (char)_ast[_x];
  }

  Exec skipWS() {
    while (true) {
      if (_x >= _ast.length) break;
      if (peek() == ' ' || peek() == ')') {
        _x++;
        continue;
      }
      break;
    }
    return this;
  }

  String unparsed() { return new String(_ast,_x,_ast.length-_x); }

  // To avoid a class-circularity hang, we need to force other members of the
  // cluster to load the Exec & AST classes BEFORE trying to execute code
  // remotely, because e.g. ddply runs functions on all nodes.
  private static boolean _inited;       // One-shot init
  private static void cluster_init() {
    if( _inited ) return;
    new DTask() {
      @Override public void compute2() {
        new ASTPlus();          // Touch a common class to force loading
        tryComplete();
      }
    }.invoke();
    _inited = true;
  }
}