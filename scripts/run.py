#!/usr/bin/env python
# -*-  encoding: utf-8  -*-
"""
Test harness.

:copyright: (c) 2016 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""
import sys
import os
import shutil
import signal
import time
import random
import getpass
import re
import subprocess
import requests
import socket
import multiprocessing
import platform

if sys.version_info[0] < 3:
    # noinspection PyPep8Naming
    import ConfigParser as configparser
else:
    import configparser



def is_rdemo(file_name):
    """
    Return True if file_name matches a regexp for an R demo.  False otherwise.
    :param file_name: file to test
    """
    packaged_demos = ["h2o.anomaly.R", "h2o.deeplearning.R", "h2o.gbm.R", "h2o.glm.R", "h2o.glrm.R", "h2o.kmeans.R",
                      "h2o.naiveBayes.R", "h2o.prcomp.R", "h2o.randomForest.R"]
    if file_name in packaged_demos: return True
    if re.match("^rdemo.*\.(r|R|ipynb)$", file_name): return True
    return False


def is_runit(file_name):
    """
    Return True if file_name matches a regexp for an R unit test.  False otherwise.
    :param file_name: file to test
    """
    if file_name == "h2o-runit.R": return False
    if re.match("^runit.*\.[rR]$", file_name): return True
    return False


def is_rbooklet(file_name):
    """
    Return True if file_name matches a regexp for an R booklet.  False otherwise.
    :param file_name: file to test
    """
    if re.match("^rbooklet.*\.[rR]$", file_name): return True
    return False


def is_pydemo(file_name):
    """
    Return True if file_name matches a regexp for a python demo.  False otherwise.
    :param file_name: file to test
    """
    if re.match("^pydemo.*\.py$", file_name): return True
    return False


def is_ipython_notebook(file_name):
    """
    Return True if file_name matches a regexp for an ipython notebook.  False otherwise.
    :param file_name: file to test
    """
    if (not re.match("^.*checkpoint\.ipynb$", file_name)) and re.match("^.*\.ipynb$", file_name): return True
    return False


def is_pyunit(file_name):
    """
    Return True if file_name matches a regexp for a python unit test.  False otherwise.
    :param file_name: file to test
    """
    if re.match("^pyunit.*\.py$", file_name): return True
    return False


def is_pybooklet(file_name):
    """
    Return True if file_name matches a regexp for a python unit test.  False otherwise.
    :param file_name: file to test
    """
    if re.match("^pybooklet.*\.py$", file_name): return True
    return False


def is_gradle_build_python_test(file_name):
    """
    Return True if file_name matches a regexp for on of the python test run during gradle build.  False otherwise.
    :param file_name: file to test
    """
    return file_name in ["gen_all.py", "test_gbm_prostate.py", "test_rest_api.py"]


def is_javascript_test_file(file_name):
    """
    Return True if file_name matches a regexp for a javascript test.  False otherwise.
    :param file_name: file to test
    """
    if re.match("^.*test.*\.js$", file_name): return True
    return False


'''
function grab_java_message() will look through the java text output and try to extract the
java messages from Java side.
'''


def grab_java_message(node_list, curr_testname):
    """scan through the java output text and extract the java messages related to running
    test specified in curr_testname.
    Parameters
    ----------
    :param node_list:  list of H2O nodes
      List of H2o nodes associated with a H2OCloud that are performing the test specified in curr_testname.
    :param curr_testname: str
      Store the unit test name (can be R unit or Py unit) that has been completed and failed.
    :return: a string object that is either empty or the java messages that associated with the test in curr_testname.
     The java messages can usually be found in one of the java_*_0.out.txt
    """

    global g_java_start_text  # contains text that describe the start of a unit test.

    java_messages = ""
    start_test = False  # denote when the current test was found in the java_*_0.out.txt file

    # grab each java file and try to grab the java messages associated with curr_testname
    for each_node in node_list:
        java_filename = each_node.output_file_name  # find the java_*_0.out.txt file
        if os.path.isfile(java_filename):
            java_file = open(java_filename, 'r')
            for each_line in java_file:
                if g_java_start_text in each_line:
                    start_str, found, end_str = each_line.partition(g_java_start_text)

                    if len(found) > 0:  # a new test is being started.
                        current_testname = end_str.strip()  # grab the test name and check if it is curr_testname
                        if current_testname == curr_testname:
                            # found the line starting with current test.  Grab everything now
                            start_test = True  # found text in java_*_0.out.txt that describe curr_testname

                            # add header to make JAVA messages visible.
                            java_messages += "\n\n**********************************************************\n"
                            java_messages += "**********************************************************\n"
                            java_messages += "JAVA Messages\n"
                            java_messages += "**********************************************************\n"
                            java_messages += "**********************************************************\n\n"


                        else:
                            # found a differnt test than our curr_testname.  We are done!
                            if start_test:
                                # in the middle of curr_testname but found a new test starting, can quit now.
                                break

                # store java message associated with curr_testname into java_messages
                if start_test:
                    java_messages += each_line

            java_file.close()  # finished finding java messages

        if start_test:
            # found java message associate with our test already. No need to continue the loop.
            break

    return java_messages


class H2OUseCloudNode(object):
    """
    A class representing one node in an H2O cloud which was specified by the user.
    Don't try to build or tear down this kind of node.

    use_ip: The given ip of the cloud.
    use_port: The given port of the cloud.
    """

    def __init__(self, use_ip, use_port):
        self.use_ip = use_ip
        self.use_port = use_port

    def start(self):
        """Not implemented."""

    def stop(self):
        """Not implemented."""

    def terminate(self):
        """Not implemented."""

    def get_ip(self):
        """Cloud's IP-address."""
        return self.use_ip

    def get_port(self):
        """Cloud's port number."""
        return self.use_port


class H2OUseCloud(object):
    """
    A class representing an H2O clouds which was specified by the user.
    Don't try to build or tear down this kind of cloud.
    """

    def __init__(self, cloud_num, use_ip, use_port):
        self.cloud_num = cloud_num
        self.use_ip = use_ip
        self.use_port = use_port

        self.nodes = []
        node = H2OUseCloudNode(self.use_ip, self.use_port)
        self.nodes.append(node)

    def start(self):
        """Not implemented."""

    def wait_for_cloud_to_be_up(self):
        """Not implemented."""

    def stop(self):
        """Not implemented."""

    def terminate(self):
        """Not implemented."""

    def get_ip(self):
        """Cloud's IP-address."""
        node = self.nodes[0]
        return node.get_ip()

    def get_port(self):
        """Cloud's port number."""
        node = self.nodes[0]
        return node.get_port()


