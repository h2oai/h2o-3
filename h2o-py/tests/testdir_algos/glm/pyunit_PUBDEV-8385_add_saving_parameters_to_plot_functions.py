import sys
sys.path.insert(1,"../../../")
import h2o
import os
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator as glm
import tempfile


def binomial_plot_test():
    benign = h2o.import_file(pyunit_utils.locate("smalldata/logreg/benign.csv"))
    response = 3
    predictors = [0, 1, 2, 4, 5, 6, 7, 8, 9, 10]
    model = glm(family="binomial")
    model.train(x=predictors, y=response, training_frame=benign)

    # Save a plot to tmpdir by handling returned H2OPlotResult:
    plt = model.plot(timestep="AUTO", metric="objective", server=True)
    tmpdir = tempfile.mkdtemp(prefix="h2o-func")
    path = "{}/plot1.png".format(tmpdir)
    plt._figure.savefig(path)
    assert os.path.isfile(path)

    # Save a plot to tmpdir by save_plot_path parameter:
    path = "{}/plot2.png".format(tmpdir)
    model.plot(timestep="AUTO", metric="objective", server=True, save_plot_path=path)
    assert os.path.isfile(path)

if __name__ == "__main__":
    pyunit_utils.standalone_test(binomial_plot_test)
else:
    binomial_plot_test()
