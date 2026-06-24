module.exports = {
  title: "H2O-3 Enterprise",
  tagline: "Distributed, scalable machine learning for the enterprise",
  url: "https://docs.h2o.ai",

  baseUrl: "/h2o-3-enterprise/",
  projectName: "h2o-3-enterprise",
  feedbackAssignee: "shaunyogeshwaran",
  searchFilter: "h2o-3-enterprise",
  onBrokenLinks: "warn",
  markdown: { math: true },

  // Versioning: the live site serves the latest released version only; the
  // in-progress `docs/` (current) is hidden until cut into a release snapshot.
  includeCurrentVersion: false,
  lastVersion: "v3.47.0",
  versions: {
    "v3.47.0": { label: "v3.47.0", path: "/" },
  },

  // Surface the latest release and link to the per-version release notes.
  // Bump BOTH `id` and `content` every release — Docusaurus keys the dismissed
  // state on `id`, so reusing it hides the new banner from anyone who closed the old one.
  /* announcementBar: {
    id: "latest-release-3-46-0-10",
    content:
      '<a href="/h2o-3-enterprise/release-notes">H2O-3 Enterprise v3.46.0.10 is out — see the highlights and which CVEs were patched.</a>',
    backgroundColor: "#fec925",
    textColor: "#000000",
    isCloseable: true,
  },
  */

  dependencies: {
    "@emotion/react": "^11.11.4",
    "@emotion/styled": "^11.11.5",
    "@mui/material": "^5.15.17",
  },
};
