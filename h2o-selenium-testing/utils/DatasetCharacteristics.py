__author__ = 'trang.huynh'

import Config
import common

class DatasetCharacteristics:
    def __init__(self, filename = Config.dataset_chars):
        self.filename = filename
        self.ds_chars = self.__load_dataset_characteristics()

    #################################################################
    # public functions
    #################################################################
    def is_available(self, dataset_id):
        return dataset_id in self.ds_chars

    def get_dataset(self):
        return self.ds_chars

    def get_data_of_column(self, dataset_id, column):
        return self.ds_chars[dataset_id][column]

    def get_filepath(self, dataset_id):
        print 'get_filepath with dataset_id: ', dataset_id

        dataset_directory = self.get_data_of_column(dataset_id, 'dataset_directory')

        filepath = Config.file_small_paths
        if 'bigdata' == dataset_directory:
            filepath = Config.file_big_paths

        filepath = filepath + (self.get_data_of_column(dataset_id, 'file_name').strip(' '))
        return filepath

    #################################################################
    # private functions
    #################################################################
    def __load_dataset_characteristics(self):
        '''
        Dataset Characteristics is an CSV file contains all the datasets used for testing
        Due to limitation of CSV file, ';' is used where ',' should be.
        Therefore a conversion is needed when loading data.
        Return sample:
        {'airquality_train1': {'column_names': 'Ozone,Solar.R,Wind,Temp,Month,Day',
                               'column_types': 'numeric,numeric,numeric,numeric,numeric,numeric',
                               'dataset_directory': 'smalldata',
                               'file_name': 'airquality_train1.csv',
                               'target': 'Ozone'},
         ...
        }
        '''
        ds_chars = common.load_csv(Config.test_data % self.filename, 1, 0)

        for chars in ds_chars.itervalues():
            for char_k in chars.keys():
                chars[char_k] = chars[char_k].replace(';', ',')

        return ds_chars


#################################################################
# define final static variable
#################################################################
regex_split_content = ','

data_set_id = 'data_set_id',
dataset_directory = 'dataset_directory',
file_name = 'file_name',
target = 'target'
column_names = 'column_names'
column_types = 'column_types'


