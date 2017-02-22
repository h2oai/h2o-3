package hex.genmodel.utils;

import org.junit.Test;

import static hex.genmodel.utils.StringEscapeUtils.*;

import static org.junit.Assert.*;

public class StringEscapeUtilsTest {

  @Test
  public void testEscapeNewlines() throws Exception {
    assertEquals("all new lines are escaped", "line1\\nline2\\nline3", escapeNewlines("line1\nline2\nline3"));
    assertEquals("forward slashes get escaped", "no\\\\ new lines", escapeNewlines("no\\ new lines"));
    assertEquals("tabs are not escaped", "not\tescaped", escapeNewlines("not\tescaped"));
  }

  @Test
  public void testUnescapeNewlines() throws Exception {
    assertEquals("all new lines are escaped", "line1\nline2\nline3", unescapeNewlines("line1\\nline2\\nline3"));
    assertEquals("forward slashes get escaped", "no\\ new lines", unescapeNewlines("no\\\\ new lines"));
    assertEquals("tabs are not escaped", "not\tescaped", unescapeNewlines("not\tescaped"));
  }

}