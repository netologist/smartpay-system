# Development Environment Setup

## Add new Module

curl -s https://start.spring.io/starter.tgz \
-d type=maven-project -d language=java \
-d bootVersion=4.1.1.RELEASE -d javaVersion=25 \
-d groupId=com.hozgan.smartpay -d artifactId=smartpay-gateway \
-d name=smartpay-gateway \
-d packageName=com.hozgan.smartpay.gateway \
-d dependencies=web,actuator,validation,data-jpa,flyway,testcontainers tar -xzf - -C smartpay-gateway