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
				withEnv([
					'SPRING_DATASOURCE_URL=',
                    'SPRING_DATASOURCE_USERNAME=',
                    'SPRING_DATASOURCE_PASSWORD=',
				])
				{
					bat 'mvn test -Dspring.profiles.active=test'
				}
			}
			post {
				always {
					junit 'target/surefire-reports/*.xml'
				}
			}
		}
		stage('Carga .env'){
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
		stage('Run App') {
			steps {
				bat 'docker-compose down && docker-compose build --no-cache && docker-compose up -d'
			}
		}
	}
	post {
		success {
			echo "Build exitoso"
			echo "API KEY: ${env.MENSSEGER_TO_THE_JENKINS}"
		}
		failure {
			echo "Falló el build"
		}
	}
}