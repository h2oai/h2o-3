import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils




def offset_gaussian():
    # Connect to a pre-existing cluster


    insurance = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/insurance.csv"))

    insurance["offset"] = insurance["Holders"].log()

    gbm = h2o.gbm(x=insurance[0:3], y=insurance["Claims"], distribution="gaussian", ntrees=600, max_depth=1, min_rows=1,
                  learn_rate=.1, offset_column="offset", training_frame=insurance)

    predictions = gbm.predict(insurance)

    # Comparison result generated from R's gbm:
    #	fit2 <- gbm(Claims ~ District + Group + Age+ offset(log(Holders)) , interaction.depth = 1,n.minobsinnode = 1,
    #               shrinkage = .1,bag.fraction = 1,train.fraction = 1,
    #   data = Insurance, distribution ="gaussian", n.trees = 600)
    #   pg = predict(fit2, newdata = Insurance, type = "response", n.trees=600)
    #   pr = pg - - log(Insurance$Holders)
    assert abs(44.33016 - gbm._model_json['output']['init_f']) < 1e-5, "expected init_f to be {0}, but got {1}". \
        format(44.33016, gbm._model_json['output']['init_f'])
    assert abs(1491.135 - gbm.mse()) < 1e-2, "expected mse to be {0}, but got {1}".format(1491.135, gbm.mse())
    assert abs(49.23438 - predictions.mean()[0]) < 1e-2, "expected prediction mean to be {0}, but got {1}". \
        format(49.23438, predictions.mean()[0])
    assert abs(-45.5720659304 - predictions.min()) < 1e-2, "expected prediction min to be {0}, but got {1}". \
        format(-45.5720659304, predictions.min())
    assert abs(207.387 - predictions.max()) < 1e-2, "expected prediction max to be {0}, but got {1}". \
        format(207.387, predictions.max())



if __name__ == "__main__":
    pyunit_utils.standalone_test(offset_gaussian)
else:
    offset_gaussian()
