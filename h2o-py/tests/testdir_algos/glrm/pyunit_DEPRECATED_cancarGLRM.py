import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils




def glrm_cancar():
    print "Importing cancar.csv data..."
    cancarH2O = h2o.upload_file(pyunit_utils.locate("smalldata/glrm_test/cancar.csv"))
    cancarH2O.describe()
    
    print "Building GLRM model with init = PlusPlus:\n"
    glrm_pp = h2o.glrm(x=cancarH2O, k=4, transform="NONE", init="PlusPlus", loss="Quadratic", regularization_x="None", regularization_y="None", max_iterations=1000)
    glrm_pp.show()

    print "Building GLRM model with init = SVD:\n"
    glrm_svd = h2o.glrm(x=cancarH2O, k=4, transform="NONE", init="SVD", loss="Quadratic", regularization_x="None", regularization_y="None", max_iterations=1000)
    glrm_svd.show()



if __name__ == "__main__":
    pyunit_utils.standalone_test(glrm_cancar)
else:
    glrm_cancar()
