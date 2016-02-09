import sys
import MySQLdb
import traceback
import time

def failure_report(args):
  mr_unit = MySQLdb.connect(host='172.16.2.178', user='root', passwd=args[1], db='mr_unit')
  mr_unit.autocommit(False)
  cursor = mr_unit.cursor()

  # the jenkins job is scheduled to run every morning at 8 am (local). By default, we report the failures starting from
  # 8 pm (local) of the previous day.
  try:
    start_time = args[2]
  except IndexError:
    yesterday = time.localtime(time.time() - 24 * 60 * 60)
    start_time = time.mktime(yesterday) - yesterday.tm_hour*60*60 - yesterday.tm_min*60 - yesterday.tm_sec + 20*60*60

  try:
    end_time = args[3]
  except IndexError:
    end_time = time.time()

  try:
    cursor.execute('select * from perf where (`start_time` > {0} and `end_time` < {1} and '
                   '`pass` = 0);'.format(start_time, end_time))
    failures = cursor.fetchall()
  except:
    cursor.close()
    traceback.print_exc()
    print "Failed to retrieve failures from the perf table in mr_unit database for the period from {0} to {1}"\
      .format(time.strftime('%Y-%m-%d:%H:%M:%S', time.localtime(start_time)),
              time.strftime('%Y-%m-%d:%H:%M:%S', time.localtime(end_time)))
    raise
  cursor.close()

  print "***********************************************************************"
  print "Failures for the period from {0} to {1}".format(time.strftime('%Y-%m-%d:%H:%M:%S', time.localtime(start_time)),
                                                         time.strftime('%Y-%m-%d:%H:%M:%S', time.localtime(end_time)))
  print "***********************************************************************\n"

  for idx, failure in enumerate(failures):
    print '\nFAILURE {0}'.format(idx+1)
    print '------------------------------------------------------------------------------'
    print 'git branch:                   {0}'.format(failure[3])
    print 'git hash:                     {0}'.format(failure[2])
    print 'job name:                     {0}'.format(failure[11])
    print 'build id:                     {0}'.format(failure[1])
    print 'test name:                    {0}'.format(failure[5])
    print 'duration (seconds):           {0}'.format(failure[7] - failure[6])
    print 'machine ip:                   {0}'.format(failure[4])
    print 'operating system:             {0}'.format(failure[10])
    print 'number of cpus:               {0}'.format(failure[9])
    print 'datetime (%Y-%m-%d:%H:%M:%S): {0}'.format(time.strftime('%Y-%m-%d:%H:%M:%S', time.localtime(failure[6])))
    print '------------------------------------------------------------------------------\n'

if __name__ == '__main__':
  failure_report(sys.argv)