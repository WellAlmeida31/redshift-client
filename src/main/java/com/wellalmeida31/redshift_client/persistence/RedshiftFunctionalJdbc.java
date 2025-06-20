package com.wellalmeida31.redshift_client.persistence;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wellalmeida31.redshift_client.exception.NoStackTraceThrowable;
import com.wellalmeida31.redshift_client.exception.RedshiftException;
import com.wellalmeida31.redshift_client.tools.EpochMilliLocalDateTimeDeserializer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.wellalmeida31.redshift_client.tools.SafeTools.supplierElseSafe;

@Component
@RequiredArgsConstructor
public class RedshiftFunctionalJdbc {

    private final DataSource dataSource;

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule().addDeserializer(LocalDateTime.class, new EpochMilliLocalDateTimeDeserializer()))
            .setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final Pattern SQL_DML_PATTERN = Pattern.compile("^(?i)(SELECT|INSERT|UPDATE|DELETE)\\s+.*", Pattern.DOTALL);
    private static final String[] UNSUPPORTED_PATTERNS = {
            "\\bMERGE\\b",
            "\\bUSING\\b.*\\bON\\b",
            "\\bON\\b\\s+CONFLICT",
            "\\bRETURNING\\b",
            "\\bARRAY\\b",
            "\\bGEOMETRY\\b",
            "\\bVACUUM\\b.*\\bDELETE\\b"
    };
    private static final Pattern SQL_UPDATE_MV_PATTERN = Pattern.compile("^(?i)(REFRESH|DROP|CREATE)\\s+.*", Pattern.DOTALL);

    private static final Set<Integer> ALLOWED_ISOLATION_LEVELS = Set.of(
            Connection.TRANSACTION_NONE,
            Connection.TRANSACTION_READ_UNCOMMITTED,
            Connection.TRANSACTION_READ_COMMITTED,
            Connection.TRANSACTION_REPEATABLE_READ,
            Connection.TRANSACTION_SERIALIZABLE
    );

    public JdbcUpdate jdbcUpdate(){
        return new JdbcUpdate(dataSource);
    }

    public JdbcBatchUpdate jdbcBatchUpdate(){
        return new JdbcBatchUpdate(dataSource);
    }

    public JdbcQuery jdbcQuery(){
        return new JdbcQuery(dataSource);
    }

    public JdbcQueryPage jdbcQueryPage(){
        return new JdbcQueryPage(dataSource);
    }

    public JdbcUpdateMv jdbcUpdateMv(){
        return new JdbcUpdateMv(dataSource);
    }

    public static class JdbcQuery {
        private final DataSource dataSource;
        private String query;
        private SQLConsumer<PreparedStatement> parameterSetter;

        private static final Pattern DQL_PATTERN = Pattern.compile("^(?i)SELECT\\s+.+\\s+FROM\\s+.+", Pattern.DOTALL);
        private static final String[] UNSUPPORTED_PATTERNS = {
                "\\bWITH\\b.*\\bRECURSIVE\\b",
                "\\bFULL OUTER JOIN\\b.*\\bON\\b",
                "\\bFETCH FIRST\\b.*\\bROWS ONLY\\b",
                "\\bOFFSET\\b.*\\bROWS\\b",
                "\\bWINDOW\\b",
                "\\bLATERAL\\b",
                "\\bQUALIFY\\b"
        };

        public JdbcQuery(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        public JdbcQuery query(String query) {
            this.validationQuery(query);
            this.query = query;
            return this;
        }

        public JdbcQuery parameters(SQLConsumer<PreparedStatement> parameterSetter) {
            this.parameterSetter = parameterSetter;
            return this;
        }

        public JdbcQuery parameters(List<Object> attributes){
            this.parameterSetter = readParameters(attributes);
            return this;
        }

        public <T> List<T> executeQuery(SQLFunction<ResultSet, T> mapper) {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement ps = connection.prepareStatement(query)) {

                if(parameterSetter != null) parameterSetter.accept(ps);

                try (ResultSet rs = ps.executeQuery()) {
                    return Stream.generate(() -> {
                                try {
                                    if (rs.next()) return supplierElseSafe(()-> mapper.apply(rs), null);
                                    else return null;
                                } catch (SQLException e) {
                                    throw new RedshiftException(e);
                                }
                            })
                            .takeWhile(Objects::nonNull)
                            .toList();
                }

            } catch (SQLException e) {
                throw new RedshiftException(e);
            }
        }

        public <T> List<T> executeQuery(Class<T> clazz) {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement ps = connection.prepareStatement(query)) {

                if(parameterSetter != null) parameterSetter.accept(ps);

                try (ResultSet rs = ps.executeQuery()) {

                    return Stream.generate(() -> {
                                try {
                                    if (rs.next()) {
                                        Map<String, Object> map = resultSetToMap(rs);
                                        return supplierElseSafe(()-> objectMapper.convertValue(map, clazz), null);
                                    }
                                    else return null;

                                } catch (SQLException e) {
                                    throw new RedshiftException(e);
                                }
                            })
                            .takeWhile(Objects::nonNull)
                            .toList();
                }

            } catch (SQLException e) {
                throw new RedshiftException(e);
            }
        }

        public <T> Optional<T> fetchOne(SQLFunction<ResultSet, T> mapper) {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement ps = connection.prepareStatement(query)) {

                if(parameterSetter != null) parameterSetter.accept(ps);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return Optional.of(mapper.apply(rs));
                    return Optional.empty();
                }

            } catch (SQLException e) {
                throw new RedshiftException(e);
            }
        }

        public <T> Optional<T> fetchOne(Class<T> clazz) {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement ps = connection.prepareStatement(query)) {

                if(parameterSetter != null) parameterSetter.accept(ps);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Map<String, Object> map = resultSetToMap(rs);
                        return Optional.of(supplierElseSafe(()-> objectMapper.convertValue(map, clazz), null));
                    }
                    return Optional.empty();
                }

            } catch (SQLException e) {
                throw new RedshiftException(e);
            }
        }

        private void validationQuery(String query) {
            if(!isValidDQL(query) || !isUnsupportedDqlRedshift(query)) {
                throw new RedshiftException(query + " is not valid");
            }
        }

        private boolean isValidDQL(String sql) {
            if (sql == null || sql.trim().isEmpty()) return false;
            String trimmedSql = sql.trim();
            return DQL_PATTERN.matcher(trimmedSql).matches();
        }

        private boolean isUnsupportedDqlRedshift(String sql) {
            for (String pattern : UNSUPPORTED_PATTERNS) {
                if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(sql).find()) return false;
            }
            return true;
        }

    }

    @Slf4j
    public static class JdbcQueryPage {
        private final DataSource dataSource;
        private String query;
        private SQLConsumer<PreparedStatement> parameterSetter;
        private int pageSize = 10;
        private int pageIndex = 0;
        private Sort sort;

        private static final Pattern DQL_PATTERN = Pattern.compile("^(?i)SELECT\\s+.+\\s+FROM\\s+.+", Pattern.DOTALL);
        private static final String[] UNSUPPORTED_PATTERNS = {
                "\\bWITH\\b.*\\bRECURSIVE\\b",
                "\\bFULL OUTER JOIN\\b.*\\bON\\b",
                "\\bFETCH FIRST\\b.*\\bROWS ONLY\\b",
                "\\bOFFSET\\b.*\\bROWS\\b",
                "\\bWINDOW\\b",
                "\\bLATERAL\\b",
                "\\bQUALIFY\\b"
        };

        public JdbcQueryPage(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        public JdbcQueryPage query(String query) {
            this.validationQuery(query);
            this.query = query;
            return this;
        }

        public JdbcQueryPage parameters(SQLConsumer<PreparedStatement> parameterSetter) {
            this.parameterSetter = parameterSetter;
            return this;
        }

        public JdbcQueryPage parameters(List<Object> attributes) {
            this.parameterSetter = readParameters(attributes);
            return this;
        }

        public JdbcQueryPage pageSize(int pageSize) {
            if (pageSize <= 0) throw new IllegalArgumentException("Page size must be greater than 0.");
            this.pageSize = pageSize;
            return this;
        }

        public JdbcQueryPage pageIndex(int pageIndex) {
            if (pageIndex < 0) throw new IllegalArgumentException("Page index cannot be negative.");
            this.pageIndex = pageIndex;
            return this;
        }

        public JdbcQueryPage sort(Sort sort){
            this.sort = sort;
            return this;
        }

        public <T> Page<T> executePagedQuery(SQLFunction<ResultSet, T> mapper) {
            String orderByClause = buildOrderByClause(sort);
            String paginatedQuery = query + orderByClause + " LIMIT ? OFFSET ?";
            long totalElements = countTotalElements();

            try (Connection connection = dataSource.getConnection();
                 PreparedStatement ps = connection.prepareStatement(paginatedQuery)) {

                if(parameterSetter != null) parameterSetter.accept(ps);
                ps.setInt(ps.getParameterMetaData().getParameterCount() - 1, pageSize);
                ps.setInt(ps.getParameterMetaData().getParameterCount(), pageIndex * pageSize);

                try (ResultSet rs = ps.executeQuery()) {
                    List<T> elements = new ArrayList<>();
                    while (rs.next()) {
                        elements.add(mapper.apply(rs));
                    }

                    return new PageImpl<>(
                            elements,
                            PageRequest.of(pageIndex, pageSize),
                            totalElements
                    );
                }
            } catch (SQLException e) {
                throw new RedshiftException(e);
            }
        }

        public <T> Page<T> executePagedQuery(Class<T> clazz) {
            String orderByClause = buildOrderByClause(sort);
            String paginatedQuery = query + orderByClause + " LIMIT ? OFFSET ?";
            long totalElements = countTotalElements();
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement ps = connection.prepareStatement(paginatedQuery)) {

                if(parameterSetter != null) parameterSetter.accept(ps);
                ps.setInt(ps.getParameterMetaData().getParameterCount() - 1, pageSize);
                ps.setInt(ps.getParameterMetaData().getParameterCount(), pageIndex * pageSize);

                try (ResultSet rs = ps.executeQuery()) {
                    List<T> elements = getElements(clazz, rs);
                    return new PageImpl<>(
                            elements,
                            PageRequest.of(pageIndex, pageSize),
                            totalElements
                    );
                }
            } catch (SQLException e) {
                throw new RedshiftException(e);
            }
        }

        private <T> List<T> getElements(Class<T> clazz, ResultSet rs) throws SQLException {
            List<T> elements = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> map = resultSetToMap(rs);
                elements.add(objectMapper.convertValue(map, clazz));
            }
            return elements;
        }

        private long countTotalElements() {
            String countQuery = "SELECT COUNT(*) FROM (" + query + ") AS count_query";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement ps = connection.prepareStatement(countQuery)) {

                if(parameterSetter != null) parameterSetter.accept(ps);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getLong(1);
                    }
                    return 0;
                }
            } catch (SQLException e) {
                throw new RedshiftException("CountTotalElements: " + e.getMessage());
            }
        }

        private String buildOrderByClause(Sort sort) {
            if (sort.isUnsorted()) return "";
            StringBuilder orderBy = new StringBuilder(" ORDER BY ");
            sort.forEach(order -> orderBy.append(order.getProperty())
                    .append(" ")
                    .append(order.isAscending() ? "ASC" : "DESC")
                    .append(", "));
            orderBy.setLength(orderBy.length() - 2);
            return orderBy.toString();
        }

        private void validationQuery(String query) {
            if(!isValidDQL(query) || !isUnsupportedDqlRedshift(query)) {
                throw new RedshiftException(query + " is not valid");
            }
        }

        private boolean isValidDQL(String sql) {
            if (sql == null || sql.trim().isEmpty()) return false;
            String trimmedSql = sql.trim();
            return DQL_PATTERN.matcher(trimmedSql).matches();
        }

        private boolean isUnsupportedDqlRedshift(String sql) {
            for (String pattern : UNSUPPORTED_PATTERNS)
                if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(sql).find()) return false;
            return true;
        }
    }

    @Slf4j
    public static class JdbcBatchUpdate {
        private final DataSource dataSource;
        private String query;
        private List<List<Object>> batchParameters = new ArrayList<>();
        private SQLConsumer<int[]> successHandler;
        private SQLConsumer<Throwable> failureHandler;
        private boolean success;
        private Throwable error;
        private Integer isolationLevel;
        private int batchSize = 100;

        public JdbcBatchUpdate(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        public JdbcBatchUpdate query(String query) {
            validationDMLQuery(query);
            this.query = query;
            return this;
        }

        public JdbcBatchUpdate addBatchParameters(List<Object> parameters) {
            batchParameters.add(parameters);
            return this;
        }

        public JdbcBatchUpdate isolationLevel(int isolationLevel) {
            validateIsolationLevel(isolationLevel);
            this.isolationLevel = isolationLevel;
            return this;
        }

        public JdbcBatchUpdate batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        public void execute() {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement ps = connection.prepareStatement(query)) {

                if (isolationLevel != null) connection.setTransactionIsolation(isolationLevel);

                int count = 0;
                List<Integer> totalCountsList = new ArrayList<>();

                for (List<Object> parameters : batchParameters) {
                    SQLConsumer<PreparedStatement> parameterSetter = readParameters(parameters);
                    parameterSetter.accept(ps);
                    ps.addBatch();
                    count++;

                    if (count % batchSize == 0) {
                        int[] updateCounts = ps.executeBatch();
                        for (int uc : updateCounts) {
                            totalCountsList.add(uc);
                        }
                        ps.clearBatch();
                    }
                }

                if (count % batchSize != 0) {
                    int[] updateCounts = ps.executeBatch();
                    for (int uc : updateCounts) {
                        totalCountsList.add(uc);
                    }
                }

                int[] totalCounts = totalCountsList.stream().mapToInt(Integer::intValue).toArray();
                success = true;
                successVerify(totalCounts);
            } catch (SQLException e) {
                failed(e);
                failureVerify();
                throw new RedshiftException(e);
            }
        }

        private void failed(Throwable t) {
            this.error = t != null ? t : new NoStackTraceThrowable((String) null);
            this.success = false;
        }

        public JdbcBatchUpdate onSuccess(SQLConsumer<int[]> successHandler) {
            this.successHandler = Objects.requireNonNull(successHandler, "successHandler is null");
            return this;
        }

        private void successVerify(int[] updateCounts) {
            if (success && successHandler != null) {
                try {
                    successHandler.accept(updateCounts);
                } catch (SQLException e) {
                    log.error("Error during success handler execution", e);
                }
            }
        }

        public JdbcBatchUpdate onFailure(SQLConsumer<Throwable> failureHandler) {
            this.failureHandler = Objects.requireNonNull(failureHandler, "failureHandler is null");
            return this;
        }

        private void failureVerify() {
            if (!success && failureHandler != null) {
                try {
                    failureHandler.accept(error);
                } catch (SQLException e) {
                    log.error("Error during failure handler execution", e);
                }
            }
        }
    }

    @Slf4j
    public static class JdbcUpdate {
        private final DataSource dataSource;
        private String query;
        private SQLConsumer<PreparedStatement> parameterSetter;
        private SQLConsumer<Integer> successHandler;
        SQLConsumer<Throwable> failureHandler;
        private boolean success;
        private Throwable error;
        private int rowsInserted;
        private Integer isolationLevel;

        JdbcUpdate(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        public JdbcUpdate query(String query) {
            validationDMLQuery(query);
            this.query = query;
            return this;
        }

        public JdbcUpdate parameters(SQLConsumer<PreparedStatement> parameterSetter) {
            this.parameterSetter = parameterSetter;
            return this;
        }

        public JdbcUpdate parameters(List<Object> attributes) {
            this.parameterSetter = readParameters(attributes);
            return this;
        }

        public JdbcUpdate isolationLevel(int isolationLevel) {
            validateIsolationLevel(isolationLevel);
            this.isolationLevel = isolationLevel;
            return this;
        }

        public void execute() {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement ps = connection.prepareStatement(query)) {
                if(parameterSetter != null) parameterSetter.accept(ps);
                if (isolationLevel != null) connection.setTransactionIsolation(isolationLevel);
                rowsInserted = ps.executeUpdate();
                success = true;
                successVerify();
            } catch (SQLException e) {
                failed(e);
                failureVerify();
                throw new RedshiftException(e);
            }
        }

        private void failed(Throwable t){
            this.error = t != null ? t : new NoStackTraceThrowable((String) null);
            this.success = false;
        }

        public JdbcUpdate onSuccess(SQLConsumer<Integer> successHandler) {
            Objects.requireNonNull(successHandler, "successHandler is null");
            this.successHandler = successHandler;
            return this;
        }

        private void successVerify() {
            if (success && successHandler != null) {
                try {
                    successHandler.accept(rowsInserted);
                } catch (SQLException e) {
                    log.error("Error during success handler execution", e);
                }
            }

        }

        public JdbcUpdate onFailure(SQLConsumer<Throwable> failureHandler) {
            Objects.requireNonNull(failureHandler, "failureHandler is null");
            this.failureHandler = failureHandler;
            return this;
        }

        private void failureVerify(){
            if (!success && failureHandler != null) {
                try {
                    failureHandler.accept(error);
                } catch (SQLException e) {
                    log.error("Error during failure handler execution", e);
                }
            }
        }

    }

    @Slf4j
    public static class JdbcUpdateMv {
        private final DataSource dataSource;
        private String query;
        private SQLConsumer<Integer> successHandler;
        SQLConsumer<Throwable> failureHandler;
        private boolean success;
        private Throwable error;
        private int rowsInserted;

        JdbcUpdateMv(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        public JdbcUpdateMv query(String query) {
            if(!isValid(query)) throw new RedshiftException(query + " is not valid");
            this.query = query;
            return this;
        }

        private static boolean isValid(String sql) {
            if (sql == null || sql.trim().isEmpty()) return false;
            String trimmedSql = sql.trim();
            return SQL_UPDATE_MV_PATTERN.matcher(trimmedSql).matches();
        }

        public void execute() {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement ps = connection.prepareStatement(query)) {
                rowsInserted = ps.executeUpdate();
                success = true;
                successVerify();
            } catch (SQLException e) {
                failed(e);
                failureVerify();
                throw new RedshiftException(e);
            }
        }

        private void failed(Throwable t){
            this.error = t != null ? t : new NoStackTraceThrowable((String) null);
            this.success = false;
        }

        public JdbcUpdateMv onSuccess(SQLConsumer<Integer> successHandler) {
            Objects.requireNonNull(successHandler, "successHandler is null");
            this.successHandler = successHandler;
            return this;
        }

        private void successVerify() {
            if (success && successHandler != null) {
                try {
                    successHandler.accept(rowsInserted);
                } catch (SQLException e) {
                    log.error("Error during success handler execution", e);
                }
            }

        }

        public JdbcUpdateMv onFailure(SQLConsumer<Throwable> failureHandler) {
            Objects.requireNonNull(failureHandler, "failureHandler is null");
            this.failureHandler = failureHandler;
            return this;
        }

        private void failureVerify(){
            if (!success && failureHandler != null) {
                try {
                    failureHandler.accept(error);
                } catch (SQLException e) {
                    log.error("Error during failure handler execution", e);
                }
            }
        }
    }

    private static Map<String, Object> resultSetToMap(ResultSet rs) throws SQLException {
        Map<String, Object> map = new LinkedHashMap<>();
        for(int i = 0; i < rs.getMetaData().getColumnCount(); i++){
            String columnName = rs.getMetaData().getColumnName(i + 1);
            Object value = rs.getObject(columnName);
            map.put(columnName, value);
        }
        return map;
    }

    public static void validationDMLQuery(String query) {
        if(!isValidDML(query) || !isCompatibleDMLRedshift(query)) {
            throw new RedshiftException(query + " is not valid");
        }
    }

    private static boolean isValidDML(String sql) {
        if (sql == null || sql.trim().isEmpty()) return false;
        String trimmedSql = sql.trim();
        return SQL_DML_PATTERN.matcher(trimmedSql).matches();
    }

    private static boolean isCompatibleDMLRedshift(String sql) {
        for (String pattern : UNSUPPORTED_PATTERNS)
            if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(sql).find()) return false;
        return true;
    }

    private static void validateIsolationLevel(Integer isolationLevel) {
        if (isolationLevel != null && !ALLOWED_ISOLATION_LEVELS.contains(isolationLevel)) {
            throw new IllegalArgumentException("Isolation level " + isolationLevel + " is not a valid isolation level.");
        }
    }

    public static SQLConsumer<PreparedStatement> readParameters(List<Object> attributes) {
        return ps -> {
            for (int i = 0; i < attributes.size(); i++) {
                Object attribute = attributes.get(i);

                switch (attribute) {
                    case null -> ps.setNull(i + 1, Types.NULL);
                    case String s -> ps.setString(i + 1, s);
                    case Integer integer -> ps.setInt(i + 1, integer);
                    case Long l -> ps.setLong(i + 1, l);
                    case Double v -> ps.setDouble(i + 1, v);
                    case Float v -> ps.setFloat(i + 1, v);
                    case Boolean b -> ps.setBoolean(i + 1, b);
                    case Timestamp timestamp -> ps.setTimestamp(i + 1, timestamp);
                    case java.sql.Date date -> ps.setDate(i + 1, date);
                    case java.util.Date date -> ps.setTimestamp(i + 1, new Timestamp(date.getTime()));
                    case java.time.LocalDateTime localDateTime -> ps.setTimestamp(i + 1, java.sql.Timestamp.valueOf(localDateTime));
                    default ->
                            throw new IllegalArgumentException("Unsupported parameter type: " + attribute.getClass());
                }
            }
        };
    }

    @FunctionalInterface
    public interface SQLConsumer<T> {
        void accept(T t) throws SQLException;
    }

    @FunctionalInterface
    public interface SQLFunction<T, R> {
        R apply(T t) throws SQLException;
    }

    @FunctionalInterface
    public interface SQLSupplier<T> {
        T get() throws SQLException;
    }


}
