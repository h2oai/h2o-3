import sys, os
import csv
import MySQLdb
import traceback
import os.path

def add_perf_results_to_mr_unit(args):
    mr_unit = MySQLdb.connect(host='mr-0x8', user='root', passwd=args[1], db='mr_unit')
    mr_unit.autocommit(False)
    cursor = mr_unit.cursor()
    try:
        perf_csv_name = os.path.join(args[2], "perf.csv")
        if os.path.isfile(perf_csv_name):
            for row in csv.reader(file(perf_csv_name)):
                row = [r.strip() for r in row]
                row[3] = row[3].split("/")[-1]
                cursor.execute('INSERT INTO perf(date, build_id, git_hash, git_branch, machine_ip, test_name, '
                               'start_time, end_time, pass, ncpu, os, job_name) VALUES("{0}", "{1}", "{2}", "{3}", '
                               '"{4}", "{5}", "{6}", "{7}", "{8}", "{9}", "{10}", "{11}")'.format(*row))
            mr_unit.commit()
        else:
            print "perf.csv does not exist in {0}, so mr_unit.py has nothing to do here. Maybe run.py failed before " \
                  "it created this file".format(perf_csv_name)
    except:
        traceback.print_exc()
        mr_unit.rollback()
        print "Failed to add performance results to perf table in mr_unit database!"
        raise

if __name__ == '__main__':
    add_perf_results_to_mr_unit(sys.argv)