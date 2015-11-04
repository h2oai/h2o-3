from testlibs import common
from testlibs.dl import ORDERS
from testlibs.dl import create_model_dl
from utils.se_functions import get_auto_configs, map_value_list_to_list
from utils import Constant


class DlBasic:

    def __init__(self, tc_id, configs, driver, dataset_chars):
        #Init configs for model
        self.cfgs = configs
        self.tc_id = tc_id

        # Helpers
        self.wd = driver
        self.ds_chars = dataset_chars

        self.add_cfgs = dict(
            dataset_chars = self.ds_chars
        )

    def setup(self):
        #Setup dataset for create model
        print 'Start running testcase:', self.tc_id
        print 'Start import dataset...'
        print '---Import train dataset:'

        train_fn = self.ds_chars.get_filepath(self.cfgs[Constant.train_dataset_id])
        print 'train file path: ', train_fn
        common.import_parse_file(self.wd, dict(file_path = train_fn,
                                               destination_key = self.cfgs[Constant.train_dataset_id]),
                                 self.add_cfgs, self.cfgs[Constant.train_dataset_id])

        print '---Import validate dataset:'
        if '' == self.cfgs[Constant.validate_dataset_id].strip():
            print 'This testcase have no validate dataset'
        elif self.ds_chars.is_available(self.cfgs[Constant.validate_dataset_id]):
            validate_fn = self.ds_chars.get_filepath(self.cfgs[Constant.validate_dataset_id])
            print 'validate file path:', validate_fn
            common.import_parse_file(self.wd, dict(file_path = validate_fn,
                                                   destination_key = self.cfgs[Constant.validate_dataset_id]),
                                     self.add_cfgs, self.cfgs[Constant.validate_dataset_id])
        else:
            print 'Dataset %s is not available in dataset characteristic' % self.cfgs[Constant.validate_dataset_id]
            print 'Test case', self.tc_id,': invalid'
            raise Exception('Test case invalid')

        print( 'Import dataset is successfully...')


    def test(self):
        #Build, predict model, get values and return result
        print 'Test now...'

        result_dict = dict(train_dataset_id = self.cfgs[Constant.train_dataset_id],
                           validate_dataset_id = self.cfgs[Constant.validate_dataset_id],
                           result = Constant.testcase_result_status_pass,
                           message = 'This tescase passed',
                           mse = '',
                           auc = '',
                           distribution = '',
                           sparse = ''
                           )

        try:
            print 'Start build model...'

            print 'Get loss from file: '
            list_loss = ['Automatic', 'CrossEntropy', 'Quadratic', 'Huber', 'Absolute']
            list_loss_file = [self.cfgs['automatic'], self.cfgs['crossentropy'], self.cfgs['meansquare'], self.cfgs['huber'], self.cfgs['absolute']]
            print 'Get loss from file Done'

            print 'Get distribution from file: '
            list_distribution = ['AUTO', 'gaussian', 'bernoulli', 'multinomial', 'poisson', 'gamma', 'tweedie']
            list_distribution_file = [self.cfgs['auto'], self.cfgs['gaussian'], self.cfgs['bernoulli'], self.cfgs['multinomial'], self.cfgs['poissan'], self.cfgs['gamma'], self.cfgs['tweedie']]
            print 'Get distribution from file Done'

            print 'Get activation from file: '
            list_activation = ['Tanh', 'TanhWithDropout', 'Rectifier', 'RectifierWithDropout', 'Maxout', 'MaxoutWithDropout']
            list_activation_file = [self.cfgs['tanh'], self.cfgs['tanhwithdropout'], self.cfgs['rectifier'], self.cfgs['rectifierwithdropout'], self.cfgs['maxout'], self.cfgs['maxoutwithdropout']]
            print 'Get activation from file Done'

            print 'Get auto configs '
            configs = get_auto_configs(ORDERS, self.cfgs)
            configs['response_column'] = self.ds_chars.get_data_of_column(self.cfgs[Constant.train_dataset_id],'target'),
            configs['distribution'] = map_value_list_to_list(list_distribution, list_distribution_file)
            configs['activation'] = map_value_list_to_list(list_activation, list_activation_file)
            configs['loss'] = map_value_list_to_list(list_loss, list_loss_file)
            print "Get auto config Done"

            create_model_dl (self.wd, configs)
            print 'Model is built successfully...'

            print 'Start predict model...'
            if not self.cfgs[Constant.validate_dataset_id].strip() == '':
                common.predict_file(self.wd, dict(frame_select = self.cfgs[Constant.validate_dataset_id]))
            else:
                common.predict_file(self.wd, dict(frame_select = self.cfgs[Constant.train_dataset_id]))

            print '---Getting value after predict model:'
            result_dict['mse'] = common.get_values(self.wd, ['mse'])

            print 'Predict model is successfully...'

            print 'Test case', self.tc_id, ': passed'
            return result_dict

        except Exception as e:
            result_dict['result']= Constant.testcase_result_status_fail
            result_dict['message'] = "Reason Failed: " + str(e.message)
            print 'Test case', self.tc_id,': falied'
            return result_dict


    def clean_up(self):
        print 'clean up now...'


def unit_test():
    pass


if __name__ == '__main__':
    unit_test()