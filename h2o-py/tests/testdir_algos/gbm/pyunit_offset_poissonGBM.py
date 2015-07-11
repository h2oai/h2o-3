import sys
sys.path.insert(1, "../../../")
import h2o

def offset_poisson(ip,port):
    # Connect to a pre-existing cluster
    h2o.init(ip,port)

    insurance = h2o.import_frame(h2o.locate("smalldata/glm_test/insurance.csv"))

    insurance["offset"] = insurance["Holders"].log()

    gbm = h2o.gbm(x=insurance[0:3], y=insurance["Claims"], distribution="poisson", ntrees=600, max_depth=1, min_rows=1,
                  learn_rate=.1, offset_column="offset", training_frame=insurance)

    predictions = gbm.predict(insurance)

    # Comparison result generated from R's gbm:
    #fit2 = gbm(Claims ~ District + Group + Age+ offset(log(Holders)) , interaction.depth = 1,n.minobsinnode = 1,
    #           shrinkage = .1,bag.fraction = 1,train.fraction = 1, data = Insurance, distribution ="poisson",
    #           n.trees = 600)
    #link = predict.gbm(fit2, Insurance, n.trees=600, type="link")
    #link.offset = link + log(Insurance$Holders)
    ##for poisson
    #pr = exp(link.offset)
    assert abs(-2.003262 - gbm._model_json['output']['init_f']) < 1e-5, "expected init_f to be {0}, but got {1}". \
        format(-2.003262, gbm._model_json['output']['init_f'])
    assert abs(49.23437 - predictions.mean()) < 1e-4, "expected prediction mean to be {0}, but got {1}". \
        format(49.23437, predictions.mean())
    assert abs(1.077275 - predictions.min()) < 1e-4, "expected prediction min to be {0}, but got {1}". \
        format(1.077275, predictions.min())
    assert abs(398.0608 - predictions.max()) < 1e-2, "expected prediction max to be {0}, but got {1}". \
        format(398.0608, predictions.max())

if __name__ == "__main__":
    h2o.run_test(sys.argv, offset_poisson)
