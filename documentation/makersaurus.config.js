// Release version stamped by CI (e.g. "3.46.0.12"). Set by the release
// workflow from the build's PROJECT_VERSION — the same value baked into the
// binary and the download page (SUBST_PROJECT_VERSION in make-dist.sh) — so
// every published docs build states exactly which H2O-3 Enterprise release it
// documents. Unset for local/dev builds, which then show no version banner.
const projectVersion = process.env.H2O_PROJECT_VERSION;
const baseUrl = process.env.BASE_URL || "/h2o-3-enterprise/";

module.exports = {
  title: "H2O-3 Enterprise",
  tagline: "Distributed, scalable machine learning for the enterprise",
  url: "https://docs.h2o.ai",
  baseUrl,
  projectName: "h2o-3-enterprise", // GitHub repo name (docs live in documentation/ of this repo)
  feedbackAssignee: "shaunyogeshwaran", // GitHub username that receives doc-feedback issues
  searchFilter: "h2o-3-enterprise", // Algolia facet — requires registration in the central dev_docs_omnisearch crawler
  onBrokenLinks: "warn", // migration-in-progress: pages cross-link to not-yet-migrated targets; revert to "throw" when complete
  markdown: { math: true }, // KaTeX for algorithm/math pages — requires makersaurus with h2oai/makersaurus#296
  ...(projectVersion && {
    // The id embeds the version so the banner reappears after each release,
    // even for users who dismissed the previous one.
    announcementBar: {
      id: `h2o-3-enterprise-${projectVersion}`,
      content: `This documentation is for H2O-3 Enterprise <b>${projectVersion}</b> — see the <a href="${baseUrl}release-notes">release notes</a>.`,
      backgroundColor: "#fec925",
      isCloseable: true,
    },
    versionCustomField: projectVersion, // exposed as siteConfig.customFields for future components
  }),
  dependencies: {
    "@emotion/react": "^11.11.4",
    "@emotion/styled": "^11.11.5",
    "@mui/material": "^5.15.17",
  },
};
