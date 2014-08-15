//package water.cascade;
////
////
////import com.google.gson.JsonObject;
//import water.H2O;
//import water.Iced;
//
//import java.util.HashMap;
////import water.Key;
////import java.util.ArrayList;
////import java.util.Arrays;
////import java.util.HashMap;
////import java.util.HashSet;
////
////
///**
//*  Transform the high-level AST passed from R into symbol tables along with an instruction set.
//*
//*  Walk the R-AST and perform execution on the spot.
//*/
//public class AST2IR extends Iced {
//  private final AST _ast;
//  private final Env _e;
//
////
//  private AST2IR(AST ast, Env e) { _ast = ast; _e = e; }
//
////  // Walk the ast and fill in the _program and _global components.
//  public void make(AST ast, Env e) { (new AST2IR(ast, e)).treeWalk(_ast, _e); }
//
//}