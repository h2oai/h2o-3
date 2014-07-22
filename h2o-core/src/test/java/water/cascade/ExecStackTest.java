package water.cascade;

import org.testng.AssertJUnit;
import org.testng.annotations.*;
import water.Iced;

public class ExecStackTest extends Iced {

  /*@Test(expectedExceptions = IllegalArgumentException.class)*/
  public void test1() {
    Env e = new Env(null);
    e._stack.push(new Double(5.0));
    e._stack.push(new Double(-123.0));
    e._stack.push("I'm a string!");
    AssertJUnit.assertEquals("stack size == 3", 3, e._stack.size());
    AssertJUnit.assertTrue("Top is a String", e._stack.peek() instanceof String);
    AssertJUnit.assertEquals("top == \"I'm a string!\"", "I'm a string!", (String) e._stack.pop());
    AssertJUnit.assertEquals("stack size == 2", 2, e._stack.size());
    e._stack.peekAt(1);
    e._stack.peekAt(9000);
    AssertJUnit.assertTrue(e._stack.peekAt(9000) == null);
    e._stack.peekAt(-1);
    AssertJUnit.assertFalse(e._stack.isEmpty());
  }
}
