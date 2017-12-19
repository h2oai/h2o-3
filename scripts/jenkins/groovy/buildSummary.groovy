class BuildSummary {

    public static final String RESULT_SUCCESS = 'success'
    public static final String RESULT_FAILURE = 'failure'

    private final List stageSummaries = []

    def addStageSummary(final String stageName) {
        if (findForName(stageName) != null) {
            throw new IllegalArgumentException("Stage Summary for %s already added".format(stageName))
        }
        def summary = [
                stageName: stageName
        ]
        stageSummaries.add(summary)
        return summary
    }

    def setStageNode(final String stageName, final String nodeName) {
        def summary = findOrThrowForName(stageName)
        summary['nodeName'] = nodeName
        return summary
    }

    def setStageResult(final String stageName, final String result) {
        def summary = findOrThrowForName(stageName)
        summary['result'] = result
        return summary
    }

    def getStageSummaries() {
        return stageSummaries
    }

    @Override
    String toString() {
        return "${stageSummaries}"
    }

    private def findForName(final String stageName) {
        return stageSummaries.find({it.stageName == stageName})
    }

    private def findOrThrowForName(final String stageName) {
        def summary = findForName(stageName)
        if (summary == null) {
            throw new IllegalStateException("Cannot find StageSummary for %s".format(stageName))
        }
        return summary
    }

}

return new BuildSummary()