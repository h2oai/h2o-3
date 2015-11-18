from testlibs import GLM
from testlibs.GLM import ORDERS

from utils import Common
from utils import Constant
from utils import Selenium
from utils import DatasetCharacteristics as DS


def test(driver, configs, dataset_chars):
    # Set value for family
    list_family = ['gaussian', 'binomial', 'poisson', 'gamma', 'tweedie']
    list_family_file = [configs['gaussian'], configs['binomial'],
                        configs['poisson'], configs['gamma'], configs['tweedie']]
    family = Common.get_value_from_2list(list_family, list_family_file)

    # Set value for solver
    list_solver = ['AUTO', 'IRLSM', 'L_BFGS', 'COORDINATE_DESCENT_NAIVE',
                   'COORDINATE_DESCENT_NAIVE', 'COORDINATE_DESCENT']
    list_solver_file = [configs['auto'], configs['irlsm'], configs['lbfgs'],
                        configs['coordinate_descent_naive'], configs['coordinate_descent']]
    solver = Common.get_value_from_2list(list_solver, list_solver_file)

    # get value from testcase file
    cfgs = Selenium.get_auto_configs(ORDERS, configs)
    cfgs[Constant.response_column] = dataset_chars.get_data_of_column(configs[Constant.train_dataset_id], DS.target)
    cfgs[Constant.family] = family
    cfgs[Constant.solver] = solver
    cfgs[Constant.lamda] = configs['lambda']

    # Build glm model
    GLM.create_model_glm(driver, cfgs)


