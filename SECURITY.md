# Security Policy

## Reporting a Vulnerability

Please report (suspected) security vulnerabilities to support@h2o.ai. You will receive a response from us within 48 hours. 
If the issue is confirmed, we will release a patch as soon as possible depending on complexity but historically within a few days.

## Known Vulnerabilities
We located these vulnerabilites from our security scans. The following list shows the vulnerabilities and the libraries they were found in:

Total: 2 (UNKNOWN: 0, LOW: 0, MEDIUM: 2, HIGH: 0, CRITICAL: 0)

| Library                         | Vulnerability    | Severity | Installed Version | Fixed Version | Title | Mitigation Status |
|---------------------------------|----------------|---------|-----------------|---------------|-------|-------------------|
| commons-lang:commons-lang       | CVE-2025-48924 | MEDIUM  | 2.6             |               | Uncontrolled Recursion vulnerability in `ClassUtils.getClass()` [Link](https://avd.aquasec.com/nvd/cve-2025-48924) | Not affected. H2O does not use `ClassUtils` anywhere in the codebase. H2O only uses safe utility methods from this library (e.g., `ArrayUtils`, `StringUtils.join`, `StringUtils.repeat`). |
| org.eclipse.jetty:jetty-http    | CVE-2024-6763  | MEDIUM  | 9.4.57.v20241219| 12.0.12       | Jetty URI parsing of invalid authority [Link](https://avd.aquasec.com/nvd/cve-2024-6763) | Not affected. The vulnerability only affects applications that use `HttpURI` directly as a utility for URI validation. H2O does not use `HttpURI` in application code; only Jetty's own internal `Response.encodeURL()` references it, which the [Jetty advisory](https://github.com/jetty/jetty.project/security/advisories/GHSA-qh8g-58pp-2wxh) confirms is not vulnerable. |

