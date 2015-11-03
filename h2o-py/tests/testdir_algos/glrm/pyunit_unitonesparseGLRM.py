import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils



import numpy as np

def glrm_unitonesparse():
    m = 1000
    n = 100
    k = 10

    print "Uploading random uniform matrix with rows = " + str(m) + " and cols = " + str(n)
    Y = np.random.rand(k,n)
    def ind_list(k):
        tmp = [0] * k
        tmp[np.random.randint(0,k)] = 1
        return tmp
    X = [ind_list(k) for x in xrange(m)]
    X = np.array(X)
    train = np.dot(X,Y)
    train_h2o = h2o.H2OFrame(zip(*train.tolist()))

    print "Run GLRM with unit one-sparse regularization on X"
    initial_y = np.random.rand(k,n)
    initial_y_h2o = h2o.H2OFrame(zip(*initial_y.tolist()))
    glrm_h2o = h2o.glrm(x=train_h2o, k=k, init="User", user_y=initial_y_h2o, loss="Quadratic", regularization_x="UnitOneSparse", regularization_y="None", gamma_x=1, gamma_y=0)
    glrm_h2o.show()

    print "Check that X matrix consists of rows of basis vectors"
    fit_x = h2o.get_frame(glrm_h2o._model_json['output']['representation_name'])
    fit_x_np = np.array(h2o.as_list(fit_x))
    def is_basis(a):
        zeros = np.where(a == 0)[0].size
        ones = np.where(a == 1)[0].size
        basis = ones == 1 and (zeros + ones) == k
        assert basis, "Got " + str(ones) + " ones and " + str(zeros) + " zeros, but expected all zeros except a single 1"
        return basis
    np.apply_along_axis(is_basis, 1, fit_x_np)

    print "Check final objective function value"
    fit_y = glrm_h2o._model_json['output']['archetypes'].cell_values
    fit_y_np = [[float(s) for s in list(row)[1:]] for row in fit_y]
    fit_y_np = np.array(fit_y_np)
    fit_xy = np.dot(fit_x_np, fit_y_np)
    glrm_obj = glrm_h2o._model_json['output']['objective']
    sse = np.sum(np.square(train.__sub__(fit_xy)))
    assert abs(glrm_obj - sse) < 1e-6, "Final objective was " + str(glrm_obj) + " but should equal " + str(sse)

    print "Impute XY and check error metrics"
    pred_h2o = glrm_h2o.predict(train_h2o)
    pred_np = np.array(h2o.as_list(pred_h2o))
    assert np.allclose(pred_np, fit_xy), "Imputation for numerics with quadratic loss should equal XY product"
    glrm_numerr = glrm_h2o._model_json['output']['training_metrics']._metric_json['numerr']
    glrm_caterr = glrm_h2o._model_json['output']['training_metrics']._metric_json['caterr']
    assert abs(glrm_numerr - glrm_obj) < 1e-3, "Numeric error was " + str(glrm_numerr) + " but should equal final objective " + str(glrm_obj)
    assert glrm_caterr == 0, "Categorical error was " + str(glrm_caterr) + " but should be zero"



if __name__ == "__main__":
    pyunit_utils.standalone_test(glrm_unitonesparse)
else:
    glrm_unitonesparse()
