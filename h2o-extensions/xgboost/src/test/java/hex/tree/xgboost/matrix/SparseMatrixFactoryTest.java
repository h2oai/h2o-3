package hex.tree.xgboost.matrix;

import org.junit.Test;
import water.TestBase;

import static org.junit.Assert.assertEquals;

public class SparseMatrixFactoryTest extends TestBase {
    
    @Test
    public void testNestedPointerInit() {
        long pos = SparseMatrix.MAX_DIM + 30L;
        SparseMatrixFactory.NestedArrayPointer p = new SparseMatrixFactory.NestedArrayPointer(pos);
        float[][] arr = new float[][] {
            new float[16], new float[32]
        };
        p.set(arr, 1f);
        assertEquals(1f, arr[1][30], 0);
    }

    @Test
    public void testNestedPointerIncrement() {
        SparseMatrixFactory.NestedArrayPointer p = new SparseMatrixFactory.NestedArrayPointer();
        float[][] arr = new float[][] {
            new float[16], new float[16]
        };
        p.set(arr, 1f);
        p.increment();
        assertEquals(1f, arr[0][0], 0);
        p.set(arr, 2f);
        p.increment();
        assertEquals(2f, arr[0][1], 0);
        for (int i = 2; i < SparseMatrix.MAX_DIM; i++) p.increment();
        p.set(arr, 3f);
        assertEquals(3f, arr[1][0], 0);
    }

}
