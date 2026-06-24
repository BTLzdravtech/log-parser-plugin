package hudson.plugins.logparser;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogParserConstsTest {

    @Test
    void themeStylesShouldDefineDarkModeOverride() {
        String styles = LogParserConsts.getThemeStyles();

        // The generated log is shown in an iframe and so must self-theme via the OS colour-scheme
        // preference rather than relying on the Jenkins theme.
        assertThat(styles).contains("@media (prefers-color-scheme: dark)");
    }

    @Test
    void themeStylesShouldColourEachStatusFromAVariable() {
        String styles = LogParserConsts.getThemeStyles();

        assertThat(styles)
                .contains(".error { color: var(--lpp-error); }")
                .contains(".warning { color: var(--lpp-warning); }")
                .contains(".info, .start { color: var(--lpp-info); }")
                .contains(".debug { color: var(--lpp-debug); }");
    }

    @Test
    void themeStylesShouldThemeTheBodyAndLinks() {
        String styles = LogParserConsts.getThemeStyles();

        assertThat(styles)
                .contains("background-color: var(--lpp-bg)")
                .contains("color: var(--lpp-fg)")
                .contains("a { color: var(--lpp-link); }");
    }

    @Test
    void themeStylesShouldMatchTheJenkinsSansFont() {
        String styles = LogParserConsts.getThemeStyles();

        // The iframe cannot see the Jenkins CSS variable, so the sans stack is repeated as a fallback.
        assertThat(styles).contains("font-family: var(--font-family-sans, system-ui");
    }

    @Test
    void themeStylesShouldMatchTheJenkinsLinkWeight() {
        String styles = LogParserConsts.getThemeStyles();

        // Summary labels use the Jenkins link weight (450) rather than bold.
        assertThat(styles).contains(".lpp-toggle-list { font-weight: var(--link-font-weight, 450); }");
    }

    @Test
    void themeStylesShouldProvideExplicitThemeOverrides() {
        String styles = LogParserConsts.getThemeStyles();

        // These let the host-theme detection script force a theme regardless of the OS colour scheme.
        assertThat(styles)
                .contains(":root[data-lpp-theme=\"dark\"]")
                .contains(":root[data-lpp-theme=\"light\"]");
    }

    @Test
    void themeScriptShouldAlignWithHostThemeWithMediaQueryFallback() {
        String script = LogParserConsts.getThemeScript();

        assertThat(script)
                .contains("window.top")
                .contains("prefers-color-scheme: dark")
                .contains("data-lpp-theme");
    }
}
