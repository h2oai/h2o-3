import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils



import numpy as np

def glrm_arrests_miss():
    missing_ratios = np.arange(0.1, 1, 0.1).tolist()
    
    print "Importing USArrests.csv data and saving for validation..."
    arrests_full = h2o.upload_file(pyunit_utils.locate("smalldata/pca_test/USArrests.csv"))
    arrests_full.describe()
    totobs = arrests_full.nrow * arrests_full.ncol
    train_err = [0]*len(missing_ratios)
    valid_err = [0]*len(missing_ratios)
    
    for i in range(len(missing_ratios)):
        ratio = missing_ratios[i]
        print "Importing USArrests.csv and inserting {0}% missing entries".format(100*ratio)
        arrests_miss = h2o.upload_file(pyunit_utils.locate("smalldata/pca_test/USArrests.csv"))
        arrests_miss = arrests_miss.insert_missing_values(fraction=ratio)
        arrests_miss.describe()
        
        print "H2O GLRM with {0}% missing entries".format(100*ratio)
        arrests_glrm = h2o.glrm(x=arrests_miss, validation_frame=arrests_full, k=4, ignore_const_cols=False, loss="Quadratic", regularization_x="None", regularization_y="None", init="PlusPlus", max_iterations=10, min_step_size=1e-6)
        arrests_glrm.show()
        
        # Check imputed data and error metrics
        glrm_obj = arrests_glrm._model_json['output']['objective']
        train_numerr = arrests_glrm._model_json['output']['training_metrics']._metric_json['numerr']
        train_caterr = arrests_glrm._model_json['output']['training_metrics']._metric_json['caterr']
        valid_numerr = arrests_glrm._model_json['output']['validation_metrics']._metric_json['numerr']
        valid_caterr = arrests_glrm._model_json['output']['validation_metrics']._metric_json['caterr']
        assert abs(train_numerr - glrm_obj) < 1e-3, "Numeric error on training data was " + str(train_numerr) + " but should equal final objective " + str(glrm_obj)
        assert train_caterr == 0, "Categorical error on training data was " + str(train_caterr) + " but should be zero"
        assert valid_caterr == 0, "Categorical error on validation data was " + str(valid_caterr) + " but should be zero"
        
        train_numcnt = arrests_glrm._model_json['output']['training_metrics']._metric_json['numcnt']
        valid_numcnt = arrests_glrm._model_json['output']['validation_metrics']._metric_json['numcnt']
        assert valid_numcnt > train_numcnt, "Number of non-missing numerical entries in training data should be less than validation data"
        assert valid_numcnt == totobs, "Number of non-missing numerical entries in validation data was " + str(valid_numcnt) + " but should be " + str(totobs)
        
        train_err[i] = train_numerr
        valid_err[i] = valid_numerr
        h2o.remove(arrests_glrm._model_json['output']['representation_name'])
    
    for i in range(len(missing_ratios)):
        print "Missing ratio: {0}% --> Training error: {1}\tValidation error: {2}".format(missing_ratios[i]*100, train_err[i], valid_err[i])
    


if __name__ == "__main__":
    pyunit_utils.standalone_test(glrm_arrests_miss)
else:
    glrm_arrests_miss()
