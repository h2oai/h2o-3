import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator
import numpy as np

# This test will generate synthetic GBM dataset using a randomly defined GBM trees.  If given to 
# a GBM model, it should be able to perform well with this dataset since the assumptions associated with 
# GBM are used to generate the dataset.
def test_define_dataset():
    family = 'bernoulli' # can be any valid GLM families
    nrow = 100000
    ncol = 10
    missing_fraction = 0
    factorRange= 50
    numericRange = 10
    targetFactor = 2
    realFrac = 0.3
    intFrac = 0.3
    enumFrac = 0.4
    ntrees=10
    max_depth=8   
    glmDataSet = generate_dataset(family, nrow, ncol, ntrees, max_depth, realFrac, intFrac, enumFrac, 
                                  missing_fraction, factorRange, numericRange, targetFactor)
    #h2o.download_csv(glmDataSet, "/Users/.../dataset.csv") # save dataset
    assert glmDataSet.nrow == nrow, "Dataset number of row: {0}, expected number of row: {1}".format(glmDataSet.nrow, 
                                                                                                     nrow)
    assert glmDataSet.ncol == (1+ncol), "Dataset number of row: {0}, expected number of row: " \
                                                          "{1}".format(glmDataSet.ncol, (1+ncol))
  
def generate_dataset(family, nrow, ncol, ntrees, max_depth, realFrac, intFrac, enumFrac, missingFrac, 
                     factorRange, numericRange, targetFactor):
    if family=="bernoulli":
        responseFactor = 2
    elif family == 'gaussian':
        responseFactor = 1;
    else :
        responseFactor = targetFactor
        
    trainData = random_dataset(nrow, ncol, realFrac=realFrac, intFrac=intFrac, enumFrac=enumFrac, factorR=factorRange,
                               integerR=numericRange, responseFactor=responseFactor, misFrac=missingFrac)
   
    myX = trainData.names
    myY = 'response'
    myX.remove(myY)
    m = H2OGradientBoostingEstimator(distribution=family,
                                     ntrees=ntrees,
                                     max_depth=max_depth)
    m.train(training_frame=trainData,x=myX,y= myY)
    f2 = m.predict(trainData)
    
    finalDataset = trainData[myX]
    finalDataset = finalDataset.cbind(f2[0])
    finalDataset.set_name(col=finalDataset.ncols-1, name='response')

    h2o.remove(trainData)
    return finalDataset

def random_dataset(nrow, ncol, realFrac = 0.4, intFrac = 0.3, enumFrac = 0.3, factorR = 10, integerR=100, 
                   responseFactor = 1, misFrac=0.01, randSeed=None):
    fractions = dict()
    fractions["real_fraction"] = realFrac  # Right now we are dropping string columns, so no point in having them.
    fractions["categorical_fraction"] = enumFrac
    fractions["integer_fraction"] = intFrac
    fractions["time_fraction"] = 0
    fractions["string_fraction"] = 0  # Right now we are dropping string columns, so no point in having them.
    fractions["binary_fraction"] = 0

    df = h2o.create_frame(rows=nrow, cols=ncol, missing_fraction=misFrac, has_response=True, 
                          response_factors = responseFactor, integer_range=integerR,
                          seed=randSeed, **fractions)
    return df


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_define_dataset)
else:
    test_define_dataset()
