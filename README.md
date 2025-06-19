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
- en-US - Import this library for use in your spring boot project:
- pt-BR - Importe esta biblioteca para uso em seu projeto spring boot:
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
AWS Redshift works with many parallel processes, but it cannot immediately return the automatically generated ID in the database through functions such as IDENTITY. Therefore, the best strategy, especially when using Hibernate, would be to generate the ID on the backend side and send it in the insert statements. (This library has classes for safely generating 64-bit and 53-bit Long numeric IDs. Note: JavaScript frameworks do not correctly display 64-bit Long numbers on the screen)

#### pt-BR - Insert com ID gerado:
O AWS Redshift trabalha com muitos processos paralelos, mas não consegue devolver imediatamente o id gerado automaticamente no banco de dados através de funções como IDENTITY. Por isso, a melhor estratégia, principalmente quando usamos o Hibernate, seria gerar o ID no lado backend e enviá-lo nas instruções de insert. (Esta biblioteca possui classes para geração segura de IDs numéricos Long de 64 e 53 bits. Obs: Frameworks JavaScript não apresentam corretamente em tela números Long 64 bits)

- @Autowired or @RequiredArgsConstructor
```Java
private final RedshiftFunctionalJdbc redshiftPool;

public Long createUser(UserDTO user) {
  Long id = new IdGeneratorThreadSafe().generate();//53-bit numeric id generator
    
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
___
#### en-US
- Insert returning the entity reference (Object-Entity) for lazy access
#### pt-BR
- Insert retornando a referencia da entidade (Object-Entity) para acesso lazy

```Java
import org.springframework.transaction.annotation.Transactional;

private final RedshiftFunctionalJdbc redshiftPool;
@PersistenceContext
private EntityManager entityManager;

@Transactional
public User createUser(UserDTO user) {
  Long id = new IdGeneratorThreadSafe().generate();

  redshiftPool.jdbcUpdate()
          .query("""
                  INSERT INTO users (id, name, email, created_at)
                  VALUES (?, ?, ?, ?)
                  """)
          .parameters(ps ->{ //preparedStatement
                    ps.setLong(1, id);
                    ps.setString(2, user.getName());
                    ps.setString(3, user.getEmail());
                    ps.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
                  }
          )
          .onSuccess(rows -> log.info("Created user {}", id))
          .onFailure(throwable -> log.error("Insert failed: {}", throwable.getMessage()))
          .execute();

  return entityManager.getReferene(User.class, id);
}
```
___
#### en-US - Delete
Delete operations
#### pt-BR - Delete
Operações com delete. 
```Java
private final RedshiftFunctionalJdbc redshiftPool;

public void deleteItemsFromOrder(Long orderId) {
  String deleteItems = "DELETE FROM item WHERE order_id = ?";

  redshiftPool
          .jdbcUpdate()
          .query(deleteItems)
          .parameters(ps -> ps.setLong(1, orderId))
          .onSuccess(rows -> log.info("Deleted items, modified {} records", rows))
          .execute();
}
```
___
#### en-US - Example of chained operations with delete and batch update.

#### pt-BR - Exemplo de operações encadeadas com delete e update em lote.

```Java
private final RedshiftFunctionalJdbc redshiftPool;

public void deleteItemsFromOrderAndAdd(Long orderId, List<Item> items) {
  String deleteItems = "DELETE FROM item WHERE order_id = ?";

  redshiftPool
          .jdbcUpdate()
          .query(deleteItems)
          .parameters(ps -> ps.setLong(1, orderId))
          .onSuccess(rows -> {
              log.info("Deleted items, modified {} records", rows);
              insertItemsBatch(orderId, items);
          })
          .onFailure(throwable -> log.error("Deleted items failed: {}", throwable.getMessage()))
          .execute();
}

