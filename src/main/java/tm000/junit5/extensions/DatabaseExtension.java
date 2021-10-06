package tm000.junit5.extensions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class DatabaseExtension implements BeforeTestExecutionCallback, AfterTestExecutionCallback {
    private static final String JDBC_SETTINGS = "testdb.properties";

    private boolean existsSql;

    private Connection getConnection() throws SQLException, IOException, ClassNotFoundException {
        Properties prop = new Properties();
        // read JDBC connection settings from testdb.properties
        prop.load(getClass().getClassLoader().getResourceAsStream(JDBC_SETTINGS));
        if (prop.getProperty("jdbc.driver.class") != null && !prop.getProperty("jdbc.driver.class").trim().isEmpty()) {
            Class.forName(prop.getProperty("jdbc.driver.class"));
        }
        Connection con = DriverManager.getConnection(prop.getProperty("jdbc.url"), prop.getProperty("jdbc.username"), prop.getProperty("jdbc.password"));
        con.setAutoCommit(false);
        return con;
    }

    @Override
    public void beforeTestExecution(ExtensionContext context) throws Exception {
        Class<?> clazz = context.getRequiredTestClass();
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getAnnotationsByType(BeforeAll.class).length > 0 || m.getAnnotationsByType(BeforeEach.class).length > 0) {
                ExecuteSql[] execSqls = m.getAnnotationsByType(ExecuteSql.class);
                execSql(execSqls);
            }
        }
        // perform @ExecuteSql annotations
        ExecuteSql[] execSqls = context.getTestMethod().get().getAnnotationsByType(ExecuteSql.class);
        execSql(execSqls);
    }

    @Override
    public void afterTestExecution(ExtensionContext context) throws Exception {
        if (existsSql) {
            // drop all tables
            try (var con = getConnection()) {
                switch (con.getClass().getName()) {
                case "org.h2.jdbc.JdbcConnection":
                    con.prepareStatement("DROP ALL OBJECTS").executeUpdate();
                    break;
                default:
                    System.err.println("not supported yet");
                }
            }
        }
    }

    private void execSql(ExecuteSql[] execSqls) throws ClassNotFoundException, SQLException, IOException {
        List<SqlInput> sqlInputs = Stream.of(execSqls).map(ann -> new SqlInput(ann.value(), ann.resource())).flatMap(Stream::of).collect(Collectors.toList());
        if (!sqlInputs.isEmpty()) {
            existsSql = true;
            try (var con = getConnection()) {
                for (int i = 0; i < sqlInputs.size(); i ++) {
                    try {
                        // execute SQL
                        sqlInputs.get(i).exec(con);
                    } catch (SqlInputException e) {
                        System.err.println(e.getLocalizedMessage());
                    }
                }
                con.commit();
            }
        }
    }

    static class SqlInput {
        String sql;
        String resource;
    
        SqlInput(String sql, String resource) {
            this.sql = sql;
            this.resource = resource;
        }

        void exec(Connection con) throws SqlInputException {
            if (!sql.isBlank()) {
                // execute single SQL
                try {
                    con.prepareStatement(sql).executeUpdate();
                } catch (SQLException e) {
                    throw new SqlInputException(e, sql);
                }
            } else {
                // read SQL file from resource
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(getClass().getClassLoader().getResourceAsStream(resource)))) {
                    try (Statement statement = con.createStatement()) {
                        boolean existsSql = false;
                        while (reader.ready()) {
                            String line = reader.readLine();
                            if (!line.startsWith("--")) {
                                statement.addBatch(line);
                                existsSql = true;
                            }
                        }
                        if (existsSql) {
                            statement.executeBatch();
                        }
                    } catch (SQLException e) {
                        e.printStackTrace();
                        throw new SqlInputException(e, resource);
                    }
                } catch (IOException | NullPointerException e) {
                    System.err.println("resource not found : " + resource);
                }
            }
        }
    }

    static class SqlInputException extends Exception {
        String sql;

        SqlInputException(SQLException e, String sql) {
            super(e);
            this.sql = sql;
        }

        @Override
        public String toString() {
            return sql;
        }
    }
}
