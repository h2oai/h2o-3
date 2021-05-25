package hex.genmodel.utils;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

@RunWith(Enclosed.class)
public class ArrayUtilsTest {

    public static class ArrayUtilsRegularTest {

        @Test
        public void testSortIndicesCutoffIsStable() {
            int arrayLen = 100;
            int[] indices = ArrayUtils.range(0, arrayLen - 1);
            float[] values = new float[arrayLen]; // intentionally only zeros
            float[] valuesInput = Arrays.copyOf(values, values.length);

            ArrayUtils.sort(indices, valuesInput, 0, indices.length, false, 1, 500);
            assertArrayEquals("Not correctly sorted or the same values were replaced",
                    ArrayUtils.range(0, arrayLen - 1), indices);
            assertArrayEquals("Values array is changed", values, valuesInput, 0);

            ArrayUtils.sort(indices, valuesInput, 0, indices.length, false, -1, 500);
            assertArrayEquals("Not correctly sorted or the same values were replaced",
                    ArrayUtils.range(0, arrayLen - 1), indices);
            assertArrayEquals("Values array is changed", values, valuesInput, 0);
        }

        @Test
        public void testSortIndicesCutoffBranch() {
            int arrayLen = 10;
            int[] indices = ArrayUtils.range(0, arrayLen - 1);
            float[] values = new float[]{-12, -5, 1, 255, 1.25f, -0.99f, 0, 2, -26, 16};
            float[] valuesInput = Arrays.copyOf(values, values.length);

            ArrayUtils.sort(indices, valuesInput, 0, indices.length, false, 1, 500);
            assertArrayEquals("Not correctly sorted", new int[]{8, 0, 1, 5, 6, 2, 4, 7, 9, 3}, indices);
            assertArrayEquals("Values array is changed", values, valuesInput, 0);
            for (int index = 1; index < arrayLen; index++)
                Assert.assertTrue(values[indices[index - 1]] + " should be <= " + values[indices[index]],
                        values[indices[index - 1]] <= values[indices[index]]);

            ArrayUtils.sort(indices, valuesInput, 0, indices.length, true, 1, 500);
            assertArrayEquals("Not correctly sorted", new int[]{6, 5, 2, 4, 7, 1, 0, 9, 8, 3}, indices);
            assertArrayEquals("Values array is changed", values, valuesInput, 0);
            for (int index = 1; index < arrayLen; index++)
                Assert.assertTrue(Math.abs(values[indices[index - 1]]) + " should be <= " + Math.abs(values[indices[index]]),
                        Math.abs(values[indices[index - 1]]) <= Math.abs(values[indices[index]]));

            ArrayUtils.sort(indices, valuesInput, 0, indices.length, false, -1, 500);
            assertArrayEquals("Not correctly sorted", new int[]{3, 9, 7, 4, 2, 6, 5, 1, 0, 8}, indices);
            assertArrayEquals("Values array is changed", values, valuesInput, 0);
            for (int index = 1; index < arrayLen; index++)
                Assert.assertTrue(values[indices[index - 1]] + " should be >= " + values[indices[index]],
                        values[indices[index - 1]] >= values[indices[index]]);

            ArrayUtils.sort(indices, valuesInput, 0, indices.length, true, -1, 500);
            assertArrayEquals("Not correctly sorted", new int[]{3, 8, 9, 0, 1, 7, 4, 2, 5, 6}, indices);
            assertArrayEquals("Values array is changed", values, valuesInput, 0);
            for (int index = 1; index < arrayLen; index++)
                Assert.assertTrue(Math.abs(values[indices[index - 1]]) + " should be >= " + Math.abs(values[indices[index]]),
                        Math.abs(values[indices[index - 1]]) >= Math.abs(values[indices[index]]));
        }

