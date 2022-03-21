def call() {
    return new HealthChecker()
}

class HealthChecker {

    private static final int ROOT_REQUIRED_FREE_SPACE_GB = 10
    private static final int HOME_REQUIRED_FREE_SPACE_GB = 100


    private List<HealthProblem> healthProblems = []

    boolean checkHealth(final context, final String node, final String dockerImage, final String dockerRegistry, final buildConfig) {
        boolean healthy = true
        String cause = ''
        final String checkRootSpaceCmd = createSpaceCheckCmd(ROOT_REQUIRED_FREE_SPACE_GB, '/')
        final int rootSpaceCheckResult = context.sh(script: checkRootSpaceCmd, returnStatus: true)
        if (rootSpaceCheckResult != 0) {
            cause = "Free space check of / failed"
            healthy = false
        }

        final String checkHomeSpaceCmd = createSpaceCheckCmd(HOME_REQUIRED_FREE_SPACE_GB, '${HOME}')
        final int homeSpaceCheckResult = context.sh(script: checkHomeSpaceCmd, returnStatus: true)
        if (homeSpaceCheckResult != 0) {
            cause = "Free space check of \${HOME} failed"
            healthy = false
        }

        def insideDocker = context.load('h2o-3/scripts/jenkins/groovy/insideDocker.groovy')
        try {
            insideDocker([], dockerImage, dockerRegistry, buildConfig, 90, 'SECONDS') {
                context.echo 'Docker health check passed'
            }
        } catch (Exception e) {
            context.echo "${e}"
            context.echo "${e}"
            cause = "Docker health check failed"
            healthy = false
        }

        if (!healthy) {
            final boolean nodeReported = healthProblems.find {it.getNode() == node} != null
            if (!nodeReported) {
                healthProblems += new HealthProblem(cause, node)
            }
        }
        return healthy
    }

    String getHealthyNodesLabel(final String defaultNodesLabel) {
        String result = defaultNodesLabel
        for (HealthProblem healthProblem : healthProblems) {
            result += " && !${healthProblem.getNode()}"
        }
        return result
    }

    List<HealthProblem> getHealthProblems() {
        return healthProblems
    }

    String toEmail(final context, final pipelineContext) {
        final def benchmarksSummary = pipelineContext.getBuildSummary().newInstance(false)

        String rowsHTML = ''
        for (HealthProblem healthProblem in healthProblems) {
            rowsHTML += """
                <tr>
                    <td style="${benchmarksSummary.TD_STYLE}">${healthProblem.getNode()}</td>
                    <td style="${benchmarksSummary.TD_STYLE}">${healthProblem.getCause()}</td>
                </tr>
            """
        }
        final String warningsTable = """
            <table style="${benchmarksSummary.TABLE_STYLE}">
                <thead>
                    <tr>
                        <th style=\"${benchmarksSummary.TH_STYLE}\">Node</th>
                        <th style=\"${benchmarksSummary.TH_STYLE}\">Cause</th>
                    </tr>
                </thead>
                <tbody>
                    ${rowsHTML}
                </tbody>
            </table>
        """
        benchmarksSummary.addSection(this, 'warnings', 'Unhealthy nodes', warningsTable)
        return benchmarksSummary.getSummaryHTML(context)
    }

    private String createSpaceCheckCmd(final int minFreeSpaceGB, final String path) {
        return """
            available=\$(df -BG ${path} --output=avail | tail -1 | tr -d %[:space:]G)
            if [ \$available -lt ${minFreeSpaceGB} ]; then 
                echo "Disk space utilization for ${path} exceeded -> Available \${available}GB, required is at least ${minFreeSpaceGB}GB";
                exit 1 
            else 
                echo "${path} space check passed"
            fi
        """
    }

    static class HealthProblem {
        private final String cause
        private final String node

        HealthProblem(String cause, String node) {
            this.cause = cause
            this.node = node
        }

        String getCause() {
            return cause
        }

        String getNode() {
            return node
        }
    }

}

return this
