

import h2o, tests

def glrm_arrests():
    print "Importing USArrests.csv data..."
    arrestsH2O = h2o.upload_file(tests.locate("smalldata/pca_test/USArrests.csv"))
    arrestsH2O.describe()
    
    print "H2O initial Y matrix:\n"
    initial_y = [[5.412,  65.24,  -7.54, -0.032],
                 [2.212,  92.24, -17.54, 23.268],
                 [0.312, 123.24,  14.46,  9.768],
                 [1.012,  19.24, -15.54, -1.732]]
    initial_y_h2o = h2o.H2OFrame(initial_y)
    initial_y_h2o.show()

    print "H2O GLRM on de-meaned data with quadratic loss:\n"
    glrm_h2o = h2o.glrm(x=arrestsH2O, k=4, transform="DEMEAN", loss="Quadratic", gamma_x=0, gamma_y=0, init="User", user_y=initial_y_h2o, recover_svd=True)
    glrm_h2o.show()


pyunit_test = glrm_arrests
