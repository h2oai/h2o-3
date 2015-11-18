from testlibs import DL
from testlibs.DL import ORDERS

from utils import Common
from utils import Constant
from utils import Selenium
from utils import DatasetCharacteristics as DS


def test(driver, configs, dataset_chars):
    # Get value for loss
    list_loss = ['Automatic', 'CrossEntropy', 'Quadratic', 'Huber', 'Absolute']
    list_loss_file = [configs['automatic'], configs['crossentropy'],
                      configs['meansquare'], configs['huber'], configs['absolute']]
    loss = Common.get_value_from_2list(list_loss, list_loss_file)

    # Get value for distribution
    list_distribution = ['AUTO', 'gaussian', 'bernoulli', 'multinomial', 'poisson', 'gamma', 'tweedie']
    list_distribution_file = [configs['auto'], configs['gaussian'],
                              configs['bernoulli'], configs['multinomial'],
                              configs['poissan'], configs['gamma'], configs['tweedie']]
    distribution = Common.get_value_from_2list(list_distribution, list_distribution_file)

    # Get value for activation
    list_activation = ['Tanh', 'TanhWithDropout', 'Rectifier',
                       'RectifierWithDropout', 'Maxout', 'MaxoutWithDropout']
    list_activation_file = [configs['tanh'], configs['tanhwithdropout'],
                            configs['rectifier'], configs['rectifierwithdropout'],
                            configs['maxout'], configs['maxoutwithdropout']]
    activation = Common.get_value_from_2list(list_activation, list_activation_file)

    # Get value from testcase file
    cfgs = Selenium.get_auto_configs(ORDERS, configs)
    cfgs[Constant.response_column] = dataset_chars.get_data_of_column(configs[Constant.train_dataset_id], DS.target)
    cfgs[Constant.distribution] = distribution
    cfgs[Constant.activation] = activation
    cfgs[Constant.loss] = loss

    # Check testcase is regression or classification
    is_regression_tc = Common.is_regression_testcase(configs, dataset_chars.get_dataset())

    # Build dl model
    DL.create_model_dl(driver, cfgs, is_regression_tc)

