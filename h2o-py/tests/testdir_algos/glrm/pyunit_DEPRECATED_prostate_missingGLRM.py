import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils



import numpy as np

def glrm_prostate_miss():
    missing_ratios = np.arange(0.1, 1, 0.1).tolist()
    
    print "Importing prostate_cat.csv data and saving for validation..."
    prostate_full = h2o.upload_file(pyunit_utils.locate("smalldata/prostate/prostate_cat.csv"), na_strings=["NA"]*8)
    prostate_full.describe()
    totnas = 0
    for i in range(prostate_full.ncol):
        totnas = totnas + prostate_full[i].isna().sum()
    totobs = prostate_full.nrow * prostate_full.ncol - totnas
    
    train_numerr = [0]*len(missing_ratios)
    valid_numerr = [0]*len(missing_ratios)
    train_caterr = [0]*len(missing_ratios)
    valid_caterr = [0]*len(missing_ratios)
    
    for i in range(len(missing_ratios)):
        ratio = missing_ratios[i]
        print "Importing prostate_cat.csv and inserting {0}% missing entries".format(100*ratio)
        prostate_miss = h2o.upload_file(pyunit_utils.locate("smalldata/prostate/prostate_cat.csv"))
        prostate_miss = prostate_miss.insert_missing_values(fraction=ratio)
        prostate_miss.describe()
        
        print "H2O GLRM with {0}% missing entries".format(100*ratio)
        prostate_glrm = h2o.glrm(x=prostate_miss, validation_frame=prostate_full, k=8, ignore_const_cols=False, loss="Quadratic", gamma_x=0.5, gamma_y=0.5, regularization_x="L1", regularization_y="L1", init="SVD", max_iterations=2000, min_step_size=1e-6)
        prostate_glrm.show()
        
        # Check imputed data and error metrics
        train_numcnt = prostate_glrm._model_json['output']['training_metrics']._metric_json['numcnt']
        valid_numcnt = prostate_glrm._model_json['output']['validation_metrics']._metric_json['numcnt']
        train_catcnt = prostate_glrm._model_json['output']['training_metrics']._metric_json['catcnt']
        valid_catcnt = prostate_glrm._model_json['output']['validation_metrics']._metric_json['catcnt']
        assert valid_numcnt >= train_numcnt, "Number of non-missing numeric entries in training data should be less than or equal to validation data"
        assert valid_catcnt >= train_catcnt, "Number of non-missing categorical entries in training data should be less than or equal to validation data"
        assert (train_numcnt + valid_numcnt) < totobs, "Total non-missing numeric entries in training and validation data was {0}, but should be less than {1}".format(train_numcnt + valid_numcnt, totobs)
        assert (valid_numcnt + valid_catcnt) == totobs, "Number of non-missing entries in validation data was {0}, but should be {1}".format(valid_numcnt + valid_catcnt, totobs)

        train_numerr[i] = prostate_glrm._model_json['output']['training_metrics']._metric_json['numerr']
        valid_numerr[i] = prostate_glrm._model_json['output']['validation_metrics']._metric_json['numerr']
        train_caterr[i] = prostate_glrm._model_json['output']['training_metrics']._metric_json['caterr']
        valid_caterr[i] = prostate_glrm._model_json['output']['validation_metrics']._metric_json['caterr']
        h2o.remove(prostate_glrm._model_json['output']['representation_name'])
    
    for i in range(len(missing_ratios)):
        print "Missing ratio: {0}% --> Training numeric error: {1}\tValidation numeric error: {2}".format(missing_ratios[i]*100, train_numerr[i], valid_numerr[i])
        
    for i in range(len(missing_ratios)):
        print "Missing ratio: {0}% --> Training categorical error: {1}\tValidation categorical error: {2}".format(missing_ratios[i]*100, train_caterr[i], valid_caterr[i])
    


if __name__ == "__main__":
    pyunit_utils.standalone_test(glrm_prostate_miss)
else:
    glrm_prostate_miss()
