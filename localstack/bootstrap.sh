#!/usr/bin/env bash
set -euo pipefail

STACK_NAME="arka-infra-stack"
TEMPLATE_FILE="$(dirname "$0")/infra.yaml"
REGION="us-east-1"

# ── Variables de entorno (inyectadas desde .env via Docker Compose) ──
DB_USER="$POSTGRES_USER"
DB_PASSWORD="$POSTGRES_PASSWORD"

# PostgreSQL databases
DB_ORDERS_NAME="$POSTGRES_ORDERS_DB"
DB_INVENTORY_NAME="$POSTGRES_INVENTORY_DB"
DB_PAYMENT_NAME="$POSTGRES_PAYMENT_DB"
DB_REPORTER_NAME="$POSTGRES_REPORTER_DB"
DB_SHIPPING_NAME="$POSTGRES_SHIPPING_DB"
DB_PROVIDER_NAME="$POSTGRES_PROVIDER_DB"

# MongoDB
MONGO_USR="$MONGO_USER"
MONGO_PWD="$MONGO_PASSWORD"

# Kafka
KAFKA_BOOTSTRAP="$KAFKA_BOOTSTRAP_SERVERS"

# Microservices hosts
MS_ORDER_URL="http://$MS_ORDER_HOST:$MS_ORDER_PORT"
MS_CATALOG_URL="http://$MS_CATALOG_HOST:$MS_CATALOG_PORT"
MS_INVENTORY_URL="http://$MS_INVENTORY_HOST:$MS_INVENTORY_PORT"
MS_CART_URL="http://$MS_CART_HOST:$MS_CART_PORT"

echo "═══════════════════════════════════════════"
echo "Desplegando stack CloudFormation: $STACK_NAME"
echo "═══════════════════════════════════════════"

# Desplegar el stack con todas las variables
awslocal cloudformation deploy \
  --stack-name "$STACK_NAME" \
  --template-file "$TEMPLATE_FILE" \
  --region "$REGION" \
  --parameter-overrides \
    pEnvironment=dev \
    pDbUser="$DB_USER" \
    pDbPassword="$DB_PASSWORD" \
    pDbOrdersHost=arka-db-orders \
    pDbOrdersName="$DB_ORDERS_NAME" \
    pDbOrdersPort=5432 \
    pDbInventoryHost=arka-db-inventory \
    pDbInventoryName="$DB_INVENTORY_NAME" \
    pDbInventoryPort=5432 \
    pDbPaymentHost=arka-db-payment \
    pDbPaymentName="$DB_PAYMENT_NAME" \
    pDbPaymentPort=5432 \
    pDbReporterHost=arka-db-reporter \
    pDbReporterName="$DB_REPORTER_NAME" \
    pDbReporterPort=5432 \
    pDbShippingHost=arka-db-shipping \
    pDbShippingName="$DB_SHIPPING_NAME" \
    pDbShippingPort=5432 \
    pDbProviderHost=arka-db-provider \
    pDbProviderName="$DB_PROVIDER_NAME" \
    pDbProviderPort=5432 \
    pMongoHost=arka-mongodb \
    pMongoPort=27017 \
    pMongoUser="$MONGO_USR" \
    pMongoPassword="$MONGO_PWD" \
    pRedisHost=arka-redis \
    pRedisPort=6379 \
    pKafkaBootstrapServers="$KAFKA_BOOTSTRAP" \
    pOrderServiceHost="$MS_ORDER_URL" \
    pCatalogServiceHost="$MS_CATALOG_URL" \
    pInventoryServiceHost="$MS_INVENTORY_URL" \
    pCartServiceHost="$MS_CART_URL" \
  --no-fail-on-empty-changeset

echo ""
echo "═══════════════════════════════════════════"
echo "Stack desplegado exitosamente"
echo "═══════════════════════════════════════════"

# ═══════════════════════════════════════════════════
# Verificar SES — Identidad de correo para ms-notifications
# ═══════════════════════════════════════════════════
echo ""
echo "Configurando SES (identidad de correo)..."
awslocal ses verify-email-identity \
  --email-address "noreply@arka.com" \
  --region "$REGION" 2>/dev/null || true

echo "SES identidad verificada: noreply@arka.com"

# ═══════════════════════════════════════════════════
# Mostrar outputs del stack
# ═══════════════════════════════════════════════════
echo ""
echo "Outputs del Stack:"
awslocal cloudformation describe-stacks \
  --stack-name "$STACK_NAME" \
  --region "$REGION" \
  --query 'Stacks[0].Outputs' \
  --output table

# Verificar secretos creados
echo ""
echo "Secretos creados en Secrets Manager:"
awslocal secretsmanager list-secrets \
  --region "$REGION" \
  --query 'SecretList[].Name' \
  --output table

# Verificar API Gateway
echo ""
echo "REST APIs creadas en API Gateway:"
awslocal apigateway get-rest-apis \
  --region "$REGION" \
  --query 'items[].{Name:name, Id:id}' \
  --output table

# Verificar S3
echo ""
echo "Buckets S3 creados:"
awslocal s3 ls --region "$REGION"

echo ""
echo "═══════════════════════════════════════════"
echo "Infraestructura Arka lista"
echo "═══════════════════════════════════════════"
