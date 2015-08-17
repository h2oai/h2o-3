import sys
sys.path.insert(1, "../../../")
import h2o

def offset_gamma(ip,port):
    # Connect to a pre-existing cluster
    

    insurance = h2o.import_file(h2o.locate("smalldata/glm_test/insurance.csv"))

    insurance["offset"] = insurance["Holders"].log()

    gbm = h2o.gbm(x=insurance[0:3], y=insurance["Claims"], distribution="gamma", ntrees=600, max_depth=1, min_rows=1,
                  learn_rate=.1, offset_column="offset", training_frame=insurance)

    predictions = gbm.predict(insurance)

    # Comparison result generated from harrysouthworth's gbm:
    #	fit2 = gbm(Claims ~ District + Group + Age+ offset(log(Holders)) , interaction.depth = 1,n.minobsinnode = 1,shrinkage = .1,bag.fraction = 1,train.fraction = 1,
    #           data = Insurance, distribution ="gamma", n.trees = 600)
    #	pr = predict(fit2, Insurance)
    #	pr = exp(pr+log(Insurance$Holders))
    assert abs(-1.714958 - gbm._model_json['output']['init_f']) < 1e-5, "expected init_f to be {0}, but got {1}". \
        format(-1.714958, gbm._model_json['output']['init_f'])
    assert abs(50.10707 - predictions.mean()) < 1e-3, "expected prediction mean to be {0}, but got {1}". \
        format(50.10707, predictions.mean())
    assert abs(0.9133843 - predictions.min()) < 1e-4, "expected prediction min to be {0}, but got {1}". \
        format(0.9133843, predictions.min())
    assert abs(392.6667 - predictions.max()) < 1e-2, "expected prediction max to be {0}, but got {1}". \
        format(392.6667, predictions.max())

if __name__ == "__main__":
    h2o.run_test(sys.argv, offset_gamma)
