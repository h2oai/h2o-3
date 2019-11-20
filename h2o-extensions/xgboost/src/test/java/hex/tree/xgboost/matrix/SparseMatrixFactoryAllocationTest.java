package hex.tree.xgboost.matrix;

import hex.tree.xgboost.XGBoostUtilsTest;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import water.TestBase;
import water.TestUtil;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class SparseMatrixFactoryAllocationTest extends TestBase {

    @BeforeClass
    public static void beforeClass(){
        TestUtil.stall_till_cloudsize(1);
    }

    @After
    public void tearDown() {
        XGBoostUtilsTest.revertDefaultSparseMatrixMaxSize();
    }

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
            {9, 3, 3, 3, 3, 3, 3}, // No overflow to last line
            {9, 3, 3, 5, 2, 1, 2},  // Overflow to last line
            {0, 2, 1, 0, 1, 1, 3}  // Overflow to last line
        });
    }

    @Parameterized.Parameter(0)
    public int nonZeroElementsCount;
    @Parameterized.Parameter(1)
    public int rowIndicesCount;
    @Parameterized.Parameter(2)
    public int sparseDataMatrixNumRows;
    @Parameterized.Parameter(3)
    public int arrNumRows;
    @Parameterized.Parameter(4)
    public int arrNumCols;
    @Parameterized.Parameter(5)
    public int arrNumColsLastRow;
    @Parameterized.Parameter(6)
    public int sparseMatrixDimensions;


    @Test
    public void testAllocateCSR() {
        XGBoostUtilsTest.setSparseMatrixMaxDimensions(sparseMatrixDimensions);
        SparseMatrixDimensions dimensions = new SparseMatrixDimensions(
            new int[] { nonZeroElementsCount }, new int[] { rowIndicesCount-1 }
        );

        final SparseMatrix sparseMatrix = SparseMatrixFactory.allocateCSRMatrix(dimensions);

        assertEquals(arrNumRows, sparseMatrix._sparseData.length);
        for (int i = 0; i < sparseMatrix._sparseData.length - 1; i++) {
            assertEquals(arrNumCols, sparseMatrix._sparseData[i].length);
        }

        if (sparseMatrix._sparseData.length != 0) { // Empty check
            assertEquals(arrNumColsLastRow, sparseMatrix._sparseData[sparseMatrix._sparseData.length - 1].length);
        }
    }
}
