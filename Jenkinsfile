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
	}
	post {
		success {
			echo "Build exitoso"
		}
		failure {
			echo "Falló el build"
		}
	}
}