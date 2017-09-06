package hex.pca.daal;

import com.intel.daal.algorithms.pca.*;
import com.intel.daal.data_management.data.NumericTable;
import com.intel.daal.data_management.data_source.DataSource;
import com.intel.daal.data_management.data_source.FileDataSource;
import com.intel.daal.services.DaalContext;
import hex.pca.PCAInterface;

import java.io.IOException;

/**
 * @author mathemage <ha@h2o.ai>
 * created on 1.9.17
 * based on Intel DAAL example PCASVDDenseBatch.java
 */
public class PCA_DAAL_SVD_DenseBatch implements PCAInterface {
  private double[][] principalComponents;
  private double[] variances;
  
  private static final String dataset       = "../data/batch/pca_normalized.csv";
  private final DaalContext daalContext;
  private final Batch pcaAlgorithm;
  
  public PCA_DAAL_SVD_DenseBatch(double[][] gramMatrix) throws IOException {
/*  	 TODO create DoubleArrayDataSource: DataSource
     override com.intel.daal.data_management.data_source.DataSource.loadDataBlock();
     */
    daalContext = new DaalContext();
  
    FileDataSource dataSource = new FileDataSource(daalContext, dataset,
        DataSource.DictionaryCreationFlag.DoDictionaryFromContext,
        DataSource.NumericTableAllocationFlag.DoAllocateNumericTable);
    dataSource.loadDataBlock();

    pcaAlgorithm = new Batch(daalContext, Float.class, Method.svdDense);
    
    NumericTable data = dataSource.getNumericTable();
    pcaAlgorithm.input.set(InputId.data, data);
  
    runSVD();
    
    daalContext.dispose();
  }

  @Override
  public double[] getVariances() {
    return variances;
  }

  @Override
  public double[][] getPrincipalComponents() {
    return principalComponents;
  }

  private void runSVD() {
    Result res = pcaAlgorithm.compute();
    NumericTable eigenValues = res.get(ResultId.eigenValues);
/*    TODO create adapter pattern: DoubleBuffer -> Double[]
class
method: DoubleBuffer in -> Double[] out
by querying DoubleBuffer getters
* */
//    DoubleBuffer.class
	  
    
    // TODO experiment with get* -> dims, and use the adapter
//    variances = eigenValues.getBlo
  
    // TODO create adapter pattern: DoubleBuffer -> Double[][]
    // TODO experiment with get* -> dims, and use the adapter
    NumericTable eigenVectors = res.get(ResultId.eigenVectors);
  }
}
