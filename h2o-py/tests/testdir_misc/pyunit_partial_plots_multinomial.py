import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

def partial_plot_test():
    # import the iris dataset:
    # this dataset is used to classify the type of iris plant
    # the original dataset can be found at https://archive.ics.uci.edu/ml/datasets/Iris
    iris = h2o.import_file(pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))

    # convert response column to a factor
    iris['class'] = iris['class'].asfactor()

    # set the predictor names and the response column name
    predictors = iris.col_names[:-1]
    response = 'class'

    # split into train and validation
    train, valid = iris.split_frame(ratios = [.8], seed=1234)

    # build model
    model = H2OGeneralizedLinearEstimator(family = 'multinomial')
    model.train(x=predictors, y=response, training_frame=train, validation_frame=valid)

    # h2o multinomial PDP
    data = model.partial_plot(data=iris, cols=["petal_len"], plot_stddev=False, plot=False, target="Iris-setosa")
    
    

if __name__ == "__main__":
    pyunit_utils.standalone_test(partial_plot_test)
else:
    partial_plot_test()
