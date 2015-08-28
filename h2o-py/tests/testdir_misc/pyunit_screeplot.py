import sys
sys.path.insert(1, "../../")
import h2o, tests

def screeplot_test(ip,port):
    
    
    kwargs = {}
    kwargs['server'] = True

    australia = h2o.upload_file(h2o.locate("smalldata/pca_test/AustraliaCoast.csv"))
    australia_pca = h2o.prcomp(x=australia[0:8], k = 4, transform = "STANDARDIZE")
    australia_pca.screeplot(type="barplot", **kwargs)
    australia_pca.screeplot(type="lines", **kwargs)

if __name__ == "__main__":
    tests.run_test(sys.argv, screeplot_test)
