def parseDotEnv(String filePath) {
	def values = [:]
	readFile(filePath).readLines().each { rawLine ->
		String line = rawLine.trim()
		if (line && !line.startsWith('#')) {
			int separatorIndex = rawLine.indexOf('=')
			if (separatorIndex > 0) {
				String key = rawLine.substring(0, separatorIndex).trim()
				String value = rawLine.substring(separatorIndex + 1).trim()
				values[key] = value
			}
		}
	}
	return values
}

def sanitizeComposeProjectName(String rawValue) {
	String sanitized = (rawValue ?: 'crypto-monitor')
		.toLowerCase()
		.replaceAll(/[^a-z0-9]+/, '-')
		.replaceAll(/^-+|-+$/, '')
	return sanitized ? sanitized.take(45) : 'crypto-monitor'
}

def composeCommand(String command, String envFile, String projectName, String arguments) {
	return "${command} --env-file \"${envFile}\" --project-name \"${projectName}\" -f docker-compose.yml ${arguments}"
}

pipeline {
	agent any
	tools {
		maven 'Maven 3.9.9'
		jdk 'jdk-21'
	}
	options {
		buildDiscarder(logRotator(numToKeepStr: '20'))
		disableConcurrentBuilds()
		skipDefaultCheckout()
		timestamps()
	}
	parameters {
		string(name: 'GIT_BRANCH', defaultValue: '', description: 'Optional branch override. Leave empty to use the Jenkins branch context.')
		string(name: 'RUNTIME_ENV_CREDENTIALS_ID', defaultValue: '', description: 'Optional Jenkins Secret File credential id containing the runtime .env file.')
		string(name: 'APP_HOST_PORT', defaultValue: '', description: 'Optional host port override for java_app. Leave empty to use APP_HOST_PORT or the compose default.')
		string(name: 'DB_HOST_PORT', defaultValue: '', description: 'Optional host port override for PostgreSQL. Leave empty to use DB_LOCAL_PORT or the compose default.')
		string(name: 'REDIS_HOST_PORT', defaultValue: '', description: 'Optional host port override for Redis. Leave empty to use REDIS_LOCAL_PORT or the compose default.')
	}
	environment {
		SPRING_PROFILES_ACTIVE = 'test'
		SONARQUBE_PROJECT_KEY = 'crypto-portfolio-monitoring'
		WORKSPACE_RUNTIME_ENV = '.jenkins/runtime.env'
		WORKSPACE_HEALTHCHECK_SCRIPT = '.jenkins/wait-for-app.ps1'
		JAVA_TOOL_OPTIONS = '-Duser.timezone=UTC'
	}

	stages {
		stage('Checkout') {
			steps {
				script {
					env.PIPELINE_GIT_BRANCH =
						params.GIT_BRANCH?.trim()
							? params.GIT_BRANCH.trim()
							: (env.CHANGE_BRANCH?.trim()
								?: env.BRANCH_NAME?.trim()
								?: 'feature/jenkins-pipeline')
				}
				git url: 'https://github.com/ArisTeo-Winner/crypto-portfolio-monitoring.git',
					branch: "${env.PIPELINE_GIT_BRANCH}",
					credentialsId: 'github-creds'
			}
		}

		stage('Prepare Runtime Config') {
			steps {
				script {
					bat 'if not exist ".jenkins" mkdir ".jenkins"'

					if (params.RUNTIME_ENV_CREDENTIALS_ID?.trim()) {
						withCredentials([file(credentialsId: params.RUNTIME_ENV_CREDENTIALS_ID.trim(), variable: 'RUNTIME_ENV_FILE')]) {
							bat 'copy /Y "%RUNTIME_ENV_FILE%" ".jenkins\\runtime.env" >nul'
						}
					} else if (fileExists('.env')) {
						bat 'copy /Y ".env" ".jenkins\\runtime.env" >nul'
					} else {
						error('No runtime env file is available. Provide a workspace .env or set RUNTIME_ENV_CREDENTIALS_ID to a Jenkins Secret File credential.')
					}

					def runtimeEnv = parseDotEnv(env.WORKSPACE_RUNTIME_ENV)
					if (params.APP_HOST_PORT?.trim()) {
						runtimeEnv.APP_HOST_PORT = params.APP_HOST_PORT.trim()
					}
					if (params.DB_HOST_PORT?.trim()) {
						runtimeEnv.DB_LOCAL_PORT = params.DB_HOST_PORT.trim()
					}
					if (params.REDIS_HOST_PORT?.trim()) {
						runtimeEnv.REDIS_LOCAL_PORT = params.REDIS_HOST_PORT.trim()
					}

					List requiredKeys = [
						'DB_NAME',
						'DB_USER',
						'DB_PASS',
						'SPRING_DATASOURCE_URL',
						'SPRING_DATASOURCE_USER',
						'SPRING_DATASOURCE_PASS',
						'JWT_SECRET_BASE64',
						'REFRESH_TOKEN_HASH_SECRET_BASE64',
						'JWT_ACCESS_EXPIRATION',
						'JWT_REFRESH_EXPIRATION',
						'DATABURSATIL_TOKEN',
						'BANXICO_TOKEN',
						'FINNHUB_API_KEY'
					]

					def invalidKeys = requiredKeys.findAll { key ->
						String value = runtimeEnv[key]
						return value == null || value.trim().isEmpty() || value.contains('__SET_')
					}
					if (!invalidKeys.isEmpty()) {
						error("Runtime env is missing required values for: ${invalidKeys.join(', ')}")
					}

					writeFile(
						file: env.WORKSPACE_RUNTIME_ENV,
						text: runtimeEnv.collect { key, value -> "${key}=${value}" }.join('\n') + '\n')

					env.RUNTIME_ENV_FILE = "${env.WORKSPACE}\\${env.WORKSPACE_RUNTIME_ENV.replace('/', '\\')}"
					env.APP_HOST_PORT_RESOLVED = runtimeEnv.get('APP_HOST_PORT', '8080')
					env.DB_HOST_PORT_RESOLVED = runtimeEnv.get('DB_LOCAL_PORT', '5432')
					env.REDIS_HOST_PORT_RESOLVED = runtimeEnv.get('REDIS_LOCAL_PORT', '6379')
					env.DOCKER_COMPOSE_PROJECT = sanitizeComposeProjectName("${env.JOB_BASE_NAME ?: 'crypto-monitor'}-${env.PIPELINE_GIT_BRANCH}")

					echo "Prepared runtime env for compose project ${env.DOCKER_COMPOSE_PROJECT} using host ports app=${env.APP_HOST_PORT_RESOLVED}, db=${env.DB_HOST_PORT_RESOLVED}, redis=${env.REDIS_HOST_PORT_RESOLVED}"
				}
			}
		}

		stage('Docker Preflight') {
			steps {
				bat 'docker version'
				script {
					env.DOCKER_COMPOSE_CMD = bat(
						returnStdout: true,
						script: '''@echo off
where docker-compose >nul 2>nul && (echo docker-compose & exit /b 0)
docker compose version >nul 2>nul && (echo docker compose & exit /b 0)
echo Neither docker-compose nor docker compose is available 1>&2
exit /b 1'''
					).trim()
					echo "Using compose command: ${env.DOCKER_COMPOSE_CMD}"
				}
			}
		}

		stage('Modulith Verification') {
			steps {
				withEnv([
					'SPRING_DATASOURCE_URL=',
					'SPRING_DATASOURCE_USERNAME=',
					'SPRING_DATASOURCE_PASSWORD='
				]) {
					bat 'mvn -B test -Dspring.profiles.active=test -Dtest=ModulithVerificationTest'
				}
			}
			post {
				always {
					junit 'target/surefire-reports/*.xml'
				}
			}
		}

		stage('Test') {
			when {
				expression {
					return env.PIPELINE_GIT_BRANCH == 'dev'
				}
			}
			steps {
				withEnv([
					'SPRING_DATASOURCE_URL=',
					'SPRING_DATASOURCE_USERNAME=',
					'SPRING_DATASOURCE_PASSWORD='
				]) {
					bat 'mvn -B test -Dspring.profiles.active=test -DexcludedGroups=contract'
				}
			}
			post {
				always {
					junit 'target/surefire-reports/*.xml'
				}
			}
		}

		stage('Contract Tests - Consumer') {
			when {
				expression {
					return env.PIPELINE_GIT_BRANCH == 'dev'
				}
			}
			steps {
				withEnv([
					'SPRING_DATASOURCE_URL=',
					'SPRING_DATASOURCE_USERNAME=',
					'SPRING_DATASOURCE_PASSWORD='
				]) {
					bat 'mvn -B test -Dspring.profiles.active=test -Dtest=AuthLoginConsumerPactTest'
				}
			}
			post {
				always {
					junit 'target/surefire-reports/*.xml'
					archiveArtifacts artifacts: 'pacts/*.json', allowEmptyArchive: true
				}
			}
		}

		stage('Contract Tests - Provider') {
			when {
				expression {
					return env.PIPELINE_GIT_BRANCH == 'dev'
				}
			}
			steps {
				withEnv([
					'SPRING_DATASOURCE_URL=',
					'SPRING_DATASOURCE_USERNAME=',
					'SPRING_DATASOURCE_PASSWORD='
				]) {
					bat 'mvn -B test -Dspring.profiles.active=test -Dtest=AuthLoginProviderPactIT'
				}
			}
			post {
				always {
					junit 'target/surefire-reports/*.xml'
				}
			}
		}

		stage('SonarQube Analysis') {
			when {
				allOf {
					expression {
						return env.PIPELINE_GIT_BRANCH in ['dev', 'main']
					}
					expression {
						return env.SONAR_HOST_URL?.trim() && env.SONARQUBE_CREDENTIALS_ID?.trim()
					}
				}
			}
			steps {
				withCredentials([string(credentialsId: env.SONARQUBE_CREDENTIALS_ID, variable: 'SONAR_TOKEN')]) {
					bat '''
						mvn -B sonar:sonar ^
						  -DskipTests ^
						  -Dspring.profiles.active=test ^
						  -Dsonar.host.url=%SONAR_HOST_URL% ^
						  -Dsonar.token=%SONAR_TOKEN% ^
						  -Dsonar.projectKey=%SONARQUBE_PROJECT_KEY%
					'''
				}
			}
			post {
				always {
					archiveArtifacts artifacts: 'target/site/jacoco/**', allowEmptyArchive: true
				}
			}
		}

		stage('Build JAR') {
			steps {
				bat 'mvn -B clean package -DskipTests'
			}
		}

		stage('Docker Build') {
			steps {
				script {
					if (!fileExists('target/crypto-portfolio-monitoring-0.0.1-SNAPSHOT.jar')) {
						error('The application JAR was not produced at target/crypto-portfolio-monitoring-0.0.1-SNAPSHOT.jar')
					}
					bat composeCommand(
						env.DOCKER_COMPOSE_CMD,
						env.RUNTIME_ENV_FILE,
						env.DOCKER_COMPOSE_PROJECT,
						'build --pull java_app')
				}
			}
		}

		stage('Run App') {
			steps {
				script {
					bat composeCommand(
						env.DOCKER_COMPOSE_CMD,
						env.RUNTIME_ENV_FILE,
						env.DOCKER_COMPOSE_PROJECT,
						'down --remove-orphans')
					bat composeCommand(
						env.DOCKER_COMPOSE_CMD,
						env.RUNTIME_ENV_FILE,
						env.DOCKER_COMPOSE_PROJECT,
						'up -d java_db redis java_app')
					bat composeCommand(
						env.DOCKER_COMPOSE_CMD,
						env.RUNTIME_ENV_FILE,
						env.DOCKER_COMPOSE_PROJECT,
						'ps')

					writeFile(
						file: env.WORKSPACE_HEALTHCHECK_SCRIPT,
						text: """
\$ErrorActionPreference = 'Stop'
\$uri = 'http://127.0.0.1:${env.APP_HOST_PORT_RESOLVED}/actuator/health'
for (\$attempt = 1; \$attempt -le 24; \$attempt++) {
  try {
    \$response = Invoke-WebRequest -UseBasicParsing -Uri \$uri -TimeoutSec 5
    if (\$response.StatusCode -eq 200 -and \$response.Content -match '"status"\\s*:\\s*"UP"') {
      Write-Host "Application is healthy at \$uri"
      exit 0
    }
  } catch {
    Write-Host "Waiting for application health endpoint on attempt \$attempt/24"
  }
  Start-Sleep -Seconds 5
}
Write-Error "Application did not become healthy at \$uri within the expected time window."
exit 1
""".stripIndent())

					bat 'powershell -NoProfile -ExecutionPolicy Bypass -File ".jenkins\\wait-for-app.ps1"'
				}
			}
			post {
				always {
					bat 'if not exist "target\\jenkins" mkdir "target\\jenkins"'
					bat "${composeCommand(env.DOCKER_COMPOSE_CMD, env.RUNTIME_ENV_FILE, env.DOCKER_COMPOSE_PROJECT, 'ps')} > target\\jenkins\\compose-ps.txt"
					archiveArtifacts artifacts: 'target/jenkins/**', allowEmptyArchive: true
				}
				failure {
					bat 'if not exist "target\\jenkins" mkdir "target\\jenkins"'
					bat "${composeCommand(env.DOCKER_COMPOSE_CMD, env.RUNTIME_ENV_FILE, env.DOCKER_COMPOSE_PROJECT, 'logs --no-color')} > target\\jenkins\\compose-logs.txt"
					archiveArtifacts artifacts: 'target/jenkins/**', allowEmptyArchive: true
				}
			}
		}
	}

	post {
		success {
			echo 'Build exitoso'
		}
		failure {
			echo 'Fallo el build'
		}
	}
}
