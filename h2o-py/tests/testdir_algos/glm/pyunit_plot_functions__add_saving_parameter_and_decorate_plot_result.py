import sys
import matplotlib

from h2o.automl import H2OAutoML
from h2o.estimators import H2OGradientBoostingEstimator
from h2o.exceptions import H2OError
from h2o.transforms import H2OPCA

sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils, test_plot_result_saving, TemporaryDirectory
from h2o.estimators.glm import H2OGeneralizedLinearEstimator as glm, H2OGeneralizedLinearEstimator
from h2o.plot import decorate_plot_result, RAISE_ON_FIGURE_ACCESS


def binomial_plot_test():
    benign = h2o.import_file(pyunit_utils.locate("smalldata/logreg/benign.csv"))
    response = 3
    predictors = [0, 1, 2, 4, 5, 6, 7, 8, 9, 10]
    model = glm(family="binomial")
    model.train(x=predictors, y=response, training_frame=benign)

    # test saving:
    with TemporaryDirectory() as tmpdir:
        path1 = "{}/plot1.png".format(tmpdir)
        path2 = "{}/plot2.png".format(tmpdir)
        test_plot_result_saving(model.plot(timestep="AUTO", metric="objective", server=True), path2,
                                model.plot(timestep="AUTO", metric="objective", server=True, save_plot_path=path1), path1)
    
        test_plot_result_saving(model.permutation_importance_plot(benign), path2,
                                model.permutation_importance_plot(benign, save_plot_path=path1), path1)


def regression__plot_learning_curve_plot():
    train = h2o.upload_file(pyunit_utils.locate("smalldata/titanic/titanic_expanded.csv"))
    y = "fare"

    # get at most one column from each type
    cols_to_test = []
    for col, typ in train.types.items():
        for ctt in cols_to_test:
            if typ == train.types[ctt] or col == y:
                break
        else:
            cols_to_test.append(col)

    gbm = H2OGradientBoostingEstimator(seed=1234, model_id="my_awesome_model")
    gbm.train(y=y, training_frame=train)

    with TemporaryDirectory() as tmpdir:
        path1 = "{}/plot1.png".format(tmpdir)
        path2 = "{}/plot2.png".format(tmpdir)
        # test saving:
        test_plot_result_saving(gbm.pd_plot(train, col), path2, gbm.pd_plot(train, col, save_plot_path=path1), path1)
        matplotlib.pyplot.close()
    
        # test pd_plot
        for col in cols_to_test:
            try:
                test_plot_result_saving(gbm.pd_plot(train, col), path2, gbm.pd_plot(train, col, save_plot_path=path1), path1)
            except ValueError:
                assert col == "name", "'name' is a string column which is not supported."
            matplotlib.pyplot.close("all")        
    
        for metric in ["auto", "deviance", "rmse"]:
            test_plot_result_saving(gbm.learning_curve_plot(metric), path2,
                                    gbm.learning_curve_plot(metric=metric.upper(), save_plot_path=path1), path1)
        matplotlib.pyplot.close("all")

  

def binomial_pd_multi_plot():
    train = h2o.upload_file(pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    y = "CAPSULE"
    train[y] = train[y].asfactor()
    # get at most one column from each type
    cols_to_test = []
    for col, typ in train.types.items():
        for ctt in cols_to_test:
            if typ == train.types[ctt] or col == y:
                break
        else:
            cols_to_test.append(col)
    
    aml = H2OAutoML(seed=1234, max_models=5)
    aml.train(y=y, training_frame=train)

    with TemporaryDirectory() as tmpdir:
        path1 = "{}/plot1.png".format(tmpdir)
        path2 = "{}/plot2.png".format(tmpdir)
        for col in cols_to_test:
            test_plot_result_saving(aml.pd_multi_plot(train, col), path2, aml.pd_multi_plot(train, col, save_plot_path=path1), path1)
        matplotlib.pyplot.close()

def test_decorate_plot_result():
    figure = "let's pretend I'm a figure"

    res = decorate_plot_result(figure=figure)
    assert res.figure() == figure

    res = decorate_plot_result((3, 5), figure=figure)
    assert isinstance(res, tuple)
    a, b = res
    assert a == 3
    assert b == 5
    assert res.figure() == figure

    res = decorate_plot_result([3, 5, 6], figure=figure)
    assert isinstance(res, list)
    assert res == [3, 5, 6]
    assert res.figure() == figure

    res = decorate_plot_result({"brand": "Ford", "model": "Mustang", "year": 1964}, figure=figure)
    assert isinstance(res, dict)
    assert res == {"brand": "Ford", "model": "Mustang", "year": 1964}
    assert res.figure() == figure

    res = decorate_plot_result("Hi", figure=figure)
    assert isinstance(res, str)
    assert res == "Hi"
    assert res.figure() == figure

    class Foo(object):
        def __init__(self, bar=None):
            self.bar=bar
    
    res = decorate_plot_result(Foo(bar="baz"), figure=figure)
    assert isinstance(res, Foo)
    assert res.bar == "baz"
    assert res.figure() == figure

    res = decorate_plot_result(Foo(bar="baz"), figure=RAISE_ON_FIGURE_ACCESS)
    try:
        res.figure()
    except H2OError:
        pass

    
def partial_plots():    
    data = h2o.import_file(pyunit_utils.locate('smalldata/prostate/prostate.csv'))
    
    x = ['AGE', 'RACE']
    y = 'CAPSULE'
    data[y] = data[y].asfactor()
    data['RACE'] = data['RACE'].asfactor()
    
    gbm_model = H2OGradientBoostingEstimator(ntrees=50, learn_rate=0.05)
    gbm_model.train(x=x, y=y, training_frame=data)
    
    # test saving:
    with TemporaryDirectory() as tmpdir:
        path1 = "{}/plot1.png".format(tmpdir)
        path2 = "{}/plot2.png".format(tmpdir)
        test_plot_result_saving(gbm_model.partial_plot(data=data, cols=['AGE'], server=True, plot=True, row_index=1), path2,
                                gbm_model.partial_plot(data=data, cols=['AGE'], server=True, plot=True, row_index=1, save_to_file=path1), path1)


def partial_plots_multinomial():
    iris = h2o.import_file(pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))

    iris['class'] = iris['class'].asfactor()
    iris['random_cat'] = iris['class']

    predictors = iris.col_names[:-1]
    response = 'class'

    train, valid = iris.split_frame(ratios=[.8], seed=1234)

    model = H2OGeneralizedLinearEstimator(family='multinomial')
    model.train(x=predictors, y=response, training_frame=train, validation_frame=valid)

    targets = ["Iris-setosa", "Iris-versicolor"]
    cols = ["random_cat"]

    with TemporaryDirectory() as tmpdir:
        path1 = "{}/plot1.png".format(tmpdir)
        path2 = "{}/plot2.png".format(tmpdir)
    
        test_plot_result_saving(model.plot(), path2, model.plot(save_plot_path=path1), path1)
        
        test_plot_result_saving(model.partial_plot(data=iris, cols=cols, targets=targets, plot_stddev=True, plot=True,
                                                   server=True), path2,
                                model.partial_plot(data=iris, cols=cols, targets=targets, plot_stddev=True, plot=True,
                                                   server=True, save_to_file=path1), path1)

