package hex;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.junit.rules.ScopeTracker;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;

import static org.junit.Assert.*;

public class RegexTokenizerTest extends TestUtil  {

  @Rule
  public ScopeTracker _tracker = new ScopeTracker();

  @BeforeClass
  public static void stall() {
    stall_till_cloudsize(1);
  }

  @Test
  public void testTokenizeWithNAs() {
    Frame f = new Frame(Key.<Frame>make());
    f.add("C1", svec("a b c", null, "1 2", "cowabunga"));
    f.add("C1", svec(null, null, "A", null));
    DKV.put(f);
    _tracker.track(f);

    Frame tokenized = new RegexTokenizer(" ").transform(f);
    _tracker.track(tokenized);

    Vec expected = svec("a", "b", "c", null, null, "1", "2", "A", null, "cowabunga", null);
    _tracker.track(expected);
    
    assertEquals(1, tokenized.numCols());
    assertStringVecEquals(expected, tokenized.vec(0));
  }

  @Test
  public void testAllParams() {
    Frame f = new Frame(Key.<Frame>make());
    f.add("C1", svec("aAa,b;CC"));
    DKV.put(f);
    _tracker.track(f);

    RegexTokenizer tokenizer = new RegexTokenizer.Builder()
            .setMinLength(2)
            .setRegex("[,;]")
            .setToLowercase(true)
            .create();
    
    Frame tokenized = tokenizer.transform(f);
    _tracker.track(tokenized);

    Vec expected = svec("aaa", "cc", null);
    _tracker.track(expected);

    assertStringVecEquals(expected, tokenized.vec(0));
  }

}
