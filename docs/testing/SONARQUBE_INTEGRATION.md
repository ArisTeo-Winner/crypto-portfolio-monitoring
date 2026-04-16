# SonarQube Integration

This project is wired to publish backend analysis and JaCoCo coverage to SonarQube.

## Maven

The Maven build now exposes the standard Sonar properties from `pom.xml`:

- `sonar.projectKey=crypto-portfolio-monitoring`
- `sonar.projectName=crypto-portfolio-monitoring`
- `sonar.sources=src/main/java`
- `sonar.tests=src/test/java`
- `sonar.junit.reportPaths=target/surefire-reports`
- `sonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml`

JaCoCo is configured to emit:

- `target/site/jacoco/jacoco.xml`
- `target/site/jacoco/index.html`

## Local execution

Run the full verification first so SonarQube can consume fresh test and coverage reports:

```powershell
mvn clean verify
```

Then publish the analysis:

```powershell
mvn sonar:sonar `
  -Dsonar.host.url=http://localhost:9000 `
  -Dsonar.token=YOUR_TOKEN
```

You can override the default project key if your SonarQube server uses a different identifier:

```powershell
mvn sonar:sonar `
  -Dsonar.host.url=http://localhost:9000 `
  -Dsonar.token=YOUR_TOKEN `
  -Dsonar.projectKey=crypto-portfolio-monitoring-dev
```

## Jenkins

The Jenkins pipeline now includes an optional `SonarQube Analysis` stage.

The stage runs only when both variables exist:

- `SONAR_HOST_URL`
- `SONARQUBE_CREDENTIALS_ID`

Expected Jenkins setup:

1. Create a Jenkins secret text credential containing the SonarQube token.
2. Set the credential id in `SONARQUBE_CREDENTIALS_ID`.
3. Set `SONAR_HOST_URL` as a Jenkins environment variable or folder/job variable.

When present, Jenkins runs:

```text
mvn -B sonar:sonar -DskipTests -Dspring.profiles.active=test ...
```

It reuses the reports produced in the prior `Test` stage and archives the JaCoCo site as a build artifact.

## Notes

- The pipeline integration is backend-only in this repository.
- `.scannerwork/` is ignored in Git.
- If SonarQube quality gates are needed later, they can be added on top of this setup once Jenkins has the Sonar plugin configured.
