

import h2o, tests

def glrm_cancar():
    print "Importing cancar.csv data..."
    cancarH2O = h2o.upload_file(tests.locate("smalldata/glrm_test/cancar.csv"))
    cancarH2O.describe()
    
    print "Building GLRM model with init = PlusPlus:\n"
    glrm_pp = h2o.glrm(x=cancarH2O, k=4, transform="NONE", init="PlusPlus", loss="Quadratic", regularization_x="None", regularization_y="None", max_iterations=1000)
    glrm_pp.show()

    print "Building GLRM model with init = SVD:\n"
    glrm_svd = h2o.glrm(x=cancarH2O, k=4, transform="NONE", init="SVD", loss="Quadratic", regularization_x="None", regularization_y="None", max_iterations=1000)
    glrm_svd.show()


pyunit_test = glrm_cancar
