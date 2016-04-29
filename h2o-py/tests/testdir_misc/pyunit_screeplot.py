import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.transforms.decomposition import H2OPCA


def screeplot_test():
    kwargs = {}
    kwargs['server'] = True
    australia = h2o.upload_file(pyunit_utils.locate("smalldata/pca_test/AustraliaCoast.csv"))
    australia_pca = H2OPCA(k=4,transform="STANDARDIZE")
    australia_pca.train(x=list(range(8)), training_frame=australia)
    australia_pca.screeplot(type="barplot", **kwargs)
    australia_pca.screeplot(type="lines", **kwargs)

if __name__ == "__main__":
    pyunit_utils.standalone_test(screeplot_test)
else:
    screeplot_test()
