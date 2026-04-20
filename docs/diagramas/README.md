# Diagramas de Arquitectura

Esta carpeta contiene los diagramas del sistema Arka.

## Diagramas C4

Los diagramas C1 (Contexto) y C2 (Contenedores) están definidos como código Mermaid en los documentos fuente originales (`tarea-original/` y archivos legacy). Las imágenes renderizadas de estos diagramas se encuentran referenciadas en los documentos de arquitectura del proyecto.

## Diagrama C1 — Contexto

```mermaid
C4Context
title Diagrama de Contexto (Nivel 1) - Sistema E-commerce Arka

Person(cliente, "Cliente B2B", "Almacenes en LATAM")
Person(admin, "Administrador", "Personal interno de Arka")

System(arka, "Plataforma E-commerce Arka", "Ventas B2B, órdenes, stock, reportes, envíos")

System_Ext(idp, "Identity Provider", "Entra ID / Cognito")
System_Ext(pasarelas, "Pasarelas de Pago", "Stripe, Wompi, MercadoPago")
System_Ext(proveedores, "Sistemas de Proveedores", "APIs externas de reabastecimiento")
System_Ext(shipping, "API Logística", "FedEx, DHL")
System_Ext(ses, "AWS SES", "Correos transaccionales")

Rel(cliente, arka, "HTTPS/REST")
Rel(admin, arka, "HTTPS/REST")
Rel(arka, idp, "JWT validation")
Rel(arka, pasarelas, "Pagos")
Rel(arka, proveedores, "Reabastecimiento")
Rel(arka, shipping, "Envíos")
Rel(arka, ses, "Emails")
```

## Diagrama C2 — Contenedores

Ver [01-arquitectura.md](../01-arquitectura.md) para la descripción de todos los contenedores y sus relaciones.
