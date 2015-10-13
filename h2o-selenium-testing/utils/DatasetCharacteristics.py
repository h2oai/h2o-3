__author__ = 'trang.huynh'

import config
import common

class DatasetCharacteristics:
    def __init__(self, filename):
        self.filename = filename
        self.ds_chars = common.load_dataset_characteristics(filename)


    def get_filepath(self, key):
        filepath = config.file__small_paths

        if 'bigdata' == self.ds_chars[key]['dataset_directory']:
            filepath = config.file__big_paths

        return filepath + (self.ds_chars[key]['file_name'])


    def get_target(self, key):
        return self.ds_chars[key]['target']


    def is_available(self, dataset_id):
        return dataset_id in self.ds_chars

    mylist= []

    is_import = dict(
        key = False,
    )


    def set_imported(self):
        if not self.is_import['key']:
            return  True
        return  False
