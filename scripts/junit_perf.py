import xml.etree.ElementTree as ET
import sys, os
import MySQLdb
import traceback
import os.path

def add_junit_perf_results_to_mr_unit(args):
  mr_unit = MySQLdb.connect(host='172.16.2.178', user='root', passwd=args[1], db='mr_unit')
  mr_unit.autocommit(False)
  cursor = mr_unit.cursor()
  try:
    if not os.path.isdir(args[2]) or len(os.listdir(args[2])) == 0:
      print "{0} does not exist or is empty, so junit_perf.py has nothing to do here. Maybe ./gradlew build failed " \
            "before it created this directory and the junit test results".format(args[2])
    else:
      for junit_suite_xml in os.listdir(args[2]):
        xml = ET.parse(os.path.join(args[2],junit_suite_xml))
        testsuite = xml.getroot()
        class_name = testsuite.attrib['name']
        ncpu = testsuite.attrib['ncpu']
        timestamp = testsuite.attrib['timestamp']
        hostname = testsuite.attrib['hostname']
        for properties in testsuite.findall('properties'):
          for property in properties.findall('property'):
            if property.attrib['name'] == 'os.name': os_name = property.attrib['value']
            if property.attrib['name'] == 'git.branch': git_branch = property.attrib['value']
            if property.attrib['name'] == 'git.commit': git_commit = property.attrib['value']
            if property.attrib['name'] == 'build.id': build_id = property.attrib['value']
            if property.attrib['name'] == 'job.name': job_name = property.attrib['value']
        for testcase in testsuite.findall('testcase'):
          passed = 0 if len(testcase.findall('failure')) > 0 else 1
          test_name = testcase.attrib['name']
          duration = testcase.attrib['time']
          cursor.execute('INSERT INTO junit(timestamp, build_id, git_hash, git_branch, hostname, test_name, '
                         'class_name, duration, pass, ncpu, os, job_name) VALUES("{0}", "{1}", "{2}", "{3}", '
                         '"{4}", "{5}", "{6}", "{7}", "{8}", "{9}", "{10}", "{11}")'
                         .format(timestamp, build_id, git_commit, git_branch, hostname, test_name, class_name, duration,
                                 passed, ncpu, os_name, job_name))
        mr_unit.commit()
  except:
    traceback.print_exc()
    mr_unit.rollback()
    print "Failed to add performance results to junit table in mr_unit database!"
    raise

if __name__ == '__main__':
  add_junit_perf_results_to_mr_unit(sys.argv)