        @Test
        public void testSortIndicesJavaSortBranch() {
            int arrayLen = 10;
            int[] indices = ArrayUtils.range(0, arrayLen - 1);
            float[] values = new float[]{-12, -5, 1, 255, 1.25f, -0.99f, 0, 2, -26, 16};
            float[] valuesInput = Arrays.copyOf(values, values.length);

            ArrayUtils.sort(indices, valuesInput, 0, indices.length, false, 1, -1);
            assertArrayEquals("Not correctly sorted", new int[]{8, 0, 1, 5, 6, 2, 4, 7, 9, 3}, indices);
            assertArrayEquals("Values array is changed", values, valuesInput, 0);
            for (int index = 1; index < arrayLen; index++)
                Assert.assertTrue(values[indices[index - 1]] + " should be <= " + values[indices[index]],
                        values[indices[index - 1]] <= values[indices[index]]);

            ArrayUtils.sort(indices, valuesInput, 0, indices.length, true, 1, -1);
            assertArrayEquals("Not correctly sorted", new int[]{6, 5, 2, 4, 7, 1, 0, 9, 8, 3}, indices);
            assertArrayEquals("Values array is changed", values, valuesInput, 0);
            for (int index = 1; index < arrayLen; index++)
                Assert.assertTrue(Math.abs(values[indices[index - 1]]) + " should be <= " + Math.abs(values[indices[index]]),
                        Math.abs(values[indices[index - 1]]) <= Math.abs(values[indices[index]]));

            ArrayUtils.sort(indices, valuesInput, 0, indices.length, false, -1, -1);
            assertArrayEquals("Not correctly sorted", new int[]{3, 9, 7, 4, 2, 6, 5, 1, 0, 8}, indices);
            assertArrayEquals("Values array is changed", values, valuesInput, 0);
            for (int index = 1; index < arrayLen; index++)
                Assert.assertTrue(values[indices[index - 1]] + " should be >= " + values[indices[index]],
                        values[indices[index - 1]] >= values[indices[index]]);

            ArrayUtils.sort(indices, valuesInput, 0, indices.length, true, -1, -1);
            assertArrayEquals("Not correctly sorted", new int[]{3, 8, 9, 0, 1, 7, 4, 2, 5, 6}, indices);
            assertArrayEquals("Values array is changed", values, valuesInput, 0);
            for (int index = 1; index < arrayLen; index++)
                Assert.assertTrue(Math.abs(values[indices[index - 1]]) + " should be >= " + Math.abs(values[indices[index]]),
                        Math.abs(values[indices[index - 1]]) >= Math.abs(values[indices[index]]));
        }

        @Test
        public void testSortIndicesRandomAttackJavaSortBranch() {
            Random randObj = new Random(12345);
            int arrayLen = 100;
            int[] indices = new int[arrayLen];
            float[] values = new float[arrayLen];
            for (int index = 0; index < arrayLen; index++) {
                values[index] = randObj.nextFloat();
                indices[index] = index;
            }

            ArrayUtils.sort(indices, values, 0, indices.length, false, 1, -1);
            for (int index = 1; index < arrayLen; index++)
                Assert.assertTrue(values[indices[index - 1]] + " should be <= " + values[indices[index]],
                        values[indices[index - 1]] <= values[indices[index]]);

            ArrayUtils.sort(indices, values, 0, indices.length, true, 1, -1);
            for (int index = 1; index < arrayLen; index++)
                Assert.assertTrue(Math.abs(values[indices[index - 1]]) + " should be <= " + Math.abs(values[indices[index]]),
                        Math.abs(values[indices[index - 1]]) <= Math.abs(values[indices[index]]));

            ArrayUtils.sort(indices, values, 0, indices.length, false, -1, -1);
            for (int index = 1; index < arrayLen; index++)
                Assert.assertTrue(values[indices[index - 1]] + " should be >= " + values[indices[index]],
                        values[indices[index - 1]] >= values[indices[index]]);

            ArrayUtils.sort(indices, values, 0, indices.length, true, -1, -1);
            for (int index = 1; index < arrayLen; index++)
                Assert.assertTrue(Math.abs(values[indices[index - 1]]) + " should be >= " + Math.abs(values[indices[index]]),
                        Math.abs(values[indices[index - 1]]) >= Math.abs(values[indices[index]]));
        }

        @Test
        public void testSortIndicesRandomAttackCutoffBranch() {
            Random randObj = new Random(12345);
            int arrayLen = 100;
            int[] indices = new int[arrayLen];
            float[] values = new float[arrayLen];
            for (int index = 0; index < arrayLen; index++) {
                values[index] = randObj.nextFloat();
                indices[index] = index;
            }

            ArrayUtils.sort(indices, values, 0, indices.length, false, 1, 500);
            for (int index = 1; index < arrayLen; index++)
                Assert.assertTrue(values[indices[index - 1]] + " should be <= " + values[indices[index]],
                        values[indices[index - 1]] <= values[indices[index]]);

            ArrayUtils.sort(indices, values, 0, indices.length, true, 1, 500);
            for (int index = 1; index < arrayLen; index++)
                Assert.assertTrue(Math.abs(values[indices[index - 1]]) + " should be <= " + Math.abs(values[indices[index]]),
                        Math.abs(values[indices[index - 1]]) <= Math.abs(values[indices[index]]));

            ArrayUtils.sort(indices, values, 0, indices.length, false, -1, 500);
            for (int index = 1; index < arrayLen; index++)
                Assert.assertTrue(values[indices[index - 1]] + " should be >= " + values[indices[index]],
                        values[indices[index - 1]] >= values[indices[index]]);

            ArrayUtils.sort(indices, values, 0, indices.length, true, -1, 500);
            for (int index = 1; index < arrayLen; index++)
                Assert.assertTrue(Math.abs(values[indices[index - 1]]) + " should be >= " + Math.abs(values[indices[index]]),
                        Math.abs(values[indices[index - 1]]) >= Math.abs(values[indices[index]]));
        }
    }

