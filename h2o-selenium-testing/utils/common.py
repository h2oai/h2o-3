import csv
import config
import xml.etree.cElementTree as ET


def load_csv(filename):
    '''
    Load a csv file and return a dict with the first column as dictionary's key.
    To mitigate data input error, raise an exception if same key is found in the csv file.
    '''
    csv_contents = {}

    # First read the headers, then for each row, create an dictionary
    with open(filename, 'rb') as f:
        reader = csv.reader(f)
        headers = reader.next()

        for row in reader:
            id = ''
            item = {} # an item

            for hdr, value in zip(headers, row):
                if hdr == headers[0]:
                    id = value
                else:
                    item[hdr] = value

            if id in csv_contents:
                raise Exception('Duplicate key "%s" found in csv file: %s' % (id, filename))

            csv_contents[id] = item

    return csv_contents


def append_csv(filename, row):
    '''
    Open a csv to write a row in append mo
    de
    '''
    with open(filename, 'a') as f:
        f.write('%s\n' % row)


def append_xml(filename, root):
    tree = ET.ElementTree(root)
    tree.write(filename)

    with open(filename,'r') as f:
        newlines = []
        for line in f.readlines():
            newlines.append(line.replace('&lt;', '<').replace('&gt;', '>'))

    with open(filename, 'w') as f:
        for line in newlines:
            f.write(line)


def load_dataset_characteristics(filename):
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
    ds_chars = load_csv(config.test_data % filename)

    for chars in ds_chars.itervalues():
        for char_k in chars.keys():
            chars[char_k] = chars[char_k].replace(';', ',')

    return ds_chars


class DatasetCharacteristics:
    def __init__(self, filename):
        self.filename = filename
        self.ds_chars = load_dataset_characteristics(filename)


    def get_filepath(self, key):
        filepath = config.file__small_paths

        if 'bigdata' == self.ds_chars[key]['dataset_directory']:
            filepath = config.file__big_paths

        return filepath + (self.ds_chars[key]['file_name'])


    def get_target(self, key):
        return self.ds_chars[key]['target']

    mylist= []


    is_import = dict(
        key = False,
    )


    def set_imported(self):
        if not self.is_import['key']:
            return  True
        return  False



def unit_test():
    from pprint import pprint as pp
    print

    # test1: load deep_learning testcases and print out to the console
    pp(load_csv(r'../test_data/deep_learning.csv'))


if __name__ == '__main__':
    unit_test()
