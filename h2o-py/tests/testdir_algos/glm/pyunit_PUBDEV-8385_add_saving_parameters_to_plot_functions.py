import sys
sys.path.insert(1,"../../../")
import h2o
import tempfile
from tests import pyunit_utils, test_plot_result_saving
from h2o.estimators.glm import H2OGeneralizedLinearEstimator as glm
from h2o.plot import decorate_plot_result

def tests():
    binomial_plot_test()
    test_decorate_plot_result()

def binomial_plot_test():
    benign = h2o.import_file(pyunit_utils.locate("smalldata/logreg/benign.csv"))
    response = 3
    predictors = [0, 1, 2, 4, 5, 6, 7, 8, 9, 10]
    model = glm(family="binomial")
    model.train(x=predictors, y=response, training_frame=benign)

    # test saving:
    tmpdir = tempfile.mkdtemp(prefix="h2o-func")
    path1 = "{}/plot1.png".format(tmpdir)
    path2 = "{}/plot2.png".format(tmpdir)
    test_plot_result_saving(model.plot(timestep="AUTO", metric="objective", server=True), path2,
                            model.plot(timestep="AUTO", metric="objective", server=True, save_plot_path=path1), path1)
    
def test_decorate_plot_result():
    figure = "let's pretend I'm a figure"

    res = decorate_plot_result(figure=figure)
    assert res.figure == figure

    res = decorate_plot_result((3, 5), figure=figure)
    assert isinstance(res, tuple)
    a, b = res
    assert a == 3
    assert b == 5
    assert res.figure == figure

    res = decorate_plot_result([3, 5, 6], figure=figure)
    assert isinstance(res, list)
    assert res == [3, 5, 6]
    assert res.figure == figure

    res = decorate_plot_result({"brand": "Ford", "model": "Mustang", "year": 1964}, figure=figure)
    assert isinstance(res, dict)
    assert res == {"brand": "Ford", "model": "Mustang", "year": 1964}
    assert res.figure == figure

    res = decorate_plot_result("Hi", figure=figure)
    assert isinstance(res, str)
    assert res == "Hi"
    assert res.figure == figure

    class Foo(object):
        def __init__(self, bar=None):
            self.bar=bar
    
    res = decorate_plot_result(Foo(bar="baz"), figure=figure)
    assert isinstance(res, Foo)
    assert res.bar == "baz"
    assert res.figure == figure
    
    

if __name__ == "__main__":
    pyunit_utils.standalone_test(binomial_plot_test)
else:
    binomial_plot_test()
