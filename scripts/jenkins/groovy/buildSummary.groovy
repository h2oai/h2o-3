import org.jvnet.hudson.plugins.groovypostbuild.GroovyPostbuildSummaryAction

def call(final boolean updateJobDescription) {
    return new BuildSummary(updateJobDescription)
}

class BuildSummary {

    public static final String RESULT_PENDING = 'pending'
    public static final String RESULT_SUCCESS = 'success'
    public static final String RESULT_FAILURE = 'failure'
    public static final String RESULT_WARNING = 'warning'

    public static final String DETAILS_SECTION_ID = 'details'
    public static final String CHANGES_SECTION_ID = 'changes'

    public static final String TABLE_STYLE = 'border-collapse: collapse'
    public static final String TD_STYLE = 'vertical-align: middle; border: 1px solid #b1b1b1; padding: 0.3em 1em;'
    public static final String TH_STYLE = 'vertical-align: middle; border: 1px solid #b1b1b1; padding: 0.5em;'

    private static final String REPO_URL = 'https://github.com/h2oai/h2o-3'

    private final List<Stage> stageSummaries = []
    private final List<Summary> summaries = []
    private final updateJobDescription

    BuildSummary(final boolean updateJobDescription) {
        this.updateJobDescription = updateJobDescription
    }

    BuildSummary newInstance(final boolean updateJobDescription) {
        return new BuildSummary(updateJobDescription)
    }

    Summary addSummary(final context, final String id, final String title, final String content, final String icon) {
        if (findSummary(id) != null) {
            throw new IllegalArgumentException('Summary with id %s already.exists'.format(id))
        }
        def summary = new Summary(id, title, content, icon)
        summaries.add(summary)
        updateBuildDetailPage(context)
        return summary
    }

    Summary addSummary(final context, final Summary summary) {
        if (findSummary(summary.getId()) != null) {
            throw new IllegalArgumentException('Summary with id %s already.exists'.format(summary.getId()))
        }
        summaries.add(summary)
        updateBuildDetailPage(context)
        return summary
    }

    Summary findSummary(final String id) {
        return summaries.find({it.getId() == id})
    }

    Summary findSummaryOrThrow(final String id) {
        def summary = findSummary(id)
        if (summary == null) {
            throw new IllegalStateException("Cannot find Summary with id %s".format(id))
        }
        return summary
    }

    Summary addDetailsSummary(final context) {
        return addDetailsSummary(context, null)
    }

    Summary addDetailsSummary(final context, final String mode) {
        if (findSummary(DETAILS_SECTION_ID) != null) {
            throw new IllegalArgumentException("Details Summary already exists")
        }
        String modeItem = ""
        if (mode != null) {
            modeItem = "<li><strong>Mode:</strong> ${mode}</li>\n"
        }
        String detailsHtml = """
            <ul>
              ${modeItem}
              <li><strong>Commit Message:</strong> ${context.env.COMMIT_MESSAGE}</li>
              <li><strong>Git Branch:</strong> ${context.env.BRANCH_NAME}</li>
              <li><strong>Git SHA:</strong> ${context.env.GIT_SHA}</li>
            </ul>
        """
        final Summary summary = new Summary(DETAILS_SECTION_ID, 'Details', detailsHtml, 'notepad.gif')
        summaries.add(summary)
        return summary
    }

    Stage addStageSummary(final context, final String stageName, final String stageDirName) {
        if (findStageSummaryWithName(stageName) != null) {
            throw new IllegalArgumentException(String.format("Stage Summary with name %s already defined", stageName))
        }
        def stage = new Stage(stageName, stageDirName)
        stageSummaries.add(stage)
        updateBuildDetailPage(context)
        return stage
    }

    Stage markStageSuccessful(final context, final String stageName) {
        final Stage stage = setStageResult(stageName, RESULT_SUCCESS)
        updateBuildDetailPage(context)
        return stage
    }

    Stage markStageFailed(final context, final String stageName) {
        final Stage stage = setStageResult(stageName, RESULT_FAILURE)
        updateBuildDetailPage(context)
        return stage
    }

    Stage setStageDetails(final context, final String stageName, final String nodeName, final String workspacePath) {
        final Stage stage = findStageSummaryWithNameOrThrow(stageName)
        stage.setNodeName(nodeName)
        stage.setWorkspace(workspacePath)
        updateBuildDetailPage(context)
        return stage
    }

    String getSummaryHTML(final context) {
        List<GroovyPostbuildSummaryAction> summaryActions = context.currentBuild.rawBuild.getActions(GroovyPostbuildSummaryAction.class)
        String result = ''
        for(GroovyPostbuildSummaryAction action : summaryActions) {
            result += createHTMLTitle("<img src=\"${imageLink(context, action.getIconPath(), ImageSize.XLARGE)}\" />")
            result += "${action.getText()}"
        }
        return result
    }

