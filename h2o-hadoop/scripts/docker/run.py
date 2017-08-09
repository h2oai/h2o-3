#! /usr/bin/python

import argparse
import subprocess
import os
import sys

DISTRIBUTION_CDH = 'CDH'
SUPPORTED_CDH_VERSIONS = ['5.4', '5.5', '5.6', '5.7', '5.8', '5.10']
DISTRIBUTION_HDP = 'HDP'
SUPPORTED_HDP_VERSIONS = ['2.2', '2.3', '2.4', '2.5', '2.6']
SUPPORTED_DISTRIBUTIONS = [DISTRIBUTION_CDH, DISTRIBUTION_HDP]

def pretty_print_array(array_to_print):
    if array_to_print is None or len(array_to_print) == 0:
        return ""
    if len(array_to_print) == 1:
        return str(array_to_print[0])
    else:
        return "%s and %s" % (", ".join(array_to_print[:-1]), array_to_print[-1])

def init_args_parser():
    supported_distributions_text = pretty_print_array(SUPPORTED_DISTRIBUTIONS)
    parser = argparse.ArgumentParser(description='Runs a Docker container for given Hadoop distribution and version.')
    parser.add_argument('--distribution', type=str, choices=SUPPORTED_DISTRIBUTIONS,
        help='Distribution of Hadoop. Currently %s are supported.' % supported_distributions_text, required=True
    )
    parser.add_argument('--version', type=str, help='Version of the Hadoop distribution.', required=True)
    parser.add_argument('--scripts-path', type=str, help='Path to folder containing custom startup scripts')
    parser.add_argument('--tests-path', type=str, help='Path to folder containing custom python tests')
    parser.add_argument('--h2o-setup', dest='h2o_setup', action='store_true', help='Download the latest nighlty build of H2O')
    parser.add_argument('--h2o-no-setup', dest='h2o_setup', action='store_false', help='Do not download H2O')
    parser.add_argument('--h2o-start', dest='h2o_start', action='store_true', help='Start H2O')
    parser.add_argument('--h2o-no-start', dest='h2o_start', action='store_false', help='Do not start H2O')
    parser.add_argument('--run-tests', dest='tests', action='store_true', help='Run tests')
    parser.add_argument('--no-tests', dest='tests', action='store_false', help='Do not run the tests')
    parser.add_argument('-v', '--volume', type=str, action='append', help='Bind mount a volume')
    parser.add_argument('-p', '--publish', action='append', help='Publish a container\'s port(s) to the host')
    parser.set_defaults(h2o_setup=True)
    parser.set_defaults(h2o_start=True)
    parser.set_defaults(tests=True)
    return parser

if __name__ == '__main__':
    parser = init_args_parser()
    args = parser.parse_args()
    cmd = "docker run "
    if args.scripts_path:
        cmd += "-v %s:/startup/ " % args.scripts_path
    if args.tests_path:
        cmd += "-v %s:/home/h2o/tests/python/ " % args.tests_path
    cmd += "-e SETUP_H2O=%s " % args.h2o_setup
    cmd += "-e START_H2O=%s " % args.h2o_start
    cmd += "-e RUN_TESTS=%s " % args.tests
    if args.volume:
        for volume in args.volume:
            cmd += "-v %s " % volume
    if args.publish:
        for port in args.publish:
            cmd += "-p %s " % publish
    cmd += "h2o-%s:%s" % (args.distribution.lower(), args.version)
    process = subprocess.Popen(cmd.split(), stdout=subprocess.PIPE, stdin=subprocess.PIPE)
    for line in iter(process.stdout.readline, ''):
        sys.stdout.write(line)
