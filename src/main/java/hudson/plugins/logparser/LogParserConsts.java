package hudson.plugins.logparser;

import jenkins.model.Jenkins;

import java.util.Arrays;
import java.util.List;

public class LogParserConsts {

    public static final String ERROR = "ERROR";
    public static final String WARNING = "WARNING";
    public static final String INFO = "INFO";
    public static final String DEBUG = "DEBUG";
    public static final String NONE = "NONE";
    public static final String START = "START"; // marks a beginning of a section
    public static final String DEFAULT = NONE;

    // Error messages
    public static final String CANNOT_PARSE = "log-parser plugin ERROR: Cannot parse log ";
    public static final String NOT_INT = " is not an integer - using default";

    public static final List<String> LEGAL_STATUS = Arrays.asList(ERROR, WARNING, INFO, DEBUG, NONE, START);
    public static final List<String> STATUSES_WITH_LINK_FILES = Arrays.asList(ERROR, WARNING, INFO, DEBUG);
    public static final List<String> STATUSES_WITH_SECTIONS_IN_LINK_FILES = Arrays.asList(ERROR, WARNING, DEBUG);

    public static String getHtmlOpeningTags() {
        final String hudsonRoot = Jenkins.get().getRootUrl();
        final String pluginResourceUrl = String.format("%s/plugin/log-parser/", Jenkins.RESOURCE_PATH).substring(1);

        return "<!DOCTYPE html>\n" + "<html>\n" + "\t<head>\n"
                + "\t\t<title>log-parser plugin page</title>\n"
                + "\t\t<meta name=\"color-scheme\" content=\"light dark\" />\n"
                + getThemeStyles()
                + getThemeScript()
                + "\t\t<script type=\"application/javascript\" src=\"" + hudsonRoot + pluginResourceUrl + "js/log-parser-behaviour.js\"></script>\n"
                +"\t</head>\n"
                + "\t<body>\n";
    }

    // Light colour tokens (default). Kept as a single declaration so the values are not duplicated
    // between the default rule and the explicit data-lpp-theme="light" override.
    private static final String LIGHT_TOKENS =
            "--lpp-bg: #ffffff; --lpp-fg: #1a1a1a; --lpp-link: #1a66cc;"
            + " --lpp-error: #cc0000; --lpp-warning: #b36b00; --lpp-info: #1a66cc; --lpp-debug: #666666;";

    // Dark colour tokens mirror the Jenkins Dark Theme plugin palette so the log blends into it:
    // background = --background, text = --text-color, debug = --text-color-secondary,
    // error = --error-color, warning = --warning-color.
    private static final String DARK_TOKENS =
            "--lpp-bg: hsl(240, 6%, 13%); --lpp-fg: rgb(250, 250, 255); --lpp-link: #55c0ff;"
            + " --lpp-error: hsl(5, 100%, 60%); --lpp-warning: hsl(35, 100%, 50%); --lpp-info: #55c0ff;"
            + " --lpp-debug: rgb(160, 160, 165);";

