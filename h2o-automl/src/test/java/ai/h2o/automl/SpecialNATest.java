package ai.h2o.automl;

import org.junit.Test;
import sun.jvm.hotspot.utilities.Assert;


public class SpecialNATest extends TestUtil {

  @Test public void intsTest() {
    ColMeta.SpecialNA specialInts = new ColMeta.SpecialNA(ColMeta.SpecialNA.INT);
    Assert.that(specialInts.typeToString().equals("int"), "type was " + specialInts.typeToString() + "; expected int");
    specialInts.add(5);
    try {
      specialInts.add("hello");
    } catch( AssertionError e) {
      Assert.that(e.getMessage().equals("asdf"), "fdsa");
    }
  }
}
