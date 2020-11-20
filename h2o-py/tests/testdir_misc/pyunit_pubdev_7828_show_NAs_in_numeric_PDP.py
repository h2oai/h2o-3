import sys
sys.path.insert(1,"../../")
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator




def show_NAs_numeric_pdp_test():
    # 1D PDP
    cars = h2o.import_file(pyunit_utils.locate("smalldata/junit/cars.csv"))
    cars.insert_missing_values()

    # set the predictor names and the response column name
    predictors = ["displacement (cc)","power (hp)","weight (lb)","0-60 mph (s)","year"]
    response = "cylinders"

    # split into train and validation sets
    train, valid = cars.split_frame(ratios = [.8], seed = 1234)

    # try using the distribution parameter:
    # Initialize and train a GBM
    cars_gbm = H2OGradientBoostingEstimator(distribution = "poisson", seed = 1234)
    cars_gbm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

    cars_gbm.partial_plot(data=train, cols=["power (hp)"], include_na=True, plot=True)
    
    # todo:
    # 1D PDP MULTINOMIAL
    
    # 2D PDP



if __name__ == "__main__":
  pyunit_utils.standalone_test(show_NAs_numeric_pdp_test)
else:
    show_NAs_numeric_pdp_test()
