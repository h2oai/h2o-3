import sys, os
import csv
import MySQLdb
import traceback

def add_perf_results_to_mr_unit(args):
    mr_unit = MySQLdb.connect(host='mr-0x8', user='root', passwd=args[1], db='mr_unit')
    mr_unit.autocommit(False)
    cursor = mr_unit.cursor()
    try:
        for row in csv.reader(file(os.path.join(args[2], "perf.csv"))):
            row = [r.strip() for r in row]
            row[3] = row[3].split("/")[-1]
            row[8] = "TRUE" if row[8] == "1" else "FALSE"
            cursor.execute('INSERT INTO perf(date, build_id, git_hash, git_branch, machine_ip, test_name, start_time, '
                           'end_time, pass, ncpu, os, job_name) VALUES("{0}", "{1}", "{2}", "{3}", "{4}", "{5}", "{6}"'
                           ', "{7}", {8}, "{9}", "{10}", "{11}")'.format(*row))
        mr_unit.commit()
    except:
        traceback.print_exc()
        mr_unit.rollback()
        assert False, "Failed to add performance results to mr_unit!"

if __name__ == '__main__':
    add_perf_results_to_mr_unit(sys.argv)