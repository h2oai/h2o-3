



def screeplot_test():
    
    
    kwargs = {}
    kwargs['server'] = True

    australia = h2o.upload_file(pyunit_utils.locate("smalldata/pca_test/AustraliaCoast.csv"))
    australia_pca = h2o.prcomp(x=australia[0:8], k = 4, transform = "STANDARDIZE")
    australia_pca.screeplot(type="barplot", **kwargs)
    australia_pca.screeplot(type="lines", **kwargs)


screeplot_test()
