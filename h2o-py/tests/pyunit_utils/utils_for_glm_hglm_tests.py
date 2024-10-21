from h2o.estimators import H2OGeneralizedLinearEstimator as glm
from h2o.exceptions import H2OValueError
from h2o.grid.grid_search import H2OGridSearch
from tests import pyunit_utils


def gen_constraint_glm_model(training_dataset, x, y, solver="AUTO", family="gaussian", linear_constraints=None, 
                             beta_constraints=None, separate_linear_beta=False, init_optimal_glm=False, startval=None,
                             constraint_eta0=0.1258925, constraint_tau=10, constraint_alpha=0.1, 
                             constraint_beta=0.9, constraint_c0=10):
    """
    This function given the parameters will return a constraint GLM model.
    """
    if linear_constraints is None:
        raise H2OValueError("linear_constraints cannot be None")
        
    params = {"family":family, "lambda_":0.0, "seed":12345, "remove_collinear_columns":True, "solver":solver, 
              "linear_constraints":linear_constraints, "init_optimal_glm":init_optimal_glm, 
              "constraint_eta0":constraint_eta0, "constraint_tau":constraint_tau, "constraint_alpha":constraint_alpha,
              "constraint_beta":constraint_beta, "constraint_c0":constraint_c0}
    if beta_constraints is not None:
        params['beta_constraints']=beta_constraints
        params["separate_linear_beta"]=separate_linear_beta
    if startval is not None:
        params["startval"]=startval
        
    constraint_glm = glm(**params)
    constraint_glm.train(x=x, y=y, training_frame=training_dataset)
    return constraint_glm


def constraint_glm_gridsearch(training_dataset, x, y, solver="AUTO", family="gaussia", linear_constraints=None,
                              beta_constraints=None, metric="logloss", return_best=True, startval=None, 
                              init_optimal_glm=False, constraint_eta0=[0.1258925],  constraint_tau=[10], 
                              constraint_alpha=[0.1], constraint_beta=[0.9], constraint_c0=[10], epsilon=1e-3):
    """
    This function given the obj_eps_hyper and inner_loop_hyper will build and run a gridsearch model and return the one
    with the best metric.
    """
    if linear_constraints is None:
        raise H2OValueError("linear_constraints cannot be None")

    params = {"family":family, "lambda_":0.0, "seed":12345, "remove_collinear_columns":True, "solver":solver,
              "linear_constraints":linear_constraints}
    hyper_parameters = {"constraint_eta0":constraint_eta0, "constraint_tau":constraint_tau, "constraint_alpha":constraint_alpha,
                   "constraint_beta":constraint_beta, "constraint_c0":constraint_c0}
    if beta_constraints is not None:
        params['beta_constraints']=beta_constraints
        hyper_parameters["separate_linear_beta"] = [False, True]
    if startval is not None:
        params["startval"]=startval
    if init_optimal_glm:
        params["init_optimal_glm"]=True
        
    glmGrid = H2OGridSearch(glm(**params), hyper_params=hyper_parameters)
    glmGrid.train(x=x, y=y, training_frame=training_dataset)
    sortedGrid = glmGrid.get_grid()
    print(sortedGrid)
    if return_best:
        print_model_hyperparameters(sortedGrid.models[0], hyper_parameters)
        return sortedGrid.models[0]
    else:
        return grid_models_analysis(sortedGrid.models, hyper_parameters, metric=metric, epsilon=epsilon)


def grid_models_analysis(grid_models, hyper_parameters, metric="logloss", epsilon=1e-3):
    """
    This method will search within the grid search models that have metrics within epsilon calculated as 
    abs(metric1-metric2)/abs(metric1) as the best model.  We are wanting to send the best model that has the best
     constraint values meaning either they have the smallest magnitude or if less than constraints, it has the smallest
     magnitude and the correct sign.  Else, the original top model will be returned.
    """
    base_metric = grid_models[0].model_performance()._metric_json[metric]
    base_constraints_table = grid_models[0]._model_json["output"]["linear_constraints_table"]
    cond_index = base_constraints_table.col_header.index("condition")
    [best_equality_constraints, best_lessthan_constraints] = grab_constraint_values(
        base_constraints_table, cond_index, len(base_constraints_table.cell_values))

    base_iteration = find_model_iterations(grid_models[0])
    num_models = len(grid_models)
    best_model_ind = 0
    model_indices = []
    model_equality_constraints_values = []
    model_lessthan_constraints_values = []
    iterations = []
    for ind in range(1, num_models):
        curr_model = grid_models[ind]
        curr_metric = grid_models[ind].model_performance()._metric_json[metric]
        metric_diff = abs(base_metric-curr_metric)/abs(base_metric)
        if metric_diff < epsilon:
            curr_constraint_table = curr_model._model_json["output"]["linear_constraints_table"]
            [equality_constraints_values, lessthan_constraints_values] = grab_constraint_values(
                curr_constraint_table, cond_index, len(curr_constraint_table.cell_values))
            # conditions used to choose the best model
            if (sum(equality_constraints_values) < sum(best_equality_constraints)) and (sum(lessthan_constraints_values) < sum(best_lessthan_constraints)):
                best_model_ind = ind
                base_iteration = find_model_iterations(curr_model)
                best_equality_constraints = equality_constraints_values
                best_lessthan_constraints = lessthan_constraints_values
            model_equality_constraints_values.append(equality_constraints_values)
            model_lessthan_constraints_values.append(lessthan_constraints_values)
            model_indices.append(ind)
            iterations.append(find_model_iterations(curr_model))
    print("Maximum iterations: {0} and it is from model index: {1}".format(base_iteration, best_model_ind))
    print_model_hyperparameters(grid_models[best_model_ind], hyper_parameters)
    return grid_models[best_model_ind]


