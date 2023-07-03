import sys
import os
sys.path.insert(1, os.path.join("..", "..", "..", "h2o-py"))
from tests import pyunit_utils
import h2o
import zipfile
import shutil

log_level_suffixes = ["-1-trace.log",
                      "-2-debug.log",
                      "-3-info.log",
                      "-4-warn.log",
                      "-5-error.log",
                      "-6-fatal.log",
                      "-httpd.log"]

log_level_suffixes_positions = {"TRACE" : 0,
                                "DEBUG" : 1,
                                "INFO"  : 2,
                                "WARN"  : 3,
                                "ERROR" : 4,
                                "FATAL" : 5,
                                "HTTPD" : 6}

def log_files_prefix(node_log_zip_name):
    node_ip = node_log_zip_name[:-4].split("_")[1]
    node_port = node_log_zip_name[:-4].split("_")[2]
    return "h2o_" + node_ip + "_" + node_port


def log_files_expected_suffixes(log_level):
    pos = log_level_suffixes_positions[log_level]
    return log_level_suffixes[pos:]

def expected_log_files(all_nodes_log_dir, all_nodes_log_zip_files, log_level):
    expected_files = []
    for node_log_name in all_nodes_log_zip_files:
        log_file_dir = path_to_single_node_logs(all_nodes_log_dir, node_log_name)
        for suffix in log_files_expected_suffixes(log_level):
            expected_files.append(log_file_dir + "/" + log_files_prefix(node_log_name) + suffix)
    return expected_files

def path_to_single_node_logs(nodes_log_dir, node_log_name):
    zip_ref = zipfile.ZipFile(nodes_log_dir + "/" + node_log_name, 'r')
    node_log_dir = nodes_log_dir + "/" + node_log_name + "_logs"
    zip_ref.extractall(node_log_dir)
    zip_ref.close()
    node_log_dir_extracted = node_log_dir
    while not contains_log_file(node_log_dir_extracted):
        node_log_dir_extracted = node_log_dir_extracted + "/" + os.listdir(node_log_dir_extracted)[0]
    return node_log_dir_extracted

def contains_log_file(dir):
    for f in os.listdir(dir):
        if os.path.isfile(dir + "/" + f) and f.endswith(".log"):
            return True
    return False

def hadoop_download_logs():
    zip_file = h2o.download_all_logs()
    extracted_dir = os.path.abspath("extracted")
    print("Logs extracted into: " + extracted_dir)
    if os.path.isdir(extracted_dir):
        shutil.rmtree(extracted_dir)
    zip_ref = zipfile.ZipFile(zip_file, 'r')
    zip_ref.extractall(extracted_dir)
    zip_ref.close()
    nodes_log_dir = extracted_dir + "/" + os.listdir(extracted_dir)[0]
    nodes_log_file_names = os.listdir(nodes_log_dir)

    for f in expected_log_files(nodes_log_dir, nodes_log_file_names, "INFO"):
        print("Checking if file " + f + " exists")
        # check that all expected files exist
        assert os.path.isfile(f)

if __name__ == "__main__":
    pyunit_utils.standalone_test(hadoop_download_logs)
else:
    hadoop_download_logs()
