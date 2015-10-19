

import h2o, tests

def glrm_benign():
    print "Importing benign.csv data..."
    benignH2O = h2o.upload_file(tests.locate("smalldata/logreg/benign.csv"))
    benignH2O.describe()
    
    for i in range(8,16,2):
        print "H2O GLRM with rank " + str(i) + " decomposition:\n"
        glrm_h2o = h2o.glrm(x=benignH2O, k=i, init="SVD", recover_svd=True)
        glrm_h2o.show()


pyunit_test = glrm_benign
