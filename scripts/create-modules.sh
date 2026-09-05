#!/usr/bin/env bash

for module in "proto" "common" "gateway" "invoice-service" "payment-service" "ledger-service"  "payout-worker" "recon-service" "risk-service" "notification-service"; do
    name="smartpay-$module"
    pkg="com.hozgan.smartpay.${name#smartpay-}"
    pkg="${pkg%-service}"
    pkg="${pkg%-worker}"

    if [ -d "./$name" ]; then
      echo "dir $name exist"
      continue
    fi

    mkdir "./$name"
    curl -s https://start.spring.io/starter.tgz \
    -d type=maven-project -d language=java \
    -d bootVersion=4.1.1.RELEASE -d javaVersion=25 \
    -d groupId=com.hozgan.smartpay -d artifactId=$name \
    -d name=$name \
    -d packageName="$pkg" \
    -d dependencies=web,actuator,validation,data-jpa,flyway,testcontainers | tar -xzf - -C $name
    echo "created $name"
done