def roc_pr_curve():
    air = h2o.import_file(pyunit_utils.locate("smalldata/airlines/AirlinesTrain.csv.zip"))

    # Constructing test and train sets by sampling (20/80)
    s = air[0].runif()
    air_train = air[s <= 0.8]
    air_valid = air[s > 0.8]
    
    myX = ["Origin", "Dest", "Distance", "UniqueCarrier", "fMonth", "fDayofMonth", "fDayOfWeek"]
    myY = "IsDepDelayed"
    
    air_gbm = H2OGradientBoostingEstimator(distribution="bernoulli", ntrees=100, max_depth=3, learn_rate=0.01)
    air_gbm.train(x=myX, y=myY, training_frame=air_train, validation_frame=air_valid)

    # Plot ROC for valid set
    perf_valid = air_gbm.model_performance(valid=True)
    with TemporaryDirectory() as tmpdir:
        path1 = "{}/plot1.png".format(tmpdir)
        path2 = "{}/plot2.png".format(tmpdir)
        test_plot_result_saving(perf_valid.plot(type="roc", server=False), path2,
                                perf_valid.plot(type="roc", server=False, save_to_file=path1), path1)
    
        # Plot ROC for test set
        air_test = h2o.import_file(pyunit_utils.locate("smalldata/airlines/AirlinesTest.csv.zip"))
        perf_test = air_gbm.model_performance(air_test)
    
        test_plot_result_saving(perf_test.plot(type="roc", server=False), path2,
                                perf_test.plot(type="roc", server=False, save_to_file=path1), path1)


def screeplot():
    kwargs = {}
    kwargs['server'] = True
    australia = h2o.upload_file(pyunit_utils.locate("smalldata/pca_test/AustraliaCoast.csv"))
    australia_pca = H2OPCA(k=4,transform="STANDARDIZE")
    australia_pca.train(x=list(range(8)), training_frame=australia)
    australia_pca.screeplot(type="barplot", **kwargs)
    screeplot_result = australia_pca.screeplot(type="lines", **kwargs)
    with TemporaryDirectory() as tmpdir:
        path="{}/plot1.png".format(tmpdir)
        test_plot_result_saving(screeplot_result, "{}/plot2.png".format(tmpdir),
                                australia_pca.screeplot(type="barplot", **kwargs, save_plot_path=path), path)
    

def std_coef__varimp():    
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
    
    # Build and train a GLM model
    cars_glm = H2OGeneralizedLinearEstimator()
    cars_glm.train(x=predictors, y=response_col, training_frame=cars_train, validation_frame=cars_valid)

    # test saving:
    with TemporaryDirectory() as tmpdir:
        path="{}/plot1.png".format(tmpdir)
        test_plot_result_saving(cars_glm.std_coef_plot(server=True), "{}/plot2.png".format(tmpdir),
                                cars_glm.std_coef_plot(server=True, save_plot_path=path), path)
    
        test_plot_result_saving(cars_glm.varimp_plot(server=True), "{}/plot2.png".format(tmpdir),
                                cars_glm.varimp_plot(server=True, save_plot_path=path), path)


pyunit_utils.run_tests([
    binomial_plot_test,
    test_decorate_plot_result,
    regression__plot_learning_curve_plot,
    binomial_pd_multi_plot,
    test_decorate_plot_result,
    partial_plots,
    partial_plots_multinomial,
    roc_pr_curve,
    screeplot
])
