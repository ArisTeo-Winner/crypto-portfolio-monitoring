pipeline {
	agent any
	tools {
		maven 'Maven 3.9.9'
		jdk 'jdk-21'
	}
	environment {
		SPRING_PROFILES_ACTIVE = 'test'
		SONARQUBE_PROJECT_KEY = 'crypto-portfolio-monitoring'
	}

	stages {

		stage('Checkout') {
			steps {
				git url: 'https://github.com/ArisTeo-Winner/crypto-portfolio-monitoring.git',
					branch: 'feature/jenkins-pipeline',
					credentialsId: 'github-creds'
			}
		}

		stage('Carga .env') {
			steps {
				script {
					def envFile = readFile('.env').split('\n')
					envFile.each {
						if (it && it.contains('=')) {
							def (key, value) = it.split('=')
							env[key.trim()] = value.trim()
						}
					}
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
				anyOf {
					branch 'dev'
				}
			}

			steps {
				withEnv([
					'SPRING_DATASOURCE_URL=',
					'SPRING_DATASOURCE_USERNAME=',
					'SPRING_DATASOURCE_PASSWORD='
				]) {
					bat 'mvn test -Dspring.profiles.active=test'
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
					anyOf {
						branch 'dev'
						branch 'main'
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
				bat 'mvn clean package -DskipTests'
			}
		}
		stage('Docker Build') {
			steps {
				bat 'docker build -t crypto-monitor:latest .'
			}
		}
		stage('Run App') {
			steps {
				bat 'docker-compose down && docker-compose build --no-cache && docker-compose up -d'
			}
		}
	}
	post {
		success {
			echo "Build exitoso"
			echo "MENSSEGER_TO_THE_JENKINS: ${env.MENSSEGER_TO_THE_JENKINS}"
		}
		failure {
			echo "Fallo el build"
		}
	}
}
