---
name: localstack-setup
description: "Agrega recursos AWS locales (SQS queue, SNS topic, S3 bucket, secreto, API Gateway route) al stack de LocalStack. Modifica infra.yaml (CloudFormation) y compose.yaml según sea necesario."
---

# LocalStack Setup — Agregar Recursos AWS Locales

## Cuándo Usar

Cuando el usuario pida agregar un recurso AWS local: SQS queue, SNS topic, S3 bucket, secreto en Secrets Manager, ruta en API Gateway, u otro recurso soportado por LocalStack.

## Archivos Involucrados

| Archivo                   | Propósito                                                   |
| ------------------------- | ----------------------------------------------------------- |
| `localstack/infra.yaml`   | CloudFormation template — definir recursos AWS              |
| `localstack/bootstrap.sh` | Script de inicialización (despliega el stack)               |
| `compose.yaml`            | Docker Compose — servicios LocalStack (variable `SERVICES`) |

## Estructura Actual de infra.yaml

```yaml
Parameters:
  pEnvironment: dev | staging | prod
  pDbUser, pDbPassword: credenciales PostgreSQL
  pDbOrdersHost/Name/Port, pDbInventoryHost/Name/Port, pDbPaymentHost/Name/Port
  pKafkaBootstrapServers: kafka:29092

Resources:
  # Secrets Manager — 3 secretos de BD + 1 de Kafka
  rOrdersDbSecret, rInventoryDbSecret, rPaymentDbSecret, rKafkaSecret
  # API Gateway — REST API con proxy a ms-orders
  rArkaRestApi, rOrdersResource, rOrdersProxyResource, rOrdersProxyMethod
  rApiDeployment (stage: v1)

Outputs:
  oApiGatewayUrl, oOrdersSecretArn, oInventorySecretArn, oPaymentSecretArn, oKafkaSecretArn
```

## Cómo Agregar Recursos

### 1. Nuevo Secreto en Secrets Manager

Agregar en `Resources:` de `infra.yaml`:

```yaml
rNewServiceSecret:
  Type: AWS::SecretsManager::Secret
  Properties:
    Name: !Sub "${pEnvironment}/arka/<secret-name>"
    Description: "Descripción del secreto"
    SecretString: !Sub |
      {
        "key": "value"
      }
    Tags:
      - Key: Service
        Value: ms-<name>
      - Key: Environment
        Value: !Ref pEnvironment
```

Agregar Output correspondiente.

### 2. SQS Queue

Agregar `sqs` al `SERVICES` en compose.yaml si no está:

```yaml
SERVICES=secretsmanager,apigateway,cloudformation,sqs
```

En `infra.yaml`:

```yaml
rMyQueue:
  Type: AWS::SQS::Queue
  Properties:
    QueueName: !Sub "${pEnvironment}-arka-<queue-name>"
    VisibilityTimeout: 30
    MessageRetentionPeriod: 345600
```

### 3. SNS Topic

Agregar `sns` al `SERVICES` en compose.yaml si no está.

```yaml
rMyTopic:
  Type: AWS::SNS::Topic
  Properties:
    TopicName: !Sub "${pEnvironment}-arka-<topic-name>"
```

### 4. S3 Bucket

Agregar `s3` al `SERVICES` en compose.yaml si no está.

```yaml
rMyBucket:
  Type: AWS::S3::Bucket
  Properties:
    BucketName: !Sub "${pEnvironment}-arka-<bucket-name>"
```

### 5. Nueva Ruta en API Gateway

Seguir el patrón existente de `/orders`:

```yaml
rNewResource:
  Type: AWS::ApiGateway::Resource
  Properties:
    RestApiId: !Ref rArkaRestApi
    ParentId: !GetAtt rArkaRestApi.RootResourceId
    PathPart: <path>

rNewProxyResource:
  Type: AWS::ApiGateway::Resource
  Properties:
    RestApiId: !Ref rArkaRestApi
    ParentId: !Ref rNewResource
    PathPart: "{proxy+}"

rNewProxyMethod:
  Type: AWS::ApiGateway::Method
  Properties:
    RestApiId: !Ref rArkaRestApi
    ResourceId: !Ref rNewProxyResource
    HttpMethod: ANY
    AuthorizationType: NONE
    RequestParameters:
      method.request.path.proxy: true
    Integration:
      Type: HTTP_PROXY
      IntegrationHttpMethod: ANY
      Uri: !Sub "${pNewServiceHost}/{proxy}"
      RequestParameters:
        integration.request.path.proxy: "method.request.path.proxy"
```

Agregar el parámetro `pNewServiceHost` en `Parameters` y agregar `DependsOn` en `rApiDeployment`.

## Reglas

- Siempre usar `!Sub "${pEnvironment}/arka/..."` para nombres de recursos
- Agregar Tags con `Service` y `Environment`
- Agregar Outputs para cada nuevo recurso (ARN y/o Name)
- Si se agrega un nuevo servicio AWS, actualizar `SERVICES` en compose.yaml
- Verificar que `bootstrap.sh` no necesite cambios (normalmente no los requiere)
- Usar convención de naming: `r<PascalCaseName>` para recursos, `o<PascalCaseName>` para outputs, `p<PascalCaseName>` para parámetros
