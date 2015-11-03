import csv
import subprocess


def load_csv(filename, index_head_row, index_id_column):
    '''
    Load a csv file and return a dict with the first column as dictionary's key.
    To mitigate data input error, raise an exception if same key is found in the csv file.
    '''
    csv_contents = {}

    # First read the headers, then for each row, create an dictionary
    with open(filename, 'rb') as f:
        print "Open File: ", filename
        reader = csv.reader(f, delimiter=',')

        #remove unused head
        for i in range(index_head_row):
            headers = reader.next()

        for row in reader:
            id = ''
            item = {} # an item
            if 'drf' in filename:
                item['testscript'] = 'drf_basic'
                item['classname'] = 'DrfBasic'
            elif 'gbm' in filename:
                item['testscript'] = 'gbm_basic'
                item['classname'] = 'GbmBasic'
            elif 'glm' in filename:
                item['testscript'] = 'glm_basic'
                item['classname'] = 'GlmBasic'
            else:
                item['testscript'] = 'dl_basic'
                item['classname'] = 'DlBasic'

            for hdr, value in zip(headers, row):
                if hdr == headers[index_id_column]:
                    id = value
                else:
                    item[hdr.strip('_')] = value

            # if id in csv_contents:
            #     raise Exception('Duplicate key "%s" found in csv file: %s' % (id, filename))

            csv_contents[id] = item

    return csv_contents


def append_csv(filename, row):
    """
    Open a csv to write a row in append
    """
    with open(filename, 'a') as f:
        f.write('%s\n' % row)


def compare_string(str1, str2):
    """
    :param str1: string
    :param str2: string
    :return: -1 if str1 < str2, 0 if str1 == str2, 1 if str1 > str2
    """

    # validate parameter
    if type(str1) is not str or type(str2) is not str:
        print 'Parameter required a string type'
        raise TypeError('Parameter required a string type')

    result = 0

    if len(str1) > len(str2):
        result = 1
    elif len(str1) < len(str2):
        result = -1
    elif str1 > str2:
        result = 1
    elif str1 < str2:
        result = -1

    return result


def kill_phantomjs(phantomjs_location):
    print 'kill phantomjs server'
    command = "kill $(ps aux | grep '" + phantomjs_location + " --webdriver=65000' | awk '{print $2}')"
    subprocess.Popen(command, shell=True)