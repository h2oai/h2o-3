import csv

from utils import Config
from testlibs import GLM, GBM, DRF, DL


def load(suitename , filename, index_head_row, index_id_column):
    """
    Load a csv file and return a dict with the first column as dictionary's key.
    To mitigate data input error, raise an exception if same key is found in the csv file.
    """
    csv_contents = {}

    # First read the headers, then for each row, create an dictionary
    with open(filename, 'rb') as f:
        print "Open File: ", filename
        reader = csv.reader(f, delimiter=',')

        # remove unused head
        for i in range(index_head_row):
            headers = reader.next()

        for row in reader:
            id = ''
            item = {}
            item['suitename'] = suitename

            for hdr, value in zip(headers, row):
                if hdr == headers[index_id_column]:
                    id = value
                else:
                    item[hdr.strip('_')] = value

            if id in csv_contents:
                raise Exception('Duplicate key "%s" found in csv file: %s' % (id, filename))

            csv_contents[id] = item

    return csv_contents


def load_testsuite(suite_name):
    """ Load testsuite from csv file """
    if 'drf' in suite_name:
        row = DRF.row
        column = DRF.column
    elif 'gbm' in suite_name:
        row = GBM.row
        column = GBM.column
    elif 'glm' in suite_name:
        row = GLM.row
        column = GLM.column
    elif 'dl' in suite_name:
        row = DL.row
        column = DL.column
    else:
        print 'Do not implement for: ', suite_name
        raise Exception('Do not implement for: ', suite_name)

    return load(suite_name, Config.load_csv % suite_name, row, column)