import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.estimators.random_forest import H2ORandomForestEstimator
from h2o.estimators.deeplearning import H2ODeepLearningEstimator
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

def varimp_plot_test():
  kwargs = {}
  kwargs['server'] = True
  
  # import data set
  cars = h2o.import_file(pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
  
  # Constructing validation and train sets by sampling (20/80)
  s = cars[0].runif()
  cars_train = cars[s <= 0.8]
  cars_valid = cars[s > 0.8]

  # set list of features, target, and convert target to factor
  predictors = ["displacement", "power", "weight", "acceleration", "year"]
  response_col = "economy_20mpg"
  cars[response_col] = cars[response_col].asfactor()

  # Build and train a DRF model
  # to do: comment this out
  cars_rf = H2ORandomForestEstimator()
  cars_rf.train(x=predictors, y=response_col, training_frame=cars_train, validation_frame=cars_valid)

  #Plot DRF Variable Importances, check that num_of_features accepts input
  cars_rf.varimp_plot(server=True)
  cars_rf.varimp_plot(num_of_features=2, server=True)

  # Build and train a GBM model
  cars_gbm = H2OGradientBoostingEstimator()
  cars_gbm.train(x=predictors, y=response_col, training_frame=cars_train, validation_frame=cars_valid)

  # Plot GBM Variable Importances
  cars_gbm.varimp_plot(server=True)
  cars_gbm.varimp_plot(num_of_features=2, server=True)

  # Build and train a Deep Learning model
  cars_dl = H2ODeepLearningEstimator(variable_importances=True)
  cars_dl.train(x=predictors, y=response_col, training_frame=cars_train, validation_frame=cars_valid)

  # Plot Deep Learning Variable Importances
  cars_dl.varimp_plot(server=True)
  cars_dl.varimp_plot(num_of_features=2, server=True)

  # check that varimp_plot() uses std_coef_plot() for a glm
  cars_glm = H2OGeneralizedLinearEstimator()
  cars_glm.train(x=predictors, y=response_col, training_frame=cars_train, validation_frame=cars_valid)
  cars_glm.varimp_plot(server=True)
  cars_glm.varimp_plot(num_of_features=2, server=True)


if __name__ == "__main__":
  pyunit_utils.standalone_test(varimp_plot_test)
else:
  varimp_plot_test()