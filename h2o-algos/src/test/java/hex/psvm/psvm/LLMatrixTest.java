package hex.psvm.psvm;

import Jama.CholeskyDecomposition;
import Jama.Matrix;
import org.junit.Test;
import water.TestBase;

import java.util.Random;

import static org.junit.Assert.*;

public class LLMatrixTest extends TestBase {

  @Test
  public void cholSolve_identity() {
    final int N = 10;
    LLMatrix matrix = new LLMatrix(N);
    double[] b = new double[N];
    Random r = new Random(N);
    for (int i = 0; i < N; i++) {
      matrix.set(i, i, 1.0);
      b[i] = r.nextDouble();
    }
    double[] expected_x = b.clone();
    double[] x = matrix.cholSolve(b);
    
    assertArrayEquals(expected_x, x, 0);
  }

  @Test
  public void cholSolve() {
    final int N = 10;
    Random r = new Random(N);

    // generate a random Symmetric Positive-Definite matrix
    Matrix spd = makeSPD(r, N);

    // spd=L*L'
    Matrix L = new CholeskyDecomposition(spd).getL();
    LLMatrix matrix = new LLMatrix(N);
    for (int i = 0; i < N; i++) {
      for (int j = 0; j <= i; j++) {
        matrix.set(i, j, L.get(i, j));
      }
    }
    
    double[] b = new double[N];
    for (int i = 0; i < N; i++) {
      b[i] = r.nextDouble();
    }

    // expected results
    Matrix B = new Matrix(new double[][]{b});
    Matrix X = new CholeskyDecomposition(spd).solve(B.transpose());

    // test out implementation
    double[] x = matrix.cholSolve(b);

    assertArrayEquals(X.transpose().getArray()[0], x, 0);
  }
  
  @Test
  public void cf() {
    final int N = 10;
    Random r = new Random(N);

    // generate a random Symmetric Positive-Definite matrix
    Matrix spd = makeSPD(r, N);

    LLMatrix matrix = new LLMatrix(N);
    for (int i = 0; i < N; i++) {
      for (int j = 0; j <= i; j++) {
        matrix.set(i, j, spd.get(i, j));
      }
    }

    Matrix expectedL = new CholeskyDecomposition(spd).getL();
    
    LLMatrix L = matrix.cf();

    for (int i = 0; i < N; i++) {
      for (int j = 0; j <= i; j++) {
        assertEquals(expectedL.get(i, j), L.get(i, j), 1e-8);
      }
    }
  }

  private Matrix makeSPD(Random r, int N) {
    double[][] A = new double[N][];
    double[][] At = new double[N][];
    for (int i = 0; i < N; i++) {
      A[i] = new double[N];
      At[i] = new double[N];
      for (int j = 0; j < N; j++) {
        A[i][j] = r.nextDouble();
      }
    }
    for (int i = 0; i < N; i++) {
      for (int j = 0; j < N; j++) {
        At[j][i] = A[i][j];
      }
    }
    return new Matrix(A).times(new Matrix(At));
  }
  
}
