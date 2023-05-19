import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.model_selection import H2OModelSelectionEstimator as modelSelection

# test modelselection works with cross-validation with fold column
def test_modelselection_cross_validation():

    d = h2o.import_file(path=pyunit_utils.locate("smalldata/glm_test/gaussian_20cols_10000Rows.csv"))
    my_y = "C21"
    my_x = ["C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10", "C11", "C12", "C13", "C14", "C15", "C16",
           "C17", "C18", "C19", "C20"]
    factorX = ["C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10"]
    for x in factorX:
        d[x] = d[x].asfactor()
    n_folds = 3
    fold_numbers = d.modulo_kfold_column(n_folds = n_folds)
    fold_numbers.set_names(["fold_numbers_modulo"])
    fold_numbers2 = d.kfold_column(n_folds = n_folds, seed=12345)
    fold_numbers2.set_names(["fold_numbers_kfold"])
    
    # append the fold_numbers column to the cars dataset
    d = d.cbind(fold_numbers)
    d = d.cbind(fold_numbers2)
    
    # cv model with fold assignment
    allsubsets_model_fa = modelSelection(seed=12345, max_predictor_number=3, fold_column="fold_numbers_modulo", 
                                      mode="allsubsets")
    allsubsets_model_fa.train(training_frame=d, x=my_x, y=my_y)
    best_r2_allsubsets_fa = allsubsets_model_fa.get_best_R2_values()

    allsubsets_model_fk = modelSelection(seed=12345, max_predictor_number=3, fold_column="fold_numbers_kfold", 
                                      mode="allsubsets")
    allsubsets_model_fk.train(training_frame=d, x=my_x, y=my_y)
    best_r2_allsubsets_fk = allsubsets_model_fk.get_best_R2_values()
     
     # both models should provide same best R2 values
    pyunit_utils.equal_two_arrays(best_r2_allsubsets_fa, best_r2_allsubsets_fk, eps=1e-6)

    # cv model with fold assignment
    maxr_model_fa = modelSelection(seed=12345, max_predictor_number=3, fold_column="fold_numbers_modulo",
                                         mode="maxr")
    maxr_model_fa.train(training_frame=d, x=my_x, y=my_y)
    best_r2_maxr_fa = maxr_model_fa.get_best_R2_values()

    maxr_model_fk = modelSelection(seed=12345, max_predictor_number=3, fold_column="fold_numbers_kfold",
                                         mode="maxr")
    maxr_model_fk.train(training_frame=d, x=my_x, y=my_y)
    best_r2_maxr_fk = maxr_model_fk.get_best_R2_values()

    # both models should provide same best R2 values
    pyunit_utils.equal_two_arrays(best_r2_allsubsets_fa, best_r2_maxr_fa, eps=1e-6)
    pyunit_utils.equal_two_arrays(best_r2_allsubsets_fk, best_r2_maxr_fk, eps=1e-6)

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_modelselection_cross_validation)
else:
    test_modelselection_cross_validation()