    @NonCPS
    private void updateBuildDetailPage(final context) {
        if (updateJobDescription) {
            String stagesSection = ''
            String stagesTableBody = ''

            if (!stageSummaries.isEmpty()) {
                for (stageSummary in stageSummaries) {
                    def nodeName = stageSummary.getNodeName() ?: 'Not yet allocated'
                    def result = stageSummary.getResult() ?: RESULT_PENDING.capitalize()
                    stagesTableBody += """
                        <tr>
                            <td style="${TD_STYLE}"><img src="${imageLink(context, stageResultToImageName(result), ImageSize.LARGE)}" /></td>
                            <td style="${TD_STYLE}">${stageSummary.getName()}</td>
                            <td style="${TD_STYLE}">${nodeName}</td>
                            <td style="${TD_STYLE}">${stageSummary.getWorkspaceText()}</td>
                            <td style="${TD_STYLE}">${stageSummary.getArtifactsHTML(context)}</td>
                        </tr>
                    """
                }
                stagesSection = createHTMLForSection('Stages Overview', """
                    <table style="${TABLE_STYLE}">
                      <thead>
                        <tr>
                          <th style="${TH_STYLE}"></th>
                          <th style="${TH_STYLE}">Name</th>
                          <th style="${TH_STYLE}">Node</th>
                          <th style="${TH_STYLE}">Workspace</th>
                          <th style="${TH_STYLE}">Artifacts</th>
                        </tr>
                      </thead>
                      <tbody>
                        ${stagesTableBody}
                      </tbody>
                    </table>
                """)
            }

            context.manager.removeSummaries()
            for (Summary summary : summaries) {
                final GroovyPostbuildSummaryAction summaryAction = context.manager.createSummary(summary.getIcon())
                summaryAction.appendText(createHTMLTitle(summary.getTitle()), false)
                summaryAction.appendText(summary.getContent(), false)
            }
            final GroovyPostbuildSummaryAction stagesSummary = context.manager.createSummary('computer.png')
            stagesSummary.appendText(stagesSection, false)
            getSummaryHTML(context)
        }
    }

    private String createHTMLTitle(final String title) {
        return "<h1 style=\"display: inline-block;\">${title}</h1>"
    }

    private String createHTMLForSection(final String title, final String content) {
        return """
            ${createHTMLTitle(title)}
            <div style="margin-left: 15px;">
                ${content}
            </div>
        """
    }

    private Stage setStageResult(final String stageName, final String result) {
        def summary = findStageSummaryWithNameOrThrow(stageName)
        summary.setResult(result)
        return summary
    }

    private String stageResultToImageName(final String result) {
        switch (result) {
            case RESULT_PENDING:
                return 'nobuilt_anime.gif'
            case RESULT_FAILURE:
                return 'red.gif'
            case RESULT_SUCCESS:
                return 'green.gif'
            default:
                return 'red.gif'
        }
    }

    private String imageLink(final context, final String imageName, final ImageSize imageSize=ImageSize.MEDIUM) {
        "${context.env.HUDSON_URL}${Jenkins.RESOURCE_PATH}/images/${imageSize.getSizeString()}/${imageName}"
    }

    private Stage findStageSummaryWithName(final String stageName) {
        return stageSummaries.find({ it.getName() == stageName })
    }

    private Stage findStageSummaryWithNameOrThrow(final String stageName) {
        def summary = findStageSummaryWithName(stageName)
        if (summary == null) {
            throw new IllegalArgumentException('Cannot find StageSummary with name %s'.format(stageName))
        }
        return summary
    }

    private enum ImageSize {
        SMALL('16x16'),
        MEDIUM('24x24'),
        LARGE('32x32'),
        XLARGE('48x48')

        private final String size

        private ImageSize(final String size) {
            this.size = size
        }

        String getSizeString() {
            return size
        }
    }

    static class Summary {
        private final String id
        private final String icon
        private String title
        private String content

        Summary(final String id, final String title, final String content, final String icon) {
            this.id = id
            this.title = title
            this.content = content
            this.icon = icon
        }

        String getId() {
            return id
        }

        String getTitle() {
            return title
        }

        String getContent() {
            return content
        }

        String getIcon() {
            return icon
        }

        void setTitle(String title) {
            this.title = title
        }

        void setContent(String content) {
            this.content = content
        }
    }

    static class Stage {
        private final String name
        private final String stageDirName
        private String nodeName
        private String workspace
        private String result
        private String artifactsLink

        Stage(final String name, final String stageDirName) {
            this.name = name
            this.stageDirName = stageDirName
            this.result = BuildSummary.RESULT_PENDING
        }

        String getName() {
            return name
        }

        String getNodeName() {
            return nodeName
        }

        void setNodeName(String nodeName) {
            this.nodeName = nodeName
        }

        String getWorkspace() {
            return workspace
        }

        void setWorkspace(String workspace) {
            this.workspace = workspace
        }

        String getResult() {
            return result
        }

        void setResult(String result) {
            this.result = result
        }

        String getWorkspaceText() {
            return getWorkspace() ?: 'Not yet allocated'
        }

        String getArtifactsHTML(final context) {
            if (result == BuildSummary.RESULT_PENDING) {
                return 'Not yet available'
            }
            return "<a href=\"${context.currentBuild.rawBuild.getAbsoluteUrl()}artifact/${stageDirName}/\" target=\"_blank\" style=\"color: black;\">Artifacts</a>"
        }
    }

}

return this