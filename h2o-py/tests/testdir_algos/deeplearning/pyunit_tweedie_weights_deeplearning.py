import sys, os
sys.path.insert(1, os.path.join("..",".."))
import h2o
from tests import pyunit_utils


def tweedie_weights():

  data = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/cancar_logIn.csv"))
  data["C1M3"] = (data["Class"] == 1 and data["Merit"] == 3).asfactor()
  data["C3M3"] = (data["Class"] == 3 and data["Merit"] == 3).asfactor()
  data["C4M3"] = (data["Class"] == 4 and data["Merit"] == 3).asfactor()
  data["C1M2"] = (data["Class"] == 1 and data["Merit"] == 2).asfactor()
  data["Merit"] = data["Merit"].asfactor()
  data["Class"] = data["Class"].asfactor()
  loss = data["Cost"] / data["Insured"]
  loss.set_name(0,"Loss")
  cancar = loss.cbind(data)

  # Without weights
  myX = ["Merit","Class","C1M3","C4M3"]

  from h2o.estimators.deeplearning import H2ODeepLearningEstimator
  dl = H2ODeepLearningEstimator(distribution="tweedie",hidden=[1],epochs=1000,
                                train_samples_per_iteration=-1,reproducible=True,
                                activation="Tanh",balance_classes=False,
                                force_load_balance=False, seed=2353123,
                                tweedie_power=1.5,score_training_samples=0,
                                score_validation_samples=0)

  dl.train(x=myX,y="Loss", training_frame=cancar)

  mean_residual_deviance = dl.mean_residual_deviance()

  # With weights
  dl.train(x=myX, y="Loss", training_frame=cancar, weights_column="Insured")

if __name__ == "__main__":
  pyunit_utils.standalone_test(tweedie_weights)
else:
  tweedie_weights()
