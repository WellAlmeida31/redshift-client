# 📘 Redshift Functional JDBC Connector

![Java](https://img.shields.io/badge/Java-17%2B-blue)
![Spring](https://img.shields.io/badge/Spring-Data-red)
![Redshift](https://img.shields.io/badge/Amazon-Redshift-orange)

- en-US - A robust and functional JDBC wrapper for Amazon Redshift operations with fluent API and automatic mapping.
- pt-BR - Um wrapper JDBC robusto e funcional para operações do Amazon Redshift com API fluente e mapeamento automático.

## Table of Contents
- [Features](#features)
- [Installation](#installation)
- [Usage Examples](#usage-examples)
    - [Basic CRUD](#1-basic-crud-operations)
    - [Batch Processing](#2-batch-operations)
    - [Pagination](#3-paginated-queries)
    - [Materialized Views](#4-materialized-views)
- [Best Practices](#best-practices)
- [API Reference](#api-reference)
- [Limitations](#limitations)

## Features ✅
- en-US

✔️ Fluent builder pattern for all operations  
✔️ Automatic ResultSet to Object mapping  
✔️ Comprehensive batch update support  
✔️ Built-in pagination handling  
✔️ Materialized view refresh  
✔️ Transaction isolation control  
✔️ Parameterized query protection  
✔️ Success/Error handlers  

- pt-BR

✔️ Padrão de construtor fluente para todas as operações  
✔️ Mapeamento automático de ResultSet para objeto   
✔️ Suporte abrangente para atualização em lote  
✔️ Manipulação de paginação integrada  
✔️ Atualização de visualização materializada  
✔️ Controle de isolamento de transação  
✔️ Proteção de consulta parametrizada  
✔️ Manipuladores de sucesso/erro  

## Installation ⚙️
- en-US - Common dependencies
- pt-BR - Dependências comuns
### Maven
#### en-US
Add the basic dependencies to your project's pom.xml:
- note: redshift-jdbc42 in case of use with AWS Redshift
#### pt-BR
Adicione as dependências básicas no pom.xml do seu projeto:
- obs: redshift-jdbc42 no caso do uso com AWS Redshift 
```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<dependency>
  <groupId>com.amazon.redshift</groupId>
  <artifactId>redshift-jdbc42</artifactId>
  <version>${latest-version}</version>
</dependency>
```
### Gradle (Groovy DSL)
```groovy
dependencies {
    implementation 'com.amazon.redshift:redshift-jdbc42:2.1.0.7'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
}
```
### Gradle (Kotlin DSL)
```kotlin
dependencies {
  implementation("com.amazon.redshift:redshift-jdbc42")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
}
```
## Usage Examples

### 1. Basic CRUD Operations
#### en-US

- 

