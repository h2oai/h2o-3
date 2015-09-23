__author__ = 'thinhnguyen'

from testlibs import common
from testlibs.glm import create_model_glm
from pprint import pprint
from testlibs.glm import ORDERS
from utils.se_functions import get_auto_configs


class GlmBasic:
    def __init__(self, tc_id, configs, additional_configs):
        self.cfgs = configs
        self.add_cfgs = additional_configs
        self.tc_id = tc_id

        self.wd = self.add_cfgs['driver']
        self.ds_chars = self.add_cfgs['dataset_chars']


    def setup(self):
        print self.tc_id

        if self.cfgs['train_dataset_id'] not in self.ds_chars.mylist:
                self.ds_chars.is_import['key'] = False
                if not self.ds_chars.is_import['key']:

                    train_fn = self.ds_chars.get_filepath(self.cfgs['train_dataset_id'])
                    common.import_parse_file(self.wd, dict(file_path = train_fn,
                                                            destination_key = self.cfgs['train_dataset_id']))

                    if not self.cfgs['validate_dataset_id'].strip() == '':
                        validate_fn = self.ds_chars.get_filepath(self.cfgs['validate_dataset_id'])
                        common.import_parse_file(self.wd, dict(file_path = validate_fn,
                                                                destination_key = self.cfgs['validate_dataset_id']))

                    else:
                        common.split_file(self.wd, dict(splitted_train_column = self.cfgs['train_dataset_id_split'],
                                                                    splitted_test_column= self.cfgs['validate_dataset_id_split']))

                    self.ds_chars.is_import['key'] = self.ds_chars.set_imported()
                    self.ds_chars.mylist.append(self.cfgs['train_dataset_id'])


    def test(self):
        print 'Test now. ..'
        result_dict = dict( result = 'PASS', message = 'This tescase is passed', mse= '', auc='',
                            train_dataset_id='', validate_dataset_id='', distribution='',
                            sparse = '')

        try:
            list_family = ['gaussian', 'binomial', 'poisson', 'gamma', 'tweedie']
            list_family_file = [self.cfgs['gaussian'], self.cfgs['binomial'], self.cfgs['poissan'], self.cfgs['gamma'], self.cfgs['tweedie']]
            family = list_family[list_family_file.index('x')]

            list_solver = ['AUTO', 'IRLSM', 'L_BFGS', 'COORDINATE_DESCENT_NAIVE', 'COORDINATE_DESCENT_NAIVE', 'COORDINATE_DESCENT']
            list_solver_file = [self.cfgs['auto'], self.cfgs['IRLSM'], self.cfgs['L_BFGS'], self.cfgs['COORDINATE_DESCENT_NAIVE'], self.cfgs['COORDINATE_DESCENT']]
            solver = list_solver[list_solver_file.index('x')]


            configs = get_auto_configs(ORDERS, self.cfgs)
            configs['response_column'] = self.ds_chars.get_target(self.cfgs['train_dataset_id']),
            configs['family'] = family
            configs['solver'] = solver

            create_model_glm (self.wd, configs)

            if not self.cfgs['validate_dataset_id'].strip() == '':
                common.predict_file(self.wd, dict(frame_select = self.cfgs['validate_dataset_id']))
            else:
                common.predict_file(self.wd, dict(frame_select = self.cfgs['validate_dataset_id_split']))

            result_dict['mse'] = common.get_values(self.wd, ['mse'])
            result_dict['train_dataset_id'] = self.cfgs['train_dataset_id']
            result_dict['validate_dataset_id'] = self.cfgs['validate_dataset_id']
            return result_dict


        except Exception as e:
            result_dict['result']= 'FAIL'
            result_dict['message'] =  "Reason Failed: " + str(e.message)
            return result_dict



    def clean_up(self):
        print 'clean up now...'


def unit_test():
    pass


if __name__ == '__main__':
    unit_test()


