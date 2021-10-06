# MyJUnit5Extensions
JUnit5 Custom Extension

There are 2 JUnit5 extensions in the project:

* `DatabaseExtension` 

```java
@ExtendWith(DatabaseExtension.class)
public class DatabaseExtensionTest {

    @Test
    @ExecuteSql(resource="schema.sql")
    @ExecuteSql("insert into EMPLOYEE values(1, 'firstname1', 'lastname1', 'test1@example.com', 'address1', 'city1')")
    @ExecuteSql("insert into EMPLOYEE values(2, 'firstname2', 'lastname2', 'test2@example.com', 'address2', 'city2')")
    void testMethod() {
    }
}
```
        
* `WebServerExtension`

```java
public class WebServerExtensionTest {
    @RegisterExtension
    static WebServerExtension server = WebServerExtension.builder()
        .enableSecurity(false)
        .build();
```
```java
public class WebServerExtensionEnableSecuriyTest {
    @RegisterExtension
    static WebServerExtension server = WebServerExtension.builder()
        .enableSecurity(true)
        .keyStoreFile(KEYSTORE_PATH)
        .trustStoreFile(KEYSTORE_PATH)
        .password(PASSWORD)
        .build();
```
