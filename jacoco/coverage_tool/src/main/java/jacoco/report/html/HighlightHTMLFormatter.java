/*******************************************************************************
 * Copyright (c) 2009, 2016 Mountainminds GmbH & Co. KG and Contributors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Marc R. Hoffmann - initial API and implementation
 *
 *******************************************************************************/
package jacoco.report.html;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import jacoco.report.internal.html.table.HighlightTable;

import org.jacoco.core.analysis.IBundleCoverage;
import org.jacoco.core.analysis.ICoverageNode.CounterEntity;
import org.jacoco.core.data.ExecutionData;
import org.jacoco.core.data.SessionInfo;
import org.jacoco.report.IMultiReportOutput;
import org.jacoco.report.IReportGroupVisitor;
import org.jacoco.report.IReportVisitor;
import org.jacoco.report.ISourceFileLocator;
import org.jacoco.report.html.HTMLFormatter;
import org.jacoco.report.internal.ReportOutputFolder;
import org.jacoco.report.internal.html.HTMLGroupVisitor;
import org.jacoco.report.internal.html.ILinkable;
import org.jacoco.report.internal.html.index.ElementIndex;
import org.jacoco.report.internal.html.index.IIndexUpdate;
import org.jacoco.report.internal.html.page.BundlePage;
import org.jacoco.report.internal.html.page.ReportPage;
import org.jacoco.report.internal.html.page.SessionsPage;
import org.jacoco.report.internal.html.resources.Resources;
import org.jacoco.report.internal.html.resources.Styles;
import org.jacoco.report.internal.html.table.BarColumn;
import org.jacoco.report.internal.html.table.CounterColumn;
import org.jacoco.report.internal.html.table.LabelColumn;
import org.jacoco.report.internal.html.table.PercentageColumn;

/**
 * Formatter for coverage reports in multiple HTML pages.
 */
public class HighlightHTMLFormatter extends HTMLFormatter {

    private Locale locale = Locale.getDefault();

    private Resources resources;

    private ElementIndex index;

    private SessionsPage sessionsPage;

    private HighlightTable table;

    /**
     * Sets the locale used for report rendering. The current default locale is
     * used by default.
     *
     * @param locale
     *            locale used for report rendering
     */
    @Override
    public void setLocale(final Locale locale) {
        this.locale = locale;
    }

    // === IHTMLReportContext ===

    @Override
    public Resources getResources() {
        return resources;
    }

    @Override
    public HighlightTable getTable() {
        if (table == null) {
            table = createTable();
        }
        return table;
    }

    private HighlightTable createTable() {
        final HighlightTable t = new HighlightTable();
        t.add("Element", null, null, new LabelColumn(), false);
        t.add("Missed Instructions", CounterEntity.INSTRUCTION, Styles.BAR, new BarColumn(CounterEntity.INSTRUCTION,
                locale), true);
        t.add("Cov.", CounterEntity.INSTRUCTION, Styles.CTR2,
                new PercentageColumn(CounterEntity.INSTRUCTION, locale), false);
        t.add("Missed Branches", CounterEntity.BRANCH, Styles.BAR, new BarColumn(CounterEntity.BRANCH, locale),
                false);
        t.add("Cov.", CounterEntity.BRANCH, Styles.CTR2, new PercentageColumn(CounterEntity.BRANCH, locale),
                false);
        addMissedTotalColumns(t, "Cxty", CounterEntity.COMPLEXITY);
        addMissedTotalColumns(t, "Lines", CounterEntity.LINE);
        addMissedTotalColumns(t, "Methods", CounterEntity.METHOD);
        addMissedTotalColumns(t, "Classes", CounterEntity.CLASS);
        return t;
    }

    private void addMissedTotalColumns(final HighlightTable table, final String label,
                                       final CounterEntity entity) {
        table.add("Missed", entity, Styles.CTR1,
                CounterColumn.newMissed(entity, locale), false);
        table.add(label, entity, Styles.CTR2, CounterColumn.newTotal(entity, locale),
                false);
    }

    @Override
    public ILinkable getSessionsPage() {
        return sessionsPage;
    }

    @Override
    public IIndexUpdate getIndexUpdate() {
        return index;
    }

    @Override
    public Locale getLocale() {
        return locale;
    }

    /**
     * Creates a new visitor to write a report to the given output.
     *
     * @param output
     *            output to write the report to
     * @return visitor to emit the report data to
     * @throws IOException
     *             in case of problems with the output stream
     */
    @Override
    public IReportVisitor createVisitor(final IMultiReportOutput output)
            throws IOException {
        final ReportOutputFolder root = new ReportOutputFolder(output);
        resources = new Resources(root);
        resources.copyResources();
        index = new ElementIndex(root);
        return new IReportVisitor() {

            private List<SessionInfo> sessionInfos;
            private Collection<ExecutionData> executionData;

            private HTMLGroupVisitor groupHandler;

            public void visitInfo(final List<SessionInfo> sessionInfos,
                                  final Collection<ExecutionData> executionData)
                    throws IOException {
                this.sessionInfos = sessionInfos;
                this.executionData = executionData;
            }

            public void visitBundle(final IBundleCoverage bundle,
                                    final ISourceFileLocator locator) throws IOException {
                final BundlePage page = new BundlePage(bundle, null, locator,
                        root, HighlightHTMLFormatter.this);
                createSessionsPage(page);
                page.render();
            }

            public IReportGroupVisitor visitGroup(final String name)
                    throws IOException {
                groupHandler = new HTMLGroupVisitor(null, root,
                        HighlightHTMLFormatter.this, name);
                createSessionsPage(groupHandler.getPage());
                return groupHandler;

            }

            private void createSessionsPage(final ReportPage rootpage) {
                sessionsPage = new SessionsPage(sessionInfos, executionData,
                        index, rootpage, root, HighlightHTMLFormatter.this);
            }

            public void visitEnd() throws IOException {
                if (groupHandler != null) {
                    groupHandler.visitEnd();
                }
                sessionsPage.render();
                output.close();
            }
        };
    }
}
