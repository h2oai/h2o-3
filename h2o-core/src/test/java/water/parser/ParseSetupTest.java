package water.parser;

import org.junit.Test;

import static org.junit.Assert.*;

public class ParseSetupTest {

  @Test
  public void isNA() throws Exception {
    ParseSetup p = new ParseSetup();
    p._na_strings = new String[][]{
      new String[] {"na"},
      null,
      new String[] {"NA", "null"}
    };

    assertTrue(p.isNA(0, new BufferedString("na")));
    assertFalse(p.isNA(0, new BufferedString("NA")));
    assertFalse(p.isNA(1, new BufferedString("na")));
    assertTrue(p.isNA(2, new BufferedString("NA")));
    assertTrue(p.isNA(2, new BufferedString("null")));
    assertFalse(p.isNA(2, new BufferedString("na")));
    assertFalse(p.isNA(3, new BufferedString("NA")));
  }

}