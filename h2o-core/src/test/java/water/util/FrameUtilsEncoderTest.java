package water.util;

import hex.CreateFrame;
import org.junit.*;
import static org.junit.Assert.*;
import org.hamcrest.CoreMatchers;
import water.*;
import water.fvec.Frame;
import water.fvec.Vec;

import static water.util.FrameUtils.*;

import java.util.HashSet;
import java.util.Set;


/**
 * Test FrameUtils interface.
 */
public class FrameUtilsEncoderTest extends TestUtil {
  int NumNoncatColumns = 5;
  int NumRows = 100;

  int[] catSizes       = {2, 3, 4, 5, 7, 8, 9, 15, 16, 30, 31, 127, 255, 256};
  int[] expBinarySizes = {2, 2, 3, 3, 3, 4, 4,  4,  5,  5,  5,   7,   8,   9};
  String[] catNames = {"duo", "Trinity", "Quart", "Star rating", "Dwarves", "Octopus legs", "Planets",
      "Game of Fifteen", "Halfbyte", "Days30", "Days31", "Periodic Table", "AlmostByte", "Byte"};

  @BeforeClass
  static public void setup() {  stall_till_cloudsize(1); }

  class TrashCan {
    Set<Lockable> trash = new HashSet<>();
    <T extends Lockable> T add(T item) {
      trash.add(item);
      return item;
    }
    void dump() { for (Lockable item : trash) item.delete(); }
  }

  @Test
  public void checkSettings() {
    assertEquals(catSizes.length, expBinarySizes.length);
    assertEquals(catSizes.length, catNames.length);
  }
  
  @Test
  public void testCategoricalColumnsBinaryEncoding() {
    TrashCan trash = new TrashCan();
    int totalExpectedColumns = NumNoncatColumns;
    for (int s : expBinarySizes) totalExpectedColumns += s;


    try {
      Frame mainFrame = trash.add(buildMainFrame());
      addCategoricalVectors(mainFrame, trash);
      CategoricalBinaryEncoder cbe = new CategoricalBinaryEncoder(mainFrame, null);
      Frame transformedFrame = trash.add(cbe.exec().get());
      assert transformedFrame != null : "Unable to transform a frame";

      assertEquals("Wrong number of columns after converting to binary encoding",
          totalExpectedColumns, transformedFrame.numCols());
      for (int i = 0; i < NumNoncatColumns; ++i) {
        assertEquals(mainFrame.name(i), transformedFrame.name(i));
        assertEquals(mainFrame.types()[i], transformedFrame.types()[i]);
      }
      for (int i = 0, colOffset = NumNoncatColumns; i < catSizes.length; colOffset += expBinarySizes[i++]) {
        for (int j = 0; j < expBinarySizes[i]; ++j) {
          int jj = colOffset + j;
          assertTrue("A categorical column should be transformed into several binary ones (col "+jj+")",
              transformedFrame.vec(jj).isBinary());
          assertThat("Transformed categorical column should carry the name of the original column",
              transformedFrame.name(jj), CoreMatchers.startsWith(mainFrame.name(NumNoncatColumns +i) + ":"));
        }
      }
    } catch (Throwable e) {
      e.printStackTrace();
      throw e;
    } finally {
      trash.dump();
    }
  }

  @Test
  public void testCategoricalColumnsOneHotEncoding() {
    TrashCan trash = new TrashCan();
    int totalExpectedColumns = NumNoncatColumns;
    for (int s : catSizes) totalExpectedColumns += s + 1;


    try {
      Frame mainFrame = trash.add(buildMainFrame());
      addCategoricalVectors(mainFrame, trash);
      CategoricalOneHotEncoder cohe = new CategoricalOneHotEncoder(mainFrame, null);
      Frame transformedFrame = trash.add(cohe.exec().get());
      assert transformedFrame != null : "Unable to transform a frame";

      assertEquals("Wrong number of columns after converting to binary encoding",
          totalExpectedColumns, transformedFrame.numCols());
      for (int i = 0; i < NumNoncatColumns; ++i) {
        assertEquals(mainFrame.name(i), transformedFrame.name(i));
        assertEquals(mainFrame.types()[i], transformedFrame.types()[i]);
      }
      
      int jj = NumNoncatColumns;
      for (int i = 0; i < catSizes.length; i++) {
        long[] catValues = new long[NumRows];
        for (int k = 0; k < NumRows; k++) {
          try {
            catValues[k] = mainFrame.vec(i+NumNoncatColumns).at8(k);
          } catch(Exception x) {
            catValues[k] = catSizes[i];
          }
        }
        
        for (int j = 0; j < catSizes[i]+1; j++, jj++) {
          Vec thisVec = transformedFrame.vec(jj);
          assertTrue("A categorical column should be transformed into several binary ones (col "+jj+")",
              thisVec.isBinary());
          assertThat("Transformed categorical column #" + jj + " should carry the name of the original column",
              transformedFrame.name(jj), CoreMatchers.startsWith(mainFrame.name(NumNoncatColumns +i) + "."));
          for (int k = 0; k < NumRows; k++) {
            long value = catValues[k];
            int bit = (int) transformedFrame.vec(jj).at8(k);
            switch(bit) {
              case 0: 
                assertTrue("Row " + k + ", column " + jj + ", val " + value, j != catValues[k]);
                break;
              case 1: 
                assertTrue("Row " + k + ", column " + jj, j == catValues[k]);
                break;
              default: fail("Row " + k + ", column " + jj + ": very bad hombre " + bit);
            }
          }

        }
      }
    } catch (Throwable e) {
      e.printStackTrace();
      throw e;
    } finally {
      trash.dump();
    }
  }

  void addCategoricalVectors(Frame mainFrame, TrashCan trash) {
    for (int i = 0; i < catSizes.length; ++i) {
      CreateFrame ccf = new CreateFrame();
      ccf.rows = 100;
      ccf.cols = 1;
      ccf.categorical_fraction = 1;
      ccf.integer_fraction = 0;
      ccf.binary_fraction = 0;
      ccf.time_fraction = 0;
      ccf.string_fraction = 0;
      ccf.factors = catSizes[i];
      Frame auxFrame = trash.add(ccf.execImpl().get());
      auxFrame._names[0] = catNames[i];
      mainFrame.add(auxFrame);
    }
  }

  Frame buildMainFrame() {
    Key<Frame> frameKey = Key.make();
    CreateFrame cf = new CreateFrame(frameKey);
    cf.rows = NumRows;
    cf.cols = NumNoncatColumns;
    cf.categorical_fraction = 0.0;
    cf.integer_fraction = 0.3;
    cf.binary_fraction = 0.1;
    cf.time_fraction = 0.2;
    cf.string_fraction = 0.1;
    Frame mainFrame = cf.execImpl().get();
    assert mainFrame != null : "Unable to create a frame";
    return mainFrame;
  }
}