class H2OCloudNode(object):
    """
    A class representing one node in an H2O cloud.
    Note that the base_port is only a request for H2O.
    H2O may choose to ignore our request and pick any port it likes.
    So we have to scrape the real port number from stdout as part of cloud startup.

    port: The actual port chosen at run time.
    pid: The process id of the node.
    output_file_name: Where stdout and stderr go.  They are merged.
    child: subprocess.Popen object.
    terminated: Only from a signal.  Not normal shutdown.
    """

    def __init__(self, is_client, cloud_num, nodes_per_cloud, node_num, cloud_name, h2o_jar, ip, base_port,
                 xmx, cp, output_dir, test_ssl, ldap_config_path, jvm_opts):
        """
        Create a node in a cloud.

        :param is_client: Whether this node is an H2O client node (vs a worker node) or not.
        :param cloud_num: Dense 0-based cloud index number.
        :param nodes_per_cloud: How many H2O java instances are in a cloud.  Clouds are symmetric.
        :param node_num: This node's dense 0-based node index number.
        :param cloud_name: The H2O -name command-line argument.
        :param h2o_jar: Path to H2O jar file.
        :param base_port: The starting port number we are trying to get our nodes to listen on.
        :param xmx: Java memory parameter.
        :param cp: Java classpath parameter.
        :param output_dir: The directory where we can create an output file for this process.
        :param ldap_config_path: path to LDAP config, if none, no LDAP will be used.
        :param jvm_opts: str with additional JVM options.
        :return The node object.
        """
        self.is_client = is_client
        self.cloud_num = cloud_num
        self.nodes_per_cloud = nodes_per_cloud
        self.node_num = node_num
        self.cloud_name = cloud_name
        self.h2o_jar = h2o_jar
        self.ip = ip
        self.base_port = base_port
        self.xmx = xmx
        self.cp = cp
        self.output_dir = output_dir
        self.ldap_config_path = ldap_config_path
        self.jvm_opts = jvm_opts

        self.port = -1
        self.pid = -1
        self.output_file_name = ""
        self.child = None
        self.terminated = False

        self.test_ssl = test_ssl

        # Choose my base port number here.  All math is done here.  Every node has the same
        # base_port and calculates it's own my_base_port.
        ports_per_node = 2
        self.my_base_port = \
            self.base_port + \
            (self.cloud_num * self.nodes_per_cloud * ports_per_node) + \
            (self.node_num * ports_per_node)

    def start(self):
        """
        Start one node of H2O.
        (Stash away the self.child and self.pid internally here.)

        :return none
        """

        # there is no hdfs currently in ec2, except s3n/hdfs
        # the core-site.xml provides s3n info
        # it's possible that we can just always hardware the hdfs version
        # to match the cdh3 cluster we're hard-wiring tests to
        # i.e. it won't make s3n/s3 break on ec2

        if self.is_client:
            main_class = "water.H2OClientApp"
        else:
            main_class = "water.H2OApp"

        if "JAVA_HOME" in os.environ:
            java = os.environ["JAVA_HOME"] + "/bin/java"
        else:
            java = "java"
        classpath_sep = ";" if sys.platform == "win32" else ":"
        classpath = self.h2o_jar if self.cp == "" else self.h2o_jar + classpath_sep + self.cp
        
        cmd = [java,
               # "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005",
               "-Xmx" + self.xmx,
               "-ea"]
        if self.jvm_opts is not None:
            cmd += [self.jvm_opts]
        cmd += ["-cp", classpath,
               main_class,
               "-name", self.cloud_name,
               "-baseport", str(self.my_base_port),
               "-ga_opt_out"]

        if self.ldap_config_path is not None:
            cmd.append('-login_conf')
            cmd.append(self.ldap_config_path)
            cmd.append('-ldap_login')

        # If the jacoco flag was included, then modify cmd to generate coverage
        # data using the jacoco agent
        if g_jacoco_include:
            root_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
            agent_dir = os.path.join(root_dir, "jacoco", "jacocoagent.jar")
            jresults_dir = os.path.join(self.output_dir, "jacoco")
            if not os.path.exists(jresults_dir):
                os.mkdir(jresults_dir)
            jresults_dir = os.path.join(jresults_dir, "{cloud}_{node}".format(cloud=self.cloud_num, node=self.node_num))
            jacoco = "-javaagent:" + agent_dir + "=destfile=" + \
                     os.path.join(jresults_dir, "{cloud}_{node}.exec".format(cloud=self.cloud_num, node=self.node_num))
            opt0, opt1 = g_jacoco_options
            if opt0 is not None:
                jacoco += ",includes={inc}".format(inc=opt0.replace(',', ':'))
            if opt1 is not None:
                jacoco += ",excludes={ex}".format(ex=opt1.replace(',', ':'))

            cmd = cmd[:1] + [jacoco] + cmd[1:]

        if self.test_ssl:
            cmd.append("-internal_security_conf")
            if g_convenient:
                cmd.append("../h2o-algos/src/test/resources/ssl.properties")
            else:
                cmd.append("../../../h2o-algos/src/test/resources/ssl3.properties")

        # Add S3N credentials to cmd if they exist.
        # ec2_hdfs_config_file_name = os.path.expanduser("~/.ec2/core-site.xml")
        # if (os.path.exists(ec2_hdfs_config_file_name)):
        #     cmd.append("-hdfs_config")
        #     cmd.append(ec2_hdfs_config_file_name)

        self.output_file_name = \
            os.path.join(self.output_dir, "java_" + str(self.cloud_num) + "_" + str(self.node_num) + ".out.txt")
        f = open(self.output_file_name, "w")

        if g_convenient:
            cwd = os.getcwd()
            here = os.path.abspath(os.path.dirname(__file__))
            there = os.path.abspath(os.path.join(here, ".."))
            os.chdir(there)
            self.child = subprocess.Popen(args=cmd,
                                          stdout=f,
                                          stderr=subprocess.STDOUT,
                                          cwd=there)
            os.chdir(cwd)
        else:
            try: 
              self.child = subprocess.Popen(args=cmd,
                                            stdout=f,
                                            stderr=subprocess.STDOUT,
                                            cwd=self.output_dir)
              self.pid = self.child.pid
              print("+ CMD: " + ' '.join(cmd))

            except OSError:
                raise "Failed to spawn %s in %s" % (cmd, self.output_dir)


    def scrape_port_from_stdout(self):
        """
        Look at the stdout log and figure out which port the JVM chose.

        If successful, port number is stored in self.port; otherwise the
        program is terminated. This call is blocking, and will wait for
        up to 30s for the server to start up.
        """
        regex = re.compile(r"Open H2O Flow in your web browser: https?://([^:]+):(\d+)")
        retries_left = 30
        while retries_left and not self.terminated:
            with open(self.output_file_name, "r") as f:
                for line in f:
                    mm = re.search(regex, line)
                    if mm is not None:
                        self.port = mm.group(2)
                        print("H2O cloud %d node %d listening on port %s\n    with output file %s" %
                              (self.cloud_num, self.node_num, self.port, self.output_file_name))
                        return
            if self.terminated: break
            retries_left -= 1
            time.sleep(1)

        if self.terminated: return
        print("\nERROR: Too many retries starting cloud %d.\nCheck the output log %s.\n" %
              (self.cloud_num, self.output_file_name))
        sys.exit(1)


    def scrape_cloudsize_from_stdout(self, nodes_per_cloud):
        """
        Look at the stdout log and wait until the cloud of proper size is formed.
        This call is blocking.
        Exit if this fails.

        :param nodes_per_cloud:
        :return none
        """
        retries = 60
        while retries > 0:
            if self.terminated: return
            f = open(self.output_file_name, "r")
            s = f.readline()
            while len(s) > 0:
                if self.terminated: return
                match_groups = re.search(r"Cloud of size (\d+) formed", s)
                if match_groups is not None:
                    size = match_groups.group(1)
                    if size is not None:
                        size = int(size)
                        if size == nodes_per_cloud:
                            f.close()
                            return

                s = f.readline()

            f.close()
            retries -= 1
            if self.terminated: return
            time.sleep(1)

        print("")
        print("ERROR: Too many retries starting cloud.")
        print("")
        sys.exit(1)

    def stop(self):
        """
        Normal node shutdown.
        Ignore failures for now.

        :return none
        """
        if self.pid > 0:
            print("Killing JVM with PID {}".format(self.pid))
            try:
                self.child.terminate()
                self.child.wait()
            except OSError:
                pass
            self.pid = -1

    def terminate(self):
        """
        Terminate a running node.  (Due to a signal.)

        :return none
        """
        self.terminated = True
        self.stop()

    def get_ip(self):
        """ Return the ip address this node is really listening on. """
        return self.ip

    def get_port(self):
        """ Return the port this node is really listening on. """
        return self.port

    def __str__(self):
        s = ""
        s += "    node {}\n".format(self.node_num)
        s += "        xmx:          {}\n".format(self.xmx)
        s += "        my_base_port: {}\n".format(self.my_base_port)
        s += "        port:         {}\n".format(self.port)
        s += "        pid:          {}\n".format(self.pid)
        return s


class H2OCloud(object):
    """
    A class representing one of the H2O clouds.
    """

    def __init__(self, cloud_num, use_client, nodes_per_cloud, h2o_jar, base_port, xmx, cp, output_dir, test_ssl,
                 ldap_config_path, jvm_opts=None):
        """
        Create a cloud.
        See node definition above for argument descriptions.

        :return The cloud object.
        """
        self.use_client = use_client
        self.cloud_num = cloud_num
        self.nodes_per_cloud = nodes_per_cloud
        self.h2o_jar = h2o_jar
        self.base_port = base_port
        self.xmx = xmx
        self.cp = cp
        self.output_dir = output_dir
        self.test_ssl = test_ssl
        self.ldap_config_path = ldap_config_path
        self.jvm_opts = jvm_opts

        # Randomly choose a seven digit cloud number.
        n = random.randint(1000000, 9999999)
        user = getpass.getuser()
        user = ''.join(user.split())

        self.cloud_name = "H2O_runit_{}_{}".format(user, n)
        self.nodes = []
        self.client_nodes = []
        self.jobs_run = 0

        if use_client:
            actual_nodes_per_cloud = self.nodes_per_cloud + 1
        else:
            actual_nodes_per_cloud = self.nodes_per_cloud

        for node_num in range(actual_nodes_per_cloud):
            is_client = False
            if use_client:
                if node_num == (actual_nodes_per_cloud - 1):
                    is_client = True
            node = H2OCloudNode(is_client,
                                self.cloud_num, actual_nodes_per_cloud, node_num,
                                self.cloud_name,
                                self.h2o_jar,
                                "127.0.0.1", self.base_port,
                                self.xmx, self.cp, self.output_dir,
                                self.test_ssl, self.ldap_config_path, self.jvm_opts)
            if is_client:
                self.client_nodes.append(node)
            else:
                self.nodes.append(node)

    def start(self):
        """
        Start H2O cloud.
        The cloud is not up until wait_for_cloud_to_be_up() is called and returns.

        :return none
        """
        for node in self.nodes:
            node.start()

        for node in self.client_nodes:
            node.start()

    def wait_for_cloud_to_be_up(self):
        """
        Blocking call ensuring the cloud is available.

        :return none
        """
        self._scrape_port_from_stdout()
        self._scrape_cloudsize_from_stdout()

    def stop(self):
        """
        Normal cloud shutdown.

        :return none
        """
        for node in self.nodes:
            node.stop()

        for node in self.client_nodes:
            node.stop()

    def terminate(self):
        """
        Terminate a running cloud.  (Due to a signal.)

        :return none
        """
        for node in self.client_nodes:
            node.terminate()

        for node in self.nodes:
            node.terminate()

    def get_ip(self):
        """ Return an ip to use to talk to this cloud. """
        if len(self.client_nodes) > 0:
            node = self.client_nodes[0]
        else:
            node = self.nodes[0]
        return node.get_ip()

    def get_port(self):
        """ Return a port to use to talk to this cloud. """
        if len(self.client_nodes) > 0:
            node = self.client_nodes[0]
        else:
            node = self.nodes[0]
        return node.get_port()

    def _scrape_port_from_stdout(self):
        for node in self.nodes:
            node.scrape_port_from_stdout()
        for node in self.client_nodes:
            node.scrape_port_from_stdout()

    def _scrape_cloudsize_from_stdout(self):
        for node in self.nodes:
            node.scrape_cloudsize_from_stdout(self.nodes_per_cloud)
        for node in self.client_nodes:
            node.scrape_cloudsize_from_stdout(self.nodes_per_cloud)

    def __str__(self):
        s = ""
        s += "cloud {}\n".format(self.cloud_num)
        s += "    name:     {}\n".format(self.cloud_name)
        s += "    jobs_run: {}\n".format(self.jobs_run)
        for node in self.nodes:
            s += str(node)
        for node in self.client_nodes:
            s += str(node)
        return s


