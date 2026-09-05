#!/usr/bin/env bash

for module in "proto" "common" "gateway" "invoice-service" "payment-service" "ledger-service"  "payout-worker" "recon-service" "risk-service" "notification-service"; do
    name="smartpay-$module"
    rm -rf "./$name/.mvn"
    rm -rf "./$name/mvnw"
    rm -rf "./$name/mvnw.cmd"
    echo "cleaned up maven wrapper in ./$name module"
done