    /**
     * Returns a self-contained stylesheet for the generated log pages.
     * <p>
     * The parsed log is rendered as a standalone HTML document embedded in an iframe, so it does not
     * inherit the Jenkins theme or its CSS variables. The stylesheet therefore defines its own colour
     * tokens with light defaults and a {@code @media (prefers-color-scheme: dark)} override (used for the
     * standalone "Show only this" view), plus explicit {@code data-lpp-theme} overrides that
     * {@link #getThemeScript()} sets to mirror the host Jenkins theme when embedded — so the log matches
     * the active Jenkins theme even when it disagrees with the operating-system colour scheme.
     *
     * @return a {@code <style>} block defining the colour tokens, body colours and per-status classes.
     * @since FIXME
     */
    public static String getThemeStyles() {
        return "\t\t<style>\n"
                + "\t\t\t:root { " + LIGHT_TOKENS + " }\n"
                + "\t\t\t@media (prefers-color-scheme: dark) { :root { " + DARK_TOKENS + " } }\n"
                // Explicit overrides win over the media query (higher specificity) so a forced Jenkins
                // theme is honoured regardless of the operating-system colour scheme.
                + "\t\t\t:root[data-lpp-theme=\"light\"] { " + LIGHT_TOKENS + " }\n"
                + "\t\t\t:root[data-lpp-theme=\"dark\"] { " + DARK_TOKENS + " }\n"
                + "\t\t\tbody {\n"
                + "\t\t\t\tmargin-left: .5em; background-color: var(--lpp-bg); color: var(--lpp-fg);\n"
                // Match the Jenkins UI font for the summary/links text. The variable is not visible
                // inside this iframe, so the Jenkins sans stack is repeated as the fallback.
                + "\t\t\t\tfont-family: var(--font-family-sans, system-ui, \"Segoe UI\", roboto, \"Noto Sans\","
                + " oxygen, ubuntu, cantarell, \"Fira Sans\", \"Droid Sans\", \"Helvetica Neue\", arial, sans-serif);\n"
                + "\t\t\t}\n"
                + "\t\t\ta { color: var(--lpp-link); }\n"
                // Match the Jenkins UI link weight for the summary labels (Jenkins uses 450, not bold).
                + "\t\t\t.lpp-toggle-list { font-weight: var(--link-font-weight, 450); }\n"
                + "\t\t\tpre { font-family: Consolas, \"Courier New\"; word-wrap: break-word; }\n"
                + "\t\t\tpre span { word-wrap: break-word; }\n"
                + "\t\t\t.error { color: var(--lpp-error); }\n"
                + "\t\t\t.warning { color: var(--lpp-warning); }\n"
                + "\t\t\t.info, .start { color: var(--lpp-info); }\n"
                + "\t\t\t.debug { color: var(--lpp-debug); }\n"
                + "\t\t</style>\n";
    }

    /**
     * Returns an inline script that aligns the page's colour scheme with the host Jenkins theme.
     * <p>
     * When the log is embedded in an iframe (same origin), it samples the background luminance of the
     * top Jenkins document and sets {@code data-lpp-theme} on the root element accordingly, so an
     * explicitly selected Jenkins theme (e.g. the Dark Theme plugin's forced "Dark") is matched even
     * when it differs from the operating-system colour scheme. When viewed standalone, or if the host
     * cannot be read, it falls back to the {@code prefers-color-scheme} media query.
     *
     * @return a {@code <script>} block that sets the {@code data-lpp-theme} attribute.
     * @since FIXME
     */
    public static String getThemeScript() {
        return "\t\t<script>\n"
                + "\t\t\t(function () {\n"
                + "\t\t\t\tfunction hostLuminance() {\n"
                + "\t\t\t\t\tif (window.top === window.self) { return null; }\n"
                + "\t\t\t\t\tvar doc = window.top.document;\n"
                + "\t\t\t\t\tvar els = [doc.body, doc.documentElement];\n"
                + "\t\t\t\t\tfor (var i = 0; i < els.length; i++) {\n"
                + "\t\t\t\t\t\tif (!els[i]) { continue; }\n"
                + "\t\t\t\t\t\tvar m = window.getComputedStyle(els[i]).backgroundColor.match(/[0-9.]+/g);\n"
                + "\t\t\t\t\t\tif (m && m.length >= 3 && (m.length < 4 || parseFloat(m[3]) > 0)) {\n"
                + "\t\t\t\t\t\t\treturn 0.2126 * m[0] + 0.7152 * m[1] + 0.0722 * m[2];\n"
                + "\t\t\t\t\t\t}\n"
                + "\t\t\t\t\t}\n"
                + "\t\t\t\t\treturn null;\n"
                + "\t\t\t\t}\n"
                + "\t\t\t\tvar dark;\n"
                + "\t\t\t\ttry {\n"
                + "\t\t\t\t\tvar lum = hostLuminance();\n"
                + "\t\t\t\t\tdark = lum !== null ? lum < 128\n"
                + "\t\t\t\t\t\t: window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;\n"
                + "\t\t\t\t} catch (e) {\n"
                + "\t\t\t\t\tdark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;\n"
                + "\t\t\t\t}\n"
                + "\t\t\t\tdocument.documentElement.setAttribute('data-lpp-theme', dark ? 'dark' : 'light');\n"
                + "\t\t\t})();\n"
                + "\t\t</script>\n";
    }

    public static final String getHtmlClosingTags() {
        return "\t</body>\n" + "</html>\n";
    }

    // Parsing in threads for performance
    public static final int LINES_PER_THREAD = 10000; // How many lines to parse
                                                      // in each thread
    public static final int MAX_THREADS = 2; // How many concurrent threads to
                                             // run (unused when implementing
                                             // cached thread pool)

}
