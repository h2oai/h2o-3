package hex.genmodel.easy;

import hex.genmodel.easy.stub.TestMojoModel;
import org.junit.Test;

import java.util.HashMap;

import static org.junit.Assert.*;

public class DomainMapConstructorTest {

  @Test
  public void create() {

    TestMojoModel testMojoModel = new TestMojoModel();

    HashMap<Integer, HashMap<String, Integer>> domainMap = new DomainMapConstructor(testMojoModel).create();
    
    assertEquals(0, (int)domainMap.get(0).get("S"));
    assertEquals(1, (int)domainMap.get(0).get("Q"));
    assertEquals(0, (int)domainMap.get(2).get("male"));
    assertEquals(1, (int)domainMap.get(2).get("female"));
    
    assertFalse(domainMap.containsKey(1));
  }
}
