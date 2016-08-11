#!/usr/bin/python

import argparse
import subprocess
import os
import tempfile
import shutil
import signal


g_runner = None


def swallow(x):
    return x


def print_barrier():
    print("----------------------------------------------------------------------")


def is_flow(file_name):
    if file_name.endswith(".flow"):
        return True

    return False


class H2OCloud:
    def __init__(self, abspath_tempdir, driver, nodes_per_cloud, xmx, output_dir, test_ssl):
        self.ip = None
        self.port = None
        self.job_id = None
        self.driver = driver
        self.nodes_per_cloud = nodes_per_cloud
        self.xmx = xmx
        self.output_dir = output_dir
        self.notify_file = os.path.join(abspath_tempdir, "notify.txt")
        self.test_ssl = test_ssl

    def start(self):
        print_barrier()
        cmd = ['hadoop', 'jar', self.driver,
               '-n', str(self.nodes_per_cloud),
               '-mapperXmx', self.xmx,
               '-output', self.output_dir,
               '-notify', self.notify_file,
               '-disown']

        if self.test_ssl:
            cmd.append("-internal_security_conf")

        print("+ CMD: " + str(cmd))
        returncode = subprocess.call(cmd)

        f = None
        try:
            f = open(self.notify_file)
            ip_port = f.readline().rstrip()
            self.ip = ip_port.split(":")[0]
            self.port = ip_port.split(":")[1]
            self.job_id = f.readline().rstrip()
            f.close()
        finally:
            if f is not None:
                f.close()

        if returncode != 0:
            print("")
            print("")
            raise Exception("Failed to start H2O application instance")

    def stop(self):
        if self.job_id is None:
            return

        try:
            print_barrier()
            cmd = ['hadoop', 'job', '-kill', self.job_id]
            print("+ CMD: " + str(cmd))
            subprocess.check_call(cmd)
        except Exception, e:
            print e
        finally:
            self.job_id = None


g_handling_signal = False


def signal_handler(signum, stackframe):
    global g_runner
    global g_handling_signal

    swallow(stackframe)

    if g_handling_signal:
        return

    g_handling_signal = True

    print("")
    print_barrier()
    print("")
    print("SIGNAL CAUGHT (" + str(signum) + ").  TEARING DOWN CLOUD.")
    print("")
    print_barrier()
    g_runner.stop()


def main():
    global g_runner

    parser = argparse.ArgumentParser()
    parser.add_argument("-driver", "--driver", help="Path to h2odriver.jar", default="./h2odriver.jar")
    parser.add_argument("-n", "-nodes", "--nodes", help="Number of H2O nodes", required=True, type=int)
    parser.add_argument("-mapperXmx", "--mapperXmx", help="Size of each H2O node", required=True)
    parser.add_argument("-script", "--script", help="Name of script to run", required=True)
    parser.add_argument("-output", "--output", help="HDFS temp output dir", required=True)
    parser.add_argument("-test_ssl", "--test_ssl", help="Testing with SSL enabled", required=True)
    args = parser.parse_args()
    print_barrier()
    print ("Path to h2odriver:  " + args.driver)
    print ("Number of nodes:    " + str(args.nodes))
    print ("Size of each node:  " + args.mapperXmx)
    print ("Script to run:      " + args.script)
    print ("HDFS output dir:    " + args.output)
    print ("SSL on:             " + args.test_ssl)

    abspath_tempdir = tempfile.mkdtemp()
    g_runner = H2OCloud(abspath_tempdir, args.driver, args.nodes, args.mapperXmx, args.output, args.test_ssl)

    # Handle killing the runner.
    signal.signal(signal.SIGINT, signal_handler)
    signal.signal(signal.SIGTERM, signal_handler)

    try:
        g_runner.start()

        if is_flow(args.script):
            cmd = ['phantomjs', 'run-flow.js',
                   '--host', g_runner.ip + ":" + str(g_runner.port),
                   '--flow', args.script]
            print("+ CMD: " + str(cmd))
            subprocess.check_call(cmd)
    except Exception, e:
        print e

    try:
        g_runner.stop()
    except Exception, ignore:
        swallow(ignore)

    try:
        print_barrier()
        print("Removing temp dir " + abspath_tempdir)
        shutil.rmtree(abspath_tempdir)
    except Exception, ignore:
        swallow(ignore)


if __name__ == "__main__":
    main()