class Test(object):
    """
    A class representing one Test.

    cancelled: Don't start this test.
    terminated: Test killed due to signal.
    returncode: Exit code of child.
    pid: Process id of the test.
    ip: IP of cloud to run test.
    port: Port of cloud to run test.
    child: subprocess.Popen object.
    """

    @staticmethod
    def test_did_not_complete():
        """
        returncode marker to know if the test ran or not.
        """
        return -9999999

    def __init__(self, test_dir, test_short_dir, test_name, output_dir, hadoop_namenode, on_hadoop):
        """
        Create a Test.

        :param test_dir: Full absolute path to the test directory.
        :param test_short_dir: Path from h2o/R/tests to the test directory.
        :param test_name: Test filename with the directory removed.
        :param output_dir: The directory where we can create an output file for this process.
        :param hadoop_namenode:
        :param on_hadoop:
        :return The test object.
        """
        self.test_dir = test_dir
        self.test_short_dir = test_short_dir
        self.test_name = test_name
        self.output_dir = output_dir
        self.output_file_name = ""
        self.hadoop_namenode = hadoop_namenode
        self.on_hadoop = on_hadoop
        self.exclude_flows = None

        self.cancelled = False
        self.terminated = False
        self.returncode = Test.test_did_not_complete()
        self.start_seconds = -1
        self.pid = -1
        self.ip = None
        self.port = -1
        self.child = None

    def start(self, ip, port):
        """
        Start the test in a non-blocking fashion.

        :param ip: IP address of cloud to run on.
        :param port: Port of cloud to run on.
        :return none
        """

        if self.cancelled or self.terminated:
            return

        self.start_seconds = time.time()
        self.ip = ip
        self.port = port

        if is_rdemo(self.test_name) or is_runit(self.test_name) or is_rbooklet(self.test_name):
            cmd = self._rtest_cmd(self.test_name, self.ip, self.port, self.on_hadoop, self.hadoop_namenode)
        elif (is_ipython_notebook(self.test_name) or is_pydemo(self.test_name) or is_pyunit(self.test_name) or
              is_pybooklet(self.test_name)):
            cmd = self._pytest_cmd(self.test_name, self.ip, self.port, self.on_hadoop, self.hadoop_namenode)
        elif is_gradle_build_python_test(self.test_name):
            cmd = ["python", self.test_name, "--usecloud", self.ip + ":" + str(self.port)]
        elif is_javascript_test_file(self.test_name):
            cmd = self._javascript_cmd(self.test_name, self.ip, self.port)
        else:
            print("")
            print("ERROR: Test runner failure with test: " + self.test_name)
            print("")
            sys.exit(1)

        test_short_dir_with_no_slashes = re.sub(r'[\\/]', "_", self.test_short_dir)
        if len(test_short_dir_with_no_slashes) > 0:
            test_short_dir_with_no_slashes += "_"
        self.output_file_name = \
            os.path.join(self.output_dir, test_short_dir_with_no_slashes + self.test_name + ".out.txt")
        f = open(self.output_file_name, "w")
        self.child = subprocess.Popen(args=cmd, stdout=f, stderr=subprocess.STDOUT, cwd=self.test_dir)
        self.pid = self.child.pid

    def is_completed(self):
        """
        Check if test has completed.

        This has side effects and MUST be called for the normal test queueing to work.
        Specifically, child.poll().

        :return True if the test completed, False otherwise.
        """
        child = self.child
        if child is None:
            return False
        child.poll()
        if child.returncode is None:
            return False
        self.pid = -1
        self.returncode = child.returncode
        return True

    def cancel(self):
        """
        Mark this test as cancelled so it never tries to start.

        :return none
        """
        if self.pid <= 0:
            self.cancelled = True

    def terminate_if_started(self):
        """
        Terminate a running test.  (Due to a signal.)

        :return none
        """
        if self.pid > 0:
            self.terminate()

    def terminate(self):
        """
        Terminate a running test.  (Due to a signal.)

        :return none
        """
        self.terminated = True
        if self.pid > 0:
            print("Killing Test {} with PID {}".format(os.path.join(self.test_short_dir, self.test_name), self.pid))
            try:
                self.child.terminate()
            except OSError:
                pass
        self.pid = -1

    def get_test_dir_file_name(self):
        """
        :return The full absolute path of this test.
        """
        return os.path.join(self.test_dir, self.test_name)

    def get_test_name(self):
        """
        :return The file name (no directory) of this test.
        """
        return self.test_name

    def get_seed_used(self):
        """
        :return The seed used by this test.
        """
        return self._scrape_output_for_seed()

    def get_ip(self):
        """
        :return IP of the cloud where this test ran.
        """
        return self.ip

    def get_port(self):
        """
        :return Integer port number of the cloud where this test ran.
        """
        return int(self.port)

    def get_passed(self):
        """
        :return True if the test passed, False otherwise.
        """
        return self.returncode == 0

    def get_skipped(self):
        """
        :return True if the test skipped, False otherwise.
        """
        return self.returncode == 42

    def get_nopass(self, nopass):
        """
        Some tests are known not to fail and even if they don't pass we don't want
        to fail the overall regression PASS/FAIL status.

        :param nopass:
        :return True if the test has been marked as NOPASS, False otherwise.
        """
        a = re.compile("NOPASS")
        return a.search(self.test_name) and not nopass

    def get_nofeature(self, nopass):
        """
        Some tests are known not to fail and even if they don't pass we don't want
        to fail the overall regression PASS/FAIL status.

        :param nopass:
        :return True if the test has been marked as NOFEATURE, False otherwise.
        """
        a = re.compile("NOFEATURE")
        return a.search(self.test_name) and not nopass

    def get_h2o_internal(self):
        """
        Some tests are only run on h2o internal network.

        :return True if the test has been marked as INTERNAL, False otherwise.
        """
        a = re.compile("INTERNAL")
        return a.search(self.test_name)

    def get_completed(self):
        """
        :return True if the test completed (pass or fail), False otherwise.
        """
        return self.returncode > Test.test_did_not_complete()

    def get_terminated(self):
        """
        For a test to be terminated it must have started and had a PID.

        :return True if the test was terminated, False otherwise.
        """
        return self.terminated

    def get_output_dir_file_name(self):
        """
        :return Full path to the output file which you can paste to a terminal window.
        """
        return os.path.join(self.output_dir, self.output_file_name)

    @staticmethod
    def _rtest_cmd(test_name, ip, port, on_hadoop, hadoop_namenode):
        if is_runit(test_name):
            r_test_driver = test_name
        else:
            r_test_driver = g_r_test_setup
        cmd = ["R", "-f", r_test_driver, "--args", "--usecloud", ip + ":" + str(port), "--resultsDir", g_output_dir,
               "--testName", test_name]
        if g_rest_log:
            cmd += ['--restLog']

        if is_runit(test_name):
            if on_hadoop: cmd += ["--onHadoop"]
            if hadoop_namenode: cmd += ["--hadoopNamenode", hadoop_namenode]
            cmd += ["--rUnit"]
        elif is_rdemo(test_name) and is_ipython_notebook(test_name):
            cmd += ["--rIPythonNotebook"]
        elif is_rdemo(test_name):
            cmd += ["--rDemo"]
        elif is_rbooklet(test_name):
            cmd += ["--rBooklet"]
        else:
            raise ValueError("Unsupported R test type: %s" % test_name)
        return cmd


    @staticmethod
    def _pytest_cmd(test_name, ip, port, on_hadoop, hadoop_namenode):
        if g_pycoverage:
            pyver = "coverage-3.5" if g_py3 else "coverage"
            cmd = [pyver, "run", "-a", g_py_test_setup, "--usecloud", ip + ":" + str(port), "--resultsDir",
                   g_output_dir,
                   "--testName", test_name]
            print("Running Python test with coverage:")
            print(cmd)
        else:
            pyver = "python3.5" if g_py3 else "python"
            cmd = [pyver, g_py_test_setup, "--usecloud", ip + ":" + str(port), "--resultsDir", g_output_dir,
                   "--testName", test_name]
        if is_pyunit(test_name):
            if on_hadoop: cmd += ["--onHadoop"]
            if hadoop_namenode: cmd += ["--hadoopNamenode", hadoop_namenode]
            cmd += ["--pyUnit"]
        elif is_ipython_notebook(test_name):
            cmd += ["--ipynb"]
        elif is_pydemo(test_name):
            cmd += ["--pyDemo"]
        else:
            cmd += ["--pyBooklet"]
        if g_jacoco_include:
            # When using JaCoCo we don't want the test to return an error if a cloud reports as unhealthy
            cmd += ["--forceConnect"]
        if g_ldap_username:
            cmd += ['--ldapUsername', g_ldap_username]
        if g_ldap_password:
            cmd += ['--ldapPassword', g_ldap_password]
        return cmd

    def _javascript_cmd(self, test_name, ip, port):
        # return ["phantomjs", test_name]
        if g_perf:
            return ["phantomjs", test_name, "--host", ip + ":" + str(port), "--timeout", str(g_phantomjs_to),
                    "--packs", g_phantomjs_packs, "--perf", g_date, str(g_build_id), g_git_hash, g_git_branch,
                   str(g_ncpu), g_os, g_job_name, g_output_dir, "--excludeFlows", self.exclude_flows]
        
        else:
            return ["phantomjs", test_name, "--host", ip + ":" + str(port), "--timeout", str(g_phantomjs_to),
                    "--packs", g_phantomjs_packs, "--excludeFlows", self.exclude_flows]

    def _scrape_output_for_seed(self):
        """
        :return The seed scraped from the output file.
        """
        res = ""
        with open(self.get_output_dir_file_name(), "r") as f:
            for line in f:
                if "SEED used" in line:
                    line = line.strip().split(' ')
                    res = line[-1]
                    break
        return res

    def __str__(self):
        s = ""
        s += "Test: {}/{}\n".format(self.test_dir, self.test_name)
        return s


