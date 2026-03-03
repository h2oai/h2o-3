# Security Policy

## Reporting a Vulnerability

Please report (suspected) security vulnerabilities to support@h2o.ai. You will receive a response from us within 48 hours. 
If the issue is confirmed, we will release a patch as soon as possible depending on complexity but historically within a few days.

## Known Vulnerabilities
We located these vulnerabilites from our security scans. The following list shows the vulnerabilities and the libraries they were found in:

Total: 8 (UNKNOWN: 0, LOW: 2, MEDIUM: 2, HIGH: 0, CRITICAL: 0)

| Library                         | Vulnerability    | Severity | Status   | Installed Version | Fixed Version | Title                                                                 |
|---------------------------------|----------------|---------|---------|-----------------|---------------|----------------------------------------------------------------------|
| commons-lang:commons-lang       | CVE-2025-48924 | MEDIUM  | affected | 2.6             |               | commons-lang/commons-lang: org.apache.commons/commons-lang3: Uncontrolled Recursion vulnerability in Apache Commons Lang [Link](https://avd.aquasec.com/nvd/cve-2025-48924) |
| org.apache.hadoop:hadoop-common | CVE-2024-23454 | LOW     | fixed    | 3.3.6           | 3.4.0         | Apache Hadoop: Temporary File Local Information Disclosure [Link](https://avd.aquasec.com/nvd/cve-2024-23454) |
| org.eclipse.jetty:jetty-http    | CVE-2024-6763  | MEDIUM  |         | 9.4.57.v20241219| 12.0.12       | org.eclipse.jetty:jetty-http: jetty: Jetty URI parsing of invalid authority [Link](https://avd.aquasec.com/nvd/cve-2024-6763) |

