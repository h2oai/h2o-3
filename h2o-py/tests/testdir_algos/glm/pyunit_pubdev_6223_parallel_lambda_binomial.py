from __future__ import print_function

import h2o
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
from tests import pyunit_utils
import pandas as pd
import numpy as np

def test_compare_lambdasearch():

    train_h2o = h2o.importFile(pyunit_utils.locate("smalldata/glm_test/lambda_search.csv"))
    print('train_h2o: {:,} x {:,} (including label, fold)'.format(*train_h2o.shape))

    glm_h2o = H2OGeneralizedLinearEstimator(family='binomial', alpha=1., lambda_search=True, nlambdas=10, seed=1)
    glm_h2o.train(y='label', training_frame=train_h2o, fold_column='fold')
    
    regpath_h2o = H2OGeneralizedLinearEstimator.getGLMRegularizationPath(glm_h2o)
    regpath_pd = pd.DataFrame(index=np.arange(len(regpath_h2o['lambdas'])), columns=['lambda','ncoef','auc'])
    for n,(lamb,coefs) in enumerate(zip(regpath_h2o['lambdas'],regpath_h2o['coefficients'])):
        mod = H2OGeneralizedLinearEstimator.makeGLMModel(model=glm_h2o, coefs=coefs)
        regpath_pd.loc[n] = [lamb, sum(1 for x in coefs.values() if abs(x)>1E-3), mod.model_performance(train_h2o).auc()]
        h2o.remove(mod)

    print(regpath_h2o)
    regpath_pd2 = regpath_pd
    allCoeffs = regpath_h2o['coefficients']
    
    for n in range(len(regpath_h2o['lambdas'])):
        lamb = regpath_h2o['lambdas'][n]
        mod = H2OGeneralizedLinearEstimator(family='binomial', alpha=1., lambda_search=False, Lambda=lamb, seed=1)
        mod.train(y='label', training_frame=train_h2o, fold_column='fold')
        tcoeffs = allCoeffs[n]
        modCoeff = mod.coef()
        for key in tcoeffs.keys():
            assert abs(tcoeffs[key]-modCoeff[key])<1e-10, "coefficients of lambda search {0} and coefficients of " \
                                                          "manual training {1} are different.".format(tcoeffs[key], modCoeff[key])
        regpath_pd2.loc[n] = [lamb, sum(1 for x in mod.coef().values() if abs(x)>1E-3), mod.model_performance().auc()]

    regpath_pd2.plot(x='ncoef', y='auc');
    regpath_pd.plot(x='ncoef', y='auc');
    

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_compare_lambdasearch)
else:
    test_compare_lambdasearch()