class TestRunner(object):
    """
    A class for running tests.

    The tests list contains an object for every test.
    The tests_not_started list acts as a job queue.
    The tests_running list is polled for jobs that have finished.
    """

    def __init__(self,
                 test_root_dir,
                 use_cloud, use_cloud2, use_client, cloud_config, use_ip, use_port,
                 num_clouds, nodes_per_cloud, h2o_jar, base_port, xmx, cp, output_dir,
                 failed_output_dir, path_to_tar, path_to_whl, produce_unit_reports,
                 testreport_dir, r_pkg_ver_chk, hadoop_namenode, on_hadoop, perf, test_ssl, ldap_config_path, jvm_opts):
        """
        Create a runner.

        :param test_root_dir: h2o/R/tests directory.
        :param use_cloud: Use this one user-specified cloud.  Overrides num_clouds.
        :param use_cloud2: Use the cloud_config to define the list of H2O clouds.
        :param cloud_config: (if use_cloud2) the config file listing the H2O clouds.
        :param use_ip: (if use_cloud) IP of one cloud to use.
        :param use_port: (if use_cloud) Port of one cloud to use.
        :param num_clouds: Number of H2O clouds to start.
        :param nodes_per_cloud: Number of H2O nodes to start per cloud.
        :param h2o_jar: Path to H2O jar file to run.
        :param base_port: Base H2O port (e.g. 54321) to start choosing from.
        :param xmx: Java -Xmx parameter.
        :param cp: Java -cp parameter (appended to h2o.jar cp).
        :param output_dir: Directory for output files.
        :param failed_output_dir: Directory to copy failed test output.
        :param path_to_tar: path to h2o R package.
        :param path_to_whl: NA
        :param produce_unit_reports: if true then runner produce xUnit test reports for Jenkins
        :param testreport_dir: directory to put xUnit test reports for Jenkins (should follow build system conventions)
        :param r_pkg_ver_chk: check R packages/versions
        :param hadoop_namenode
        :param on_hadoop
        :param perf
        :param ldap_config_path: path to LDAP config which should be used, or null if no LDAP is required
        :param jvm_opts: str with additional JVM options
        :return The runner object.
        """
        self.test_root_dir = test_root_dir

        self.use_cloud = use_cloud
        self.use_cloud2 = use_cloud2
        self.use_client = use_client

        # Valid if use_cloud is True
        self.use_ip = use_ip
        self.use_port = use_port

        self.test_ssl = test_ssl

        # Valid if use_cloud is False
        self.num_clouds = num_clouds
        self.nodes_per_cloud = nodes_per_cloud
        self.h2o_jar = h2o_jar
        self.base_port = base_port
        self.output_dir = output_dir
        self.failed_output_dir = failed_output_dir
        self.produce_unit_reports = produce_unit_reports
        self.testreport_dir = testreport_dir
        self.completed_tests_count = 0

        self.start_seconds = time.time()
        self.terminated = False
        self.clouds = []
        self.suspicious_clouds = []
        self.bad_clouds = []
        self.tests = []
        self.tests_not_started = []
        self.tests_running = []
        self.regression_passed = False
        self._create_output_dir()
        self._create_failed_output_dir()
        if produce_unit_reports:
            self._create_testreport_dir()
        self.nopass_counter = 0
        self.nofeature_counter = 0
        self.h2o_internal_counter = 0
        self.path_to_tar = path_to_tar
        self.path_to_whl = path_to_whl
        self.r_pkg_ver_chk = r_pkg_ver_chk
        self.hadoop_namenode = hadoop_namenode
        self.on_hadoop = on_hadoop
        self.perf = perf
        self.perf_file = None
        self.exclude_list = []

        self.ldap_config_path = ldap_config_path
        self.jvm_opts = jvm_opts

        if use_cloud:
            node_num = 0
            cloud = H2OUseCloud(node_num, use_ip, use_port)
            self.clouds.append(cloud)
        elif use_cloud2:
            clouds = TestRunner.read_config(cloud_config)
            node_num = 0
            for c in clouds:
                cloud = H2OUseCloud(node_num, c[0], c[1])
                self.clouds.append(cloud)
                node_num += 1
        else:
            for i in range(self.num_clouds):
                cloud = H2OCloud(i, self.use_client, self.nodes_per_cloud, h2o_jar, self.base_port, xmx, cp,
                                 self.output_dir, self.test_ssl, self.ldap_config_path, self.jvm_opts)
                self.clouds.append(cloud)

    @staticmethod
    def find_test(test_to_run):
        """
        Be nice and try to help find the test if possible.
        If the test is actually found without looking, then just use it.
        Otherwise, search from the script's down directory down.
        :param test_to_run:
        """
        if os.path.exists(test_to_run):
            abspath_test = os.path.abspath(test_to_run)
            return abspath_test

        for d, subdirs, files in os.walk(os.getcwd()):
            for f in files:
                if f == test_to_run:
                    return os.path.join(d, f)

        # Not found, return the file, which will result in an error downstream when it can't be found.
        print("")
        print("ERROR: Test does not exist: " + test_to_run)
        print("")
        sys.exit(1)

    @staticmethod
    def read_config(config_file):
        """
        Read configuration file.
        """
        clouds = []  # a list of lists. Inner lists have [node_num, ip, port]
        cfg = configparser.RawConfigParser()
        cfg.read(config_file)
        for s in cfg.sections():
            items = cfg.items(s)
            cloud = [items[0][1], int(items[1][1])]
            clouds.append(cloud)
        return clouds

    def read_test_list_file(self, test_list_file):
        """
        Read in a test list file line by line.  Each line in the file is a test
        to add to the test run.

        :param test_list_file: Filesystem path to a file with a list of tests to run.
        :return none
        """
        try:
            f = open(test_list_file, "r")
            s = f.readline()
            while len(s) != 0:
                stripped = s.strip()
                if len(stripped) == 0:
                    s = f.readline()
                    continue
                if stripped.startswith("#"):
                    s = f.readline()
                    continue
                found_stripped = TestRunner.find_test(stripped)
                self.add_test(found_stripped)
                s = f.readline()
            f.close()
        except IOError as e:
            print("")
            print("ERROR: Failure reading test list: " + test_list_file)
            print("       (errno {0}): {1}".format(e.errno, e.strerror))
            print("")
            sys.exit(1)

    def read_exclude_list_file(self, exclude_list_file):
        """
        Read in a file of excluded tests line by line.  Each line in the file is a test
        to NOT add to the test run.

        :param exclude_list_file: Filesystem path to a file with a list of tests to NOT run.
        :return none
        """
        try:
            f = open(exclude_list_file, "r")
            s = f.readline()
            while len(s) != 0:
                stripped = s.strip()
                if len(stripped) == 0:
                    s = f.readline()
                    continue
                if stripped.startswith("#"):
                    s = f.readline()
                    continue
                self.exclude_list.append(stripped)
                s = f.readline()
            f.close()
        except IOError as e:
            print("")
            print("ERROR: Failure reading exclude list: " + exclude_list_file)
            print("       (errno {0}): {1}".format(e.errno, e.strerror))
            print("")
            sys.exit(1)

    def build_test_list(self, test_group, run_small, run_medium, run_large, run_xlarge, nopass, nointernal):
        """
        Recursively find the list of tests to run and store them in the object.
        Fills in self.tests and self.tests_not_started.

        :param test_group: Name of the test group of tests to run.
        :param run_small:
        :param run_medium:
        :param run_large:
        :param run_xlarge:
        :param nopass:
        :param nointernal:
        :return none
        """
        if self.terminated: return

        for root, dirs, files in os.walk(self.test_root_dir):
            if root.endswith("Util"):
                continue

            # http://stackoverflow.com/questions/18282370/os-walk-iterates-in-what-order
            # os.walk() yields in each step what it will do in the next steps.
            # You can in each step influence the order of the next steps by sorting the
            # lists the way you want them. Quoting the 2.7 manual:

            # When topdown is True, the caller can modify the dirnames list in-place
            # (perhaps using del or slice assignment), and walk() will only recurse into the
            # subdirectories whose names remain in dirnames; this can be used to prune the search,
            # impose a specific order of visiting

            # So sorting the dirNames will influence the order in which they will be visited:
            # do an inplace sort of dirs. Could do an inplace sort of files too, but sorted() is fine next.
            dirs.sort()

            # always do same order, for determinism when run on different machines
            for f in sorted(files):
                # Figure out if the current file under consideration is a test.
                is_test = False
                if is_rdemo(f):
                    is_test = True
                if is_runit(f):
                    is_test = True
                if is_rbooklet(f):
                    is_test = True
                if is_ipython_notebook(f):
                    is_test = True
                if is_pydemo(f):
                    is_test = True
                if is_pyunit(f):
                    is_test = True
                if is_pybooklet(f):
                    is_test = True
                if is_gradle_build_python_test(f):
                    is_test = True
                if not is_test:
                    continue

                is_small = False
                is_medium = False
                is_large = False
                is_xlarge = False
                is_nopass = False
                is_nofeature = False
                is_h2o_internal = False

                if "xlarge" in f:
                    is_xlarge = True
                elif "medium" in f:
                    is_medium = True
                elif "large" in f:
                    is_large = True
                else:
                    is_small = True

                if "NOPASS" in f:
                    is_nopass = True
                if "NOFEATURE" in f:
                    is_nofeature = True
                if "INTERNAL" in f:
                    is_h2o_internal = True

                if is_small and not run_small:
                    continue
                if is_medium and not run_medium:
                    continue
                if is_large and not run_large:
                    continue
                if is_xlarge and not run_xlarge:
                    continue

                if is_nopass and not nopass:
                    # skip all NOPASS tests for regular runs but still count the number of NOPASS tests
                    self.nopass_counter += 1
                    continue
                if is_nofeature and not nopass:
                    # skip all NOFEATURE tests for regular runs but still count the number of NOFEATURE tests
                    self.nofeature_counter += 1
                    continue
                if nopass and not is_nopass and not is_nofeature:
                    # if g_nopass flag is set, then ONLY run the NOPASS and NOFEATURE tests (skip all other tests)
                    continue

                if test_group is not None:
                    test_short_dir = self._calc_test_short_dir(os.path.join(root, f))
                    if (test_group.lower() not in test_short_dir) and test_group.lower() not in f:
                        continue

                if is_h2o_internal:
                    # count all applicable INTERNAL tests
                    if nointernal: continue
                    self.h2o_internal_counter += 1
                self.add_test(os.path.join(root, f))

    def add_test(self, test_path):
        """
        Add one test to the list of tests to run.
        :param test_path: File system path to the test.
        :return none
        """
        abs_test_path = os.path.abspath(test_path)
        abs_test_dir = os.path.dirname(abs_test_path)
        test_file = os.path.basename(abs_test_path)

        if not os.path.exists(abs_test_path):
            print("")
            print("ERROR: Test does not exist: " + abs_test_path)
            print("")
            sys.exit(1)

        test_short_dir = self._calc_test_short_dir(test_path)

        test = Test(abs_test_dir, test_short_dir, test_file, self.output_dir, self.hadoop_namenode, self.on_hadoop)
        if is_javascript_test_file(test.test_name): test.exclude_flows = ';'.join(self.exclude_list)
        if test.test_name in self.exclude_list:
            print("INFO: Skipping {0} because it was placed on the exclude list.".format(test_path))
        else:
            self.tests.append(test)
            self.tests_not_started.append(test)

    def start_clouds(self):
        """
        Start all H2O clouds.
        :return none
        """
        if self.terminated: return
        if self.use_cloud: return

        print("")
        print("Starting clouds...")
        print("")

        for cloud in self.clouds:
            if self.terminated: return
            cloud.start()

        print("")
        print("Waiting for H2O nodes to come up...")
        print("")

        for cloud in self.clouds:
            if self.terminated: return
            cloud.wait_for_cloud_to_be_up()

    def run_tests(self, nopass):
        """
        Run all tests.
        :param nopass:
        :return none
        """
        if self.terminated: return

        if self.perf:
            self.perf_file = os.path.join(self.output_dir, "perf.csv")

        if self.on_hadoop and self.hadoop_namenode is None:
            print("")
            print("ERROR: Must specify --hadoopNamenode when using --onHadoop option.")
            print("")
            sys.exit(1)

        if self.r_pkg_ver_chk:
            self._r_pkg_ver_chk()

        elif self.path_to_tar is not None:
            self._install_h2o_r_pkg(self.path_to_tar)

        elif self.path_to_whl is not None:
            self._install_h2o_py_whl(self.path_to_whl)

        num_tests = len(self.tests)
        num_nodes = self.num_clouds * self.nodes_per_cloud
        self._log("")
        if self.use_client:
            client_message = " (+ client mode)"
        else:
            client_message = ""
        if self.use_cloud:
            self._log("Starting {} tests...".format(num_tests))
        elif self.use_cloud2:
            self._log("Starting {} tests on {} clouds...".format(num_tests, len(self.clouds)))
        else:
            self._log("Starting {} tests on {} clouds with {} total H2O worker nodes{}...".format(num_tests,
                                                                                                  self.num_clouds,
                                                                                                  num_nodes,
                                                                                                  client_message))
        self._log("")

        # Start the first n tests, where n is the lesser of the total number of tests and the total number of clouds.
        start_count = min(len(self.tests_not_started), len(self.clouds), 30)
        if g_use_cloud2:
            start_count = min(start_count, 75)  # only open up 30 processes locally
        for i in range(start_count):
            cloud = self.clouds[i]
            ip = cloud.get_ip()
            port = cloud.get_port()
            self._start_next_test_on_ip_port(ip, port)

        # As each test finishes, send a new one to the cloud that just freed up.
        while len(self.tests_not_started) > 0:
            if self.terminated:
                return
            cld = self._wait_for_available_cloud(nopass)
            # Check if no cloud was found
            if cld is None:
                self._log('NO GOOD CLOUDS REMAINING...')
                self.terminate()
            available_ip, available_port = cld
            if self.terminated:
                return
            if self._h2o_exists_and_healthy(available_ip, available_port):
                self._start_next_test_on_ip_port(available_ip, available_port)

        # Wait for remaining running tests to complete.
        while len(self.tests_running) > 0:
            if self.terminated: return
            completed_test = self._wait_for_one_test_to_complete()
            if self.terminated: return
            self._report_test_result(completed_test, nopass)

    def check_clouds(self):
        """
        for all clouds, check if connection to h2o exists, and that h2o is healthy.
        """
        time.sleep(3)
        print("Checking cloud health...")
        for c in self.clouds:
            if self._h2o_exists_and_healthy(c.get_ip(), c.get_port()):
                print("Node {} healthy.".format(c))
            else:
                print("Node with IP {} and port {} NOT HEALTHY" .format(c.get_ip(),c.get_port()))
                # should an exception be thrown?

    def stop_clouds(self):
        """
        Stop all H2O clouds.
        :return: none
        """
        if self.terminated: return

        if self.use_cloud or self.use_cloud2:
            print("")
            print("All tests completed...")
            print("")
            return

        print("")
        print("All tests completed; tearing down clouds...")
        print("")
        for cloud in self.clouds:
            cloud.stop()

    def report_summary(self, nopass):
        """
        Report some summary information when the tests have finished running.

        :param nopass:
        :return: none
        """
        passed = 0
        skipped = 0
        skipped_list = []
        nopass_but_tolerate = 0
        nofeature_but_tolerate = 0
        failed = 0
        notrun = 0
        total = 0
        h2o_internal_failed = 0
        true_fail_list = []
        terminated_list = []
        for test in self.tests:
            if test.get_passed():
                passed += 1
            elif test.get_skipped():
                skipped += 1
                skipped_list += [test.test_name]
            else:
                if test.get_h2o_internal():
                    h2o_internal_failed += 1

                if test.get_nopass(nopass):
                    nopass_but_tolerate += 1

                if test.get_nofeature(nopass):
                    nofeature_but_tolerate += 1

                if test.get_completed():
                    failed += 1
                    if not (test.get_nopass(nopass) or test.get_nofeature(nopass)):
                        true_fail_list.append(test.get_test_name())
                else:
                    notrun += 1

                if test.get_terminated():
                    terminated_list.append(test.get_test_name())
            total += 1

        if passed + nopass_but_tolerate + nofeature_but_tolerate == total:
            self.regression_passed = True
        else:
            self.regression_passed = False

        end_seconds = time.time()
        delta_seconds = end_seconds - self.start_seconds
        run = total - notrun
        self._log("")
        self._log("----------------------------------------------------------------------")
        self._log("")
        self._log("SUMMARY OF RESULTS")
        self._log("")
        self._log("----------------------------------------------------------------------")
        self._log("")
        self._log("Total tests:               " + str(total))
        self._log("Passed:                    " + str(passed))
        self._log("Did not pass:              " + str(failed))
        self._log("Did not complete:          " + str(notrun))
        if skipped > 0:
            self._log("SKIPPED tests:             " + str(skipped))
        self._log("H2O INTERNAL tests:        " + str(self.h2o_internal_counter))
        self._log("H2O INTERNAL failures:     " + str(h2o_internal_failed))
        # self._log("Tolerated NOPASS:         " + str(nopass_but_tolerate))
        # self._log("Tolerated NOFEATURE:      " + str(nofeature_but_tolerate))
        self._log("NOPASS tests (not run):    " + str(self.nopass_counter))
        self._log("NOFEATURE tests (not run): " + str(self.nofeature_counter))
        self._log("")
        if skipped > 0:
            self._log("SKIPPED list:          " + ", ".join([t for t in skipped_list]))
        self._log("Total time:              %.2f sec" % delta_seconds)
        if run > 0:
            self._log("Time/completed test:     %.2f sec" % (delta_seconds / run))
        else:
            self._log("Time/completed test:     N/A")
        self._log("")
        if len(true_fail_list) > 0:
            self._log("True fail list:          " + ", ".join(true_fail_list))
        if len(terminated_list) > 0:
            self._log("Terminated list:         " + ", ".join(terminated_list))
        if len(self.bad_clouds) > 0:
            self._log("Bad cloud list:          " + ", ".join(["{0}:{1}".format(bc[0], bc[1])
                      for bc in self.bad_clouds]))

    def terminate(self):
        """
        Terminate all running clouds.  (Due to a signal.)
        :return none
        """
        self.terminated = True

        for test in self.tests:
            test.cancel()

        for test in self.tests:
            test.terminate_if_started()

        for cloud in self.clouds:
            cloud.terminate()

    def get_regression_passed(self):
        """
        Return whether the overall regression passed or not.

        :return true if the exit value should be 0, false otherwise.
        """
        return self.regression_passed

    # --------------------------------------------------------------------
    # Private methods below this line.
    # --------------------------------------------------------------------
    def _install_h2o_r_pkg(self, h2o_r_pkg_path):
        """
        Installs h2o R package from the specified location.
        """

        self._log("")
        self._log("Installing H2O R package...")

        cmd = ["R", "CMD", "INSTALL", h2o_r_pkg_path]
        child = subprocess.Popen(args=cmd)
        rv = child.wait()
        if self.terminated:
            return
        if rv == 1:
            self._log("")
            self._log("ERROR: failed to install H2O R package.")
            sys.exit(1)

    def _install_h2o_py_whl(self, h2o_py_pkg_path):
        """
        Installs h2o wheel from the specified location.
        """
        self._log("")
        self._log("Setting up Python H2O package...")
        out_file_name = os.path.join(self.output_dir, "pythonSetup.out.txt")
        out = open(out_file_name, "w")

        cmd = ["pip", "install", h2o_py_pkg_path, "--force-reinstall"]
        child = subprocess.Popen(args=cmd,
                                 stdout=out,
                                 stderr=subprocess.STDOUT)
        rv = child.wait()
        if self.terminated:
            return
        if rv != 0:
            print("")
            print("ERROR: Python setup failed.")
            print("       (See " + out_file_name + ")")
            print("")
            sys.exit(1)
        out.close()

    def _r_pkg_ver_chk(self):
        """
        Run R script that checks if the Jenkins-approve R packages and versions are present. Exit, if they are not
        present or if the requirements file cannot be retrieved.
        """

        global g_r_pkg_ver_chk_script
        self._log("")
        self._log("Conducting R package/version check...")
        out_file_name = os.path.join(self.output_dir, "package_version_check_out.txt")
        out = open(out_file_name, "w")

        cmd = ["R", "--vanilla", "-f", g_r_pkg_ver_chk_script, "--args", "check", ]

        child = subprocess.Popen(args=cmd, stdout=out)
        rv = child.wait()
        if self.terminated:
            return
        if rv == 1:
            self._log("")
            self._log("ERROR: " + g_r_pkg_ver_chk_script + " failed.")
            self._log("       See " + out_file_name)
            sys.exit(1)
        out.close()

    def _calc_test_short_dir(self, test_path):
        """
        Calculate directory of test relative to test_root_dir.

        :param test_path: Path to test file.
        :return test_short_dir, relative directory containing test (relative to test_root_dir).
        """
        abs_test_root_dir = os.path.abspath(self.test_root_dir)
        abs_test_path = os.path.abspath(test_path)
        abs_test_dir = os.path.dirname(abs_test_path)

        test_short_dir = abs_test_dir

        # Look to elide longest prefix first.
        prefix = os.path.join(abs_test_root_dir, "")
        if test_short_dir.startswith(prefix):
            test_short_dir = test_short_dir.replace(prefix, "", 1)

        prefix = abs_test_root_dir
        if test_short_dir.startswith(prefix):
            test_short_dir = test_short_dir.replace(prefix, "", 1)

        return test_short_dir

    def _create_failed_output_dir(self):
        try:
            if not os.path.exists(self.failed_output_dir):
                os.makedirs(self.failed_output_dir)
        except OSError as e:
            print("")
            print("mkdir failed (errno {0}): {1}".format(e.errno, e.strerror))
            print("    " + self.failed_output_dir)
            print("")
            print("(try adding --wipe)")
            print("")
            sys.exit(1)

    def _create_output_dir(self):
        try:
            if not os.path.exists(self.output_dir):
                os.makedirs(self.output_dir)
        except OSError as e:
            print("")
            print("mkdir failed (errno {0}): {1}".format(e.errno, e.strerror))
            print("    " + self.output_dir)
            print("")
            print("(try adding --wipe)")
            print("")
            sys.exit(1)

    def _create_testreport_dir(self):
        try:
            if not os.path.exists(self.testreport_dir):
                os.makedirs(self.testreport_dir)
        except OSError as e:
            print("")
            print("mkdir failed (errno {0}): {1}".format(e.errno, e.strerror))
            print("    " + self.testreport_dir)
            print("")
            sys.exit(1)

    def _start_next_test_on_ip_port(self, ip, port):
        test = self.tests_not_started.pop(0)
        self.tests_running.append(test)
        test.start(ip, port)

    def _wait_for_one_test_to_complete(self):
        while True:
            if len(self.tests_running) > 0:
                for test in self.tests_running:
                    if test.is_completed():
                        self.tests_running.remove(test)
                        return test
                time.sleep(1)
            else:
                self._log('WAITING FOR ONE TEST TO COMPLETE, BUT THERE ARE NO RUNNING TESTS. EXITING...')
                sys.exit(1)

    def _wait_for_available_cloud(self, nopass, timeout=60):
        """
        Waits for an available cloud to appear by either a test completing or by a cloud on the suspicious_clouds list
        reporting as healthy, and then returns a tuple containing its ip and port. If no tests are running and no clouds
        are reporting as healthy, then the function will wait until the designated timeout time expires before returning
        None.
        """
        timer_on = False
        t_start = None
        while True:
            if timer_on:
                if time.time() - t_start > timeout:
                    return None

            for ip, port in self.suspicious_clouds:
                if self._h2o_exists_and_healthy(ip, port):
                    self.suspicious_clouds.remove([ip, port])
                    return ip, port

            if len(self.tests_running) > 0:
                for test in self.tests_running:
                    if test.is_completed():
                        self.tests_running.remove(test)
                        self._report_test_result(test, nopass)
                        return test.get_ip(), test.get_port()
            elif len(self.suspicious_clouds) == 0:
                self._log('WAITING FOR ONE TEST TO COMPLETE, BUT THERE ARE NO RUNNING TESTS. EXITING...')
                sys.exit(1)
            else:
                t_start = time.time()
                timer_on = True


    def _report_test_result(self, test, nopass):
        self.completed_tests_count += 1
        index = self.completed_tests_count
        port = test.get_port()
        finish_seconds = time.time()
        duration = finish_seconds - test.start_seconds
        test_name = test.get_test_name()
        if not test.get_skipped():
            if self.perf and not is_javascript_test_file(test.test_name): self._report_perf(test, finish_seconds)
        if test.get_passed():
            s = "%-4d pass  %005d %4ds %s" % (index, port, duration, test_name)
            self._log(s)
            if self.produce_unit_reports: self._report_xunit_result("r_suite", test_name, duration, False)
        elif test.get_skipped():
            s = "%-4d skip  %005d %4ds %s" % (index, port, duration, test_name)
            self._log(s)
            if self.produce_unit_reports:
                self._report_xunit_result("r_suite", test_name, duration, False)
        else:
            s = "%-4d FAIL  %005d %4ds %s  %s  %s" % \
                (index, port, duration, test.get_test_name(), test.get_output_dir_file_name(), test.get_seed_used())
            self._log(s)
            f = self._get_failed_filehandle_for_appending()
            f.write(test.get_test_dir_file_name() + "\n")
            f.close()
            # Report junit
            if self.produce_unit_reports:
                if not test.get_nopass(nopass):
                    self._report_xunit_result("r_suite", test_name, duration, False, "TestFailure", "Test failed",
                                              "See {}".format(test.get_output_dir_file_name()))
                else:
                    self._report_xunit_result("r_suite", test_name, duration, True)
            # Copy failed test output into directory failed
            if not test.get_nopass(nopass) and not test.get_nofeature(nopass):
                shutil.copy(test.get_output_dir_file_name(), self.failed_output_dir)

    # XSD schema for xunit reports is here; http://windyroad.com.au/dl/Open%20Source/JUnit.xsd
    def _report_xunit_result(self, testsuite_name, testcase_name, testcase_runtime,
                             skipped=False, failure_type=None, failure_message=None, failure_description=None):

        global g_use_xml2  # True if user want to enable log capturing in xml file.

        errors = 0
        failures = 1 if failure_type else 0
        skip = 1 if skipped else 0
        failure = "" if not failure_type else """"<failure type="{}" message="{}">{}</failure>""" \
            .format(failure_type, failure_message, failure_description)

        if g_use_xml2:
            # need to change the failure content when using new xml format.
            # first get the output file that contains the python/R output error
            if failure_description is not None:  # for tests that fail.
                failure_file = failure_description.split()[1]
                failure_message = open(failure_file, 'r').read()  # read the whole content in here.
                java_errors = None

                # add the error message from Java side here, java filename is in self.clouds[].output_file_name
                for each_cloud in self.clouds:
                    java_errors = grab_java_message(each_cloud.nodes, testcase_name)
                    if len(java_errors) > 0:  # found java message and can quit now
                        if g_use_client:
                            failure_message += "\n##### Java message from server node #####\n"
                        failure_message += java_errors
                        break

                # scrape the logs on client nodes as well
                if g_use_client:
                    # add the error message from Java side here, java filename is in self.clouds[].output_file_name
                    for each_cloud in self.clouds:
                        java_errors = grab_java_message(each_cloud.client_nodes, testcase_name)
                        if len(java_errors) > 0:  # found java message and can quit now
                            failure_message += "\n\n##### Java message from client node #####\n"
                            failure_message += java_errors

                            break

                if not java_errors:
                    failure_message += "\n\n"
                    failure_message += "#" * 83 + "\n"
                    failure_message += "########### Problems encountered extracting Java messages. " \
                                       "Massive Jenkins or test failure.\n"
                    failure_message += "#" * 83 + "\n\n"

                if failure_message:
                    if failure_type:
                        failure = """<failure type="{}" message="{}"><![CDATA[{}]]></failure>""" \
                                  .format(failure_type, failure_description, failure_message)
                    else:
                        failure = ""

    # fixed problem with test name repeated in Jenkins job test report.
        xml_report = """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="{testsuiteName}" tests="1" errors="{errors}" failures="{failures}" skip="{skip}">
  <testcase name="{testcaseName}" time="{testcaseRuntime}">
  {failure}
  </testcase>
</testsuite>
""".format(testsuiteName=testsuite_name, testcaseName=testcase_name,
           testcaseRuntime=testcase_runtime, failure=failure,
           errors=errors, failures=failures, skip=skip)

        self._save_xunit_report(testsuite_name, testcase_name, xml_report)

    def _report_perf(self, test, finish_seconds):
        f = open(self.perf_file, "a")
        f.write('{0}, {1}, {2}, {3}, {4}, {5}, {6}, {7}, {8}, {9}, {10}, {11}\n'
                ''.format(g_date, g_build_id, g_git_hash, g_git_branch, g_machine_ip, test.get_test_name(),
                          test.start_seconds, finish_seconds, 1 if test.get_passed() else 0, g_ncpu, g_os, g_job_name))
        f.close()

    def _save_xunit_report(self, testsuite, testcase, report):
        f = self._get_testreport_filehandle(testsuite, testcase)
        f.write(report)
        f.close()

    def _log(self, s):
        f = self._get_summary_filehandle_for_appending()
        print(s)
        sys.stdout.flush()
        f.write(s + "\n")
        f.close()

    def _get_summary_filehandle_for_appending(self):
        summary_file_name = os.path.join(self.output_dir, "summary.txt")
        f = open(summary_file_name, "a+")
        return f

    def _get_failed_filehandle_for_appending(self):
        summary_file_name = os.path.join(self.output_dir, "failed.txt")
        f = open(summary_file_name, "a+")
        return f

    def _get_testreport_filehandle(self, testsuite, testcase):
        testreport_file_name = os.path.join(self.testreport_dir, "TEST_{0}_{1}.xml".format(testsuite, testcase))
        f = open(testreport_file_name, "w+")
        return f

    def __str__(self):
        s = "\n"
        s += "test_root_dir:    {}\n".format(self.test_root_dir)
        s += "output_dir:       {}\n".format(self.output_dir)
        s += "h2o_jar:          {}\n".format(self.h2o_jar)
        s += "num_clouds:       {}\n".format(self.num_clouds)
        s += "nodes_per_cloud:  {}\n".format(self.nodes_per_cloud)
        s += "base_port:        {}\n".format(self.base_port)
        s += "\n"
        for c in self.clouds:
            s += str(c)
        s += "\n"
        # for t in self.tests:
        #     s += str(t)
        return s

    def _h2o_exists_and_healthy(self, ip, port):
        """
        check if connection to h2o exists, and that h2o is healthy.
        """
        if not port or int(port) <= 0:
            return False
        h2o_okay = False
        try:
            auth = None
            if g_ldap_password is not None and g_ldap_username is not None:
                auth = (g_ldap_username, g_ldap_password)
            http = requests.get("http://{}:{}/3/Cloud?skip_ticks=true".format(ip, port), auth=auth)
            json = http.json()
            if "cloud_healthy" in json:
                h2o_okay = json["cloud_healthy"]
        except requests.exceptions.ConnectionError: pass
        if not h2o_okay:
            # JaCoCo tends to cause clouds to temporarily report as unhealthy even when they aren't,
            # so we'll just consider an unhealthy cloud as suspicious
            if g_jacoco_include: self._suspect_cloud(ip, port)
            else: self._remove_cloud(ip, port)
        return h2o_okay

    def _remove_cloud(self, ip, port):
        """
        add the ip, port to TestRunner's bad cloud list. remove the bad cloud from the TestRunner's cloud list.
        terminate TestRunner if no good clouds remain.
        """
        if not [ip, str(port)] in self.bad_clouds: self.bad_clouds.append([ip, str(port)])
        cidx = 0
        found_cloud = False
        for cloud in self.clouds:
            if cloud.get_ip() == ip and cloud.get_port() == str(port):
                found_cloud = True
                break
            cidx += 1
        if found_cloud: self.clouds.pop(cidx)
        if len(self.clouds) == 0:
            self._log('NO GOOD CLOUDS REMAINING...')
            self.terminate()

    def _suspect_cloud(self, ip, port):
        """
        add the ip, port to TestRunner's suspicious cloud list. This way the cloud is considered to have the potential
        to report as being healthy sometime in the future. Unlike _remove_cloud(), the suspicious cloud is not removed
        from the TestRunner's cloud list.
        """
        if not [ip, str(port)] in self.suspicious_clouds: self.suspicious_clouds.append([ip, str(port)])



