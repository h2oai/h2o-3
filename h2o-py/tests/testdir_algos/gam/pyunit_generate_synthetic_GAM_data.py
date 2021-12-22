import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator as gam
from random import random
import numpy as np

# This test will generate synthetic GAM dataset.  If given to a GAM model, it should be able to perform well with 
# this dataset since the assumptions associated with GAM are used to generate the dataset.  However, pay attention
# to the data types and you may have to cast enum columns to factors manually since during the save, column types
# information may be lost.
#
# Apart from saving the dataset using h2o.download_csv, remember to save the column  types as
# np.save('my_file.npy', types_dict) 
#
# when you want to load the dataset, remember to load the types dictionary as
# types_dict = np.load('my_file.npy',allow_pickle='TRUE').item()
#
# then load your synthetic dataset specifying the column type as
# train = h2o.import_file("mydata.csv", col_types=types_dict)
def test_define_dataset():
    family = 'multinomial' # can be any valid GLM families
    nrow = 10000
    ncol = 10
    realFrac = 0.4
    intFrac = 0.3
    enumFrac = 0.3
    missing_fraction = 0
    factorRange= 50
    numericRange = 10
    targetFactor = 4 # number of class for multinomial
    numGamCols = 3
    seed = 54321
    
    assert numGamCols <= ncol*realFrac, "Number of real columns {0} should exceed the number of gam columns " \
                                       "{1}".format(ncol*realFrac, numGamCols) # gam can be only real columns
    gamDataSet = generate_dataset(family, nrow, ncol, realFrac, intFrac, enumFrac, missing_fraction, factorRange, 
                                  numericRange, targetFactor, numGamCols, seed)
    #h2o.download_csv(gamDataSet, "/Users/wendycwong/temp/dataset.csv") # save dataset
    #np.save('/Users/wendycwong/temp/datasetTypes.npy', gamDataSet.types)

    assert gamDataSet.nrow == nrow, "Dataset number of row: {0}, expected number of row: {1}".format(gamDataSet.nrow, 
                                                                                                     nrow)
    assert gamDataSet.ncol == (1+ncol), "Dataset number of row: {0}, expected number of row: " \
                                                          "{1}".format(gamDataSet.ncol, (1+ncol))
  
def generate_dataset(family, nrow, ncol, realFrac, intFrac, enumFrac, missingFrac, factorRange, numericRange, 
                     targetFactor, numGamCols, seed):
    if family=="binomial":
        responseFactor = 2
    elif family == 'gaussian':
        responseFactor = 1;
    else :
        responseFactor = targetFactor
        
    trainData = random_dataset(nrow, ncol, realFrac=realFrac, intFrac=intFrac, enumFrac=enumFrac, factorR=factorRange, 
                               integerR=numericRange, responseFactor=responseFactor, misFrac=missingFrac, randSeed=seed)
    myX = trainData.names
    myY = 'response'
    myX.remove(myY)
  
    colNames = trainData.names
    colNames.remove("response")
    print("gam columns: {0}".format(colNames[0:numGamCols]))
    # train model to know the coefficient size
    m = gam(family=family, gam_columns = colNames[0:numGamCols], max_iterations=2)
    m.train(training_frame=trainData,x=myX,y= myY)
    coef = m.coef()
    coefLen = len(coef)
    if (family == 'multinomial'):
        coefLen = len(coef['coefficients'])
    randCoeff = [random() for ind in range(coefLen)]
    # train model again with random coefficients to generate the synthetic datasets
    m = gam(family=family, gam_columns = colNames[0:numGamCols], max_iterations=1, startval = randCoeff)
    m.train(training_frame=trainData,x=myX,y= myY)
    f2 = m.predict(trainData)
    # to see coefficient, do m.coef()
    finalDataset = trainData[myX]
    finalDataset = finalDataset.cbind(f2[0])
    finalDataset.set_name(col=finalDataset.ncols-1, name='response')
    print(finalDataset.types)
    return finalDataset

def random_dataset(nrow, ncol, realFrac = 0.4, intFrac = 0.3, enumFrac = 0.3, factorR = 10, integerR=100, 
                   responseFactor = 1, misFrac=0.01, randSeed=None):
    fractions = dict()
    if (ncol==1) and (realFrac >= 1.0):
        fractions["real_fraction"] = 1  # Right now we are dropping string columns, so no point in having them.
        fractions["categorical_fraction"] = 0
        fractions["integer_fraction"] = 0
        fractions["time_fraction"] = 0
        fractions["string_fraction"] = 0  # Right now we are dropping string columns, so no point in having them.
        fractions["binary_fraction"] = 0
        
        return h2o.create_frame(rows=nrow, cols=ncol, missing_fraction=misFrac, has_response=True,
                                response_factors = responseFactor, integer_range=integerR,
                                seed=randSeed, **fractions)
    
    real_part = pyunit_utils.random_dataset_real_only(nrow, (int)(realFrac*ncol), misFrac=misFrac, randSeed=randSeed)
    cnames = ['c_'+str(ind) for ind in range(real_part.ncol)]
    real_part.set_names(cnames)
    enumFrac = enumFrac + (1-realFrac)/2
    intFrac = 1-enumFrac
    fractions["real_fraction"] = 0  # Right now we are dropping string columns, so no point in having them.
    fractions["categorical_fraction"] = enumFrac
    fractions["integer_fraction"] = intFrac
    fractions["time_fraction"] = 0
    fractions["string_fraction"] = 0  # Right now we are dropping string columns, so no point in having them.
    fractions["binary_fraction"] = 0

    df = h2o.create_frame(rows=nrow, cols=(ncol-real_part.ncol), missing_fraction=misFrac, has_response=True, 
                          response_factors = responseFactor, integer_range=integerR,
                          seed=randSeed, **fractions)
    return real_part.cbind(df)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_define_dataset)
else:
    test_define_dataset()
