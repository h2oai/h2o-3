#!/usr/bin/env python
# -*- encoding: utf-8 -*-
from __future__ import print_function

import h2o

import sys
sys.path.insert(1,"../../../")  # allow us to run this standalone

from h2o.estimators.random_forest import H2ORandomForestEstimator
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.estimators.stackedensemble import H2OStackedEnsembleEstimator
from tests import pyunit_utils


def stackedensemble_levelone_frame_test():

    train = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_train.csv"))
    y = "species"
    x = list(range(4))
    train[y] = train[y].asfactor()
    nfolds = 5
    num_base_models = 2
    num_col_level_one_frame = (train[y].unique().nrow) * num_base_models + 1 #Predicting 3 classes across two base models + response (3*2+1)


    # train and cross-validate a GBM
    my_gbm = H2OGradientBoostingEstimator(distribution="multinomial",
                                          nfolds=nfolds,
                                          ntrees=10,
                                          fold_assignment="Modulo",
                                          keep_cross_validation_predictions=True,
                                          seed=1)
    my_gbm.train(x=x, y=y, training_frame=train)

    # train and cross-validate a RF
    my_rf = H2ORandomForestEstimator(ntrees=10,
                                     nfolds=nfolds,
                                     fold_assignment="Modulo",
                                     keep_cross_validation_predictions=True,
                                     seed=1)

    my_rf.train(x=x, y=y, training_frame=train)


    # Train a stacked ensemble using the GBM and GLM above
    stack = H2OStackedEnsembleEstimator(base_models=[my_gbm.model_id,  my_rf.model_id], keep_levelone_frame=True)
    stack.train(x=x, y=y, training_frame=train)  # also test that validation_frame is working
    level_one_frame = h2o.get_frame(stack.levelone_frame_id()["name"])
    assert level_one_frame.ncols == num_col_level_one_frame, "The number of columns in a level one frame should be numClasses * numBaseModels + 1."
    assert level_one_frame.nrows == train.nrows, "The number of rows in the level one frame should match train number of rows. "

    stack2 = H2OStackedEnsembleEstimator(base_models=[my_gbm.model_id,  my_rf.model_id])
    stack2.train(x=x, y=y, training_frame=train)  # also test that validation_frame is working
    assert stack2.levelone_frame_id() is None, "Level one frame is only available when keep_levelone_frame is True."

if __name__ == "__main__":
    pyunit_utils.standalone_test(stackedensemble_levelone_frame_test)
else:
    stackedensemble_levelone_frame_test()


