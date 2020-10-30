package water.jdbc;

import org.junit.Test;
import org.junit.runner.RunWith;
import water.Key;
import water.Keyed;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.*;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class SQLManagerKeyOverwiteTest {
  
  @Test public void nextKeyHasRightPrefixAndPostfix() {
    final String prefix = "foo";
    final String postfix = "bar";
    
    final Key<Frame> key = SQLManager.nextTableKey(prefix, postfix);

    assertTrue(key.toString().startsWith(prefix));
    assertTrue(key.toString().endsWith(postfix));
  }
  
  @Test public void nextKeyKeyHasNoWhitechars() {
    final Key<Frame> key = SQLManager.nextTableKey("f o o ", "b a r");
    
    assertFalse(key.toString().contains("\\W"));
  }

  @Test public void makeRandomKeyCreatesUniqueKeys() {
    final int count = 1000;

    final long actualCount = IntStream.range(0, count)
            .boxed()
            .parallel()
            .map(i -> SQLManager.nextTableKey("foo", "bar"))
            .map(Key::toString)
            .count();

    assertEquals(count, actualCount);
  }
}
