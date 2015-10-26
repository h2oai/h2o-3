from testlibs import common
from testlibs.glm import create_model_glm
from testlibs.glm import ORDERS
from utils.se_functions import get_auto_configs
from utils import Constant


class GlmBasic:
    def __init__(self, tc_id, configs, additional_configs):
        #Init configs for model
        self.cfgs = configs
        self.add_cfgs = additional_configs
        self.tc_id = tc_id
        self.wd = self.add_cfgs['driver']
        self.ds_chars = self.add_cfgs['dataset_chars']


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

        print 'Import dataset is successfully...'


    def test(self):
        #Build, predict model, get values and return result
        print 'Test now:'
        result_dict = dict( result = Constant.testcase_result_status_pass, message = 'This tescase is passed', mse = '', auc ='',
                            train_dataset_id ='', validate_dataset_id ='', distribution ='', sparse = '')
        result_dict[Constant.train_dataset_id] = self.cfgs[Constant.train_dataset_id]
        result_dict[Constant.validate_dataset_id] = self.cfgs[Constant.validate_dataset_id]
        try:
            print 'Start build model...'
            list_family = ['gaussian', 'binomial', 'poisson', 'gamma', 'tweedie']
            print 'set list_family'
            list_family_file = [self.cfgs['gaussian'], self.cfgs['binomial'], self.cfgs['poisson'],
                                self.cfgs['gamma'], self.cfgs['tweedie']]
            family = list_family[list_family_file.index('x')]
            print 'set list_family done'

            list_solver = ['AUTO', 'IRLSM', 'L_BFGS', 'COORDINATE_DESCENT_NAIVE',
                           'COORDINATE_DESCENT_NAIVE', 'COORDINATE_DESCENT']
            list_solver_file = [self.cfgs['auto'], self.cfgs['irlsm'], self.cfgs['lbfgs'],
                                self.cfgs['coordinate_descent_naive'], self.cfgs['coordinate_descent']]
            print 'set list_solver'
            if 0 == list_solver_file.count('x'):
                solver = list_solver[1]
            else:
                solver = list_solver[list_solver_file.index('x')]
            print 'set list_solver done'

            configs = get_auto_configs(ORDERS, self.cfgs)
            configs['response_column'] = self.ds_chars.get_data_of_column(self.cfgs[Constant.train_dataset_id], 'target'),
            configs['family'] = family
            configs['solver'] = solver

            create_model_glm (self.wd, configs)
            print 'Model is built successfully...'

            print 'Start predict model...'
            if not self.cfgs[Constant.validate_dataset_id].strip() == '':
                common.predict_file(self.wd, dict(frame_select = self.cfgs[Constant.validate_dataset_id]))
            else:
                common.predict_file(self.wd, dict(frame_select = self.cfgs[Constant.train_dataset_id]))
            print 'Predict model is successfully...'

            print '---Getting value after predict model:'
            result_dict['mse'] = common.get_values(self.wd, ['mse'])


            print 'Test case', self.tc_id, ': passed'
            return result_dict

        except Exception as e:
            result_dict['result']= Constant.testcase_result_status_fail
            result_dict['message'] =  "Reason Failed: " + str(e.message)
            print 'Test case', self.tc_id,': falied'
            return result_dict


    def clean_up(self):
        print 'clean up now...'


def unit_test():
    pass


if __name__ == '__main__':
    unit_test()


