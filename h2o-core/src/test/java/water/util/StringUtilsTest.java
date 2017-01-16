package water.util;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static water.util.StringUtils.*;
import static org.junit.Assert.*;

/**
 * Tests for StringUtils
 */
public class StringUtilsTest {

  @Test
  public void testToString() throws Exception {

  }

  @Test
  public void testIsNullOrEmpty() throws Exception {

  }

  @Test
  public void testIsNullOrEmpty1() throws Exception {

  }

  @Test
  public void testExpandPath() throws Exception {

  }

  @Test
  public void testCleanString() throws Exception {

  }

  @Test
  public void testTokenize() throws Exception {

  }

  @Test
  public void testTokensToArray() throws Exception {

  }

  @Test
  public void testTexts2array() throws Exception {

  }

  @Test
  public void testJoinArray() throws Exception {
    assertEquals("null,a,4,null", join(",", new String[]{null, "a", "4", null}));
    assertEquals("", join("::", new String[]{}));
    assertEquals("xxxxx", join("x", new String[]{"x", "xx", ""}));
  }

  @Test
  public void testJoinCollection() throws Exception {
    assertEquals("null,a,4,null", join(",", Arrays.asList(null, "a", "4", null)));
    assertEquals("", join("::", Collections.EMPTY_SET));
    assertEquals("xxxxx", join("x", Arrays.asList("x", "xx", "")));
  }
}