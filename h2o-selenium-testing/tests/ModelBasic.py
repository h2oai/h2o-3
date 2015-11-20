from testlibs import Common

from utils import Constant
from utils import Selenium
from tests import DrfBasic, GbmBasic, GlmBasic, DlBasic

class ModelBasic:

    def __init__(self, tc_id, configs, driver, dataset_chars):
        """
        Init configs for model
        """
        self.cfgs = configs
        self.tc_id = tc_id

        self.wd = driver
        self.ds_chars = dataset_chars
        self.suite_name = configs['suitename']


    def test(self):
        """
        Implement all step to create a perfect model
        """
        result_dict = dict(
                result = Constant.testcase_result_status_pass,
                message = 'This tescase is passed',
                mse = '',
                auc = '',
                train_dataset_id = self.cfgs[Constant.train_dataset_id],
                validate_dataset_id = self.cfgs[Constant.validate_dataset_id],
            )
        print 'Test case %s is running.' % self.tc_id

        print 'Start import train dataset: ', self.cfgs[Constant.train_dataset_id]

        train_fn = self.ds_chars.get_filepath(self.cfgs[Constant.train_dataset_id])
        Common.import_parse_file(self.wd, dict(file_path = train_fn,
                                               destination_key = self.cfgs[Constant.train_dataset_id]),
                                 self.ds_chars, self.cfgs[Constant.train_dataset_id])

        print 'Train dataset is imported successfully.'

        print 'Start import validate dataset: ', self.cfgs[Constant.validate_dataset_id]

        if '' == self.cfgs[Constant.validate_dataset_id].strip():
            print 'This testcase have no validate dataset'
        elif self.ds_chars.is_available(self.cfgs[Constant.validate_dataset_id]):
            validate_fn = self.ds_chars.get_filepath(self.cfgs[Constant.validate_dataset_id])
            print 'validate dataset: ', self.cfgs[Constant.validate_dataset_id]
            Common.import_parse_file(self.wd, dict(file_path = validate_fn,
                                                   destination_key = self.cfgs[Constant.validate_dataset_id]),
                                     self.ds_chars, self.cfgs[Constant.validate_dataset_id])
        else:
            print 'Dataset %s is not available in dataset characteristic' % self.cfgs[Constant.validate_dataset_id]
            print 'Test case %s invalid' % self.tc_id
            raise Exception('Test case invalid')

        print 'Validate dataset %s is imported successfully.' % self.cfgs[Constant.validate_dataset_id]

        try:
            print 'Testsuite is: ', self.suite_name
            if 'drf' in self.suite_name:
                DrfBasic.test(self.wd, self.cfgs, self.ds_chars)
            elif 'gbm' in self.suite_name:
                GbmBasic.test(self.wd, self.cfgs, self.ds_chars)
            elif 'glm' in self.suite_name:
                GlmBasic.test(self.wd, self.cfgs, self.ds_chars)
            elif 'dl' in self.suite_name:
                DlBasic.test(self.wd, self.cfgs, self.ds_chars)
            else:
                error_message = 'Unknow suite name: ' + self.suite_name

                result_dict['result'] = Constant.testcase_result_status_invalid
                result_dict['message'] = error_message

                print error_message
                return result_dict

            # Predict glm model
            if not self.cfgs[Constant.validate_dataset_id].strip() == '':
                Common.predict_model(self.wd, dict(frame_select = self.cfgs[Constant.validate_dataset_id]))
            else:
                Common.predict_model(self.wd, dict(frame_select = self.cfgs[Constant.train_dataset_id]))

            # Get MSE value after predict model
            result_dict['mse'] = Selenium.get_value(self.wd, xpath_key='mse')

            print 'Test case %s is passed.' % self.tc_id
            return result_dict

        except Exception as e:
            result_dict['result'] = Constant.testcase_result_status_fail
            result_dict['message'] = "Reason Failed: " + str(e.message)
            print 'Test case %s is failed' % self.tc_id

            print e.message
            print str(e)
            print e.__doc__

            return result_dict

    def clean_up(self):
        print 'clean up now...'
