package water.cascade;

//import com.google.gson.JsonObject;
//import com.google.gson.JsonParser;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import water.Iced;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.NFSFileVec;
import water.util.Log;

import java.io.File;

public class ExecTest extends Iced {
  @BeforeClass public static void stall() { TestUtil tu = new TestUtil(); tu.setupCloud(); }
  @AfterClass public static void checkLeakedKeys() { TestUtil.checkLeakedKeys(); }

//  @Test public void test1() {
//    Frame fr = parse_test_file("smalldata/iris/iris.csv", "Last.value.0");
//    Log.info(fr.vecs()[0].at(0));
//    Log.info("Checking that the JSON AST representing the R expression `hex + 5` is executed");
//    JsonObject ast = test1_json();
//    Exec e = new Exec(ast);
//    Frame fr2 = (Frame) e.runMain();
//    Log.info(fr2.vecs()[0].at(0));
//    assert fr2.vecs()[0].at(0) == 5 + fr.vecs()[0].at(0);
//    fr.delete();
//    fr2.delete();
//  }

//  static JsonObject test1_json() {
//    JsonParser parser = new JsonParser();
//    String s = "{\"astop\":{\"type\":\"BinaryOperator\",\"operator\":\"+\",\"infix\":true,\"node_type\":\"ASTOp\",\"operands\":{\"left\":{\"type\":\"Frame\",\"value\":\"Last.value.0\",\"node_type\":\"ASTFrame\"},\"right\":{\"type\":\"Numeric\",\"value\":5,\"node_type\":\"ASTNumeric\"}}}}";
//    return (JsonObject)parser.parse(s);
//  }

  /** Hunt for test files in likely places.  Null if cannot find.
   *  @param fname Test filename
   *  @return      Found file or null */
  protected File find_test_file( String fname ) {
    File file = new File(fname);
    if( !file.exists() )
      file = new File("target/" + fname);
    if( !file.exists() )
      file = new File("../" + fname);
    if( !file.exists() )
      file = new File("../target/" + fname);
    if( !file.exists() )
      file = null;
    return file;
  }

  /**
   * Find & parse a CSV file.  NPE if file not found.
   * @param fname Test filename
   * @param kname Test filename key name.
   * @return       Frame or NPE */
  public Frame parse_test_file(String fname, String kname) {
    NFSFileVec nfs = NFSFileVec.make(find_test_file(fname));
    return water.parser.ParseDataset2.parse(Key.make(kname), nfs._key);
  }
}
