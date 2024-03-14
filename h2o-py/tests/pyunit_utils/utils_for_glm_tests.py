import h2o
from h2o.estimators import H2OGeneralizedLinearEstimator as glm
from h2o.exceptions import H2OValueError
from h2o.grid.grid_search import H2OGridSearch

def gen_constraint_glm_model(training_dataset, x, y, solver="AUTO", family="gaussian", linear_constraints=None, 
                             beta_constraints=None, separate_linear_beta=False, init_optimal_glm=False, startval = None):
    """
    This function given the parameters will return a constraint GLM model.
    """
    if linear_constraints is None:
        raise H2OValueError("linear_constraints cannot be None")
        
    params = {"family":family, "lambda_":0.0, "seed":12345, "remove_collinear_columns":True, "solver":solver, 
              "linear_constraints":linear_constraints, "init_optimal_glm":init_optimal_glm}
    if beta_constraints is not None:
        params['beta_constraints']=beta_constraints
    if startval is not None:
        params["startval"]=startval
        
    constraint_glm = glm(**params)
    constraint_glm.train(x=x, y=y, training_frame=training_dataset)
    return constraint_glm

def constraint_glm_gridsearch(training_dataset, x, y, solver="AUTO", family="gaussia", linear_constraints=None,
                              beta_constraints=None, startval=None, init_optimal_glm=False):
    """
    This function given the obj_eps_hyper and inner_loop_hyper will build and run a gridsearch model and return the one
    with the best metric.
    """
    if linear_constraints is None:
        raise H2OValueError("linear_constraints cannot be None")

    params = {"family":family, "lambda_":0.0, "seed":12345, "remove_collinear_columns":True, "solver":solver,
              "linear_constraints":linear_constraints}
    hyperParams = {}
    if beta_constraints is not None:
        params['beta_constraints']=beta_constraints
        hyperParams["separate_linear_beta"] = [True, False]
    if startval is not None:
        params["startval"]=startval
    if init_optimal_glm:
        params["init_optimal_glm"]=True
        
    glmGrid = H2OGridSearch(glm(**params), hyper_params=hyperParams)
    glmGrid.train(x=x, y=y, training_frame=training_dataset)
    sortedGrid = glmGrid.get_grid()
    print(sortedGrid)

    return sortedGrid.models[0]

def find_glm_iterations(glm_model):
    """
    Given a glm constrainted model, this method will obtain the number of iterations from the model summary.
    """
    cell_values = glm_model._model_json["output"]["model_summary"].cell_values
    lengths = len(cell_values)
    iteration_index = glm_model._model_json["output"]["model_summary"].col_header.index("number_of_iterations")
    return cell_values[lengths-1][iteration_index]
    
