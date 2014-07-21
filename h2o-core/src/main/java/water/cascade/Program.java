package water.cascade;


import water.DKV;
import water.H2O;
import water.Key;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

/**
 * The Program class: A unit of work to be executed by Env.
 *
 * A program can lookup the types and values of identifiers.
 * It may also write to the symbol table if it has permission.
 *
 * Valid operations are:
 *
 *  >push:   Push a blob of data onto the stack.
 *  >pop:    Pop a blob of data from the stack.
 *  >dup:    Push a copy of the data blob on the top of the stack back onto the stack.
 *  >op:     Do some operator specific instruction (handles all binary & prefix operators)
 *  >call:   Do some function call (handles all user-defined functions)
 *  >return: Return the result of the program (possibly to some return address)
 */
public class Program implements Iterable<Program.Statement> {

  final static public int OP   =0;
  final static public int CALL =1;
  final static public int PUSH =2;

  private SymbolTable _global; // The global symbol table.
  private SymbolTable _local;  // The local symbol table, null if program is main.
  private boolean     _isMain; // A boolean stating that this program is main.
  private String _name;   // The program's name.
  private ArrayList<Statement> _stmts;  // The list of program statements
  final private HashSet<Key> _locked = new HashSet<>();  // Set of keys existing in the global scope that are locked.

  public static final class Statement {
    private String _op;       // One of the valid statement operations: push, pop, dup, op, call, return
    private String _name;     // The value of the blob being pushed (boolean, double, string, etc.), or the name of a call/op.
    private String _dataType; // The data type of the object being pushed (null if a call/op --> not involved in push statement)
    private int    _kind;

    Statement(String op) {
      _op = op;
      _name = null;
      _dataType = null;
      _kind = OP;
    }

    Statement(String op, String call_name, int i /*just to differentiate from the Statement constructor for strings*/) {
      _op = op;
      _name = call_name;
      _dataType = null;
      _kind = CALL;
    }

    Statement(String op, double num) {
      _op = op;
      _name = String.valueOf(num);
      _dataType = "double";
      _kind = PUSH;
    }

    Statement(String op, String string) {
      _op = op;
      _name = string;
      _dataType = "String";
      _kind = PUSH;
    }

    Statement(String op, Key key) {
      _op = op;
      _name = key.toString();
      _dataType = "Key";
      _kind = PUSH;
    }

    public int kind() { return _kind; }
    public String name() { return _name; }
    public String type() { return _dataType; }
    public String op()   { return _op; }

    public Object value() {
      if (type().equals("double")) return Double.valueOf(name());
      if (type().equals("String")) return name();
      if (type().equals("Key"))    return DKV.get(Key.make(name())).get();
      assert false;
      throw H2O.fail("Error trying to get the value of the program statement.");
    }

      @Override public String toString() {
      String op   = this._op       == null ? "" : this._op;
      String name = this._name     == null ? "" : this._name;
      String type = this._dataType == null ? "" : "("+this._dataType+")";
      return op + " " + name + " " + type + "\n";
    }
  }

  Program(SymbolTable global, SymbolTable local, String name) {
    _global = global;
    _local  = local;
    _isMain = _local == null;
    _name   = name;
    _stmts  = new ArrayList<>();
  }

  public String name() { return _name; }
  protected int start() { return 0; }
  protected int end() { return _stmts.size(); }
  protected final boolean isMain() { return _isMain; }
  protected final boolean canWriteToGlobal() { return isMain(); }
  protected final HashSet<Key> locked() { return _locked; }
  protected final void addToLocked(Key k) { _locked.add(k); }

  @Override
  public String toString() {
    String str = "";
    for (Statement s : this) {
      str += s.toString();
    }
    return str;
  }

  protected final String readType(String name) {
    if (_local != null) {
      if (_local.typeOf(name) != null) return _local.typeOf(name);
    }
    if (_global.typeOf(name)!= null) return _global.typeOf(name);
    throw new IllegalArgumentException(
            "Could not find the identifier in the local or global scope while looking up type of: "+name);
  }

  protected final String readValue(String name) {
    if (_local != null) {
      if (_local.valueOf(name) != null) return _local.valueOf(name);
    }
    if (_global.valueOf(name)!= null) return _global.valueOf(name);
    throw new IllegalArgumentException(
            "Could not find the identifier in the local or global scopes while looking up value of: "+name);
  }

  // These write methods will stomp on the attributes for identifiers in the symbol table.
  protected final void writeType(String id, String type) {
    if (canWriteToGlobal()) {
      _global.writeType(id, type);
    } else {
      _local.writeValue(id, type);
    }
  }

  protected final void writeValue(String id, String value) {
    if (canWriteToGlobal()) {
      _global.writeValue(id, value);
    } else {
      _local.writeValue(id, value);
    }
  }

  protected final void putToTable(String name, String type, String value) {
    if (canWriteToGlobal()) {
      _global.put(name, type, value);
    } else {
      _local.put(name, type, value);
    }
  }

  protected final void addStatement(Statement stmt) { _stmts.add(stmt); }
  protected Statement getStmt(int idx) {return _stmts.get(idx); }

  public final Iterator<Statement> iterator() { return new StatementIter(start(), end()); }
  private class StatementIter implements Iterator<Statement> {
    int _pos = 0; final int _end;
    public StatementIter(int start, int end) { _pos = start; _end = end;}
    public boolean hasNext() { return _pos < _end;}
    public Statement next() { return getStmt(_pos++); }
    public void remove() { throw new RuntimeException("Unsupported"); }
  }
}