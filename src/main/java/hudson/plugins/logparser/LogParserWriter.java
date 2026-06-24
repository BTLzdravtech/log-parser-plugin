package hudson.plugins.logparser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public final class LogParserWriter {

    public static void writeHeaderTemplateToAllLinkFiles(
            final HashMap<String, BufferedWriter> writers,
            final int sectionCounter) throws IOException {
        final List<String> statuses = LogParserConsts.STATUSES_WITH_SECTIONS_IN_LINK_FILES;
        for (String status : statuses) {
            final BufferedWriter linkWriter = writers.get(status);
            String str = "HEADER HERE: #" + sectionCounter;
            linkWriter.write(str + "\n");
        }

    }

    public static void writeWrapperHtml(final String buildWrapperPath)
            throws IOException {
        final String wrapperHtml = "<frameset cols=\"270,*\">\n"
                + "<frame src=\"log_ref.html\" scrolling=auto name=\"sidebar\">\n"
                + "<frame src=\"log_content.html\" scrolling=auto name=\"content\">\n"
                + "<noframes>\n"
                + "<p>Viewing the build report requires a Frames-enabled browser</p>\n"
                + "<a href='build.log'>build log</a>\n" + "</noframes>\n"
                + "</frameset>\n";

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(buildWrapperPath))) {
            writer.write(wrapperHtml);
        }
    }

    public static void writeReferenceHtml(final String buildRefPath,
            final ArrayList<String> headerForSection,
            final HashMap<String, Integer> statusCountPerSection,
            // Retained for backwards compatibility; status markers are now themed inline SVGs.
            final HashMap<String, String> iconTable,
            final HashMap<String, String> linkListDisplay,
            final HashMap<String, String> linkListDisplayPlural,
            final HashMap<String, Integer> statusCount,
            final HashMap<String, String> linkFiles,
            final List<String> extraTags) throws IOException {

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(buildRefPath))) {
            // Hudson stylesheets
            writer.write(LogParserConsts.getHtmlOpeningTags());
            // Write Errors
            writeLinks(writer, LogParserConsts.ERROR, headerForSection,
                    statusCountPerSection, linkListDisplay,
                    linkListDisplayPlural, statusCount, linkFiles);
            // Write Warnings
            writeLinks(writer, LogParserConsts.WARNING, headerForSection,
                    statusCountPerSection, linkListDisplay,
                    linkListDisplayPlural, statusCount, linkFiles);
            // Write Infos
            writeLinks(writer, LogParserConsts.INFO, headerForSection,
                    statusCountPerSection, linkListDisplay,
                    linkListDisplayPlural, statusCount, linkFiles);
            // Write Debugs
            writeLinks(writer, LogParserConsts.DEBUG, headerForSection,
                    statusCountPerSection, linkListDisplay,
                    linkListDisplayPlural, statusCount, linkFiles);
            // Write extra tags
            for (String extraTag : extraTags) {
                writeLinks(writer, extraTag, headerForSection,
                        statusCountPerSection, linkListDisplay,
                        linkListDisplayPlural, statusCount, linkFiles);
            }
            writer.write(LogParserConsts.getHtmlClosingTags());
        }
    }

    private static void writeLinks(final BufferedWriter writer,
            final String status, final ArrayList<String> headerForSection,
            final HashMap<String, Integer> statusCountPerSection,
            final HashMap<String, String> linkListDisplay,
            final HashMap<String, String> linkListDisplayPlural,
            final HashMap<String, Integer> statusCount,
            final HashMap<String, String> linkFiles) throws IOException {
        String linkListDisplayStr = linkListDisplay.get(status);
        if (linkListDisplayStr == null) {
            linkListDisplayStr = LogParserDisplayConsts.getDefaultLinkListDisplay(status);
        }
        String linkListDisplayStrPlural = linkListDisplayPlural.get(status);
        if (linkListDisplayStrPlural == null) {
            linkListDisplayStrPlural = LogParserDisplayConsts.getDefaultLinkListDisplayPlural(status);
        }
        final String linkListCount = statusCount.get(status).toString();

        final String styles =
            "<style>\n" 
            + "    ul {margin-left: 0; padding-left: 1em;}\n"
            + "    ul li {font-size: small; white-space: nowrap; text-overflow: ellipsis; overflow: hidden; margin-top: .5em; }\n"
            + "    ul li:hover {white-space: normal;}\n"
            + "    ul li a:link {text-decoration: none;}\n"
            + "    ul li:hover a:link {text-decoration: underline;}\n"
            + "</style>\n";
        writer.write(styles);
		
        // Render the status marker as an inline, theme-aware SVG dot. It inherits its colour from the
        // matching status class (see LogParserConsts.getThemeStyles()) via currentColor, so it follows
        // the light / dark colour scheme instead of relying on a fixed-background GIF.
        final String icon = "<svg class=\"" + status.toLowerCase() + "\" width=\"16\" height=\"16\""
                + " viewBox=\"0 0 16 16\" role=\"img\" aria-label=\"" + linkListDisplayStr + " Icon\""
                + " style=\"vertical-align: middle; margin: 2px;\"><circle cx=\"8\" cy=\"8\" r=\"6\" fill=\"currentColor\" /></svg>\n";
        // Weight comes from the .lpp-toggle-list rule (Jenkins link weight) rather than <strong>.
        final String linksStart = icon
                + "<a class=\"lpp-toggle-list\" href=\"#\" data-display-category=\"" + linkListDisplayStr + "\">"
                + linkListDisplayStr + " (" + linkListCount + ")</a><br />\n"
                + "<ul style=\"display: none;\" id=\""
                + linkListDisplayStr + "\" >\n";
        writer.write(linksStart);

        // Read the links file and insert here
        try (BufferedReader reader = new BufferedReader(new FileReader(linkFiles.get(status)))) {
            final String summaryLine = "<br/>(SUMMARY_INT_HERE LINK_LIST_DISPLAY_STR in this section)<br/>";

            final String headerTemplateRegexp = "HEADER HERE:";
            final String headerTemplateSplitBy = "#";

            // If it's a header line - put the header of the section
            String line;
            while ((line = reader.readLine()) != null) {
                String curSummaryLine = null;
                if (line.startsWith(headerTemplateRegexp)) {
                    final int headerNum = Integer.parseInt(line.split(headerTemplateSplitBy)[1]);
                    line = headerForSection.get(headerNum);
                    final String key = LogParserUtils.getSectionCountKey(status, headerNum);
                    final Integer summaryInt = statusCountPerSection.get(key);
                    if (summaryInt == null || summaryInt == 0) {
                        // Don't write the header if there are no relevant lines for
                        // this section
                        line = null;
                    } else {
                        String linkListDisplayStrWithPlural = linkListDisplayStr;
                        if (summaryInt > 1) {
                            linkListDisplayStrWithPlural = linkListDisplayStrPlural;
                        }
                        curSummaryLine = summaryLine.replace("SUMMARY_INT_HERE",
                                summaryInt.toString()).replace(
                                "LINK_LIST_DISPLAY_STR",
                                linkListDisplayStrWithPlural);
                    }

                }

                if (line != null) {
                    writer.write(line);
                    writer.newLine(); // Write system dependent end of line.
                }
                if (curSummaryLine != null) {
                    writer.write(curSummaryLine);
                    writer.newLine(); // Write system dependent end of line.
                }
            }
        }

        final String linksEnd = "</ul>\n";
        writer.write(linksEnd);

    }

    private LogParserWriter() {
        // PMD warning to use singleton or bypass by private empty constructor
    }

}