#--------------------------------------------------------------------
# Main program
#--------------------------------------------------------------------

# Global variables that can be set by the user.
g_script_name = ""
g_base_port = 40000
g_num_clouds = 5
g_nodes_per_cloud = 1
g_wipe_test_state = False
g_wipe_output_dir = False
g_test_to_run = None
g_test_list_file = None
g_exclude_list_file = None
g_test_group = None
g_run_small = True
g_run_medium = True
g_run_large = True
g_run_xlarge = True
g_use_cloud = False
g_use_cloud2 = False
g_use_client = False
g_config = None
g_use_ip = None
g_use_port = None
g_no_run = False
g_jvm_xmx = "1g"
g_jvm_cp = ""
g_nopass = False
g_nointernal = False
g_convenient = False
g_jacoco_include = False
g_jacoco_options = [None, None]
g_path_to_h2o_jar = None
g_path_to_tar = None
g_path_to_whl = None
g_produce_unit_reports = True
g_phantomjs_to = 3600
g_phantomjs_packs = "examples"
g_r_pkg_ver_chk = False
g_on_hadoop = False
g_hadoop_namenode = None
g_build_id = None
g_perf = False
g_git_hash = None
g_git_branch = None
g_job_name = None
g_py3 = False
g_pycoverage = False
g_test_ssl = False
g_ldap_config = None
g_ldap_username = None
g_ldap_password = None
g_rest_log = False
g_jvm_opts = None

