package ai.h2o.automl;

import ai.h2o.automl.guessers.column.SpecialNAGuesser.SpecialNA;
import org.junit.Assert;
import org.junit.Test;

// no H2O cloud needed for this test...
public class SpecialNATest {

  @Test public void intsTest() {
    SpecialNA specialInts = new SpecialNA(SpecialNA.INT);
    Assert.assertTrue("type was " + specialInts.typeToString() + "; expected int", specialInts.typeToString().equals("int"));
    specialInts.add(5);

    // error to add String
    try {
      specialInts.add("hello");
    } catch( AssertionError e) {
      Assert.assertTrue("expected assertion",e.getMessage().equals("expected int; type was String"));
    }

    // error to add double
    try {
      specialInts.add(5.34289);
    } catch( AssertionError e) {
      Assert.assertTrue("expected assertion",e.getMessage().equals("expected int; type was double"));
    }

    // check the auto-resizing
    for(int i=0;i<50;++i)
      specialInts.add(i);
  }

  @Test public void strsTest() {
    SpecialNA specialStrs = new SpecialNA(SpecialNA.STR);
    Assert.assertTrue("type was " + specialStrs.typeToString() + "; expected String", specialStrs.typeToString().equals("String"));
    specialStrs.add("hello");

    // error to add int
    try {
      specialStrs.add(5);
    } catch( AssertionError e) {
      Assert.assertTrue("expected assertion",e.getMessage().equals("expected String; type was int"));
    }

    // error to add double
    try {
      specialStrs.add(5.34289);
    } catch( AssertionError e) {
      Assert.assertTrue("expected assertion",e.getMessage().equals("expected String; type was double"));
    }

    // check the auto-resizing
    for(int i=0;i<50;++i)
      specialStrs.add("_"+i);
  }

  @Test public void dblsTest() {
    SpecialNA specialDbls = new SpecialNA(SpecialNA.DBL);
    Assert.assertTrue("type was " + specialDbls.typeToString() + "; expected double", specialDbls.typeToString().equals("double"));
    specialDbls.add(3.14159);

    // error to add String
    try {
      specialDbls.add("hello");
    } catch( AssertionError e) {
      Assert.assertTrue("expected assertion",e.getMessage().equals("expected double; type was String"));
    }

    // error to add int
    try {
      specialDbls.add(5);
    } catch( AssertionError e) {
      Assert.assertTrue("expected assertion", e.getMessage().equals("expected double; type was int"));
    }

    // check the auto-resizing
    for(int i=0;i<50;++i)
      specialDbls.add(i+.34324);
  }
}
