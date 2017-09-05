#! /usr/bin/python

import argparse
import os
import re
import subprocess
import sys

DISTRIBUTION_CDH = 'CDH'
DISTRIBUTION_HDP = 'HDP'
SUPPORTED_DISTRIBUTIONS = [DISTRIBUTION_CDH, DISTRIBUTION_HDP]

SUPPORTED_CDH_VERSIONS = ['5.4', '5.5', '5.6', '5.7', '5.8', '5.10']
SUPPORTED_HDP_VERSIONS = ['2.2', '2.3', '2.4', '2.5', '2.6']

SUPPORTED_SPARK_VERSIONS = [
    '1.6.0', '1.6.1', '1.6.2', '1.6.3',
    '2.0.0', '2.0.1', '2.0.2',
    '2.1.0', '2.1.1',
    '2.2.0'
]


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
                        help='Distribution of Hadoop. Currently %s are supported.' % supported_distributions_text,
                        required=True
                        )
    parser.add_argument('--version', type=str, help='Version of the Hadoop distribution.', required=True)
    parser.add_argument('--scripts-path', type=str, help='Path to folder containing custom startup scripts.')
    parser.add_argument('-v', '--volume', type=str, action='append', help='Bind mount a volume.')
    parser.add_argument('-p', '--publish', action='append', help='Publish a container\'s port(s) to the host.')
    parser.add_argument('-s', '--spark-version', choices=SUPPORTED_SPARK_VERSIONS,
                        help="Version of Spark to point to.")
    parser.add_argument('-c', '--custom-spark-path', type=str, help='Path to custom Spark')
    parser.add_argument('-t', '--tag', type=str, help="Tag of the docker image.")
    parser.add_argument('-w', '--sparkling-water', type=str, help="Version of Sparkling Water which should be downloaded")
    return parser


if __name__ == '__main__':
    parser = init_args_parser()
    args = parser.parse_args()
    cmd = "docker run "
    if args.scripts_path:
        cmd += "-v %s:/startup/ " % os.path.abspath(args.scripts_path)
    if args.volume:
        for volume in args.volume:
            cmd += "-v %s " % os.path.abspath(volume)
    if args.publish:
        for port in args.publish:
            cmd += "-p %s " % args.publish
    tag = "h2o-%s:%s" % (args.distribution.lower(), args.version)
    if args.spark_version:
        cmd += "-e ACTIVATE_SPARK=%s " % args.spark_version
        if not args.tag:
            images = []
            output = subprocess.check_output(["docker", "images"])
            for line in output.split('\n'):
                line = line.lstrip()
                if line.startswith('h2o-%s-spark' % args.distribution.lower()):
                    image = re.search("h2o-%s-spark[^\s]*" % args.distribution.lower(), line).group(0)
                    if args.spark_version in image:
                        images.append(image)
                        tag = "%s:%s" % (image, args.version)
            if len(images) > 0:
                print(
                "There are more docker imgages supporting Spark %s. Please select which to run: " % args.spark_version)
                for i in range(len(images)):
                    print ("[%s] %s" % ((i + 1), images[i]))
                input = raw_input('Run: ')
                try:
                    choice = int(input)
                    if choice < 1:
                        raise ValueError('Selection < 1')
                    tag = "%s:%s" % (images[choice - 1], args.version)
                except ValueError as e:
                    print("Selection not valid")
                    exit(1)
    if args.sparkling_water:
        cmd += "-e DOWNLOAD_SW=%s " % args.sparkling_water
    if args.tag:
        tag = "%s:%s" % (args.tag, args.version)
    cmd += tag
    print("Running container with cmd: %s" % cmd)
    try:
        process = subprocess.Popen(cmd.split(), stdout=subprocess.PIPE)
        for line in iter(process.stdout.readline, ''):
            sys.stdout.write(line)
    except KeyboardInterrupt as e:
        process.kill()
