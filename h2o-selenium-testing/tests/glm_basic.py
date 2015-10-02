from testlibs import common
from testlibs.glm import create_model_glm
from testlibs.glm import ORDERS
from utils.se_functions import get_auto_configs

import sys

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

        if self.cfgs['train_dataset_id'] not in self.ds_chars.mylist:
            self.ds_chars.is_import['key'] = False
            if not self.ds_chars.is_import['key']:

                print '---Import train dataset:'
                train_fn = self.ds_chars.get_filepath(self.cfgs['train_dataset_id'])
                common.import_parse_file(self.wd, dict(file_path = train_fn,
                                                        destination_key = self.cfgs['train_dataset_id']))

                print '---Import validate dataset:'
                if not self.cfgs['validate_dataset_id'].strip() == '':
                    validate_fn = self.ds_chars.get_filepath(self.cfgs['validate_dataset_id'])
                    common.import_parse_file(self.wd, dict(file_path = validate_fn,
                                                           destination_key = self.cfgs['validate_dataset_id']))
              
                self.ds_chars.is_import['key'] = self.ds_chars.set_imported()
                self.ds_chars.mylist.append(self.cfgs['train_dataset_id'])
        print( 'Import dataset is successfully...')


    def test(self):
        #Build, predict model, get values and return result
        print 'Test now:'
        result_dict = dict( result = 'PASS', message = 'This tescase is passed', mse= '', auc='',
                            train_dataset_id='', validate_dataset_id='', distribution='', sparse = '')
        result_dict['train_dataset_id'] = self.cfgs['train_dataset_id']
        result_dict['validate_dataset_id'] = self.cfgs['validate_dataset_id']
        try:
            print 'Start build model...'
            list_family = ['gaussian', 'binomial', 'poisson', 'gamma', 'tweedie']
            list_family_file = [self.cfgs['gaussian'], self.cfgs['binomial'], self.cfgs['poissan'],
                                self.cfgs['gamma'], self.cfgs['tweedie']]
            family = list_family[list_family_file.index('x')]

            list_solver = ['AUTO', 'IRLSM', 'L_BFGS', 'COORDINATE_DESCENT_NAIVE',
                           'COORDINATE_DESCENT_NAIVE', 'COORDINATE_DESCENT']
            list_solver_file = [self.cfgs['auto'], self.cfgs['IRLSM'], self.cfgs['L_BFGS'],
                                self.cfgs['COORDINATE_DESCENT_NAIVE'], self.cfgs['COORDINATE_DESCENT']]
            if 0 == list_solver_file.count('x'):
                solver = list_solver[1]
            else:
                solver = list_solver[list_solver_file.index('x')]

            configs = get_auto_configs(ORDERS, self.cfgs)
            configs['response_column'] = self.ds_chars.get_target(self.cfgs['train_dataset_id']),
            configs['family'] = family
            configs['solver'] = solver

            create_model_glm (self.wd, configs)
            print 'Model is built successfully...'

            print 'Start predict model...'
            if not self.cfgs['validate_dataset_id'].strip() == '':
                common.predict_file(self.wd, dict(frame_select = self.cfgs['validate_dataset_id']))
            else:
                common.predict_file(self.wd, dict(frame_select = self.cfgs['train_dataset_id']))
            print 'Predict model is successfully...'

            print '---Getting value after predict model:'
            result_dict['mse'] = common.get_values(self.wd, ['mse'])


            print 'Test case is passed'
            return result_dict

        except Exception as e:
            result_dict['result']= 'FAIL'
            result_dict['message'] =  "Reason Failed: " + str(e.message)
            print 'Test case is falied'
            return result_dict


    def clean_up(self):
        print 'clean up now...'


def unit_test():
    pass


if __name__ == '__main__':
    unit_test()