# globals added to support better reporting in xml files
g_use_xml2 = False  # by default, use the original xml file output
g_java_start_text = 'STARTING TEST:'  # test being started in java

# Global variables that are set internally.
g_output_dir = None
g_runner = None
g_handling_signal = False

g_r_pkg_ver_chk_script = os.path.realpath(os.path.join(os.path.dirname(os.path.realpath(__file__)),
                                                       "../h2o-r/scripts/package_version_check_update.R"))
g_r_test_setup = os.path.realpath(os.path.join(os.path.dirname(os.path.realpath(__file__)),
                                               "../h2o-r/scripts/h2o-r-test-setup.R"))
g_py_test_setup = os.path.realpath(os.path.join(os.path.dirname(os.path.realpath(__file__)),
                                                "../h2o-py/scripts/h2o-py-test-setup.py"))
g_date = time.strftime('%Y-%m-%d', time.localtime(time.time()))
try:
    # If you get an exception in this line, then reboot your WiFi (or restart computer)
    g_machine_ip = socket.gethostbyname(socket.gethostname())
except socket.gaierror:
    g_machine_ip = "127.0.0.1"
g_ncpu = multiprocessing.cpu_count()
g_os = platform.system()


# noinspection PyUnusedLocal
def signal_handler(signum, stackframe):
    """Helper function to handle caught signals."""
    global g_runner
    global g_handling_signal

    if g_handling_signal:
        # Don't do this recursively.
        return
    g_handling_signal = True

    print("")
    print("----------------------------------------------------------------------")
    print("")
    print("SIGNAL CAUGHT (" + str(signum) + ").  TEARING DOWN CLOUDS.")
    print("")
    print("----------------------------------------------------------------------")
    g_runner.terminate()


