import argparse
import re


# Format of backup xml file
testng_report_fn = 'testng-results.xml'
testng_backup_fn = '%s~' % testng_report_fn


# Regular expressions for finding <test-method>...</test-method>
# and all needed values
testmethod_re = '<test-method.*?</test-method>'
testname_re = 'index="0".*?DATA\[(.*?)\]'
reporter_out_re = '<reporter-output>.*?</reporter-output>'

# subtitutions
testname_find_re = 'name=".*?"'
testname_format = 'name="%s"'

status_find_re = 'status=".*?"'
invalid_status = 'status="INVALID"'
invalid_key = '[INVALID]' #


def parse_argument():
    parser = argparse.ArgumentParser(description='Update TestNG report with INVALID status and test case name')
    parser.add_argument('-d', dest = 'testng_dir', help='Directory storing testNG test result', required = True)

    return  parser.parse_args()


def update_testinfo(content):
    '''
    . update test with real test case name
    . change status from FAILED to INVALID for those invalid input cases
    '''
    testname = re.search(testname_re, content, re.S).group(1)
    reporter_out = re.search(reporter_out_re, content, re.S).group(0)
    
    content = re.sub(testname_find_re, testname_format % testname, content)

    if invalid_key in reporter_out:
        content = re.sub(status_find_re, invalid_status, content)
        
    return content


def read_and_backup_test_report(testng_dir):
    '''
    . read all content of testNG report to a buffer
    . copy all of this to a back up file for comparision
    '''
    content = ''

    full_filepath_format = '%s%s' if testng_dir.endswith('/') else '%s/%s'
    testng_fn = full_filepath_format % (testng_dir, testng_report_fn)
    bkup_fn = full_filepath_format % (testng_dir, testng_backup_fn)
    
    print 'Reading from TestNG report file: %s' % testng_fn
    with open(testng_fn, 'r') as f:
        content = f.read()
    
    with open(bkup_fn, 'w') as f:
        f.write(content)

    return (testng_fn, content)


if __name__ == '__main__':
    testng_fn  = ''
    testng_bkup_fn = ''
    
    content = ''
    output = ''
    end = 0

    args = parse_argument()
    
    testng_fn, content = read_and_backup_test_report(args.testng_dir)

    for testmethod_m in re.finditer(testmethod_re, content, re.S):
        testmethod = testmethod_m.group(0)
        
        output += content[end: testmethod_m.start()]
        end = testmethod_m.end()

        try:
            output += update_testinfo(testmethod)
        except:
            output += testmethod

        
    output += content[end:]

    with open(testng_fn, 'w') as f:
        f.write(output)