    @RunWith(Parameterized.class)
    public static class ArrayUtilsParametrizedTest {

        @Parameterized.Parameter
        public int fromIndex;

        @Parameterized.Parameter(value = 1)
        public int toIndex;

        @Parameterized.Parameters
        public static List<Object[]> data() {
            Object[][] dataValues = new Object[][]{{0, 10}, {1, 10}, {0, 9}, {2, 8}};
            return Arrays.asList(dataValues);
        }

        @Test
        public void testSortIndicesCutoffBranchParam() {
            int arrayLen = 10;
            int[] indices = ArrayUtils.range(0, arrayLen - 1);
            float[] values = new float[]{-12, -5, 1, 255, 1.25f, -0.99f, 0, 2, -26, 16};
            float[] valuesInput = Arrays.copyOf(values, values.length);
            int[] indicesOrig = ArrayUtils.range(0, arrayLen - 1);

            ArrayUtils.sort(indices, valuesInput, fromIndex, toIndex, false, 1, 500);
            assertArrayEqualsExcept("Array is sorted before fromIndex or after toIndex", indicesOrig, indices, fromIndex, toIndex);
            assertArrayEquals("Values array is changed", values, valuesInput, 0);
            for (int index = fromIndex + 1; index < toIndex; index++)
                Assert.assertTrue(values[indices[index - 1]] + " should be <= " + values[indices[index]],
                        values[indices[index - 1]] <= values[indices[index]]);

            ArrayUtils.sort(indices, valuesInput, fromIndex, toIndex, true, 1, 500);
            assertArrayEqualsExcept("Array is sorted before fromIndex or after toIndex", indicesOrig, indices, fromIndex, toIndex);
            assertArrayEquals("Values array is changed", values, valuesInput, 0);
            for (int index = fromIndex + 1; index < toIndex; index++)
                Assert.assertTrue(Math.abs(values[indices[index - 1]]) + " should be <= " + Math.abs(values[indices[index]]),
                        Math.abs(values[indices[index - 1]]) <= Math.abs(values[indices[index]]));

            ArrayUtils.sort(indices, valuesInput, fromIndex, toIndex, false, -1, 500);
            assertArrayEqualsExcept("Array is sorted before fromIndex or after toIndex", indicesOrig, indices, fromIndex, toIndex);
            assertArrayEquals("Values array is changed", values, valuesInput, 0);
            for (int index = fromIndex + 1; index < toIndex; index++)
                Assert.assertTrue(values[indices[index - 1]] + " should be >= " + values[indices[index]],
                        values[indices[index - 1]] >= values[indices[index]]);

            ArrayUtils.sort(indices, valuesInput, fromIndex, toIndex, true, -1, 500);
            assertArrayEqualsExcept("Array is sorted before fromIndex or after toIndex", indicesOrig, indices, fromIndex, toIndex);
            assertArrayEquals("Values array is changed", values, valuesInput, 0);
            for (int index = fromIndex + 1; index < toIndex; index++)
                Assert.assertTrue(Math.abs(values[indices[index - 1]]) + " should be >= " + Math.abs(values[indices[index]]),
                        Math.abs(values[indices[index - 1]]) >= Math.abs(values[indices[index]]));
        }

        private void assertArrayEqualsExcept(String message, int[] expected, int[] actual, int fromIndex, int toIndex) {
            for (int i = 0; i < fromIndex; i++) {
                assertEquals(message + ": arrays differed at element [" + i + "]", expected[i], actual[i]);
            }
            for (int i = toIndex; i < expected.length; i++) {
                assertEquals("", expected[i], actual[i]);
            }
        }
    }
}
