---
title: Release notes
---

# Release notes

The release and version history for H2O-3 Enterprise. Binaries are available to
licensed customers, but this history is **public** — scan it to see exactly
what shipped in each version, including the security vulnerabilities patched, so
you can tell what's in your version at a glance.

:::note Prototype
This page is a format prototype, auto-populated from the H2O-3 `Changes.md`
changelog (latest 12 releases). Dates are shown as recorded in the source and a
few still need verification before publish.
:::

| Version | Released | Highlights | Security fixes | API / compatibility |
|---|---|---|---|---|
| **3.46.0.10** | Mar 12, 2026 | • Control-variable MOJO support (regression & binomial)<br/>• Added R 4.5 support<br/>• GAM / GLM / ModelSelection fixes | • log4j `CVE-2025-68161`<br/>• jackson-databind `GHSA-72hv-8253-57qq`<br/>• FedRAMP remediation<br/>• Blocked vulnerable PostgreSQL JDBC params | • Dropped Python 3.6<br/>• Added R 4.5 |
| **3.46.0.9** | Nov 24, 2025 | • Control variables in GLM (regression & binomial)<br/>• Fixed GLM AIC calculation<br/>• Fixed `relevel` with special characters | • `CVE-2024-7768` (H2O-3) | — |
| **3.46.0.8** | Oct 8, 2025 | • CoxPH MOJOs from 3.32.x now uploadable<br/>• Fixed XGBoost MOJO scoring with offset column<br/>• GLM grid accepts `lambda_` alias | • MySQL JDBC `CVE-2025-6544` / `CVE-2025-5662`<br/>• commons-beanutils `CVE-2025-48734`<br/>• commons-lang3 `CVE-2024-48924`<br/>• nimbus-jose-jwt `CVE-2025-53864`<br/>• protobuf-java `CVE-2024-7254` | • GLM grid: `lambda_` alias<br/>• Docker image no longer runs as root |
| **3.46.0.7** | Mar 27, 2025 | • Removed Hadoop HDP packages (vendor EOL)<br/>• Fixed `uplift_drf` demo notebook | • mina-core `CVE-2024-52046` | • Removed Hadoop HDP packages |
| **3.46.0.6** | Jan 11, 2024 | • HGLM is now a standalone algorithm (Gaussian)<br/>• Adjustable Parquet import timezone<br/>• Constrained-GLM fixes | • JDBC param validation `CVE-2024-8862`<br/>• avro 1.11.4 `CVE-2024-47561`<br/>• commons-collections `sonatype-2024-3350`<br/>• `CVE-2024-5979` (AstRunTool crash) | • HGLM moved from a parameter to a standalone algorithm |
| **3.46.0.5** | Aug 28, 2024 | • Load data from Snowflake via JDBC<br/>• ModelSelection categorical-predictor fix<br/>• MOJO for Isolation Forest & Extended IF | • jackson-databind 2.17.2 `sonatype-2024-0171`<br/>• dnsjava 3.6.0 `CVE-2024-25638` | — |
| **3.46.0.4** | Jul 9, 2024 | • Security-focused maintenance release<br/>• User-guide updates (clients, data ingest) | • jackson-databind `PRISMA-2023-0067` | — |
| **3.46.0.3** | Jun 11, 2024 | • WebSocket support in `steam.jar`<br/>• Auto multi-thread for `as_data_frame`<br/>• Explainability plotting fixes | — | • `as_data_frame` can auto-enable multi-threading |
| **3.46.0.2** | May 13, 2024 | • ZSTD compression support<br/>• Linear constraints in the GLM toolbox<br/>• XGBoost `gblinear` parameter support | • aws-java-sdk `CVE-2024-21634`<br/>• commons-configuration2 `CVE-2024-29131` | • Removed `custom_metric_func` from ModelSelection |
| **3.46.0.1** | Mar 13, 2024 | • MLflow flavors for MOJOs / POJOs<br/>• Custom-metric support for XGBoost<br/>• MLI for Uplift DRF (PDP + variable importance)<br/>• GLM loglikelihood & AIC for built models | • POJO import disabled by default `CVE-2023-6016`<br/>• jackson-databind `CVE-2023-35116`<br/>• nimbus-jose-jwt `SNYK-...-6247633`<br/>• Filesystem access filter `CVE-2023-6038` / `CVE-2023-6569` | • Java property to disable auto POJO import<br/>• Filesystem read/write filter option |
| **3.44.0.3** | Dec 20, 2023 | • AdaBoost deep-learning weak learner<br/>• Scoring history for Extended Isolation Forest<br/>• polars-based DataFrame transforms | • nanohttpd replaced `CVE-2022-21230` | • `H2OFrame` constructor accepts an existing `H2OFrame` |
| **3.44.0.2** | Nov 8, 2023 | • Binomial `thresholds_and_metric_scores` fix<br/>• CoxPH learning-curve plot fix<br/>• Friedman–Popescu H-statistic docs | • jython / jnr-posix `CWE-416` | • Renamed `partial_plot` `data` parameter → `frame` |

Releases prior to 3.44.0.2 are in the full [changelog](/release-notes) (link TBD).
