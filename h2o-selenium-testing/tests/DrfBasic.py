from testlibs import DRF
from testlibs.DRF import ORDERS

from utils import Constant
from utils import Selenium
from utils import Common
from utils import DatasetCharacteristics as DS


def test(driver, configs, dataset_chars):
    # get value from testcase file
    cfgs = Selenium.get_auto_configs(ORDERS, configs)
    cfgs[Constant.response_column] = dataset_chars.get_data_of_column(configs[Constant.train_dataset_id], DS.target)

    # Check testcase is regression or classification
    is_regression_tc = Common.is_regression_testcase(configs, dataset_chars.get_dataset())

    # Build drf model
    DRF.create_model_drf(driver, cfgs, is_regression_tc)


