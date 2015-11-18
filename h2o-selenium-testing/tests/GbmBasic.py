from testlibs import GBM
from testlibs.GBM import ORDERS

from utils import Common
from utils import Constant
from utils import Selenium
from utils import DatasetCharacteristics as DS


def test(driver, configs, dataset_chars):
    # Set value for distribution
    list_distribution = ['AUTO', 'gaussian', 'bernoulli', 'multinomial', 'poisson', 'gamma', 'tweedie']
    list_distribution_file = [configs['auto'], configs['gaussian'],
                              configs['binomial'],configs['multinomial'],
                              configs['poisson'], configs['gamma'], configs['tweedie']]
    distribution = Common.get_value_from_2list(list_distribution, list_distribution_file)

    # get value from testcase file
    cfgs = Selenium.get_auto_configs(ORDERS, configs)
    cfgs[Constant.response_column] = dataset_chars.get_data_of_column(configs[Constant.train_dataset_id], DS.target)
    cfgs[Constant.distribution] = distribution

    # Check testcase is regression or classification
    is_regression_tc = Common.is_regression_testcase(configs, dataset_chars.get_dataset())

    # Build gbm model
    GBM.create_model_gbm (driver, cfgs, is_regression_tc)



