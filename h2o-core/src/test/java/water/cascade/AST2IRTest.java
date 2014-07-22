package water.cascade;

import java.util.Arrays;
import org.testng.annotations.*;
import water.Iced;
import water.TestUtil;
import water.util.Log;

public class AST2IRTest extends TestUtil {

  //@Test public void test1() {
  //  Log.info("Checking the instructions produced by the JSON AST representing the R expression `hex + 5`");
  //  JsonObject ast = test1_json();
  //  printInstructions(ast, new String[]{"push 5.0 (double)\npush Last.value.0 (Key)\n+  \n"});
  //}
  //
  //@Test public void test2() {
  //  Log.info("Checking the instructions produced by the JSON AST representing the R expression `hex + 5 + 10`");
  //  JsonObject ast = test2_json();
  //  printInstructions(ast, new String[]{"push 10.0 (double)\npush 5.0 (double)\npush Last.value.0 (Key)\n+  \n+  \n"});
  //}
  //
  //@Test public void test3() {
  //  Log.info("Checking the expression `hex + 5 - 1 * hex + 15 * (23 / hex)`");
  //  JsonObject ast = test3_json();
  //  printInstructions(ast, new String[]{"push Last.value.0 (Key)\npush 23.0 (double)\n/  \npush 15.0 (double)\n*  \npush Last.value.0 (Key)\npush 1.0 (double)\n*  \npush 5.0 (double)\npush Last.value.0 (Key)\n+  \n-  \n+  \n"});
  //}
  //
  //static void printInstructions(JsonObject ast, String[] checkInstructions) {
  //  AST2IR main = new AST2IR(ast);
  //  main.make();
  //  Log.info((Object)main._toString());
  //  Log.info((Object)checkInstructions);
  //  if (!checkInstructions[0].equals("")) {
  //    assert checkInstructions[0].equals(main._toString()[0]);
  //  }
  //}
  //
  //static JsonObject test1_json() {
  //  JsonParser parser = new JsonParser();
  //  String s = "{\"astop\":{\"type\":\"BinaryOperator\",\"operator\":\"+\",\"infix\":true,\"node_type\":\"ASTOp\",\"operands\":{\"left\":{\"type\":\"Frame\",\"value\":\"Last.value.0\",\"node_type\":\"ASTFrame\"},\"right\":{\"type\":\"Numeric\",\"value\":5,\"node_type\":\"ASTNumeric\"}}}}";
  //  return (JsonObject)parser.parse(s);
  //}
  //
  //static JsonObject test2_json() {
  //  JsonParser parser = new JsonParser();
  //  String s= "{\"astop\":{\"type\":\"BinaryOperator\",\"operator\":\"+\",\"infix\":true,\"node_type\":\"ASTOp\",\"operands\":{\"left\":{\"astop\":{\"type\":\"BinaryOperator\",\"operator\":\"+\",\"infix\":true,\"node_type\":\"ASTOp\",\"operands\":{\"left\":{\"type\":\"Frame\",\"value\":\"Last.value.0\",\"node_type\":\"ASTFrame\"},\"right\":{\"type\":\"Numeric\",\"value\":5,\"node_type\":\"ASTNumeric\"}}}},\"right\":{\"type\":\"numeric\",\"value\":10,\"node_type\":\"ASTNumeric\"}}}}";
  //  return (JsonObject)parser.parse(s);
  //}
  //
  //static JsonObject test3_json() {
  //  JsonParser parser = new JsonParser();
  //  String s = "{\"astop\":{\"type\":\"BinaryOperator\",\"operator\":\"+\",\"infix\":true,\"node_type\":\"ASTOp\",\"operands\":{\"left\":{\"astop\":{\"type\":\"BinaryOperator\",\"operator\":\"-\",\"infix\":true,\"node_type\":\"ASTOp\",\"operands\":{\"left\":{\"astop\":{\"type\":\"BinaryOperator\",\"operator\":\"+\",\"infix\":true,\"node_type\":\"ASTOp\",\"operands\":{\"left\":{\"type\":\"Frame\",\"value\":\"Last.value.0\",\"node_type\":\"ASTFrame\"},\"right\":{\"type\":\"Numeric\",\"value\":5,\"node_type\":\"ASTNumeric\"}}}},\"right\":{\"astop\":{\"type\":\"BinaryOperator\",\"operator\":\"*\",\"infix\":true,\"node_type\":\"ASTOp\",\"operands\":{\"left\":{\"type\":\"numeric\",\"value\":1,\"node_type\":\"ASTNumeric\"},\"right\":{\"type\":\"Frame\",\"value\":\"Last.value.0\",\"node_type\":\"ASTFrame\"}}}}}}},\"right\":{\"astop\":{\"type\":\"BinaryOperator\",\"operator\":\"*\",\"infix\":true,\"node_type\":\"ASTOp\",\"operands\":{\"left\":{\"type\":\"numeric\",\"value\":15,\"node_type\":\"ASTNumeric\"},\"right\":{\"astop\":{\"type\":\"BinaryOperator\",\"operator\":\"/\",\"infix\":true,\"node_type\":\"ASTOp\",\"operands\":{\"left\":{\"type\":\"numeric\",\"value\":23,\"node_type\":\"ASTNumeric\"},\"right\":{\"type\":\"Frame\",\"value\":\"Last.value.0\",\"node_type\":\"ASTFrame\"}}}}}}}}}}";
  //  return (JsonObject)parser.parse(s);
  //
  //  /*
  //  *  Here's the tree for this expression `hex + 5 - 1 * hex + 15 * (23 / hex)`:
  //  *                         +
  //  *                      /     \
  //  *                     /        \
  //  *                    /           \
  //  *                   /              \
  //  *                  /                 \
  //  *                 /                    \
  //  *                -                      *
  //  *              /     \               /     \
  //  *             +       *            15      '/'
  //  *          /    \     / \                /    \
  //  *         /      \   /   \              /      \
  //  * Last.value.0   5  1  Last.value.0    23   Last.value.0
  //  */
  //}
}