def print_model_hyperparameters(model, hyper_parameters):
    print("***** Hyper parameter values for best model chosen are:")
    params = model.actual_params
    param_keys = hyper_parameters.keys()
    actual_keys = params.keys()
    for param in actual_keys:
        if param in param_keys:
            print("{0} value {1}.".format(param, params[param]))
            
        
def grab_constraint_values(curr_constraint_table, cond_index, num_constraints):
    equality_constraints_values = []
    lessthan_constraints_values = []
    for ind2 in range(0, num_constraints): # collect all equality constraint info
        if curr_constraint_table.cell_values[ind2][cond_index]=="== 0":
            equality_constraints_values.append(curr_constraint_table.cell_values[ind2][cond_index-1])
        else:
            if curr_constraint_table.cell_values[ind2][cond_index-1] < 0:
                lessthan_constraints_values.append(0)
            else:
                lessthan_constraints_values.append(curr_constraint_table.cell_values[ind2][cond_index-1])
    return [equality_constraints_values, lessthan_constraints_values]
    

def is_always_lower_than(original_tuple, new_tuple):
    """
    This function will return True if new_tuple has smaller magnitude elements than what is in original_tuple.
    """
    assert len(original_tuple) == len(new_tuple), "expected tuple length: {0}, actual length: {1} and they are " \
                                                  "different".format(len(original_tuple), len(new_tuple))
    return all(abs(orig) > abs(new) for orig, new in zip(original_tuple, new_tuple))
           
def find_model_iterations(glm_model):
    """
    Given a glm constrainted model, this method will obtain the number of iterations from the model summary.
    """
    cell_values = glm_model._model_json["output"]["model_summary"].cell_values
    lengths = len(cell_values)
    iteration_index = glm_model._model_json["output"]["model_summary"].col_header.index("number_of_iterations")
    return cell_values[lengths-1][iteration_index]

def add_to_random_coef_dict(normalized_coefs, normalized_one_coefs, level2_val, random_coefs_names):
    one_list = []
    for one_name in random_coefs_names:
        one_list.append(normalized_one_coefs[one_name])
    normalized_coefs[level2_val] = one_list

def extract_coef_dict(random_coeffs, level2_name, random_coefs_names):
    random_coef_level2 = dict()
    index = 0
    for cname in random_coefs_names:
        random_coef_level2[cname] = random_coeffs[level2_name][index]
        index = index+1
    return random_coef_level2    

def compare_dicts_with_tupple(dict1, dict2, tolerance=1e-6):
    keys = dict1.keys()
    for cname in keys:
        pyunit_utils.equal_two_arrays(dict1[cname], dict2[cname], tolerance = tolerance, throw_error=True)
        
def compare_list_h2o_frame(one_list, h2oframe, col_name_start):
    list_len = len(one_list)
    for index in range(list_len):
        assert col_name_start+h2oframe[index, 0] in one_list, "Value: {0} is not found in the list.".format(h2oframe[index, 0])
        
def check_icc_calculation(tmat, varEVar, icc, tolerance=1e-6):
    t_size = len(icc)
    varSum = varEVar
    for ind in range(t_size):
        varSum = varSum + tmat[ind][ind]
    oneOVarSum = 1.0/varSum
        
    for ind in range(t_size):
        one_icc = tmat[ind][ind]*oneOVarSum
        assert abs(one_icc - icc[ind]) < tolerance, "Expected ICC value {0} for coef {1}, actual ICC value {2}.  " \
                                                    "They are not equal or close.".format(one_icc, icc[ind], ind)
        
