import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator as gam
import pandas as pd
import matplotlib.pyplot as plt
import numpy as np

class cv_graph_generator:

    loss_data = []
    # This test will generate synthetic GAM dataset.  If given to a GAM model, it should be able to perform well with 
    # this dataset since the assumptions associated with GAM are used to generate the dataset.
    def test_define_dataset(self):
        family = 'gaussian' # can be any valid GLM families
        nrow = 100
        ncol = 1
        realFrac = 1
        intFrac = 0
        enumFrac = 0
        missing_fraction = 0
        factorRange= 50
        numericRange = 10
        targetFactor = 1
        numGamCols = 1
        min_ratio = 1e-1
        num_trials = 10
        nfolds = nrow

        loss = self.generate_dataset(family, nrow, ncol, realFrac, intFrac, enumFrac, missing_fraction, factorRange,
                                     numericRange, targetFactor, numGamCols, min_ratio, nfolds, num_trials)
        df = pd.DataFrame(loss)
        df.to_csv("loss-data.csv")
        print(df)
        print("Done")
        # self.loss_data.append(self.generate_dataset("binomial", nrow, ncol, realFrac, intFrac, enumFrac, missing_fraction, factorRange,
        #                                             numericRange, targetFactor, numGamCols, scale, nfolds, scale_div))
        # 
        # self.loss_data.append(self.generate_dataset("binomial", nrow, ncol, realFrac, intFrac, enumFrac, missing_fraction, factorRange,
        #                                             numericRange, targetFactor, numGamCols, scale, 5, scale_div))
        # 
        # self.loss_data.append(self.generate_dataset("multinomial", nrow, ncol, realFrac, intFrac, enumFrac, missing_fraction, factorRange,
        #                                             numericRange, 5, numGamCols, scale, nfolds, scale_div))
        # 
        # self.loss_data.append(self.generate_dataset("multinomial", nrow, ncol, realFrac, intFrac, enumFrac, missing_fraction, factorRange,
        #                                             numericRange, 5, numGamCols, scale, 5, scale_div))


    def generate_dataset(self, family, nrow, ncol, realFrac, intFrac, enumFrac, missingFrac, factorRange, numericRange,
                         targetFactor, numGamCols, min_ratio=1e-4, nfolds=0, num_trials=1):
        if family=="binomial":
            responseFactor = 2
        elif family == 'gaussian':
            responseFactor = 1
        else :
            responseFactor = targetFactor

        trainData = self.random_dataset(nrow, ncol, realFrac=realFrac, intFrac=intFrac, enumFrac=enumFrac, factorR=factorRange,
                                        integerR=numericRange, responseFactor=responseFactor, misFrac=missingFrac)

        myX = trainData.names
        myY = 'response'
        myX.remove(myY)

        colNames = trainData.names
        colNames.remove("response")
        avg_loss = []
        scale = 2947.189508523056 * 1000
        scaleParam = []
        for i in range(2, num_trials + 2):
            dec = min_ratio**(1.0/(i - 1))
            scale *= dec
            scaleParam.append(scale)
            m = gam(family=family, gam_columns = colNames[0:numGamCols], lambda_=0, alpha=0, scale=[scale], nfolds=nfolds, fold_assignment="modulo", seed=1)
            m.train(training_frame=trainData, x=myX, y=myY)
            # loss = 0
            # for j in range(nfolds):
            #     loss += (m.cross_validation_models()[j].mse() / nfolds)
            avg_loss.append((-(2 * (i - 1)), m.mse(xval=True)))
        f2 = m.predict(trainData)
        # to see coefficient, do m.coef()
        finalDataset = trainData[myX]
        finalDataset = finalDataset.cbind(f2[0])
        finalDataset.set_name(col=finalDataset.ncols-1, name='response')
        h2o.download_csv(finalDataset, "dataset.csv")
        return avg_loss

    def random_dataset(self, nrow, ncol, realFrac = 0.4, intFrac = 0.3, enumFrac = 0.3, factorR = 10, integerR=100,
                       responseFactor = 1, misFrac=0.01, randSeed=7):
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
        enumFrac = enumFrac + (1-realFrac)/2
        intFrac = 1-enumFrac
        fractions["real_fraction"] = 0  # Right now we are dropping string columns, so no point in having them.
        fractions["categorical_fraction"] = enumFrac
        fractions["integer_fraction"] = intFrac
        fractions["time_fraction"] = 0
        fractions["string_fraction"] = 0  # Right now we are dropping string columns, so no point in having them.
        fractions["binary_fraction"] = 0

        df = h2o.create_frame(rows=nrow, cols=(ncol-real_part.ncol), missing_fraction=misFrac, has_response=True,
                              response_factors=responseFactor, integer_range=integerR,
                              seed=randSeed, **fractions)
        return real_part.cbind(df)

def generate_graphs():
    generator = cv_graph_generator()
    generator.test_define_dataset()
    for dataset in generator.loss_data:
        print(dataset)
    print("done")

if __name__ == "__main__":
    h2o.init(ip='192.168.1.4', port=54321, strict_version_check=False)
    pyunit_utils.standalone_test(generate_graphs())
else:
    h2o.init(ip='192.168.1.4', port=54321, strict_version_check=False)
    generate_graphs()
