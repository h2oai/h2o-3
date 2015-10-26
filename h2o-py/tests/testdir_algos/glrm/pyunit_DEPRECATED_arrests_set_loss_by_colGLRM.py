import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils



import numpy as np

def glrm_set_loss_by_col():
    print "Importing USArrests.csv data..."
    arrestsH2O = h2o.upload_file(pyunit_utils.locate("smalldata/pca_test/USArrests.csv"))
    arrestsPy = np.array(h2o.as_list(arrestsH2O))
    arrestsH2O.describe()
    
    print "H2O GLRM with loss by column = Absolute, Quadratic, Quadratic, Huber"
    glrm_h2o = h2o.glrm(x=arrestsH2O, k=3, loss="Quadratic", loss_by_col=["Absolute","Huber"], loss_by_col_idx=[0,3], regularization_x="None", regularization_y="None")
    glrm_h2o.show()
    
    fit_y = glrm_h2o._model_json['output']['archetypes'].cell_values
    fit_y_np = [[float(s) for s in list(row)[1:]] for row in fit_y]
    fit_y_np = np.array(fit_y_np)
    fit_x = h2o.get_frame(glrm_h2o._model_json['output']['representation_name'])
    fit_x_np = np.array(h2o.as_list(fit_x))
    
    print "Check final objective function value"
    fit_xy = np.dot(fit_x_np, fit_y_np)
    fit_diff = arrestsPy.__sub__(fit_xy)
    obj_val = np.absolute(fit_diff[:,0]) + np.square(fit_diff[:,1]) + np.square(fit_diff[:,2])
    def huber(a):
        return a*a/2 if abs(a) <= 1 else abs(a)-0.5
    huber = np.vectorize(huber)
    obj_val = obj_val + huber(fit_diff[:,3])
    obj_val = np.sum(obj_val)
    glrm_obj = glrm_h2o._model_json['output']['objective']
    assert abs(glrm_obj - obj_val) < 1e-6, "Final objective was " + str(glrm_obj) + " but should equal " + str(obj_val)



if __name__ == "__main__":
    pyunit_utils.standalone_test(glrm_set_loss_by_col)
else:
    glrm_set_loss_by_col()