def usage():
    """
    Print USAGE help.
    """
    print("")
    print("Usage:  " + g_script_name + " [...options...]")
    print("")
    print("    (Output dir is: " + str(g_output_dir) + ")")
    print("    (Default number of clouds is: " + str(g_num_clouds) + ")")
    print("")
    print("    --wipeall        Remove all prior test state before starting, particularly")
    print("                     random seeds.")
    print("                     (Removes master_seed file and all Rsandbox directories.")
    print("                     Also wipes the output dir before starting.)")
    print("")
    print("    --wipe           Wipes the output dir before starting.  Keeps old random seeds.")
    print("")
    print("    --baseport       The first port at which H2O starts searching for free ports.")
    print("")
    print("    --numclouds      The number of clouds to start.")
    print("                     Each test is randomly assigned to a cloud.")
    print("")
    print("    --numnodes       The number of nodes in the cloud.")
    print("                     When this is specified, numclouds must be 1.")
    print("")
    print("    --test           If you only want to run one test, specify it like this.")
    print("")
    print("    --testlist       A file containing a list of tests to run (for example the")
    print("                     'failed.txt' file from the output directory).")
    print("    --excludelist    A file containing a list of tests to NOT run.")
    print("")
    print("    --testgroup      Test a group of tests by function:")
    print("                     pca, glm, kmeans, gbm, rf, deeplearning, algos, golden, munging, parser")
    print("")
    print("    --testsize       Sizes (and by extension length) of tests to run:")
    print("                     s=small (seconds), m=medium (a minute or two), l=large (longer), x=xlarge (very big)")
    print("                     (Default is to run all tests.)")
    print("")
    print("    --usecloud       ip:port of cloud to send tests to instead of starting clouds.")
    print("                     (When this is specified, numclouds is ignored.)")
    print("")
    print("    --usecloud2      cloud.cfg: Use a set clouds defined in cloud.config to run tests on.")
    print("                     (When this is specified, numclouds, numnodes, and usecloud are ignored.)")
    print("")
    print("    --client         Send REST API commands through client mode.")
    print("")
    print("    --norun          Perform side effects like wipe, but don't actually run tests.")
    print("")
    print("    --jvm.xmx        Configure size of launched JVM running H2O. E.g. '--jvm.xmx 3g'")
    print("")
    print("    --jvm.cp         Classpath argument, in addition to h2o.jar path. E.g. "
          "'--jvm.cp /Users/h2o/mysql-connector-java-5.1.38-bin.jar'")
    print("")
    print("    --nopass         Run the NOPASS and NOFEATURE tests only and do not ignore any failures.")
    print("")
    print("    --nointernal     Don't run the INTERNAL tests.")
    print("")
    print("    --c              Start the JVMs in a convenient location.")
    print("")
    print("    --h2ojar         Supply a path to the H2O jar file.")
    print("")
    print("    --tar            Supply a path to the R TAR.")
    print("")
    print("")
    print("    --pto            The phantomjs timeout in seconds. Default is 3600 (1hr).")
    print("")
    print("    --noxunit        Do not produce xUnit reports.")
    print("")
    print("    --rPkgVerChk     Check that Jenkins-approved R packages/versions are present")
    print("")
    print("    --onHadoop       Indication that tests will be run on h2o multinode hadoop clusters.")
    print("                     `locate` and `sandbox` runit/pyunit test utilities use this indication in order to")
    print("                     behave properly. --hadoopNamenode must be specified if --onHadoop option is used.")
    print("    --hadoopNamenode Specifies that the runit/pyunit tests have access to this hadoop namenode.")
    print("                     runit/pyunit test utilities have ability to retrieve this value.")
    print("")
    print("    --perf           Save Jenkins build id, date, machine ip, git hash, name, start time, finish time,")
    print("                     pass, ncpus, os, and job name of each test to perf.csv in the results directory.")
    print("                     Takes three parameters: git hash, git branch, and build id, job name in that order.")
    print("")
    print("    --jacoco         Generate code coverage data using JaCoCo. Class includes and excludes may optionally")
    print("                     follow in the format of [includes]:[excludes] where [...] denotes a list of")
    print("                     classes, each separated by a comma (,). Wildcard characters (* and ?) may be used.")
    print("")
    print("    --geterrs        Generate xml file that contains the actual unit test errors and the actual Java error.")
    print("")
    print("    --test.ssl       Runs all the nodes with SSL enabled.")
    print("")
    print("    --ldap.username  Username for LDAP.")
    print("")
    print("    --ldap.password  Password for LDAP.")
    print("")
    print("    --ldap.config    Path to LDAP config. If set, all nodes will be started with LDAP support.")
    print("")
    print("    --jvm.opts       Additional JVM options.")
    print("")
    print("    --restLog        If set, enable REST API logging. Logs will be available at <resultsDir>/rest.log.")
    print("                     Please note, that enablig REST API logging will increase the execution time and that")
    print("                     the log file might be large (> 2GB).")
    print("    If neither --test nor --testlist is specified, then the list of tests is")
    print("    discovered automatically as files matching '*runit*.R'.")
    print("")
    print("")
    print("Examples:")
    print("")
    print("    Just accept the defaults and go (note: output dir must not exist):")
    print("        " + g_script_name)
    print("")
    print("    Remove all random seeds (i.e. make new ones) but don't run any tests:")
    print("        " + g_script_name + " --wipeall --norun")
    print("")
    print("    For a powerful laptop with 8 cores (keep default numclouds):")
    print("        " + g_script_name + " --wipeall")
    print("")
    print("    For a big server with 32 cores:")
    print("        " + g_script_name + " --wipeall --numclouds 16")
    print("")
    print("    Just run the tests that finish quickly")
    print("        " + g_script_name + " --wipeall --testsize s")
    print("")
    print("    Run one specific test, keeping old random seeds:")
    print("        " + g_script_name + " --wipe --test path/to/test.R")
    print("")
    print("    Rerunning failures from a previous run, keeping old random seeds:")
    print("        # Copy failures.txt, otherwise --wipe removes the directory with the list!")
    print("        cp " + os.path.join(g_output_dir, "failures.txt") + " .")
    print("        " + g_script_name + " --wipe --numclouds 16 --testlist failed.txt")
    print("")
    print("    Run tests on a pre-existing cloud (e.g. in a debugger), keeping old random seeds:")
    print("        " + g_script_name + " --wipe --usecloud ip:port")
    print("")
    print("    Run tests with JaCoCo enabled, excluding org.example1 and org.example2")
    print("        " + g_script_name + " --jacoco :org.example1,org.example2")
    sys.exit(1)


def unknown_arg(s):
    """Unknown argument found -- print error message and exit."""
    print("")
    print("ERROR: Unknown argument: " + s)
    print("")
    usage()


def bad_arg(s):
    """Invalid argument found -- print error message and exit."""
    print("")
    print("ERROR: Illegal use of (otherwise valid) argument: " + s)
    print("")
    usage()


def error(s):
    """Other error encountered -- print error message and exit."""
    print("")
    print("ERROR: " + s)
    print("")
    usage()


