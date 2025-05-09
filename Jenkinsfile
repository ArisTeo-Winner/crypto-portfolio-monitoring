pipeline {
	agent any
	tools {
		maven 'Maven 3.9.9'
		jdk 'jdk-21'
	}
	stages {
		
		stage('Checkout') {
			steps {
				git url: 'https://github.com/ArisTeo-Winner/crypto-portfolio-monitoring.git', credentialsId: 'github-creds'
			}
		}
		stage('Carga .env'){
			step {
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

		stage('Test'){
			steps {
				bat 'mvn test -Dspring.profiles.active=test'
			}
			post {
				always {
					junit 'target/surefire-reports/*.xml'
				}
			}
		}
		stage('Build JAR'){
			steps {
				bat 'mvn clean package -DskipTests'
			}
		}
		stage('Docker Build'){
			steps {
				bat 'docker build -t crypto-monitor:latest .'
			}
		}
	}
	post {
		success {
			echo "Build exitoso"
			echo "API KEY: ${env.API_COINMARKETCAP_BASE_UR}"
		}
		failure {
			echo "Falló el build"
		}
	}
}