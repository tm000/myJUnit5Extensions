# MyJUnit5Extensions
JUnit5 Custom Extension

There are 2 JUnit5 extensions in the project:

## `DatabaseExtension` 
テストデータを準備するためのExtensionです。
任意のSQLを実行してデータを準備します。

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

テストで使用するDBの接続情報はtest/resources/testdb.propertiesに指定します。
```
jdbc.driver.class=
jdbc.url=jdbc:h2:/tmp/h2db
jdbc.username=sa
jdbc.password=
```

@ExecuteSqlにはSQLを文字列かリソースファイルで指定します。
@BeforeAll、@BeforeEachを付与したメソッドにも設定可能です。
h2 databaseを使用した場合、1つのテストが終了する度にDROP ALL OBJECTSを実行してDBを初期化します。h2 database以外はテストしておらず同じような処理を自分で実装する必要があります。

## `WebServerExtension`
テスト用Webサーバーを立ち上げるExtensionです。
@WebServerResponse、@SimpleHttpResponseを指定して任意のレスポンスを返却できます。
enableSecurityにtrueを設定することでTLS1.2によるセキュリティを有効にできます。
受信したリクエストはgetRequestsメソッドで取得できます。


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
