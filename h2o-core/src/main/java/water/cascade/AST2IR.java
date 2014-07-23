package water.cascade;
//
//
//import com.google.gson.JsonObject;
import water.Iced;
//import water.Key;
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.HashMap;
//import java.util.HashSet;
//
//
///**
// *  Transform the high-level AST passed from R into symbol tables along with an instruction set.
// *
// *  Walk the R-AST and produce execution instructions and symbol tables to be passed into an object of type Env. There
// *  are at most two symbol tables for every scope (i.e. every Env represents its own scope, see Env.java for more):
// *    1. The Global symbol table;
// *    2. The Local  symbol table;
// *  The symbol table class explains this data structure in more detail.
// *
// *  More Details:
// *  -------------
// *  The Intermediate Representation of the R expression:
// *
// *  The first phase of executing the R expression occurs in the "front-end" R session, where all operations and calls
// *  are intercepted by the h2o R package and subsequently "packed away" into abstract syntax trees. These ASTs are
// *  converted to JSON, sent over the wire, and dropped here for further analysis.
// *
// *  First cut: Don't do any analysis or java code generation. Generate simple instructions for the interpreter.
// *
// *  This phase generates an array of Program objects, which hold references to Symbol Tables and instructions for the
// *  interpreter.
// *
// *  There is a special type of program that is the "global" program. This is the *main* program. It is responsible for
// *  switching control to other programs (user-defined functions and other calls), and managing returned values. There
// *  is always a main, even in cases such as "result <- f(...)". The left-arrow assignment is the main in this case.
// *  If f(...) is called without assignment, then there is a temporary key created and spit out to the console. The
// *  lifetime of this temporary key is discussed elsewhere.
// */
public class AST2IR extends Iced {
//  private final JsonObject _ast;
//  private SymbolTable _global;
//  private ArrayList<Program> _program;
//
//  final private String[] ARITHMETICOPS = new String[]{"+", "-", "/", "*"};
//  final private String[] BITWISEOPS = new String[]{"&", "&&", "|", "||", "!"};
//  final private String[] COMPAREOPS = new String[]{"!=", "<=", "==", ">=", "<", ">"};
//
//  public AST2IR(JsonObject ast) {
//    _ast = ast;
//    _global = new SymbolTable();
//    _program = new ArrayList<Program>();
//  }
//
//  public JsonObject ast() { return _ast; }
//  public SymbolTable table() { return _global; }
//  public Program[] program() { return _program.toArray(new Program[_program.size()]); }
//  public HashSet<Key> getLocked() { return program()[0].locked(); }
//
//  // Walk the ast and fill in the _program and _global components.
//  public void make() { treeWalk(_ast, 0, new Program(_global, null, "main")); }
//
//  // toString the program as text instructions
//  public String[] _toString() {
//    Program[] programs = program();
//    String[] progs = new String[programs.length];
//    for (int i = 0; i < progs.length; ++i) {
//      progs[i] = programs[i].toString();
//    }
//    return progs;
//  }
//
//  //--------------------------------------------------------------------------------------------------------------------
//  // Node inspectors
//  //--------------------------------------------------------------------------------------------------------------------
//  // Simple util function for getting the node type as a String
//  private String getNodeType(JsonObject node) { return node.get("node_type").getAsString(); }
//
//  // Check if the node we have is an ASTOp of some sort if true, can check the type of Op below
//  private boolean isASTOp(JsonObject node) {
//    String node_type = node.get("node_type").getAsString();
//    return node_type != null && node_type.equals("ASTOp");
//  }
//
//  // Check for standard arithmetic operations
//  private boolean isArithmeticOp(JsonObject node) {
//    if (isOp(node)) {
//      JsonObject jo = node.get("astop").getAsJsonObject();
//      if (jo.get("type").getAsString().equals("BinaryOperator")) {
//        if (Arrays.asList(ARITHMETICOPS).contains(jo.get("operator").getAsString()))
//          return true;
//      }
//    }
//    return false;
//  }
//
//  // Check for bitwise operations
//  private boolean isBitwiseOp(JsonObject node) {
//    if (isOp(node)) {
//      JsonObject jo = node.get("astop").getAsJsonObject();
//      if (jo.get("type").getAsString().equals("BinaryOperator")) {
//        if (Arrays.asList(BITWISEOPS).contains(jo.get("operator").getAsString()))
//          return true;
//      }
//    }
//    return false;
//  }
//
//  // Check if any of the node is a comparison node
//  private boolean isCompareOp(JsonObject node) {
//    if (isOp(node)) {
//      JsonObject jo = node.get("astop").getAsJsonObject();
//      if (jo.get("type").getAsString().equals("BinaryOperator")) {
//        if (Arrays.asList(COMPAREOPS).contains(jo.get("operator").getAsString()))
//          return true;
//      }
//    }
//    return false;
//  }
//
//  // A function call has a top level entry point called "astcall", similar to "astop"
//  private boolean isCall(JsonObject node) { return node.get("astcall") != null; }
//
//  // Any name that is being assigned to is an ID. These are ASTUnk types where isFormal is false.
//  private boolean isId(JsonObject node) {
//    String node_type = getNodeType(node);
//    return node_type != null && node_type.equals("ASTUnk") && !node.get("isFormal").getAsBoolean();
//  }
//
//  // Check if the node has a type of numeric.
//  private boolean isFrame(JsonObject node) {
//    String node_type = getNodeType(node);
//    return node_type != null && node_type.equals("ASTFrame");
//  }
//
//  // Check if the node has a type of numeric.
//  private boolean isConst(JsonObject node) {
//    String node_type = getNodeType(node);
//    return node_type != null && node_type.equals("ASTNumeric");
//  }
//
//  // Check if the node has a type of String.
//  private boolean isString(JsonObject node) {
//    String node_type = getNodeType(node);
//    return node_type != null && node_type.equals("ASTString");
//  }
//
//  // Check if the node is an arg to a call
//  private boolean isArg(JsonObject node) {
//    String node_type = getNodeType(node);
//    return node_type != null && node_type.equals("ASTUnk") && node.get("isFormal").getAsBoolean();
//  }
//
//  // Differs from isASTOp in that the JSON structure has "astop : { ast_opNode : {node_type : "ASTOp", ...},...}"
//  private boolean isOp(JsonObject node) { return node.get("astop") != null; }
//
//  // Check if the node is a leaf
//  private boolean isLeaf(JsonObject node) {
//    return isId(node) || isFrame(node) || isConst(node) || isCall(node) || isString(node) || isArg(node);
//  }
//
//  // Can get the numeric node value as a boolean...
//  private boolean canGetAsBoolean(JsonObject node) {
//    return node.get("value").getAsDouble() == 1.0 || node.get("value").getAsDouble() == 0.0;
//  }
//
//  // Add a new op statement to the program list of statements
//  private void addNewOpStatement(String op, Program p) { p.addStatement(new Program.Statement(op)); }
//
//  // Add a new call statement to the program list of statements
//  private void addNewCallStatement(String call, Program p) { p.addStatement(new Program.Statement("call", call, 0)); }
//
//  // Add a push statement to the program list of statements
//  private void stringPushStatement(String obj, Program p) { p.addStatement(new Program.Statement("push", obj)); }
//  private void numPushStatement(   double obj, Program p) { p.addStatement(new Program.Statement("push", obj)); }
//  private void keyPushStatement(      Key obj, Program p) { p.addStatement(new Program.Statement("push", obj));
//    if (p.isMain()) p.addToLocked(obj);
//  }
//
//  //--------------------------------------------------------------------------------------------------------------------
//  // Node getters
//  //--------------------------------------------------------------------------------------------------------------------
//  private Double  getNum      (JsonObject node) { return node.get("value").getAsDouble();           }
//  private Boolean getBoolean  (JsonObject node) { return node.get("value").getAsBoolean();          }
//  private String  getString   (JsonObject node) { return node.get("value").getAsString();           }
//  private Key     getKey      (JsonObject node) { return Key.make(node.get("value").getAsString()); }
//  private String  getArgName  (JsonObject node) { return node.get("arg_name").getAsString();        }
//  private String  getArgType  (JsonObject node) { return node.get("arg_type").getAsString();        }
//  private String  getArgValue (JsonObject node) { return node.get("arg_value").getAsString();       }
//  private int     getArgNumber(JsonObject node) { return node.get("arg_number").getAsInt();         }
//  private String  getIdValue  (JsonObject node) { return node.get("key").getAsString();             }
//
//  //--------------------------------------------------------------------------------------------------------------------
//  // Tree Walker -- Code Generator
//  //--------------------------------------------------------------------------------------------------------------------
//
//  /**
//   * Walk an AST and fill in _program.
//   * @param tree A node of the AST -- preferably some kind of root node.
//   * @param lineNum A line number in the program. Programs start at 0 and go up.
//   *
//   *
//   * A root node in the AST has a few different possible key names:
//   *  a. "astop"   : Has operands list of nodes that must be recursed
//   *  b. "astcall" : Must create a new Program object
//   *  c. "left"    : Recurse down this guy, should not need to be checked. ever.
//   *  d. "right"   : Same as 'c'.           should not need to be checked. ever.
//   *
//   * Inspect the tree node and append a statement to the Program p.
//   */
//  //TODO: Need to handle prefix ops as well, currently only supports binary infix.
//  private void treeWalk(JsonObject tree, int lineNum, Program p) {
//
//    // First check if we're a top-level node of type astop
//    if (isOp(tree)) {
//
//      tree = tree.get("astop").getAsJsonObject();
//
//      if (isASTOp(tree)) {
//        JsonObject operands = tree.get("operands").getAsJsonObject();
//
////TODO: Operator-specific instructions may be needed?
////        // Can be a binary arithmetic operator
////        if (isArithmeticOp(tree)) {
////          addNewOpStatement(tree.get("operator").getAsString(), p);
////
////        // Can be a binary bitwise operator
////        } else if (isBitwiseOp(tree)) {
////          addNewOpStatement(tree.get("operator").getAsString(), p);
////
////        // Can be a binary comparison operator (also binary)
////        } else if (isCompareOp(tree)) {
////          addNewOpStatement(tree.get("operator").getAsString(), p);
////        }
//
//        treeWalk(operands.get("right").getAsJsonObject(), lineNum + 1, p);
//        treeWalk(operands.get("left").getAsJsonObject(), lineNum + 1, p);
//        addNewOpStatement(tree.get("operator").getAsString(), p);
//
//      } else {
//        throw new IllegalArgumentException("Unkown operator type: "+getNodeType(tree));
//      }
//    // Check if we have an argument node
//    } else if (isArg(tree)) {
//      p.putToTable(getArgName(tree), getArgType(tree), getArgValue(tree));
//
//    // Check if we have an argument node
//    } else if (isId(tree)) {
//      p.putToTable(getIdValue(tree), null,  null);
//
//    // Check if we have an argument node
//    } else if (isString(tree)) {
//      stringPushStatement(getString(tree), p);
//
//    // Check if we have an argument node
//    } else if (isConst(tree)) {
//      numPushStatement(getNum(tree), p);
//
//    // Check if we have an argument node
//    } else if (isFrame(tree)) {
//      keyPushStatement(getKey(tree), p);
//
//    // Check if we're a top-level node of type astcall, this should be parsed into a separate program...
//    } else if (isCall(tree)) {
//      JsonObject call_tree = tree.get("astcall").getAsJsonObject();
//      String call_name = call_tree.getAsString();
//
//      //TODO: Have an isRecursive field??
//
//      // Add the call statement to the program
//      addNewCallStatement(call_name, p);
//
//      treeWalk(call_tree, 0, new Program(_global, new SymbolTable(), call_name));
//    }
//
//    // Add the program to the list and exit
//    if(!isLeaf(tree) && lineNum == 0) { _program.add(p); }
//  }
//}
//
///**
// *  The Symbol Table Data Structure: A mapping between identifiers and their values.
// *
// *  The role of the symbol table is to track the various identifiers and their attributes that appear in the nodes of
// *  the AST passed from R. There are three cases that are of interest:
// *    1. The identifier is a variable that references some blob of data having a type, value, and scope.
// *    2. The identifier is part of an assignment and its type is whatever the type is on the right-hand side.
// *    3. There is no identifier: a non-case, but worth mentioning.
// *
// *  As already stated, each identifier has a name, type, value, and scope. The scoping is implied by the Env object,
// *  so it is not necessary to include this attribute.
// *
// *  Valid types:
// *
// *    Usual types: string, int, double, float, boolean,
// *    unk: An identifier being assigned to
// *    arg: An argument to a function call
// *    call: A function call (if UDF, value is the body of the function)
// *    key: An h2o key
// *
// *  Symbol Table Permissions:
// *  -------------------------
// *
// *  Every Program object will have at most two symbol tables: the global and local tables.
// *  The global table is read only by every Program that has a non-null local table.
// *  The global table is read-write by the main program only.
// *
// *  NB: The existence of a non-null symbol table implies that execution is occurring in a non-global scope.
// */
//class SymbolTable extends Iced {
//
//  HashMap<String, SymbolAttributes> _table;
//  SymbolTable() { _table = new HashMap<String, SymbolAttributes>(); }
//
//  public void put(String name, String type, String value) {
//    if (_table.containsKey(name)) { return; }
//    SymbolAttributes attributes = new SymbolAttributes(type, value);
//    _table.put(name, attributes);
//  }
//
//  public String typeOf(String name) {
//    assert _table.containsKey(name) : "No such identifier in the symbol table: " + name;
//    return _table.get(name).typeOf();
//  }
//
//  public String valueOf(String name) {
//    assert _table.containsKey(name) : "No such identifier in the symbol table: " + name;
//    return _table.get(name).valueOf();
//  }
//
//  public void writeType(String name, String type) {
//    assert _table.containsKey(name) : "No such identifier in the symbol table: " + name;
//    SymbolAttributes attrs = _table.get(name);
//    attrs.writeType(type);
//    _table.put(name, attrs);
//  }
//
//  public void writeValue(String name, String value) {
//    assert _table.containsKey(name) : "No such identifier in the symbol table: " + name;
//    SymbolAttributes attrs = _table.get(name);
//    attrs.writeValue(value);
//    _table.put(name, attrs);
//  }
//
//  private class SymbolAttributes {
//    private String _type;
//    private String _value;
//
//    SymbolAttributes(String type, String value) { _type = type; _value = value; }
//
//    public String typeOf ()  { return  _type;  }
//    public String valueOf()  { return  _value; }
//
//    public void writeType(String type)   { this._type  = type; }
//    public void writeValue(String value) { this._value = value;}
//
//  }
}