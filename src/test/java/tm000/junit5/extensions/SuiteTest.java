package tm000.junit5.extensions;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.runner.RunWith;

@RunWith(org.junit.platform.runner.JUnitPlatform.class)
@SelectClasses({WebServerExtensionTest.class, WebServerExtensionEnableSecuriyTest.class})
public class SuiteTest {
}