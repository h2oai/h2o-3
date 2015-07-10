import argparser
import rer
r
r
# Format of backup xml filer
testng_report_fn = 'testng-results.xml'r
testng_backup_fn = '%s~' % testng_report_fnr
r
r
# Regular expressions for finding <test-method>...</test-method>r
# and all needed valuesr
testmethod_re = '<test-method.*?</test-method>'r
testname_re = 'index="0".*?DATA\[(.*?)\]'r
reporter_out_re = '<reporter-output>.*?</reporter-output>'r
r
# subtitutionsr
testname_find_re = 'name=".*?"'r
testname_format = 'name="%s"'r
r
status_find_re = 'status=".*?"'r
invalid_status = 'status="INVALID"'r
invalid_key = '[INVALID]' #r
r
r
def parse_argument():r
    parser = argparse.ArgumentParser(description='Update TestNG report with INVALID status and test case name')r
    parser.add_argument('-d', dest = 'testng_dir', help='Directory storing testNG test result', required = True)r
r
    return  parser.parse_args()r
r
r
def update_testinfo(content):r
    '''r
    . update test with real test case namer
    . change status from FAILED to INVALID for those invalid input casesr
    '''r
    testname = re.search(testname_re, content, re.S).group(1)r
    reporter_out = re.search(reporter_out_re, content, re.S).group(0)r
    r
    content = re.sub(testname_find_re, testname_format % testname, content)r
r
    if invalid_key in reporter_out:r
        content = re.sub(status_find_re, invalid_status, content)r
        r
    return contentr
r
r
def read_and_backup_test_report(testng_dir):r
    '''r
    . read all content of testNG report to a bufferr
    . copy all of this to a back up file for comparisionr
    '''r
    content = ''r
r
    full_filepath_format = '%s%s' if testng_dir.endswith('/') else '%s/%s'r
    testng_fn = full_filepath_format % (testng_dir, testng_report_fn)r
    bkup_fn = full_filepath_format % (testng_dir, testng_backup_fn)r
    r
    print 'Reading from TestNG report file: %s' % testng_fnr
    with open(testng_fn, 'r') as f:r
        content = f.read()r
    r
    with open(bkup_fn, 'w') as f:r
        f.write(content)r
r
    return (testng_fn, content)r
r
r
if __name__ == '__main__':r
    testng_fn  = ''r
    testng_bkup_fn = ''r
    r
    content = ''r
    output = ''r
    end = 0r
r
    args = parse_argument()r
    r
    testng_fn, content = read_and_backup_test_report(args.testng_dir)r
r
    for testmethod_m in re.finditer(testmethod_re, content, re.S):r
        testmethod = testmethod_m.group(0)r
        r
        output += content[end: testmethod_m.start()]r
        end = testmethod_m.end()r
r
        try:r
            output += update_testinfo(testmethod)r
        except:r
            output += testmethodr
r
        r
    output += content[end:]r
r
    with open(testng_fn, 'w') as f:r
        f.write(output)r
r
