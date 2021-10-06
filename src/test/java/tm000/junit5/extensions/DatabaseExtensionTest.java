package tm000.junit5.extensions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Properties;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(DatabaseExtension.class)
public class DatabaseExtensionTest {

    private Connection con;
    private Connection getConnection() throws SQLException, IOException {
        if (con == null) {
            Properties prop = new Properties();
            prop.load(getClass().getClassLoader().getResourceAsStream("testdb.properties"));
            con = DriverManager.getConnection(prop.getProperty("jdbc.url"), prop.getProperty("jdbc.username"), prop.getProperty("jdbc.password"));
            con.setAutoCommit(false);
        }
        return con;
    }

    @BeforeAll
    @ExecuteSql(resource="schema.sql")
    static void beforeall() {
    }

    @BeforeEach
    @ExecuteSql("insert into EMPLOYEE values(10, 'firstname10', 'lastname10', 'test10@example.com', 'address10', 'city10')")
    void beforeeach() {
    }
    
    @Test
    @ExecuteSql("insert into EMPLOYEE values(1, 'firstname1', 'lastname1', 'test1@example.com', 'address1', 'city1')")
    @ExecuteSql("insert into EMPLOYEE values(2, 'firstname2', 'lastname2', 'test2@example.com', 'address2', 'city2')")
    void test001(TestReporter testReporter) {
        try (var con = getConnection()) {
            var rs = con.prepareStatement("select * from EMPLOYEE").executeQuery();
            ResultSetMetaData rsmd = rs.getMetaData();
            for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                System.out.print(rsmd.getColumnLabel(i) + (i == rsmd.getColumnCount() ? System.lineSeparator() : ", "));
            }
            while (rs.next()) {
                for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                    System.out.print(rs.getString(i) + (i == rsmd.getColumnCount() ? System.lineSeparator() : ", "));
                }
            }
        } catch (SQLException | IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    @ExecuteSql("insert into TODO values(1, 'clean my room', '2021-09-05', false)")
    @ExecuteSql("insert into TODO values(2, 'wash my T shirts', '2021-09-08', true)")
    void test002(TestReporter testReporter) {
        try (var con = getConnection()) {
            var rs = con.prepareStatement("select * from TODO").executeQuery();
            rs.next();
            assertEquals("clean my room", rs.getString(2));
            assertEquals("20210905", new SimpleDateFormat("yyyyMMdd").format(rs.getDate(3)));
            assertFalse(rs.getBoolean(4));
            rs.next();
            assertEquals("wash my T shirts", rs.getString(2));
            assertEquals("20210908", new SimpleDateFormat("yyyyMMdd").format(rs.getDate(3)));
            assertTrue(rs.getBoolean(4));
        } catch (SQLException | IOException e) {
            e.printStackTrace();
        }
    }
}