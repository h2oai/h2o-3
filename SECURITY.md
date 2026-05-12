# Security Policy

## Reporting a Vulnerability

Please report (suspected) security vulnerabilities to support@h2o.ai. You will receive a response from us within 48 hours. 
If the issue is confirmed, we will release a patch as soon as possible depending on complexity but historically within a few days.

## Known Vulnerabilities
We located these vulnerabilites from our security scans. The following list shows the vulnerabilities and the libraries they were found in:

Total: 4 (UNKNOWN: 0, LOW: 2, MEDIUM: 2, HIGH: 0, CRITICAL: 0)

| Library                         | Vulnerability    | Severity | Installed Version | Fixed Version | Title | Mitigation Status |
|---------------------------------|----------------|---------|-----------------|---------------|-------|-------------------|
| commons-lang:commons-lang       | CVE-2025-48924 | MEDIUM  | 2.6             |               | Uncontrolled Recursion vulnerability in `ClassUtils.getClass()` [Link](https://avd.aquasec.com/nvd/cve-2025-48924) | Not affected. H2O does not use `ClassUtils` anywhere in the codebase. H2O only uses safe utility methods from this library (e.g., `ArrayUtils`, `StringUtils.join`, `StringUtils.repeat`). |
| org.eclipse.jetty:jetty-http    | CVE-2024-6763  | MEDIUM  | 9.4.57.v20241219| 12.0.12       | Jetty URI parsing of invalid authority [Link](https://avd.aquasec.com/nvd/cve-2024-6763) | Not affected. The vulnerability only affects applications that use `HttpURI` directly as a utility for URI validation. H2O does not use `HttpURI` in application code; only Jetty's own internal `Response.encodeURL()` references it, which the [Jetty advisory](https://github.com/jetty/jetty.project/security/advisories/GHSA-qh8g-58pp-2wxh) confirms is not vulnerable. |
| org.apache.hadoop:hadoop-common | CVE-2024-23454 | LOW     | 3.3.6           | 3.4.0         | Apache Hadoop: Temporary File Local Information Disclosure [Link](https://avd.aquasec.com/nvd/cve-2024-23454) | Not affected. The vulnerability involves Hadoop's `FileUtil.createTempFile()` creating temporary files with world-readable permissions (0666). H2O does not use Hadoop's `FileUtil` for temporary file creation. All temp file operations use Java's standard `File.createTempFile()`, and credential/keytab files are written via Java's `FileOutputStream` directly. |
| org.eclipse.jetty:jetty-http    | CVE-2025-11143 | LOW     | 9.4.57.v20241219| 12.0.31, 12.1.5 | Security bypass due to different URI parsing between Jetty HttpURI and java.net.URI [Link](https://avd.aquasec.com/nvd/cve-2025-11143) | Not affected. The vulnerability requires an application to use both Jetty's `HttpURI` and Java's `java.net.URI` for security decisions, creating a parsing inconsistency bypass. H2O only uses Jetty's servlet API (`getServletPath()`) for URI extraction, does not use `HttpURI` or `java.net.URI` for security-critical comparisons, and its authentication constraint uses a blanket wildcard `/*` path that applies to all requests regardless of URI parsing. |

