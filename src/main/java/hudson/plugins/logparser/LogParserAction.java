package hudson.plugins.logparser;

import hudson.Functions;
import hudson.model.Action;
import hudson.model.AbstractBuild;
import hudson.model.Job;
import hudson.model.Run;
import hudson.plugins.logparser.action.LogParserProjectAction;
import hudson.util.Area;
import hudson.util.ChartUtil;
import hudson.util.ColorPalette;
import hudson.util.DataSetBuilder;
import hudson.util.ShiftedCategoryAxis;
import hudson.util.StackedAreaRenderer2;

import jenkins.tasks.SimpleBuildStep;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

import java.util.Collection;
import java.util.Collections;

import javax.imageio.ImageIO;
import javax.servlet.ServletException;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.StackedAreaRenderer;
import org.jfree.data.category.CategoryDataset;
import org.jfree.ui.RectangleInsets;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

public class LogParserAction implements Action, SimpleBuildStep.LastBuildAction {

    final private Run<?, ?> build;
    final private LogParserResult result;
    final private boolean showGraphs;

    private static String urlName = "parsed_console";

    // Fully transparent fill so the page background (light or dark theme) shows through the chart.
    private static final Color TRANSPARENT = new Color(0, 0, 0, 0);
    // Mid-grey with alpha for gridlines, readable on both light and dark backgrounds.
    private static final Color GRIDLINE_COLOR = new Color(128, 128, 128, 128);
    // Opaque mid-grey for axis lines, ticks and labels, readable on both themes.
    private static final Color AXIS_COLOR = new Color(128, 128, 128);

    @Deprecated
    public LogParserAction(final AbstractBuild<?, ?> build, final LogParserResult result, final boolean showGraphs) {
        this((Run<?, ?>) build, result, showGraphs);
    }

    public LogParserAction(final Run<?, ?> build, final LogParserResult result, final boolean showGraphs) {
        this.build = build;
        this.result = result;
        this.showGraphs = showGraphs;
    }

