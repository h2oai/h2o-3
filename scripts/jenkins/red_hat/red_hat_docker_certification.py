import argparse
import sys
import time

import requests


def has_scan_errors(digest, pid, api_key):
    """
    Waits for image scan to complete and checks the results. Prints errors found.

    :param digest: A digest of the docker image to check errors of
    :param pid: Project identified (PID) from Red Hat Connect portal
    :param api_key: API Key associated with the Red Hat account. Considered secret.
    :return: True if errors are found, otherwise false.
    """
    url = "https://connect.redhat.com/api/v2/container/{}/certResults/{}".format(pid, digest)
    headers = {"accept": "*/*",
               "Authorization": "Bearer {}".format(api_key)}

    # Wait for HTTP 200 Response from the certification results endpoint
    response = None
    while response is None:
        intermediate_response = requests.get(url=url, headers=headers)
        if intermediate_response.status_code == 200:
            response = intermediate_response
        else:
            time.sleep(5)

    # Iterate over scan results, print them and search for erroneous states
    scan_err_present = False
    print("Scan of image '{}' complete.".format(digest))
    for requirement, check_result in response.json()["data"]["results"].items():
        print("{}: {}".format(requirement, check_result))
        if not check_result:
            scan_err_present = True

    if scan_err_present:
        print("Scan errors found. Please address the issues and push the image again.")

    return scan_err_present


def publish(digest, pid, api_key, tag):
    """
    Publishes given image identified by given image digest. Prints the result of this operation.
    Latest tag is automatically applied.
    :param digest: A digest of the docker image to check errors of
    :param pid: Project identified (PID) from Red Hat Connect portal
    :param api_key: API Key associated with the Red Hat account. Considered secret.
    :param tag: An existing container tag for container identification
    :return: Nothing.
    """
    url = "https://connect.redhat.com/api/v2/projects/{}/containers/{}/tags/{}/publish".format(pid, digest, tag)
    headers = {"accept": "*/*",
               "Content-Type": "application/json",
               "Authorization": "Bearer {}".format(api_key)}

    response = requests.post(url, headers=headers)

    if response.status_code != 201:
        print("Unable to publish, invalid status code: {}.".format(response.status_code))
        print(response)
        print(response.content)
        sys.exit(1)
    else:
        print("Docker image '{}' successfully scheduled for publishing.".format(digest))


def extract_arguments(args):
    parsed_args = args.parse_args()
    return parsed_args.digest, parsed_args.pid, parsed_args.api_key, parsed_args.tag


if __name__ == '__main__':
    args = argparse.ArgumentParser("H2O Operator Red Hat certification tool")
    args.add_argument("digest", help="Digest (typically SHA256) of the Docker image intended for certification",
                      metavar="D", type=str)
    args.add_argument("--pid", required=True, help="Red Hat Project ID (PID). To be found in connect.redhat.com",
                      type=str)
    args.add_argument("--api_key", required=True, help="API Key for Red Hat Connect Portal API", type=str)
    args.add_argument("--tag", required=True,
                      help="An existing tag of the docker image published. Make sure the docker image was pushed with this tag for scanning.")

    digest, pid, api_key, tag = extract_arguments(args)
    if not has_scan_errors(digest=digest, pid=pid, api_key=api_key):
        publish(digest=digest, pid=pid, api_key=api_key, tag=tag)
    else:
        sys.exit(1)
