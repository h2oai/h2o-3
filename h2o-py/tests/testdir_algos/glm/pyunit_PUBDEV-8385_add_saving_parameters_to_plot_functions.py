import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils, test_plot_result_saving
from h2o.estimators.glm import H2OGeneralizedLinearEstimator as glm
import tempfile


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

if __name__ == "__main__":
    pyunit_utils.standalone_test(binomial_plot_test)
else:
    binomial_plot_test()
