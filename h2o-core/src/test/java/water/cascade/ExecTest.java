package water.cascade;

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

public class ExecTest extends TestUtil {

  //@Test public void test1() {
  //  Frame fr = parse_test_file("smalldata/iris/iris.csv", "Last.value.0");
  //  Log.info(fr.vecs()[0].at(0));
  //  Log.info("Checking that the JSON AST representing the R expression `hex + 5` is executed");
  //  JsonObject ast = test1_json();
  //  Exec e = new Exec(ast);
  //  Frame fr2 = (Frame) e.runMain();
  //  Log.info(fr2.vecs()[0].at(0));
  //  assert fr2.vecs()[0].at(0) == 5 + fr.vecs()[0].at(0);
  //  fr.delete();
  //  fr2.delete();
  //}
  //
  //static JsonObject test1_json() {
  //  JsonParser parser = new JsonParser();
  //  String s = "{\"astop\":{\"type\":\"BinaryOperator\",\"operator\":\"+\",\"infix\":true,\"node_type\":\"ASTOp\",\"operands\":{\"left\":{\"type\":\"Frame\",\"value\":\"Last.value.0\",\"node_type\":\"ASTFrame\"},\"right\":{\"type\":\"Numeric\",\"value\":5,\"node_type\":\"ASTNumeric\"}}}}";
  //  return (JsonObject)parser.parse(s);
  //}

}
