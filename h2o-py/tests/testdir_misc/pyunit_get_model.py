import sys
sys.path.insert(1, "../../")
import h2o

def get_model_test(ip,port):
    
    

    prostate = h2o.import_frame(path=h2o.locate("smalldata/logreg/prostate.csv"))

    r = prostate[0].runif()
    train = prostate[r < 0.70]
    test = prostate[r >= 0.70]

    # Regression
    regression_gbm1 = h2o.gbm(y=train[1], x=train[2:9], distribution="gaussian")
    predictions1 = regression_gbm1.predict(test)

    regression_gbm2 = h2o.get_model(regression_gbm1._id)
    assert regression_gbm2._model_json['output']['model_category'] == "Regression"
    predictions2 = regression_gbm2.predict(test)

    for r in range(predictions1.nrow()):
        p1 = predictions1[r,0]
        p2 = predictions2[r,0]
        assert p1 == p2, "expected regression predictions to be the same for row {}, but got {} and {}".format(r, p1, p2)

    # Binomial
    train[1] = train[1].asfactor()
    bernoulli_gbm1 = h2o.gbm(y=train[1], x=train[2:], distribution="bernoulli")
    predictions1 = bernoulli_gbm1.predict(test)

    bernoulli_gbm2 = h2o.get_model(bernoulli_gbm1._id)
    assert bernoulli_gbm2._model_json['output']['model_category'] == "Binomial"
    predictions2 = bernoulli_gbm2.predict(test)

    for r in range(predictions1.nrow()):
        p1 = predictions1[r,0]
        p2 = predictions2[r,0]
        assert p1 == p2, "expected binomial predictions to be the same for row {}, but got {} and {}".format(r, p1, p2)

    # Clustering
    benign_h2o = h2o.import_frame(path=h2o.locate("smalldata/logreg/benign.csv"))
    km_h2o = h2o.kmeans(x=benign_h2o, k=3)
    benign_km = h2o.get_model(km_h2o._id)
    assert benign_km._model_json['output']['model_category'] == "Clustering"

    # Multinomial
    train[4] = train[4].asfactor()
    multinomial_dl1 = h2o.deeplearning(x=train[0:2], y=train[4], loss='CrossEntropy')
    predictions1 = multinomial_dl1.predict(test)

    multinomial_dl2 = h2o.get_model(multinomial_dl1._id)
    assert multinomial_dl2._model_json['output']['model_category'] == "Multinomial"
    predictions2 = multinomial_dl2.predict(test)

    for r in range(predictions1.nrow()):
        p1 = predictions1[r,0]
        p2 = predictions2[r,0]
        assert p1 == p2, "expected multinomial predictions to be the same for row {0}, but got {1} and {2}" \
                         "".format(r, p1, p2)

if __name__ == "__main__":
    h2o.run_test(sys.argv, get_model_test)
