package water.rapids.ast.prims.string.algorithms;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LevenstheinDistanceComparatorTest {

  private static LevenstheinDistanceComparator levenstheinDistanceComparator = new LevenstheinDistanceComparator();


  @Test
  public void isTokenized() {
    assertTrue(levenstheinDistanceComparator.isTokenized());
  }

  @Test
  public void compare() {
    final double stringDistance = levenstheinDistanceComparator.compare("abcd", "abcd");
    assertEquals(1D, stringDistance, 0D);
  }

  @Test
  public void compareEmpty() {
    final double stringDistance = levenstheinDistanceComparator.compare("", "");
    assertEquals(1D, stringDistance, 0D);
  }

  @Test
  public void compareLeftEmpty() {
    final double stringDistance = levenstheinDistanceComparator.compare("", "abcd");
    assertEquals(0D, stringDistance, 0D);
  }

  @Test
  public void compareRightEmpty() {
    final double stringDistance = levenstheinDistanceComparator.compare("abcd", "");
    assertEquals(0D, stringDistance, 0D);
  }

  @Test
  public void compareCaseSensitive() {
    final double stringDistance = levenstheinDistanceComparator.compare("ABcd", "abcd");
    assertEquals(0.5D, stringDistance, 0D);
  }

  @Test
  public void compareLongDistance() {
    final double stringDistance = levenstheinDistanceComparator.compare("bAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
        "baaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    assertEquals(0.03, stringDistance, 0.001D);
  }

  @Test
  public void compareLongDistanceNoCommon() {
    final double stringDistance = levenstheinDistanceComparator.compare("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    assertEquals(0D, stringDistance, 0D);
  }

  /**
   * Check no transpositions are taken into account when the distance is computed, e.g. Damerau-Levensthein behavior.
   */
  @Test
  public void compareTranspositions() {
    final double stringDistance = levenstheinDistanceComparator.compare("h2o2",
        "ho22");
    assertEquals(0.5, stringDistance, 0D);
  }
}