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
O AWS Redshift trabalha com muitos processos paralelos, mas não consegue devolver o id gerados no banco de dados através de funções como IDENTITY quando tentamos recuperar o id gerado imediatamente. Por isso, a melhor estratégia, principalmente quando usamos o Hibernate, seria gerar o ID no lado backend e enviá-lo nas instruções de insert. (Esta biblioteca possui classes para geração segura de IDs numeriros Long de 64 e 53 bits. Obs: Frameworks JavaScript não apresentam corretamente numeros Long 64 bits gerados em Java)

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
        .onFailure(throwable -> log.error("Insert failed: {}", throwable.getMessage()))
        .execute();
    
    return id;
}
```

#### en-US - Query with object mapping:
Redshift by default does not work case sensitive (It is possible to enable/disable this property), to correctly serialize attributes it is possible to use the @JsonAlias ​​annotation to correctly adapt their names.

#### pt-BR - Consulta com mapeamento de classe:
O Redshift por padrão não trabalha case sensitive (É possível ativar / desativar esta propriedade), para serializar corretamente atributos é possível usar a anotação @JsonAlias para adequar corretamente os seus nomes

```Java
//Hypothetical class
@Builder
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

//Query examples
private final RedshiftFunctionalJdbc redshiftPool;

public Optional<User> findUser(Long id) {
    return redshiftPool.jdbcQuery()
        .query("SELECT * FROM users WHERE id = ?")
        .parameters(Collections.singletonList(id))
        .fetchOne(User.class);
}

//Or Implementation with PreparedStatement
public Optional<User> findUser(Long id) {
    var query = """
            SELECT id, name, email,created_at FROM users WHERE id = ?
            """;
    return redshiftPool
            .jdbcQuery()
            .query(query)
            .parameters(ps-> ps.setLong(1, id))//PreparedStatement
            .fetchOne(User.class);
}

//Or Implementation with ResultSet
public Optional<User> findUser(Long id) {
  var query = """
          SELECT
              id, name, email,created_at
          FROM
              users
          WHERE id = ?
          """;
  return redshiftPool
          .jdbcQuery()
          .query(query)
          .parameters(ps-> ps.setLong(1, id))//PreparedStatement
          .fetchOne(rs -> User.builder()
                  .id(rs.getLong("id"))//ResultSet
                  .name(rs.getString("name"))//ResultSet
                  .email(rs.getString("email"))//ResultSet
                  .createdAt(rs.getString("created_at"))//ResultSet
                  .build());
}


```

[//]: # (Fazer Intruções delete, update, consulta com paginação, materialized view)










## Best Practices
### Performance Optimization
#### en-US
- Batch Size: Use 100-1000 rows per batch for optimal Redshift performance
- Connection Pooling: Configure your DataSource properly (HikariCP recommended)
- Column Selection: Always specify columns instead of using SELECT *
- In DDL statements, create your tables using the correct DISTSTYLE. Use columns that serve as indexes for the DIST KEY type. For small tables or with data that does not change constantly or is repetitive, you can use DISTSTYLE ALL
- When creating tables, understand that Redshift has a columnar architecture, so avoid too many relationships. If there are relationships, create them by relating the DIST KEY of one Table to the DIST KEY of the other. For SORTKEY, choose columns where the data is usually cardinal and use the SORTKEY column as a where clause or ordering.
- When creating attributes for your table, choose the ENCODE of each attribute intelligently according to its type and use.
  Example: for BOOLEAN, choose ENCODE RAW or zstd, for VARCHAR, choose ENCODE lzo, for repetitive data such as enums, choose ENCODE bytedict, for BIGINT, choose ENCODE az64

#### pt-BR
- Tamanho do Lote: Use de 100 a 1.000 linhas por lote para otimizar o desempenho do Redshift
- Pool de Conexões: Configure sua Fonte de Dados corretamente (recomenda-se HikariCP)
- Seleção de Colunas: Sempre especifique colunas em vez de usar SELECT *
- Nas instruções de DDL, crie suas tabelas com o uso correto do DISTSTYLE. Use colunas que servem como índice para o tipo DIST KEY. Para pequenas tabelas ou com dados que não mudam constantemente ou são repetitivos, pode-se usar DISTSTYLE ALL
- Na crição de tabelas, entenda que o Redshift tem sua arquitetura colunar, portanto, evite muitos relacionamentos. Caso existam relacionamentos, faça-os relacionando a DIST KEY de uma Tabela com a DIST KEY da outra. Para SORTKEY escolha colunas onde os dados costumam ser cardinais e use a coluna SORTKEY  como clausula where ou ordenação
- Na criação dos atributos de sua tabela escolha de forma inteligente o ENCODE de cada atributo de acordo com seu tipo e uso.
  Exemplo: para BOOLEAN prefira ENCODE RAW ou zstd, para VARCHAR prefira ENCODE lzo, para dados repetitivos como enums prefira ENCODE bytedict, BIGINT prefira ENCODE az64


## API Reference


## Limitations