import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils


def benign():
  training_data = h2o.import_file(pyunit_utils.locate("smalldata/logreg/benign.csv"))

  Y = 3
  X = range(3) + range(4,11)

  from h2o.estimators.glm import H2OGeneralizedLinearEstimator
  model = H2OGeneralizedLinearEstimator(family="binomial", alpha=0, Lambda=1e-5)
  model.train(x=X,y=Y, training_frame=training_data)

  #Log.info("Check that the columns used in the model are the ones we passed in.")
  #Log.info("===================Columns passed in: ================")
  in_names = [training_data.names[i] for i in X]
  #Log.info("===================Columns passed out: ================")
  out_names = [model._model_json['output']['coefficients_table'].cell_values[c][0] for c in range(len(X)+1)]
  assert in_names == out_names[1:]




if __name__ == "__main__":
  pyunit_utils.standalone_test(benign)
else:
  benign()
