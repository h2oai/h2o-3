import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils




def offset_tweedie():
    # Connect to a pre-existing cluster


    insurance = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/insurance.csv"))

    insurance["offset"] = insurance["Holders"].log()

    gbm = h2o.gbm(x=insurance[0:3], y=insurance["Claims"], distribution="tweedie", ntrees=600, max_depth=1, min_rows=1,
                  learn_rate=.1, offset_column="offset", training_frame=insurance)

    predictions = gbm.predict(insurance)

    # Comparison result generated from harrysouthworth's gbm:
    #	fit2 = gbm(Claims ~ District + Group + Age+ offset(log(Holders)) , interaction.depth = 1,n.minobsinnode = 1,shrinkage = .1,bag.fraction = 1,train.fraction = 1,
    #           data = Insurance, distribution ="tweedie", n.trees = 600)
    #	pr = predict(fit2, Insurance)
    #	pr = exp(pr+log(Insurance$Holders))
    assert abs(-1.869702 - gbm._model_json['output']['init_f']) < 1e-5, "expected init_f to be {0}, but got {1}".\
        format(-1.869702, gbm._model_json['output']['init_f'])
    assert abs(49.21591 - predictions.mean()[0]) < 1e-3, "expected prediction mean to be {0}, but got {1}". \
        format(49.21591, predictions.mean()[0])
    assert abs(1.0258 - predictions.min()) < 1e-4, "expected prediction min to be {0}, but got {1}". \
        format(1.0258, predictions.min())
    assert abs(392.4651 - predictions.max()) < 1e-2, "expected prediction max to be {0}, but got {1}". \
        format(392.4651, predictions.max())



if __name__ == "__main__":
    pyunit_utils.standalone_test(offset_tweedie)
else:
    offset_tweedie()
