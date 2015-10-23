import sys, os
import csv
import MySQLdb
import traceback

def add_perf_results_to_mr_unit(args):
    pwd = args[1]
    results_dir = args[2]
    perf_results_path = os.path.join(results_dir, "perf.csv")
    if not os.path.exists(perf_results_path): raise(ValueError, "perf.csv does not exist in {}".format(results_dir))

    mr_unit = MySQLdb.connect(host='mr-0x8', user='root', passwd=pwd, db='mr_unit')
    mr_unit.autocommit(False)
    cursor = mr_unit.cursor()
    perf = csv.reader(file(perf_results_path))
    for row in perf:
        try:
            [r.strip() for r in row]
            cursor.execute('INSERT INTO perf(date, build_id, git_hash, git_branch, machine_ip, test_name, start_time, end_time) '
                           'VALUES("{0}", "{1}", "{2}", "{3}", "{4}", "{5}", {6}, {7})'.format(*row))
        except:
            traceback.print_exc()
            mr_unit.rollback()
            assert False, "Failed to add performance results to mr_unit!"
    mr_unit.commit()

if __name__ == '__main__':
    add_perf_results_to_mr_unit(sys.argv)