    public Collection<? extends Action> getProjectActions() {
        if (showGraphs) {
            final Job<?, ?> job = build.getParent();
            return Collections.singleton(new LogParserProjectAction(job));
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public String getIconFileName() {
        return "clipboard.gif";
    }

    @Override
    public String getDisplayName() {
        return "Console Output (parsed)";
    }

    @Override
    public String getUrlName() {
        return urlName;
    }

    public static String getUrlNameStat() {
        return urlName;
    }

    public Run<?, ?> getOwner() {
        return build;
    }

    // Used by the summary.jelly of this class to show some totals from the
    // result
    public LogParserResult getResult() {
        return result;
    }

    public LogParserAction getPreviousAction() {
        Run<?, ?> build = this.getOwner();

        while (true) {

            build = build.getPreviousBuild();

            if (build == null)
                return null;
            LogParserAction action = build.getAction(LogParserAction.class);
            if (action != null)
                return action;
        }
    }

    public void doDynamic(final StaplerRequest req, final StaplerResponse rsp)
            throws IOException, ServletException, InterruptedException {
        final String dir = result.getHtmlLogPath();
        final String file = req.getRestOfPath();
        final String fileArray[] = file.split("/");
        final String lastFileInPath = fileArray[fileArray.length - 1];
        final File f = new File(dir + "/" + lastFileInPath);
        rsp.serveFile(req, f.toURI().toURL());

    }

    public void doGraph(StaplerRequest req, StaplerResponse rsp)
            throws IOException {
        if (ChartUtil.awtProblemCause != null) {
            // not available. send out error message
            rsp.sendRedirect2(req.getContextPath() + "/images/headless.png");
            return;
        }

        if (req.checkIfModified(getOwner().getTimestamp(), rsp))
            return;

        // Render the PNG ourselves with an alpha channel so the chart's transparent background is
        // preserved (ChartUtil.generateGraph would flatten it onto an opaque background). This lets the
        // graph blend into whichever Jenkins theme (light or dark) is active.
        final Area size = calcDefaultSize();
        final JFreeChart chart = createChart(req, buildDataSet());
        final BufferedImage image = chart.createBufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB, null);
        rsp.setContentType("image/png");
        try (OutputStream out = rsp.getOutputStream()) {
            ImageIO.write(image, "png", out);
        }
    }

    public void doGraphMap(StaplerRequest req, StaplerResponse rsp)
            throws IOException {
        if (req.checkIfModified(this.getOwner().getTimestamp(), rsp))
            return;
        ChartUtil.generateClickableMap(req, rsp,
                createChart(req, buildDataSet()), calcDefaultSize());
    }

    private Area calcDefaultSize() {
        Area res = Functions.getScreenResolution();
        if (res != null && res.width <= 800)
            return new Area(250, 100);
        else
            return new Area(500, 200);
    }

    private CategoryDataset buildDataSet() {

        DataSetBuilder<String, ChartUtil.NumberOnlyBuildLabel> dsb = new DataSetBuilder<>();

        for (LogParserAction a = this; a != null; a = a.getPreviousAction()) {
            dsb.add(a.result.getTotalErrors(), "0",
                    new ChartUtil.NumberOnlyBuildLabel(a.getOwner()));
            dsb.add(a.result.getTotalWarnings(), "1",
                    new ChartUtil.NumberOnlyBuildLabel(a.getOwner()));
            dsb.add(a.result.getTotalInfos(), "2",
                    new ChartUtil.NumberOnlyBuildLabel(a.getOwner()));
            dsb.add(a.result.getTotalDebugs(), "3",
                    new ChartUtil.NumberOnlyBuildLabel(a.getOwner()));
            for (String extraTag : a.result.getExtraTags()) {
                dsb.add(a.result.getTotalCountsByExtraTag(extraTag), extraTag,
                        new ChartUtil.NumberOnlyBuildLabel(a.getOwner()));
            }
        }
        return dsb.build();
    }

    private JFreeChart createChart(StaplerRequest req, CategoryDataset dataset) {

        final String relPath = getRelPath(req);

        final JFreeChart chart = ChartFactory.createStackedAreaChart(
                null, // chart title
                null, // unused
                "count", // range axis label
                dataset, // data
                PlotOrientation.VERTICAL, // orientation
                false, // include legend
                true, // tooltips
                false // urls
                );

        // NOW DO SOME OPTIONAL CUSTOMISATION OF THE CHART...

        // set the background color for the chart...

        //final StandardLegend legend = (StandardLegend) chart.getLegend();
        //legend.setAnchor(StandardLegend.SOUTH);

        // Transparent backgrounds and theme-neutral (mid-grey) gridlines and axis text so the graph
        // reads correctly against either a light or a dark Jenkins theme. The transparent areas are
        // preserved because doGraph() encodes the PNG with an alpha channel.
        chart.setBackgroundPaint(TRANSPARENT);

        final CategoryPlot plot = chart.getCategoryPlot();

        //plot.setAxisOffset(new Spacer(Spacer.ABSOLUTE, 5.0, 5.0, 5.0, 5.0));
        plot.setBackgroundPaint(TRANSPARENT);
        plot.setOutlinePaint(null);
        plot.setForegroundAlpha(0.8f);
        //plot.setDomainGridlinesVisible(true);
        //plot.setDomainGridlinePaint(Color.white);
        plot.setRangeGridlinesVisible(true);
        plot.setRangeGridlinePaint(GRIDLINE_COLOR);

        CategoryAxis domainAxis = new ShiftedCategoryAxis(null);
        plot.setDomainAxis(domainAxis);
        domainAxis.setCategoryLabelPositions(CategoryLabelPositions.UP_90);
        domainAxis.setLowerMargin(0.0);
        domainAxis.setUpperMargin(0.0);
        domainAxis.setCategoryMargin(0.0);
        domainAxis.setTickLabelPaint(AXIS_COLOR);
        domainAxis.setLabelPaint(AXIS_COLOR);
        domainAxis.setAxisLinePaint(AXIS_COLOR);

        final NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        rangeAxis.setTickLabelPaint(AXIS_COLOR);
        rangeAxis.setLabelPaint(AXIS_COLOR);
        rangeAxis.setAxisLinePaint(AXIS_COLOR);

        StackedAreaRenderer ar = new StackedAreaRenderer2() {

            private static final long serialVersionUID = 1L;

            @Override
            public String generateURL(CategoryDataset dataset, int row,
                    int column) {
                ChartUtil.NumberOnlyBuildLabel label = (ChartUtil.NumberOnlyBuildLabel) dataset
                        .getColumnKey(column);
                return relPath + label.build.getNumber() + "/testReport/";
            }

            @Override
            public String generateToolTip(CategoryDataset dataset, int row,
                    int column) {
                ChartUtil.NumberOnlyBuildLabel label = (ChartUtil.NumberOnlyBuildLabel) dataset
                        .getColumnKey(column);
                LogParserResult result = label.build.getAction(LogParserAction.class).getResult();
                switch (row) {
                case 0:
                    return "Errors: " + result.getTotalErrors();
                case 1:
                    return "Warnings: " + result.getTotalWarnings();
                case 2:
                    return "Infos: " + result.getTotalInfos();
                default:
                    return "Debugs: " + result.getTotalDebugs();
                }
            }
        };
        plot.setRenderer(ar);
        ar.setSeriesPaint(0, ColorPalette.RED);    // error
        ar.setSeriesPaint(1, ColorPalette.YELLOW); // warning
        ar.setSeriesPaint(2, ColorPalette.BLUE);   // info
        ar.setSeriesPaint(3, ColorPalette.GREY);   // debug

        // crop extra space around the graph
        plot.setInsets(new RectangleInsets(0, 0, 0, 5.0));

        return chart;
    }

    private String getRelPath(StaplerRequest req) {
        String relPath = req.getParameter("rel");
        if (relPath == null)
            return "";
        return relPath;
    }

}
