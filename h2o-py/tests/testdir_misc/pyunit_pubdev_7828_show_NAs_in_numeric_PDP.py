import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.estimators.glm import H2OGeneralizedLinearEstimator




def show_NAs_numeric_pdp_test():
    # 1D PDP
    cars = h2o.import_file(pyunit_utils.locate("smalldata/junit/cars.csv"))
    cars.insert_missing_values()
    predictors = ["displacement (cc)", "power (hp)", "weight (lb)", "0-60 mph (s)", "year"]
    response = "cylinders"
    train, valid = cars.split_frame(ratios = [.8], seed = 1234)
    cars_gbm = H2OGradientBoostingEstimator(distribution = "poisson", seed = 1234)
    cars_gbm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)
    cars_gbm.partial_plot(data = train, cols = ["power (hp)"], include_na = True, plot = True, plot_stddev = False)

    # 1D PDP MULTINOMIAL
    iris = h2o.import_file(pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
    iris['class'] = iris['class'].asfactor()
    iris['random_cat'] = iris['class']
    iris.insert_missing_values()
    predictors = iris.col_names[:-1]
    response = 'class'
    train, valid = iris.split_frame(ratios=[.8], seed=1234)
    model = H2OGeneralizedLinearEstimator(family='multinomial')
    model.train(x=predictors, y=response, training_frame=train, validation_frame=valid)
    col = ["petal_len"]
    # 1 class target
    model.partial_plot(data=iris, cols=col, targets=["Iris-setosa"], plot_stddev=False, include_na=True,
                                      plot=True, server=True)
    model.partial_plot(data=iris, cols=col, targets=["Iris-setosa"], plot_stddev=True, include_na=True,
                       plot=True, server=True)
    # 2 class target
    model.partial_plot(data=iris, cols=col, targets=["Iris-setosa", "Iris-virginica"], plot_stddev=False, include_na=True,
                                             plot=True, server=True)
    model.partial_plot(data=iris, cols=col, targets=["Iris-setosa", "Iris-virginica"], plot_stddev=True, include_na=True,
                       plot=True, server=True)
    # 3 class target
    model.partial_plot(data=iris, cols=col, targets=["Iris-setosa", "Iris-virginica", "Iris-versicolor"], plot_stddev=True, include_na=True,
                                                    plot=True, server=True)
    # 2 cols 3 classes
    model.partial_plot(data=iris, cols=["sepal_len", "petal_len"], targets=["Iris-setosa", "Iris-virginica", "Iris-versicolor"], plot_stddev=True,
                                                              include_na=True, plot=True, server=True)




if __name__ == "__main__":
  pyunit_utils.standalone_test(show_NAs_numeric_pdp_test)
else:
    show_NAs_numeric_pdp_test()