def parse_args(argv):
    """
    Parse the arguments into globals (ain't this an ugly duckling?).

    TODO: replace this machinery with argparse module.
    """
    global g_base_port
    global g_num_clouds
    global g_nodes_per_cloud
    global g_wipe_test_state
    global g_wipe_output_dir
    global g_test_to_run
    global g_test_list_file
    global g_exclude_list_file
    global g_test_group
    global g_run_small
    global g_run_medium
    global g_run_large
    global g_run_xlarge
    global g_use_cloud
    global g_use_cloud2
    global g_use_client
    global g_config
    global g_use_ip
    global g_use_port
    global g_no_run
    global g_jvm_xmx
    global g_jvm_cp
    global g_nopass
    global g_nointernal
    global g_convenient
    global g_path_to_h2o_jar
    global g_path_to_tar
    global g_path_to_whl
    global g_jacoco_include
    global g_jacoco_options
    global g_produce_unit_reports
    global g_phantomjs_to
    global g_phantomjs_packs
    global g_r_pkg_ver_chk
    global g_on_hadoop
    global g_hadoop_namenode
    global g_r_test_setup
    global g_py_test_setup
    global g_perf
    global g_git_hash
    global g_git_branch
    global g_machine_ip
    global g_date
    global g_build_id
    global g_ncpu
    global g_os
    global g_job_name
    global g_py3
    global g_pycoverage
    global g_use_xml2
    global g_test_ssl
    global g_ldap_username
    global g_ldap_password
    global g_ldap_config
    global g_rest_log
    global g_jvm_opts

    i = 1
    while i < len(argv):
        s = argv[i]

        if s == "--baseport":
            i += 1
            if i >= len(argv):
                usage()
            g_base_port = int(argv[i])
        elif s == "--py3":
            g_py3 = True
        elif s == "--coverage":
            g_pycoverage = True
        elif s == "--numclouds":
            i += 1
            if i >= len(argv):
                usage()
            g_num_clouds = int(argv[i])
        elif s == "--numnodes":
            i += 1
            if i >= len(argv):
                usage()
            g_nodes_per_cloud = int(argv[i])
        elif s == "--wipeall":
            g_wipe_test_state = True
            g_wipe_output_dir = True
        elif s == "--wipe":
            g_wipe_output_dir = True
        elif s == "--test":
            i += 1
            if i >= len(argv):
                usage()
            g_test_to_run = TestRunner.find_test(argv[i])
        elif s == "--testlist":
            i += 1
            if i >= len(argv):
                usage()
            g_test_list_file = argv[i]
        elif s == "--excludelist":
            i += 1
            if i >= len(argv):
                usage()
            g_exclude_list_file = argv[i]
        elif s == "--testgroup":
            i += 1
            if i >= len(argv):
                usage()
            g_test_group = argv[i]
        elif s == "--testsize":
            i += 1
            if i >= len(argv):
                usage()
            v = argv[i]
            if re.match(r'(s)?(m)?(l)?', v):
                if 's' not in v:
                    g_run_small = False
                if 'm' not in v:
                    g_run_medium = False
                if 'l' not in v:
                    g_run_large = False
                if 'x' not in v:
                    g_run_xlarge = False
            else:
                bad_arg(s)
        elif s == "--usecloud":
            i += 1
            if i >= len(argv):
                usage()
            s = argv[i]
            m = re.match(r'(\S+):([1-9][0-9]*)', s)
            if m is None:
                unknown_arg(s)
            g_use_cloud = True
            g_use_ip = m.group(1)
            port_string = m.group(2)
            g_use_port = int(port_string)
        elif s == "--usecloud2":
            i += 1
            if i >= len(argv):
                usage()
            s = argv[i]
            if s is None:
                unknown_arg(s)
            g_use_cloud2 = True
            g_config = s
        elif s == "--client":
            g_use_client = True
        elif s == "--nopass":
            g_nopass = True
        elif s == "--nointernal":
            g_nointernal = True
        elif s == "--c":
            g_convenient = True
        elif s == "--h2ojar":
            i += 1
            g_path_to_h2o_jar = os.path.abspath(argv[i])
        elif s == "--pto":
            i += 1
            g_phantomjs_to = int(argv[i])
        elif s == "--ptt":
            i += 1
            g_phantomjs_packs = argv[i]
        elif s == "--tar":
            i += 1
            g_path_to_tar = os.path.abspath(argv[i])
        elif s == "--whl":
            i += 1
            g_path_to_whl = os.path.abspath(argv[i])
        elif s == "--jvm.xmx":
            i += 1
            if i >= len(argv):
                usage()
            g_jvm_xmx = argv[i]
        elif s == "--jvm.cp":
            i += 1
            if i > len(argv):
                usage()
            g_jvm_cp = argv[i]
        elif s == "--norun":
            g_no_run = True
        elif s == "--noxunit":
            g_produce_unit_reports = False
        elif s == "--jacoco":
            g_jacoco_include = True
            if i + 1 < len(argv):
                s = argv[i + 1]
                m = re.match(r'(?P<includes>([^:,]+(,[^:,]+)*)?):(?P<excludes>([^:,]+(,[^:,]+)*)?)$', s)
                if m is not None:
                    g_jacoco_options[0] = m.group("includes")
                    g_jacoco_options[1] = m.group("excludes")
        elif s == "-h" or s == "--h" or s == "-help" or s == "--help":
            usage()
        elif s == "--rPkgVerChk":
            g_r_pkg_ver_chk = True
        elif s == "--onHadoop":
            g_on_hadoop = True
        elif s == "--hadoopNamenode":
            i += 1
            if i >= len(argv):
                usage()
            g_hadoop_namenode = argv[i]
        elif s == "--perf":
            g_perf = True

            i += 1
            if i >= len(argv):
                usage()
            g_git_hash = argv[i]

            i += 1
            if i >= len(argv):
                usage()
            g_git_branch = argv[i]

            i += 1
            if i >= len(argv):
                usage()
            g_build_id = argv[i]

            i += 1
            if i >= len(argv):
                usage()
            g_job_name = argv[i]
        elif s == "--geterrs":
            g_use_xml2 = True
        elif s == "--test_ssl":
            g_test_ssl = True
        elif s == '--ldap.config':
            i += 1
            if i >= len(argv):
                usage()
            g_ldap_config = argv[i]
        elif s == '--ldap.username':
            i += 1
            if i >= len(argv):
                usage()
            g_ldap_username = argv[i]
        elif s == '--ldap.password':
            i += 1
            if i >= len(argv):
                usage()
            g_ldap_password = argv[i]
        elif s == '--jvm.opts':
            i += 1
            if i >= len(argv):
                usage()
            g_jvm_opts = argv[i]
        elif s == '--restLog':
            g_rest_log = True
        else:
            unknown_arg(s)

        i += 1

    if int(g_use_client) + int(g_use_cloud) + int(g_use_cloud2) > 1:
        print("")
        print("ERROR: --client, --usecloud and --usecloud2 are mutually exclusive.")
        print("")
        sys.exit(1)


def wipe_output_dir():
    """Clear the output directory."""
    print("Wiping output directory.")
    try:
        if os.path.exists(g_output_dir):
            shutil.rmtree(str(g_output_dir))
    except OSError as e:
        print("ERROR: Removing output directory %s failed: " % g_output_dir)
        print("       (errno {0}): {1}".format(e.errno, e.strerror))
        print("")
        sys.exit(1)


def wipe_test_state(test_root_dir):
    """Clear the test state."""
    print("Wiping test state (including random seeds).")
    if True:
        possible_seed_file = os.path.join(test_root_dir, str("master_seed"))
        if os.path.exists(possible_seed_file):
            try:
                os.remove(possible_seed_file)
            except OSError as e:
                print("")
                print("ERROR: Removing seed file failed: " + possible_seed_file)
                print("       (errno {0}): {1}".format(e.errno, e.strerror))
                print("")
                sys.exit(1)
    for d, subdirs, files in os.walk(test_root_dir):
        for s in subdirs:  # top level directory off tests directory
            remove_sandbox(d, s)  # attempt to remove sandbox directory if they exist

            # need to get down to second level
            for e, subdirs2, files2 in os.walk(os.path.join(d, s)):
                for s2 in subdirs2:
                    remove_sandbox(e, s2)

                    # need to get down to third level
                    for f, subdirs3, files3 in os.walk(os.path.join(e, s2)):
                        for s3 in subdirs3:
                            remove_sandbox(f, s3)

                            # this is the level for sandbox for dynamic tests
                            for g, subdirs4, files4 in os.walk(os.path.join(f, s3)):
                                for s4 in subdirs4:
                                    remove_sandbox(g, s4)

                                    # if ("Rsandbox" in s):
                                    #     rsandbox_dir = os.path.join(d, s)
                                    #     try:
                                    #         if sys.platform == "win32":
                                    #             os.system(r'C:/cygwin64/bin/rm.exe -r -f "{0}"'.format(rsandbox_dir))
                                    #         else: shutil.rmtree(rsandbox_dir)
                                    #     except OSError as e:
                                    #         print("")
                                    #         print("ERROR: Removing RSandbox directory failed: " + rsandbox_dir)
                                    #         print("       (errno {0}): {1}".format(e.errno, e.strerror))
                                    #         print("")
                                    #         sys.exit(1)


def remove_sandbox(parent_dir, dir_name):
    """
    This function is written to remove sandbox directories if they exist under the
    parent_dir.

    :param parent_dir: string denoting full parent directory path
    :param dir_name: string denoting directory path which could be a sandbox
    :return: None
    """
    if "Rsandbox" in dir_name:
        rsandbox_dir = os.path.join(parent_dir, dir_name)
        try:
            if sys.platform == "win32":
                os.system(r'C:/cygwin64/bin/rm.exe -r -f "{0}"'.format(rsandbox_dir))
            else:
                shutil.rmtree(rsandbox_dir)
        except OSError as e:
            print("")
            print("ERROR: Removing RSandbox directory failed: " + rsandbox_dir)
            print("       (errno {0}): {1}".format(e.errno, e.strerror))
            print("")
            sys.exit(1)


def main(argv):
    """
    Main program.
    :param argv Command-line arguments
    :return none
    """
    global g_script_name
    global g_num_clouds
    global g_nodes_per_cloud
    global g_output_dir
    global g_test_to_run
    global g_test_list_file
    global g_exclude_list_file
    global g_test_group
    global g_runner
    global g_nopass
    global g_nointernal
    global g_path_to_tar
    global g_path_to_whl
    global g_perf
    global g_git_hash
    global g_git_branch
    global g_machine_ip
    global g_date
    global g_build_id
    global g_ncpu
    global g_os
    global g_job_name
    global g_test_ssl

    g_script_name = os.path.basename(argv[0])

    # Calculate test_root_dir.
    test_root_dir = os.path.realpath(os.getcwd())

    # Calculate global variables.
    g_output_dir = os.path.join(test_root_dir, str("results"))
    g_failed_output_dir = os.path.join(g_output_dir, str("failed"))
    testreport_dir = os.path.join(test_root_dir, str("../build/test-results"))

    # Override any defaults with the user's choices.
    parse_args(argv)

    # Look for h2o jar file.
    h2o_jar = g_path_to_h2o_jar
    if h2o_jar is None:
        possible_h2o_jar_parent_dir = test_root_dir
        while True:
            possible_h2o_jar_dir = os.path.join(possible_h2o_jar_parent_dir, "build")
            possible_h2o_jar = os.path.join(possible_h2o_jar_dir, "h2o.jar")
            if os.path.exists(possible_h2o_jar):
                h2o_jar = possible_h2o_jar
                break

            next_possible_h2o_jar_parent_dir = os.path.dirname(possible_h2o_jar_parent_dir)
            if next_possible_h2o_jar_parent_dir == possible_h2o_jar_parent_dir:
                break

            possible_h2o_jar_parent_dir = next_possible_h2o_jar_parent_dir

    # Wipe output directory if requested.
    if g_wipe_output_dir:
        wipe_output_dir()

    # Wipe persistent test state if requested.
    if g_wipe_test_state:
        wipe_test_state(test_root_dir)

    # Create runner object.
    # Just create one cloud if we're only running one test, even if the user specified more.
    if g_test_to_run is not None:
        g_num_clouds = 1

    g_runner = TestRunner(test_root_dir,
                          g_use_cloud, g_use_cloud2, g_use_client, g_config, g_use_ip, g_use_port,
                          g_num_clouds, g_nodes_per_cloud, h2o_jar, g_base_port, g_jvm_xmx, g_jvm_cp,
                          g_output_dir, g_failed_output_dir, g_path_to_tar, g_path_to_whl, g_produce_unit_reports,
                          testreport_dir, g_r_pkg_ver_chk, g_hadoop_namenode, g_on_hadoop, g_perf, g_test_ssl,
                          g_ldap_config, g_jvm_opts)

    # Build test list.
    if g_exclude_list_file is not None:
        g_runner.read_exclude_list_file(g_exclude_list_file)

    if g_test_to_run is not None:
        g_runner.add_test(g_test_to_run)
    elif g_test_list_file is not None:
        g_runner.read_test_list_file(g_test_list_file)
    else:
        # Test group can be None or not.
        g_runner.build_test_list(g_test_group, g_run_small, g_run_medium, g_run_large, g_run_xlarge, g_nopass,
                                 g_nointernal)

    # If no run is specified, then do an early exit here.
    if g_no_run:
        sys.exit(0)

    # Handle killing the runner.
    signal.signal(signal.SIGINT, signal_handler)
    signal.signal(signal.SIGTERM, signal_handler)

    # Sanity check existence of H2O jar file before starting the cloud.
    if not (h2o_jar and os.path.exists(h2o_jar)):
        print("")
        print("ERROR: H2O jar not found")
        print("")
        sys.exit(1)

    # Run.
    try:
        g_runner.start_clouds()
        g_runner.run_tests(g_nopass)
    finally:
        g_runner.check_clouds()
        g_runner.stop_clouds()
        g_runner.report_summary(g_nopass)

    # If the overall regression did not pass then exit with a failure status code.
    if not g_runner.get_regression_passed():
        sys.exit(1)


if __name__ == "__main__":
    main(sys.argv)
