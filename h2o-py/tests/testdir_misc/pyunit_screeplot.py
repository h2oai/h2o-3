import sys
import tempfile

sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils, test_plot_result_saving
from h2o.estimators.pca import H2OPrincipalComponentAnalysisEstimator as H2OPCA


def screeplot_test():
    kwargs = {}
    kwargs['server'] = True
    australia = h2o.upload_file(pyunit_utils.locate("smalldata/pca_test/AustraliaCoast.csv"))
    australia_pca = H2OPCA(k=4,transform="STANDARDIZE")
    australia_pca.train(x=list(range(8)), training_frame=australia)
    australia_pca.screeplot(type="barplot", **kwargs)
    screeplot_result = australia_pca.screeplot(type="lines", **kwargs)

    # test saving:
    tmpdir = tempfile.mkdtemp(prefix="h2o-func")
    path="{}/plot1.png".format(tmpdir)
    test_plot_result_saving(screeplot_result, "{}/plot2.png".format(tmpdir), australia_pca.screeplot(type="barplot", **kwargs, save_plot_path=path), path)

if __name__ == "__main__":
    pyunit_utils.standalone_test(screeplot_test)
else:
    screeplot_test()
