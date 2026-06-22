module.exports = {
  title: "H2O-3 Enterprise",
  tagline: "Distributed, scalable machine learning for the enterprise",
  url: "https://docs.h2o.ai",
  baseUrl: "/h2o-3-enterprise/", // docs.h2o.ai mount path — placeholder, confirm with docs.h2o.ai owners
  projectName: "h2o-3-enterprise", // GitHub repo name (docs live in documentation/ of this repo)
  feedbackAssignee: "shaunyogeshwaran", // GitHub username that receives doc-feedback issues
  searchFilter: "h2o-3-enterprise", // Algolia facet — requires registration in the central dev_docs_omnisearch crawler
  onBrokenLinks: "warn", // migration-in-progress: pages cross-link to not-yet-migrated targets; revert to "throw" when complete
  markdown: { math: true }, // KaTeX for algorithm/math pages — requires makersaurus with h2oai/makersaurus#296
  dependencies: {
    "@emotion/react": "^11.11.4",
    "@emotion/styled": "^11.11.5",
    "@mui/material": "^5.15.17",
  },
};
