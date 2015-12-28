package ai.h2o.automl;

import org.junit.Test;
import sun.jvm.hotspot.utilities.Assert;


public class SpecialNATest {

  @Test public void intsTest() {
    ColMeta.SpecialNA specialInts = new ColMeta.SpecialNA(ColMeta.SpecialNA.INT);
    Assert.that(specialInts.typeToString().equals("int"), "type was " + specialInts.typeToString() + "; expected int");
    specialInts.add(5);

    // error to add String
    try {
      specialInts.add("hello");
    } catch( AssertionError e) {
      Assert.that(e.getMessage().equals("expected int; type is String"), "expected assertion");
    }

    // error to add double
    try {
      specialInts.add(5.34289);
    } catch( AssertionError e) {
      Assert.that(e.getMessage().equals("expected int; type is double"), "expected assertion");
    }

    for(int i=0;i<50;++i)
      specialInts.add(i);

    System.out.println();
  }
}
