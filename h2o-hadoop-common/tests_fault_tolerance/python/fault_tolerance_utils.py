import os
import subprocess
from subprocess import PIPE, STDOUT, CalledProcessError


def get_workdir():
    return os.getenv("HDFS_WORKSPACE")


def get_script_path(env_var):
    return os.getenv("H2O_HOME") + "/" + os.getenv(env_var)


def start_cluster(name):
    script = get_script_path("H2O_START_SCRIPT")
    notify_file = "notify_" + name
    driver_log_file = "driver_" + name + ".log"
    clouding_dir = get_workdir() + "_clouding_" + name
    job_name = os.getenv("H2O_JOB_NAME") + "_" + name
    args = [
        script,
        "--cluster-name", name,
        "--clouding-dir", clouding_dir,
        "--notify-file", notify_file,
        "--driver-log-file", driver_log_file,
        "--hadoop-version", os.getenv("H2O_HADOOP"),
        "--job-name", job_name,
        "--nodes", "3", "--xmx", "8G",
        "--disown"
    ]
    notify_file_path = os.getenv("H2O_HOME") + "/" + notify_file
    if os.path.exists(notify_file_path):
        os.remove(notify_file_path)
    run_script(args)
    with open(notify_file_path) as f:
        cluster_url = f.readline()
    return "http://" + cluster_url.rstrip()


def stop_cluster(name):
    script = get_script_path("H2O_KILL_SCRIPT")
    notify_file = "notify_" + name
    driver_log_file = "driver_%s.log" % name
    yarn_logs_file = "yarn_%s.log" % name
    args = [
        script,
        "--notify-file", notify_file,
        "--driver-log-file", driver_log_file,
        "--yarn-logs-file", yarn_logs_file
    ]
    run_script(args)


def run_script(args):
    try:
        result = subprocess.run(
            args, cwd=os.getenv("H2O_HOME"), 
            stdout=PIPE, stderr=STDOUT, 
            check=True, universal_newlines=True
        )
        print(args[0] + " script output:")
        print("--------------------")
        print(result.stdout)
        print("--------------------")
    except CalledProcessError as err:
        print(args[0] + " script failed:")
        print("--------------------")
        print(err.stdout)
        print("--------------------")
