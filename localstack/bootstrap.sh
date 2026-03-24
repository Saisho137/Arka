#!/usr/bin/env bash
set -euo pipefail

STACK_NAME="arka-infra-stack"
TEMPLATE_FILE="$(dirname "$0")/infra.yaml"
LOCALSTACK_ENDPOINT="http://localhost:4566"
REGION="us-east-1"

# ── Variables de entorno (inyectadas desde .env via Docker Compose) ──
DB_USER="$POSTGRES_USER"
DB_PASSWORD="$POSTGRES_PASSWORD"
DB_ORDERS_NAME="$POSTGRES_ORDERS_DB"
DB_ORDERS_PORT="$POSTGRES_ORDERS_PORT"
DB_INVENTORY_NAME="$POSTGRES_INVENTORY_DB"
DB_INVENTORY_PORT="$POSTGRES_INVENTORY_PORT"
DB_PAYMENT_NAME="$POSTGRES_PAYMENT_DB"
DB_PAYMENT_PORT="$POSTGRES_PAYMENT_PORT"
KAFKA_BOOTSTRAP="$KAFKA_BOOTSTRAP_SERVERS"
MS_ORDERS_URL="http://$MS_ORDERS_HOST:$MS_ORDERS_PORT"

echo "Desplegando stack CloudFormation: $STACK_NAME"

# aws s3 ls --endpoint-url http://localhost:4566


# Desplegar (o actualizar) el stack con variables de entorno
aws cloudformation deploy \
  --stack-name "$STACK_NAME" \
  --template-file "$TEMPLATE_FILE" \
  --region "$REGION" \
  --parameter-overrides \
    pEnvironment=dev \
    pDbUser="$DB_USER" \
    pDbPassword="$DB_PASSWORD" \
    pDbOrdersHost=arka-db-orders \
    pDbOrdersName="$DB_ORDERS_NAME" \
    pDbOrdersPort="$DB_ORDERS_PORT" \
    pDbInventoryHost=arka-db-inventory \
    pDbInventoryName="$DB_INVENTORY_NAME" \
    pDbInventoryPort="$DB_INVENTORY_PORT" \
    pDbPaymentHost=arka-db-payment \
    pDbPaymentName="$DB_PAYMENT_NAME" \
    pDbPaymentPort="$DB_PAYMENT_PORT" \
    pKafkaBootstrapServers="$KAFKA_BOOTSTRAP" \
    pOrdersServiceHost="$MS_ORDERS_URL" \
  --no-fail-on-empty-changeset
  --endpoint-url=http://localhost:4566

echo ""
echo "═══════════════════════════════════════════"
echo "Stack desplegado exitosamente"
echo "═══════════════════════════════════════════"

# Mostrar outputs del stack
echo ""
echo "Outputs del Stack:"
aws cloudformation describe-stacks \
  --stack-name "$STACK_NAME" \
  --region "$REGION" \
  --query 'Stacks[0].Outputs' \
  --output table
  --endpoint-url=http://localhost:4566

# Verificar secretos creados
echo ""
echo "Secretos creados en Secrets Manager:"
aws secretsmanager list-secrets \
  --region "$REGION" \
  --query 'SecretList[].Name' \
  --output table
  --endpoint-url=http://localhost:4566

# Verificar API Gateway v1 (REST API)
echo ""
echo "REST APIs creadas en API Gateway:"
aws apigateway get-rest-apis \
  --region "$REGION" \
  --query 'items[].{Name:name, Id:id}' \
  --output table
  --endpoint-url=http://localhost:4566
