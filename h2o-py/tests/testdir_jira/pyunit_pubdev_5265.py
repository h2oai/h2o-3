import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

try:    # redirect python output
    from StringIO import StringIO  # for python 3
except ImportError:
    from io import StringIO  # for python 2

def pubdev_5265():
    training_data = {
        'response': ['A', 'A', 'A', 'A', 'A',
                  'B', 'B', 'B', 'B', 'B', 'B',
                  'C', 'C', 'C', 'C', 'C', 'C', 'C'],
        'explanatory': ['nan', 1, 1, 1, 1,
                   2, 2, 2, 2, 2, 2,
                   3, 3, 3, 3, 3, 3, 3]
    }

    test_data = {
        'response': ['A', 'A', 'A', 'A', 'A',
                     'B', 'B', 'B', 'B', 'B', 'B',
                     'C', 'C', 'C', 'C', 'C', 'C', 'C'],
        'explanatory': ['nan', 1, 1, 1, 1,
                        2, 2, 2, 2, 2, 2,
                        3, 3, 3, 3, 3, 3, 4]
    }

    training_data = h2o.H2OFrame(training_data)
    training_data['explanatory'] = training_data['explanatory'].asfactor()

    test_data = h2o.H2OFrame(test_data)
    test_data['explanatory'] = test_data['explanatory'].asfactor()

    glm_estimator = H2OGeneralizedLinearEstimator(family="multinomial", missing_values_handling="MeanImputation",
                                                seed=1234, lambda_=0)

    glm_estimator.train(x=["explanatory"], y="response", training_frame=training_data)
    buffer = StringIO() # redirect output
    sys.stderr=buffer


# Training on the given dataset should not fail if there is a missing categorical variable (present in training dataset)
    glm_estimator.predict(test_data=test_data) # remove as_data_frame to avoid warning generated unrelated to missing categorical values
    warns = buffer.getvalue()
    assert "Test/Validation dataset column 'explanatory' has levels not trained on: [\"4\"]" in warns
    
    # The very first value corresponding to 'A' in the explanatory variable column should be replaced by the mode value, which is 3.
    # As a result, 8 occurances of type C should be predicted
    sys.stderr=buffer
    grouped_occurances = glm_estimator.predict(test_data=test_data).group_by((0)).count().get_frame() \
        .as_data_frame()
    grouped_occurances.values.tolist() ==  [['A', 4], ['B', 6], ['C', 8]]
    
    

if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_5265)
else:
    pubdev_5265()
