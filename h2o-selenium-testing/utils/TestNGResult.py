__author__ = 'chaucao'

import xml.etree.ElementTree as ET

from utils import Config, Constant


class TestNGResult:

    def __init__(self, filename = Config.result_filename):
        self.filename = filename
        self.root = ET.Element("testng_results")
        test_element = ET.SubElement(self.root, "test")
        self.class_element = ET.SubElement(test_element, "class", name = 'h2o-selenium.h2o-selenium-testing')
        self.current_test_method_element = 'NA'

    def set_summary_attribute(self, num_skip_tc, num_fail_tc, num_pass_tc):
        total_tc = num_skip_tc + num_fail_tc + num_pass_tc
        self.root.set('skipped', str(num_skip_tc))
        self.root.set('failed', str(num_fail_tc))
        self.root.set('passed', str(num_pass_tc))
        self.root.set('total', str(total_tc))

    def add_test_method(self, status, testcase_id, started_at, finished_at):

        duration = (finished_at - started_at).seconds * 1000

        #set test_method attributes
        test_method_attributes = dict()
        test_method_attributes['name'] = testcase_id
        test_method_attributes['status'] = status
        test_method_attributes['started-at'] = str(started_at.strftime("%Y-%m-%dT%H:%M:%SZ"))
        test_method_attributes['finished-at'] = str(finished_at.strftime("%Y-%m-%dT%H:%M:%SZ"))
        test_method_attributes['duration-ms'] = str(duration)

        self.current_test_method_element = ET.SubElement(self.class_element, "test-method", test_method_attributes)

    def add_params(self, *params):
        if 'NA' == self.current_test_method_element:
            print 'You have to add_test_method function before'
            return

        params_element = ET.SubElement(self.current_test_method_element, 'params')

        #add param list
        index = 0
        for param_text in params:
            param_element = ET.SubElement(params_element, 'param', index = '%s' % index)
            ET.SubElement(param_element, 'value').text = r"<![CDATA[%s]]>" % param_text
            index += 1

    def add_reporter_output(self, *logs):
        if 'NA' == self.current_test_method_element:
            print 'You have to add_test_method function before'
            return

        reporter_output_element = ET.SubElement(self.current_test_method_element, "reporter_output")

        for log in logs:
            ET.SubElement(reporter_output_element, 'line').text = r"<![CDATA[%s]]>" % log

        self.current_test_method_element = 'NA'

    def add_test_method_n_child(self, testcase_id, started_at, finished_at, test_results, log):

        summary_result = 'Testcase %s %s' % (testcase_id, test_results['result'])
        self.add_test_method(test_results['result'], testcase_id, started_at, finished_at)
        self.add_params(testcase_id, test_results[Constant.train_dataset_id], test_results[Constant.validate_dataset_id])
        self.add_reporter_output(log, summary_result)

    def write(self):
        tree = ET.ElementTree(self.root)
        tree.write(self.filename)

        # todo:how to enhance it
        with open(self.filename,'r') as f:
            newlines = []
            for line in f.readlines():
                newlines.append(line.replace('&lt;', '<').replace('&gt;', '>'))

        with open(self.filename, 'w') as f:
            for line in newlines:
                f.write(line)
