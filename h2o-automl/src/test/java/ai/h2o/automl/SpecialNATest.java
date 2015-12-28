package ai.h2o.automl;

import org.junit.Test;
import sun.jvm.hotspot.utilities.Assert;

// no H2O cloud needed for this test...
public class SpecialNATest {

  @Test public void intsTest() {
    ColMeta.SpecialNA specialInts = new ColMeta.SpecialNA(ColMeta.SpecialNA.INT);
    Assert.that(specialInts.typeToString().equals("int"), "type was " + specialInts.typeToString() + "; expected int");
    specialInts.add(5);

    // error to add String
    try {
      specialInts.add("hello");
    } catch( AssertionError e) {
      Assert.that(e.getMessage().equals("expected int; type was String"), "expected assertion");
    }

    // error to add double
    try {
      specialInts.add(5.34289);
    } catch( AssertionError e) {
      Assert.that(e.getMessage().equals("expected int; type was double"), "expected assertion");
    }

    // check the auto-resizing
    for(int i=0;i<50;++i)
      specialInts.add(i);
  }

  @Test public void strsTest() {
    ColMeta.SpecialNA specialStrs = new ColMeta.SpecialNA(ColMeta.SpecialNA.STR);
    Assert.that(specialStrs.typeToString().equals("String"), "type was " + specialStrs.typeToString() + "; expected String");
    specialStrs.add("hello");

    // error to add int
    try {
      specialStrs.add(5);
    } catch( AssertionError e) {
      Assert.that(e.getMessage().equals("expected String; type was int"), "expected assertion");
    }

    // error to add double
    try {
      specialStrs.add(5.34289);
    } catch( AssertionError e) {
      Assert.that(e.getMessage().equals("expected String; type was double"), "expected assertion");
    }

    // check the auto-resizing
    for(int i=0;i<50;++i)
      specialStrs.add("_"+i);
  }

  @Test public void dblsTest() {
    ColMeta.SpecialNA specialDbls = new ColMeta.SpecialNA(ColMeta.SpecialNA.DBL);
    Assert.that(specialDbls.typeToString().equals("double"), "type was " + specialDbls.typeToString() + "; expected double");
    specialDbls.add(3.14159);

    // error to add String
    try {
      specialDbls.add("hello");
    } catch( AssertionError e) {
      Assert.that(e.getMessage().equals("expected double; type was String"), "expected assertion");
    }

    // error to add int
    try {
      specialDbls.add(5);
    } catch( AssertionError e) {
      Assert.that(e.getMessage().equals("expected double; type was int"), "expected assertion");
    }

    // check the auto-resizing
    for(int i=0;i<50;++i)
      specialDbls.add(i+.34324);
  }
}
