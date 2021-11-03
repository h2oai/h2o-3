import os
import sys
import tempfile

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
    iris['random_cat'] = iris['class']

    # set the predictor names and the response column name
    predictors = iris.col_names[:-1]
    response = 'class'

    # split into train and validation
    train, valid = iris.split_frame(ratios=[.8], seed=1234)

    # build model
    model = H2OGeneralizedLinearEstimator(family='multinomial')
    model.train(x=predictors, y=response, training_frame=train, validation_frame=valid)

    # h2o multinomial PDP
    
    # numeric columns
    # one class target
    cols = ["petal_len"]
    targets = ["Iris-setosa"]
    pdp_petal_len_se = model.partial_plot(data=iris, cols=cols, targets=targets, plot_stddev=False, 
                                          plot=True, server=True)
    print(pdp_petal_len_se)
    
    pdp_petal_len_se_std = model.partial_plot(data=iris, cols=cols, targets=targets, plot_stddev=True, 
                                              plot=True, server=True)
    print(pdp_petal_len_se_std)
    
    # two clasess target
    targets = ["Iris-setosa", "Iris-virginica"]
    pdp_petal_len_se_vi = model.partial_plot(data=iris, cols=cols, targets=targets, plot_stddev=False, 
                                             plot=True, server=True)
    print(pdp_petal_len_se_vi)
    
    pdp_petal_len_se_vi_std = model.partial_plot(data=iris, cols=cols, targets=targets, plot_stddev=True, 
                                                 plot=True, server=True)
    print(pdp_petal_len_se_vi_std)
    
    # three classes target
    targets = ["Iris-setosa", "Iris-virginica", "Iris-versicolor"]
    pdp_petal_len_se_vi_ve_std = model.partial_plot(data=iris, cols=cols, targets=targets, plot_stddev=True, 
                                                    plot=True, server=True)
    print(pdp_petal_len_se_vi_ve_std)
    
    # two columns and three classes target
    cols = ["sepal_len", "petal_len"]
    pdp_petal_len_sepal_len_se_vi_ve_std = model.partial_plot(data=iris, cols=cols, targets=targets, plot_stddev=True, 
                                                              plot=True, server=True)
    print(pdp_petal_len_sepal_len_se_vi_ve_std)
    
    # three columns and three classes target  
    cols = ["sepal_len","petal_len", "sepal_wid"]
    pdp_petal_len_sepal_len_sepal_wid_se_vi_ve = model.partial_plot(data=iris, cols=cols, targets=targets, 
                                                                    plot_stddev=False, plot=True, server=True)
    print(pdp_petal_len_sepal_len_sepal_wid_se_vi_ve)
    
    pdp_petal_len_sepal_len_sepal_wid_se_vi_ve_std = model.partial_plot(data=iris, cols=cols, targets=targets, 
                                                                        plot_stddev=True, plot=True, server=True)
    print(pdp_petal_len_sepal_len_sepal_wid_se_vi_ve_std)
    
    # categorical column - nonsense column, just for testing
    cols = ["random_cat"]
    targets = ["Iris-setosa"]
    pdp_petal_len_cat = model.partial_plot(data=iris, cols=cols, targets=targets, plot_stddev=False, plot=True, 
                                           server=True)
    print(pdp_petal_len_cat)

    targets = ["Iris-setosa", "Iris-versicolor"]
    pdp_petal_len_cat_std = model.partial_plot(data=iris, cols=cols, targets=targets, plot_stddev=True, plot=True, 
                                               server=True)
    print(pdp_petal_len_cat_std)

    # test saving with parameter:
    tmpdir = tempfile.mkdtemp(prefix="h2o-func")
    path = "{}/plot1.png".format(tmpdir)
    model.partial_plot(data=iris, cols=cols, targets=targets, plot_stddev=True, plot=True,
                       server=True, save_to_file=path)
    assert os.path.isfile(path)
    # test saving through H2OPlotResult:
    path = "{}/plot2.png".format(tmpdir)
    plot_result = model.partial_plot(data=iris, cols=cols, targets=targets, plot_stddev=True, plot=True,
                                     server=True)
    plot_result.figure.savefig(fname=path)
    assert os.path.isfile(path)


if __name__ == "__main__":
    pyunit_utils.standalone_test(partial_plot_test)
else:
    partial_plot_test()
