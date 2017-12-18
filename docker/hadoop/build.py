#! /usr/bin/python

import argparse
import subprocess
import sys
import os

DISTRIBUTION_CDH = 'CDH'
DISTRIBUTION_HDP = 'HDP'
SUPPORTED_DISTRIBUTIONS = [DISTRIBUTION_CDH, DISTRIBUTION_HDP]

SUPPORTED_CDH_VERSIONS = ['5.4', '5.5', '5.6', '5.7', '5.8', '5.10']
SUPPORTED_HDP_VERSIONS = ['2.2', '2.3', '2.4', '2.5', '2.6']


def pretty_print_array(array_to_print):
    if array_to_print is None or len(array_to_print) == 0:
        return ""
    if len(array_to_print) == 1:
        return str(array_to_print[0])
    return "%s and %s" % (", ".join(array_to_print[:-1]), array_to_print[-1])


def init_args_parser():
    args_parser = argparse.ArgumentParser(description='Builds a docker image for given Hadoop distribution and version.')
    args_parser.add_argument('-d', '--distribution',
                             type=str, choices=SUPPORTED_DISTRIBUTIONS,
                             help='Hadoop Distribution.',
                             required=True
                             )
    args_parser.add_argument('-v', '--version', type=str,
                             help='Version of the Hadoop distribution.', required=True
                             )
    args_parser.add_argument('-t', '--tag', type=str, help="Image tag.")
    args_parser.add_argument('-u', '--uid', type=int, help="UID of user h2o. Default is the uid of user invoking this script.")
    return args_parser


def validate_version(distribution, version):
    message = 'Version %s of %s not supported. Supported versions are %s'
    if distribution == DISTRIBUTION_CDH:
        if version not in SUPPORTED_CDH_VERSIONS:
            parser.error(message % (version, distribution, pretty_print_array(SUPPORTED_CDH_VERSIONS)))
    elif distribution == DISTRIBUTION_HDP:
        if version not in SUPPORTED_HDP_VERSIONS:
            parser.error(message % (version, distribution, pretty_print_array(SUPPORTED_HDP_VERSIONS)))
    else:
        raise ValueError('Distribution %s not supported' % distribution)


if __name__ == '__main__':
    parser = init_args_parser()
    args = parser.parse_args()
    validate_version(args.distribution, args.version)
    print("Building Docker image for %s version %s" % (args.distribution, args.version))

    if args.tag:
        tag = args.tag
    else:
        tag = "h2o-%s-%s:latest" % (args.distribution.lower(), args.version)

    uid = os.getuid()
    if args.uid:
        uid = args.uid

    cmd = "docker build -t %s --build-arg VERSION=%s --build-arg PATH_PREFIX=%s --build-arg DEFAULT_USER_UID=%s -f %s/Dockerfile ." % (
        tag, args.version, args.distribution.lower(), uid, args.distribution.lower())
    print("Building image with cmd: %s" % cmd)
    process = subprocess.Popen(cmd.split(), stdout=subprocess.PIPE, stdin=subprocess.PIPE)
    for line in iter(process.stdout.readline, ''):
        sys.stdout.write(line)
