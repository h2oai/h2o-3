import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
import random

def glrm_iris():
    print "Importing iris_wheader.csv data..."
    irisH2O = h2o.upload_file(pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
    irisH2O.describe()
    
    for trans in ["NONE", "DEMEAN", "DESCALE", "STANDARDIZE"]:
        rank = random.randint(1,7)
        gx = random.uniform(0,1)
        gy = random.uniform(0,1)
        
        print "H2O GLRM with rank k = " + str(rank) + ", gamma_x = " + str(gx) + ", gamma_y = " + str(gy) + ", transform = " + trans
        glrm_h2o = h2o.glrm(x=irisH2O, k=rank, loss="Quadratic", gamma_x=gx, gamma_y=gy, transform=trans)
        glrm_h2o.show()
        
        print "Impute original data from XY decomposition"
        pred_h2o = glrm_h2o.predict(irisH2O)
        pred_h2o.describe()
        h2o.remove(glrm_h2o._model_json['output']['representation_name'])



if __name__ == "__main__":
    pyunit_utils.standalone_test(glrm_iris)
else:
    glrm_iris()
