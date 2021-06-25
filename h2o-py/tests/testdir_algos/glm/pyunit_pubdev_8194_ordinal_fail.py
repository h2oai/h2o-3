from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
import pandas as pd

# test taken from Ben Epstein.  Thank you.
# PUBDEV-8197: ordinal prediction returns the wrong class even though other classes have higher probability.
def test_model(cars):
    r = cars[0].runif()
    train = cars[r > 0.2]
    valid = cars[r <= 0.2]
    response = "cylinders"
    predictors = [
        "displacement (cc)",
        "power (hp)",
        "weight (lb)",
        "0-60 mph (s)",
        "year_make",
    ]
    
    model = H2OGeneralizedLinearEstimator(seed=1234, family="ordinal")
    model.train(
        x=predictors, y=response, training_frame=train, validation_frame=valid
    )

    features = h2o.H2OFrame(pd.DataFrame([[18,101,22,23.142,1]], columns=predictors))
    prediction = model.predict(features)
    model_raw_preds = prediction.as_data_frame().values.tolist()[0]
    
    model_pred = model_raw_preds[0] # Label
    probs = model_raw_preds[1:] # Probabilities
    labels = [3, 4, 5, 6, 8]

    
    max_prob = max(probs)
    max_prob_index = probs.index(max_prob)
    prob_pred = labels[max_prob_index]
    
    label_probs = dict(zip(labels, probs))
    print("Model pred: {0}, probabilities: {1}".format(model_pred, label_probs))
    assert prob_pred==model_pred, "Predictions are wrong, model gave {0} but max prob was {1} with probability {2}. " \
                                  "All probs: {3}".format(model_pred, prob_pred, max_prob, label_probs)

def test_ordinal():
    cars = h2o.import_file(pyunit_utils.locate("smalldata/junit/cars.csv"))
    cars["cylinders"] = cars["cylinders"].asfactor()
    cars.rename(columns={"year": "year_make"})
    for _ in range(50):
        test_model(cars)


if __name__ == "__main__":
  pyunit_utils.standalone_test(test_ordinal)
else:
    test_ordinal()
