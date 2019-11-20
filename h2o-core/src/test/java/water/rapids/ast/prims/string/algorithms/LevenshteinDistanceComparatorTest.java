package water.rapids.ast.prims.string.algorithms;

import org.junit.Test;
import water.TestBase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LevenshteinDistanceComparatorTest extends TestBase {

  private static LevenshteinDistanceComparator levenshteinDistanceComparator = new LevenshteinDistanceComparator();


  @Test
  public void isTokenized() {
    assertTrue(levenshteinDistanceComparator.isTokenized());
  }

  @Test
  public void compare() {
    final double stringDistance = levenshteinDistanceComparator.compare("abcd", "abcd");
    assertEquals(1D, stringDistance, 0D);
  }

  @Test
  public void compareEmpty() {
    final double stringDistance = levenshteinDistanceComparator.compare("", "");
    assertEquals(1D, stringDistance, 0D);
  }

  @Test
  public void compareLeftEmpty() {
    final double stringDistance = levenshteinDistanceComparator.compare("", "abcd");
    assertEquals(0D, stringDistance, 0D);
  }

  @Test
  public void compareRightEmpty() {
    final double stringDistance = levenshteinDistanceComparator.compare("abcd", "");
    assertEquals(0D, stringDistance, 0D);
  }

  @Test
  public void compareCaseSensitive() {
    final double stringDistance = levenshteinDistanceComparator.compare("ABcd", "abcd");
    assertEquals(0.5D, stringDistance, 0D);
  }

  @Test
  public void compareLongDistance() {
    final double stringDistance = levenshteinDistanceComparator.compare("bAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
        "baaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    assertEquals(0.03, stringDistance, 0.001D);
  }

  @Test
  public void compareLongDistanceNoCommon() {
    final double stringDistance = levenshteinDistanceComparator.compare("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    assertEquals(0D, stringDistance, 0D);
  }

  /**
   * Check no transpositions are taken into account when the distance is computed, e.g. Damerau-Levenshtein behavior.
   */
  @Test
  public void compareTranspositions() {
    final double stringDistance = levenshteinDistanceComparator.compare("h2o2",
        "ho22");
    assertEquals(0.5, stringDistance, 0D);
  }
}
