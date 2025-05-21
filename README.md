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

## Features
✅
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

## Installation
⚙️
- en-US - Import the library for use:
- pt-BR - Importe a biblioteca para uso:
```xml
<dependency>
  <groupId>com.wellalmeida31</groupId>
  <artifactId>redshift-client</artifactId>
  <version>0.0.1</version>
</dependency>
```

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
#### en-US - Insert with generated ID:
AWS Redshift works with many parallel processes, but it cannot return the ID generated in the database through functions like IDENTITY when we try to retrieve the generated ID immediately. Therefore, the best strategy, especially when using Hibernate, would be to generate the ID on the backend side and send it in the insert statements. (This library has classes for safely generating 64-bit and 53-bit Long number IDs. Note: JavaScript frameworks do not correctly display 64-bit Long numbers generated in Java)

#### pt-BR - Insert com ID gerado:
O AWS Redshift trabalha com muitos processos paralelos, mas não consegue devolver o id gerados no banco de dados através de funções como IDENTITY quando tentamos recuperar o id gerado imediatamente. Por isso, a melhor estratégia, principalmente quando usamos o Hibernate, seria gerar o ID no lado backend e enviá-lo nas instruões de insert. (Esta biblioteca possui classes para geração segura de IDs numeriros Long de 64 e 53 bits. Obs: Frameworks JavaScript não apresentam corretamente numeros Long 64 bits gerados em Java)

- @Autowired or @RequiredArgsConstructor
```Java
private final RedshiftFunctionalJdbc redshiftPool;

public Long createUser(UserDTO user) {
  Long id = (Long) new IdGeneratorThreadSafe().generate(null, null);
    
    redshiftPool.jdbcUpdate()
        .query("""
            INSERT INTO users (id, name, email, created_at)
            VALUES (?, ?, ?, ?)
            """)
        .parameters(Arrays.asList(
            id,
            user.getName(),
            user.getEmail(),
            user.getCreatedAt()
        ))
        .onSuccess(rows -> log.info("Created user {}", id))
        .execute();
    
    return id;
}
```

#### en-US - Query with object mapping:
Redshift by default does not work case sensitive (It is possible to enable/disable this property), to correctly serialize attributes it is possible to use the @JsonAlias ​​annotation to correctly adapt their names.

#### pt-BR - Consulta com mapeamento de classe:
O Redshift por padrão não trabalha case sensitive (É possível ativar / desativar esta propriedade), para serializar corretamente atributos é possível usar a anotação @JsonAlias para adequar corretamente os seus nomes

```Java
private final RedshiftFunctionalJdbc redshiftPool;

public Optional<User> findUser(Long id) {
    return redshiftPool.jdbcQuery()
        .query("SELECT * FROM users WHERE id = ?")
        .parameters(Collections.singletonList(id))
        .fetchOne(User.class);
}

@NoArgsConstructor 
@Getter
@Setter
public class User {
  private Long id;
  private String name;
  private String email;
  @JsonAlias("created_at")
  private LocalDateTime createdAt;
}
```









## Best Practices


## API Reference


## Limitations