public void insertItemsBatch(Long orderId, List<Item> items){
  if (items.isEmpty()) return;

  String query = "INSERT INTO item (id, description, name, orderId) VALUES(?, ?, ?, ?);";
  RedshiftFunctionalJdbc.JdbcBatchUpdate batchUpdate = redshiftPool.jdbcBatchUpdate().query(query);

  items.forEach(item -> {
    Long id = new IdGeneratorThreadSafe().generate();
    batchUpdate.addBatchParameters(Arrays.asList(
            id,
            item.getDescription(),
            item.getName(),
            orderId
    ));
  });
  batchUpdate
          .isolationLevel(Connection.TRANSACTION_READ_COMMITTED)
          .batchSize(items.size())
          .onSuccess(rows -> log.info("Batch insert items executed, inserted {} records", rows.length))
          .onFailure(throwable -> log.error("Batch insert items failed: {}", throwable.getMessage()))
          .execute();
}
```
___
#### en-US - Example of chained operations with delete and batch update using Project Reactor.

#### pt-BR - Exemplo de operações encadeadas com delete e update em lote usando Project Reactor.

```Java
private final RedshiftFunctionalJdbc redshiftPool;

public Mono<Void> deleteItemsFromOrderAndAdd(Long orderId, List<Item> items) {

  String deleteItems = "DELETE FROM item WHERE order_id = ?";

  return Mono.fromRunnable(() -> redshiftPool
                  .jdbcUpdate()
                  .query(deleteItems)
                  .parameters(ps -> ps.setLong(1, orderId))
                  .onSuccess(rows -> log.info("Deleted items for update, modified {} records", rows))
                  .onFailure(throwable -> log.error("Batch insert items failed: {}", throwable.getMessage()))
                  .execute())
          .then(Mono.defer(() -> {
            if (items.isEmpty()) return Mono.empty();

            String query = "INSERT INTO item (id, description, name, orderId) VALUES(?, ?, ?, ?);";
            RedshiftFunctionalJdbc.JdbcBatchUpdate batchUpdate = redshiftPool.jdbcBatchUpdate().query(query);

            items.forEach(item -> {
              Long id = new IdGeneratorThreadSafe().generate();
              batchUpdate.addBatchParameters(Arrays.asList(
                      id,
                      item.getDescription(),
                      item.getName(),
                      orderId
              ));
            });

            return Mono.fromRunnable(() -> batchUpdate
                    .isolationLevel(Connection.TRANSACTION_READ_COMMITTED)
                    .batchSize(items.size())
                    .onSuccess(rows -> log.info("Batch insert items executed, inserted {} records", rows.length))
                    .onFailure(throwable -> log.error("Batch insert items failed: {}", throwable.getMessage()))
                    .execute()
            );
          }));
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

//Or Implementation with Project Reactor (Webflux)
public Mono<User> findUser(Long id) {
  var query = """
          SELECT
              id, name, email, created_at
          FROM
              users
          WHERE id = ?
          """;

  return Mono.fromCallable(() -> redshiftPool
                  .jdbcQuery()
                  .query(query)
                  .parameters(ps -> ps.setLong(1, id))
                  .fetchOne(rs -> User.builder()
                          .id(rs.getLong("id"))
                          .name(rs.getString("name"))
                          .email(rs.getString("email"))
                          .createdAt(rs.getString("created_at"))
                          .build()))
          .flatMap(Mono::justOrEmpty);
}


```
#### en-US - Query find All:
Search all query, with automatic mapping or with ResultSet

#### pt-BR - Consultar todos:
Consulta do tipo buscar todos, com mapeamento automático ou com ResultSet

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

private final RedshiftFunctionalJdbc redshiftPool;


public List<User> findAllUsers() {
  var query = """
          SELECT id, name, email, created_at FROM users
          """;
  return redshiftPool
          .jdbcQuery()
          .query(query)
          .executeQuery(User.class);
}

public List<User> findUsersByIds(List<Long> userIds) {
  var idList = userIds.stream()
          .map(String::valueOf)
          .collect(Collectors.joining(","));
    
  var query = """
          SELECT
              id, name, email, created_at
          FROM
              users
          WHERE id in (%s)
          """.formatted(idList);
  
  return redshiftPool
          .jdbcQuery()
          .query(query)
          .executeQuery(rs -> User.builder()
                  .id(rs.getLong("id"))//ResultSet
                  .name(rs.getString("name"))//ResultSet
                  .email(rs.getString("email"))//ResultSet
                  .createdAt(rs.getString("created_at"))//ResultSet
                  .build());
}
```

[//]: # (Fazer Intruções delete, update, consulta com paginação, materialized view)

### 2-batch-operations




### 3-paginated-queries



### 4-materialized-views

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
### Core Methods
#### en-US
| Method                | Description                                   |
|-----------------------|-----------------------------------------------|
| **jdbcQuery()**       | Creates a SELECT query builder                |
| **jdbcUpdate()**      | Creates an INSERT/UPDATE/DELETE builder       |
| **jdbcBatchUpdate()** | Creates a batch operation builder             |
| **jdbcQueryPage()**   | Creates a paginated query builder             |
| **jdbcUpdateMv()**    | Creates a materialized view operation builder |
___

### Common Builder Methods
| Method	                      | Available For	 | Description                           |
|------------------------------|:--------------:|---------------------------------------|
| **.query(String)**           |      All       | Sets the SQL query                    |
| **.parameters(List)**	       |      All       | Sets parameters as ordered list       |
| **.parameters(SQLConsumer)** |      All       | Sets parameters via PreparedStatement |
| **.onSuccess()**             |  Update/Batch  | Success callback                      |
| **.onFailure()**             |  Update/Batch  | Error callback                        |
| **.pageSize()**              |   QueryPage    | Sets page size                        |                      
| **.pageIndex()**             |   QueryPage    | Sets page number                      |                      
| **.sort()**                  |   QueryPage    | Sets sorting criteria                 |                 
| **.batchSize()**             |     Batch      | Sets batch chunk size                 |                 
| **.isolationLevel()**        |  Update/Batch  | Sets transaction isolation            |
___

### Constructor terminal methods
| Method	                                |             Available For	              | Description                                                                                                                                                              |
|----------------------------------------|:---------------------------------------:|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **.executeQuery(SQLFunction)**         |         JdbcQuery/JdbcQueryPage         | Executes the algorithm defined in the construction steps and maps the attributes one by one with ResultSet                                                               |
| **.executeQuery(Class<T> clazz)**      |         JdbcQuery/JdbcQueryPage         | Executes the algorithm defined in the construction steps and automatically maps the attributes to the defined class                                                      |
| **.fetchOne(SQLFunction)**             |                JdbcQuery                | Executes the algorithm defined in the construction steps and maps the attributes one by one with ResultSet returning only one result in Optional<>                       |
| **.fetchOne(Class<T> clazz)**          |                JdbcQuery                | Executes the algorithm defined in the construction steps and automatically maps the attributes to the defined class returning only one result in Optional<>              |
| **.executePagedQuery(SQLFunction)**    |              JdbcQueryPage              | Executes the algorithm defined in the construction steps and maps the attributes one by one with ResultSet returning a Page<T> containing the paginated Objects          |
| **.executePagedQuery(Class<T> clazz)** |              JdbcQueryPage              | Executes the algorithm defined in the construction steps and automatically maps the attributes to the defined class returning a Page<T> containing the paginated Objects |
| **.execute()**                         | JdbcUpdate/JdbcBatchUpdate/JdbcUpdateMv | Executes the algorithm defined in the construction steps and executes single, batch, or DML statements for Redshift Materialized Views                                   |
___

### Métodos principais
#### pt-BR
| Método	               | Descrição                                                    |
|-----------------------|--------------------------------------------------------------|
| **jdbcQuery()**       | Cria um construtor de consulta SELECT                        |
| **jdbcUpdate()**      | Cria um construtor INSERT/UPDATE/DELETE                      |
| **jdbcBatchUpdate()** | Cria um construtor de operações em lote                      |
| **jdbcQueryPage()**   | Cria um construtor de consulta paginada                      |
| **jdbcUpdateMv()**    | Cria um construtor de operação de visualização materializada |
___

### Métodos comuns do construtor
| Método	                      | Disponível para	 | Descrição                               |
|------------------------------|:----------------:|-----------------------------------------|
| **.query(String)**           |      Todos       | Define a instrução SQL                  |
| **.parameters(List)**	       |      Todos       | Define parâmetros como lista ordenada   |
| **.parameters(SQLConsumer)** |      Todos       | Define parâmetros via PreparedStatement |
| **.onSuccess()**             |   Update/Batch   | Retorno de chamada de sucesso           |
| **.onFailure()**             |   Update/Batch   | Retorno de chamada de erro              |
| **.pageSize()**              |    QueryPage     | Define o tamanho da página              |
| **.pageIndex()**             |    QueryPage     | Define o número da página               |
| **.sort()**                  |    QueryPage     | Define critérios de classificação       |
| **.batchSize()**             |      Batch       | Define o tamanho do bloco do lote       |
| **.isolationLevel()**        |   Update/Batch   | Define o isolamento da transação        |
___

### Métodos terminais do construtor
| Método	                                |            Disponível para	             | Descrição                                                                                                                                                              |
|----------------------------------------|:---------------------------------------:|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **.executeQuery(SQLFunction)**         |         JdbcQuery/JdbcQueryPage         | Executa o algoritmo definido nas etapas de construção e mapeia um a um os atributos com ResultSet                                                                      |
| **.executeQuery(Class<T> clazz)**      |         JdbcQuery/JdbcQueryPage         | Executa o algoritmo definido nas etapas de construção e mapeia automaticamente os atributos para a classe definida                                                     |
| **.fetchOne(SQLFunction)**             |                JdbcQuery                | Executa o algoritmo definido nas etapas de construção e mapeia um a um os atributos com ResultSet devolvendo apenas um resultado em Optional<>                         |
| **.fetchOne(Class<T> clazz)**          |                JdbcQuery                | Executa o algoritmo definido nas etapas de construção e mapeia automaticamente os atributos para a classe definida devolvendo apenas um resultado em Optional<>        |
| **.executePagedQuery(SQLFunction)**    |              JdbcQueryPage              | Executa o algoritmo definido nas etapas de construção e mapeia um a um os atributos com ResultSet devolvendo um Page<T> contendo os Objetos paginados                  |
| **.executePagedQuery(Class<T> clazz)** |              JdbcQueryPage              | Executa o algoritmo definido nas etapas de construção e mapeia automaticamente os atributos para a classe definida devolvendo um Page<T> contendo os Objetos paginados |
| **.execute()**                         | JdbcUpdate/JdbcBatchUpdate/JdbcUpdateMv | Executa o algoritmo definido nas etapas de construção e executa instruções DML únicas, em lotes ou instruções para Materialized Views Redshift                         |


## Limitations

#### en-US
### Redshift Compatibility
- ✖ No support for array data types
- ✖ No MERGE statement support
- ✖ Limited transactional support (avoid long-running transactions)
- ✖ No RETURNING clause in INSERT/UPDATE

### Library Constraints
- Maximum 1000 parameters per query (Redshift limitation)
- Complex joins may require query hints
- JSON operations require explicit casting (See documentation on SUPER types)

### Compatibilidade com Redshift

#### pt-BR
- ✖ Sem suporte para tipos de dados de array
- ✖ Sem suporte para instruções MERGE
- ✖ Suporte transacional limitado (evita transações de longa duração)
- ✖ Sem cláusula RETURNING em INSERT/UPDATE

### Restrições da biblioteca
- Máximo de 1000 parâmetros por consulta (limitação do Redshift)
- Junções complexas podem exigir dicas de consulta
- Operações JSON exigem conversão explícita (Ver documentação sobre tipos SUPER)
___

To download this as a file, you can:

1. Copy the entire content above
2. Save it as `README.md` in your project root
3. Or use this JavaScript snippet in your browser console to download it:

```javascript
const content = `[PASTE_THE_ENTIRE_MARKDOWN_HERE]`;
const blob = new Blob([content], {type: 'text/markdown'});
const url = URL.createObjectURL(blob);
const a = document.createElement('a');
a.href = url;
a.download = 'README.md';
document.body.appendChild(a);
a.click();
document.body.removeChild(a);
```

## 📄 Licença

Software Open Source. Código aberto para comunidade Java.
O presente código foi testado primorosamente e manteve-se útil à sua finalidade em produção, sendo utilizado em grandes projetos com AWS Redshift.
---
Projeto segue a licença de Código Aberto Apache License, Version 2.0

```text
  Copyright 2025 Well Soft Tecnologia LTDA
   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
```
http://www.apache.org/licenses/LICENSE-2.0

---