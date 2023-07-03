#!/usr/bin/python

#
# This is a really old experimental script that was probably never used, 
# but I'm adding for posterity.
#
# Now that -proxy capability was added properly by MichalK, this will
# probably never be needed.
#

import argparse
import subprocess
import os
import tempfile
import shutil
import signal
import socket
import thread
import time
import uuid


g_runner = None


def swallow(x):
    return x


def print_barrier():
    print("----------------------------------------------------------------------")


def server(*settings):
    try:
        dock_socket = settings[0]
        while True:
            client_socket = dock_socket.accept()[0]
            server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            server_socket.connect((settings[1], int(settings[2])))
            thread.start_new_thread(forward, (client_socket, server_socket))
            thread.start_new_thread(forward, (server_socket, client_socket))
    finally:
        thread.start_new_thread(server, settings)


def forward(source, destination):
    string = ' '
    while string:
        string = source.recv(1024*1024)
        if string:
            destination.sendall(string)
        else:
            try:
                source.shutdown(socket.SHUT_RD)
            except:
                pass

            try:
                destination.shutdown(socket.SHUT_WR)
            except:
                pass


class H2OCloud:
    def __init__(self, abspath_tempdir, driver, nodes_per_cloud, xmx, output_dir):
        self.ip = None
        self.port = None
        self.job_id = None
        self.driver = driver
        self.nodes_per_cloud = nodes_per_cloud
        self.xmx = xmx
        self.output_dir = output_dir
        self.notify_file = os.path.join(abspath_tempdir, "notify.txt")

    def start(self):
        print_barrier()
        cmd = ['hadoop', 'jar', self.driver,
               '-n', str(self.nodes_per_cloud),
               '-mapperXmx', self.xmx,
               '-output', self.output_dir,
               '-notify', self.notify_file,
               '-disown']
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
            print(e)
        finally:
            self.job_id = None


g_handling_signal = False

g_keep_running = True


def signal_handler(signum, stackframe):
    global g_runner
    global g_handling_signal
    global g_keep_running

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
    g_keep_running = False


def main():
    global g_runner
    global g_keep_running

    parser = argparse.ArgumentParser()
    parser.add_argument("-driver", "--driver", help="Path to h2odriver.jar", default="./h2odriver.jar")
    parser.add_argument("-n", "-nodes", "--nodes", help="Number of H2O nodes", required=True, type=int)
    parser.add_argument("-mapperXmx", "--mapperXmx", help="Size of each H2O node", required=True)
    parser.add_argument("-output", "--output", help="HDFS temp output dir")
    parser.add_argument("-port", "--port", help="Local incoming port listening for connection", required=True, type=int)
    args = parser.parse_args()
    if args.output is None:
        args.output = 'h2o-tmp/' + str(uuid.uuid4())
    print_barrier()
    print("Path to h2odriver:  " + args.driver)
    print("Number of nodes:    " + str(args.nodes))
    print("Size of each node:  " + args.mapperXmx)
    print("HDFS output dir:    " + args.output)
    print("Local port:         " + str(args.port))

    abspath_tempdir = tempfile.mkdtemp()
    g_runner = H2OCloud(abspath_tempdir, args.driver, args.nodes, args.mapperXmx, args.output)

    # Handle killing the runner.
    signal.signal(signal.SIGINT, signal_handler)
    signal.signal(signal.SIGTERM, signal_handler)

    try:
        dock_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        dock_socket.bind(('', args.port))
        dock_socket.listen(5)

        g_runner.start()
        settings = (dock_socket, g_runner.ip, g_runner.port)
        thread.start_new_thread(server, settings)

        print_barrier()
        print("")
        print("Listening on port " + str(args.port) + " and forwarding...")
        print("")

        # wait for <ctrl-c>
        while g_keep_running:
            time.sleep(1)
    except Exception, e:
        print(e)

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
