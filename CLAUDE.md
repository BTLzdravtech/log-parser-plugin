# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

This is the Jenkins **Log Parser Plugin** — it parses a build's console log against a configurable rule set, classifying each line as ERROR / WARNING / INFO / DEBUG (plus arbitrary user-defined "extra tags") and producing linked, color-coded HTML reports shown on the build and project pages.

It is a standard Maven Jenkins plugin (`hpi` packaging) built on the `org.jenkins-ci.plugins:plugin` parent POM. Java baseline is 17; CI builds against JDK 21 and 25.

## Commands

Use the Maven wrapper (`./mvnw`, or `mvnw.cmd` on Windows). There is no local Maven required.

```bash
./mvnw clean install        # full build + tests (what CI runs)
./mvnw test                 # run the test suite
./mvnw pmd:check            # static analysis — CI fails the build on violations
./mvnw hpi:run              # run a live Jenkins with the plugin on http://localhost:8080
```

Run a single test class or method (JUnit 5 + Surefire):

```bash
./mvnw test -Dtest=LogParserPublisherTest
./mvnw test -Dtest=LineToStatusTest#someMethod
```

Docker-based workflow (no JDK/Maven on host needed) — see `Makefile` / `docker-compose.yml`:

```bash
make tests                  # docker-compose run --rm mvn mvn clean test
docker compose up           # runs hpi:run, Jenkins exposed on http://localhost:8081
```

## Architecture

The flow runs as a Jenkins post-build step and is split deliberately across the controller and the agent (build node), because logs may live on a remote agent.

**Entry point — `LogParserPublisher`** (`Recorder` + `SimpleBuildStep`, `@Symbol("logParser")`): the configurable build step. Its `DescriptorImpl` holds the global parsing-rule files (configured in Jenkins global config) and the legacy-formatting flag. `perform()` resolves which rule file to use (global vs. project-relative `projectRulePath`), runs the parse, and optionally marks the build FAILURE (on errors) or UNSTABLE (on warnings).

**Orchestration — `LogParserParser`**: owns the parse for one build. It compiles the rule patterns once (`CompiledPatterns`), then in `parseLogBody()`:
1. Ships line-classification to the build node via `channel.call(new LogParserStatusComputer(...))` — this returns a `Map<lineNumber, status>`.
2. Re-reads the log through the **Timestamper API** and, per line, calls `parseLine()` to HTML-escape, strip `ConsoleNote` annotations and xterm escape sequences, color the line, and emit anchors/links.

It writes several HTML artifacts into the build's root dir: `log_content.html` (the body), per-status link files (`logerrorLinks.html`, etc.), `log_ref.html` (the reference/summary frame), and `log.html` (the wrapping frameset). Output assembly lives in `LogParserWriter`; the displayed result + counts are carried in `LogParserResult` and surfaced via `LogParserAction` (build) and `action/LogParserProjectAction` (project trend graphs).

**Classification strategies** — `LogParserStatusComputer` is a `MasterToSlaveCallable` that runs on the agent. It picks a `ParsingStrategy` via `ParsingStrategyLocator`, selected by the system property `hudson.plugins.logparser.ParsingStrategy`:
- `ClassicParsingStrategy` (default): copies the log to a temp file, splits it into chunks, and classifies in parallel via a thread pool of `LogParserThread` workers (`LogParserUtils.getLinesPerThread()` lines each), then reassembles in order.
- `StreamParsingStrategy`: lazily streams lines through `LineToStatus`. Lower memory — intended for very large logs (added to fix OOM in workflow builds).

Both implement the `ParsingStrategy` interface and consume a `ParsingInput`. When changing classification logic, keep the two strategies behaviorally equivalent.

**UI** lives in Jelly views under `src/main/resources/hudson/plugins/logparser/**` (e.g. `LogParserPublisher/config.jelly`, `LogParserAction/*.jelly`). Anything reachable from a Jelly view via `${instance.x}` / `${descriptor.x}` is part of the contract.

## Conventions (from `.github/CONTRIBUTING.md`)

- **Target the `develop` branch** for changes and PRs (this is the default branch), not `main`.
- All bug fixes and features must be covered by tests. Tests use **JUnit 5** — JUnit 4 imports are banned and enforced by the build (`ban-junit4-imports.skip=false`); use AssertJ / JenkinsRule (`JenkinsConfiguredWithCodeRule` for JCasC).
- 4-space indentation for Java (2 for XML); spaces only, no tabs; wrap at ~120 columns.
- `src/main` code: no wildcard imports, avoid `static` imports. Fields private; `Serializable` types need a `serialVersionUID` (initial value `1L`).
- New public classes/methods get Javadoc and a `@since FIXME` tag (the releaser replaces `FIXME` with the version).
- Backwards compatibility matters: this plugin has shipped widely. Old constructors/methods are kept and marked `@Deprecated` rather than removed (see `LogParserParser.parseLog(AbstractBuild)` and `LogParserPublisher`'s legacy constructor) — preserve serialized-field compatibility.
- Commit messages follow Angular conventions (`feat:`, `fix:`, scopes like `fix(Memory):`) — the `CHANGELOG.md` is generated from them.
