import sys
sys.path.insert(1, "../../../")
import h2o

def offset_gaussian(ip,port):
    # Connect to a pre-existing cluster
    h2o.init(ip,port)

    insurance = h2o.import_frame(h2o.locate("smalldata/glm_test/insurance.csv"))

    insurance["offset"] = insurance["Holders"].log()

    gbm = h2o.gbm(x=insurance[0:3], y=insurance["Claims"], distribution="gaussian", ntrees=600, max_depth=1, min_rows=1,
                  learn_rate=.1, offset_column=insurance["offset"], training_frame=insurance)

    predictions = gbm.predict(insurance)

    # Comparison result generated from R's gbm:
    #	fit2 <- gbm(Claims ~ District + Group + Age+ offset(log(Holders)) , interaction.depth = 1,n.minobsinnode = 1,
    #               shrinkage = .1,bag.fraction = 1,train.fraction = 1,
    #   data = Insurance, distribution ="gaussian", n.trees = 600)
    #   pg = predict(fit2, newdata = Insurance, type = "response", n.trees=600)
    #   pr = pg - - log(Insurance$Holders)

    print " abs(44.33016 - gbm._model_json['output']['init_f']): "
    print abs(44.33016 - gbm._model_json['output']['init_f'])

    print " abs(1491.135 - gbm.mse()): "
    print abs(1491.135 - gbm.mse())

    print " abs(49.23438 - predictions.mean()): "
    print abs(49.23438 - predictions.mean())

    print " abs(-45.54382 - predictions.min()): "
    print abs(-45.54382 - predictions.min())

    print " abs(207.348 - predictions.max()): "
    print abs(207.348 - predictions.max())

if __name__ == "__main__":
    h2o.run_test(sys.argv, offset_gaussian)
