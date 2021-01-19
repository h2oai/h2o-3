import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator as glm
import numpy as np

# This test will generate synthetic GLM dataset.  If given to a GLM model, it should be able to perform well with 
# this dataset since the assumptions associated with GLM are used to generate the dataset.  However, pay attention
# to the data types and you may have to cast enum columns to factors manually since during the save, column types
# information may be lost.
#
# Apart from saving the dataset using h2o.download_csv, remember to save the column  types as
# np.save('my_file.npy', dictionary) np.save('my_file.npy', varDict) 
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
    targetFactor = 4
    glmDataSet = generate_dataset(family, nrow, ncol, realFrac, intFrac, enumFrac, missing_fraction, factorRange, 
                                  numericRange, targetFactor)
    #h2o.download_csv(gamDataSet, "/Users/wendycwong/temp/dataset.csv") # save dataset
    #np.save('/Users/wendycwong/temp/datasetTypes.npy', gamDataSet.types)
    assert glmDataSet.nrow == nrow, "Dataset number of row: {0}, expected number of row: {1}".format(glmDataSet.nrow, 
                                                                                                     nrow)
    assert glmDataSet.ncol == (1+ncol), "Dataset number of row: {0}, expected number of row: " \
                                                          "{1}".format(glmDataSet.ncol, (1+ncol))
  
def generate_dataset(family, nrow, ncol, realFrac, intFrac, enumFrac, missingFrac, factorRange, numericRange, 
                     targetFactor):
    if family=="binomial":
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

    m = glm(family=family, max_iterations=1)
    m.train(training_frame=trainData,x=myX,y= myY)
    r = glm.getGLMRegularizationPath(m)
    coeffDict = r['coefficients'][0]
    coeffLen = len(coeffDict)
    randCoeffVals = np.random.uniform(low=-3, high=3, size=coeffLen).tolist()
    keyset = coeffDict.keys()
    count = 0
    for key in keyset:
        coeffDict[key] = randCoeffVals[count]
        count = count+1
    
    m2 = glm.makeGLMModel(model=m,coefs=coeffDict) # model generated from setting coefficients to model
    f2 = m2.predict(trainData)
    
    finalDataset = trainData[myX]
    finalDataset = finalDataset.cbind(f2[0])
    finalDataset.set_name(col=finalDataset.ncols-1, name='response')

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
    print(df.types)
    return df


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_define_dataset)
else:
    test_define_dataset()
