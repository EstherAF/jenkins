package jenkins.model;

import java.net.HttpURLConnection;

import com.gargoylesoftware.htmlunit.ElementNotFoundException;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.jenkinsci.Symbol;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.recipes.WithPlugin;
import org.kohsuke.stapler.StaplerRequest;

import net.sf.json.JSONObject;

import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.PluginWrapper;
import hudson.cli.CLICommandInvoker;
import hudson.cli.DisablePluginCommand;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.labels.LabelAtom;
import hudson.security.Permission;
import hudson.tasks.Shell;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static hudson.cli.CLICommandInvoker.Matcher.failedWith;

/**
 * As Jenkins.MANAGE can be enabled on startup with jenkins.security.ManagePermission property, we need a test class
 * with this property activated.
 */
public class JenkinsManagePermissionTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @BeforeClass //TODO: remove once Jenkins.MANAGE is no longer an experimental feature
    public static void enableManagePermission() {
        System.setProperty("jenkins.security.ManagePermission", "true");
    }

    @AfterClass //TODO: remove once Jenkins.MANAGE is no longer an experimental feature
    public static void disableManagePermission() {
        System.clearProperty("jenkins.security.ManagePermission");
    }


    @Test
    public void testFeatureHasBeenEnabled(){
        assertTrue("Manage permission is enabled", Jenkins.MANAGE.getEnabled());
    }

    // -------------------------
    // Moved from hudson/model/labels/LabelAtomPropertyTest.java
    /**
     * Tests the configuration persistence between disk, memory, and UI.
     */
    @Issue("JENKINS-60266")
    @Test
    public void configAllowedWithManagePermission() throws Exception {
        final String MANAGER = "manager";
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                                                   .grant(Jenkins.READ, Jenkins.MANAGE).everywhere().to(MANAGER));

        LabelAtom label = j.jenkins.getLabelAtom("foo");

        // it should survive the configuration roundtrip
        HtmlForm labelConfigForm = j.createWebClient().login(MANAGER).goTo("label/foo/configure").getFormByName("config");
        labelConfigForm.getTextAreaByName("description").setText("example description");
        j.submit(labelConfigForm);

        assertEquals("example description",label.getDescription());
    }

    /**
     * Tests the configuration persistence between disk, memory, and UI.
     */
    @Issue("JENKINS-60266")
    @Test
    public void configForbiddenWithoutManageOrAdminPermissions() throws Exception {
        final String UNAUTHORIZED = "reader";
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                                                   .grant(Jenkins.READ).everywhere().to(UNAUTHORIZED));

        j.jenkins.getLabelAtom("foo");

        // Unauthorized user can't be able to access the configuration form
        JenkinsRule.WebClient webClient = j.createWebClient().login(UNAUTHORIZED).withThrowExceptionOnFailingStatusCode(false);
        webClient.assertFails("label/foo/configure", HttpURLConnection.HTTP_FORBIDDEN);

        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                                                   .grant(Jenkins.ADMINISTER).everywhere().to(UNAUTHORIZED));

        // And can't submit the form neither
        HtmlForm labelConfigForm = webClient.goTo("label/foo/configure").getFormByName("config");
        labelConfigForm.getTextAreaByName("description").setText("example description");

        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                                                   .grant(Jenkins.READ).everywhere().to(UNAUTHORIZED));
        HtmlPage submitted = j.submit(labelConfigForm);
        assertEquals(HttpURLConnection.HTTP_FORBIDDEN, submitted.getWebResponse().getStatusCode());
    }
    // End of Moved from hudson/model/labels/LabelAtomPropertyTest.java
    //-------


    // -----------------------------
    //Moved from DisablePluginCommandTest
    @Issue("JENKINS-60266")
    @Test
    @WithPlugin({ "depender-0.0.2.hpi", "dependee-0.0.2.hpi"})
    public void managerCanNotDisablePlugin() {

        //GIVEN a user with Jenkins.MANAGE permission
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                                                   .grant(Jenkins.MANAGE).everywhere().to("manager")
        );

        //WHEN trying to disable a plugin
        assertThat(disablePluginsCLiCommandAs("manager", "dependee"), failedWith(6));
        //THEN it's refused and the plugin is not disabled.
        assertPluginEnabled("dependee");
    }

    /**
     * Disable a list of plugins using the CLI command.
     * @param user Username
     * @param args Arguments to pass to the command.
     * @return Result of the command. 0 if succeed, 16 if some plugin couldn't be disabled due to dependent plugins.
     */
    private CLICommandInvoker.Result disablePluginsCLiCommandAs(String user, String... args) {
        return new CLICommandInvoker(j, new DisablePluginCommand()).asUser(user).invokeWithArgs(args);
    }


    private void assertPluginEnabled(String name) {
        PluginWrapper plugin = j.getPluginManager().getPlugin(name);
        assertThat(plugin, is(notNullValue()));
        assertTrue(plugin.isEnabled());
    }

    // End of Moved from DisablePluginCommandTest
    //-------

    // -----------------------------
    //Moved from ComputerTest
    @Issue("JENKINS-60266")
    @Test
    public void dumpExportTableForbiddenWithoutAdminPermission() throws Exception {
        final String READER = "reader";
        final String MANAGER = "manager";
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                                                   .grant(Jenkins.READ).everywhere().to(READER)
                                                   .grant(Jenkins.MANAGE).everywhere().to(MANAGER)
                                                   .grant(Jenkins.READ).everywhere().to(MANAGER)
        );
        j.createWebClient().login(READER).assertFails("computer/(master)/dumpExportTable", HttpURLConnection.HTTP_FORBIDDEN);
        j.createWebClient().login(MANAGER).assertFails("computer/(master)/dumpExportTable", HttpURLConnection.HTTP_FORBIDDEN);
    }

    // End of Moved from ComputerTest
    //-------

    // -----------------------------
    //Moved from HusdonTest
    @Issue("JENKINS-60266")
    @Test
    public void someGlobalConfigurationIsNotDisplayedWithManagePermission() throws Exception {
        //GIVEN a user with Jenkins.MANAGE permission
        setPermissionToEveryone(Jenkins.MANAGE);

        //WHEN the user goes to /configure page
        HtmlForm form = j.createWebClient().goTo("configure").getFormByName("config");
        String formText = form.asText();
        //THEN items restricted to ADMINISTER only should not be displayed.
        assertThat("Shouldn't be able to configure # of executors", formText, not(containsString("executors")));
        assertThat("Shouldn't be able to configure Global properties", formText,
                   not(containsString("Global properties")));
        assertThat("Shouldn't be able to configure Administrative monitors", formText, not(containsString(
                "Administrative "
                + "monitors")));
        assertThat("Shouldn't be able to configure Shell", formText, not(containsString("Shell")));
    }

    @Issue("JENKINS-60266")
    @Test
    public void someGlobalConfigCanNotBeModifiedWithManagePermission() throws Exception {
        //GIVEN the Global Configuration Form, with some changes unsaved
        int currentNumberExecutors = j.getInstance().getNumExecutors();
        String shell = getShell();
        HtmlForm form = j.createWebClient().goTo("configure").getFormByName("config");
        form.getInputByName("_.numExecutors").setValueAttribute(""+(currentNumberExecutors+1));
        form.getInputByName("_.shell").setValueAttribute("/fakeShell");

        // WHEN a user with Jenkins.MANAGE permission only try to save those changes
        setPermissionToEveryone(Jenkins.MANAGE);
        j.submit(form);
        // THEN the changes on fields forbidden to a Jenkins.MANAGE permission are not saved
        assertEquals("shouldn't be allowed to change the number of executors", currentNumberExecutors, j.getInstance().getNumExecutors());
        assertEquals("shouldn't be allowed to change the shell executable", shell, getShell());
    }

    @Issue("JENKINS-60266")
    @Test
    public void globalConfigAllowedWithManagePermission() throws Exception {
        setPermissionToEveryone(Jenkins.MANAGE);

        HtmlForm form = j.createWebClient().goTo("configure").getFormByName("config");
        HtmlPage updated = j.submit(form);
        assertThat("User with Jenkins.MANAGE permission should be able to update global configuration",
                     updated.getWebResponse(), hasResponseCode(HttpURLConnection.HTTP_OK));
    }

    private String getShell() {
        Descriptor descriptorByName = j.getInstance().getDescriptorByName("hudson.tasks.Shell");
        return ((Shell.DescriptorImpl) descriptorByName).getShell();
    }

    private static Matcher<WebResponse> hasResponseCode(final int httpStatus) {
        return new BaseMatcher<WebResponse>() {
            @Override
            public boolean matches(final Object item) {
                final WebResponse response = (WebResponse) item;
                return (response.getStatusCode() == httpStatus);
            }

            @Override
            public void describeTo(final Description description) {
                description.appendText("Jenkins to return  ").appendValue(httpStatus);
            }

            @Override
            public void describeMismatch(Object item, Description description) {
                WebResponse response = (WebResponse) item;
                description.appendText("Response code was: ");
                description.appendValue(response.getStatusCode());
                description.appendText(" with error message: ");
                description.appendText(response.getStatusMessage());
                description.appendText("\n with headers ").appendValueList("", "\n    ", "", response.getResponseHeaders());
                description.appendText("\nPage content: ").appendValue(response.getContentAsString());
            }
        };
    }

    // End of Moved from HusdonTest
    //-------

    @Issue("JENKINS-60266")
    @Test
    public void testGlobalConfigDescriptor_withManagePermission_NotDisplayedByDefault() throws Exception {
        // WHEN a user with Jenkins.MANAGE permission render the config form
        setPermissionToEveryone(Jenkins.MANAGE);
        HtmlForm form = j.createWebClient().goTo("configure").getFormByName("config");

        //THEN the field is NOT displayed
        //This case is authorized only to Administer by default
        try {
            HtmlInput input = form.getInputByName("_.globalExampleInput");
            assertNull("_.globalExampleInput input should not be displayed with Manage permission", input);
        } catch (ElementNotFoundException ex) {
            //good, not found is what we expect
        }
    }

    @Issue("JENKINS-60266")
    @Test
    public void testConfigDescriptor_withManagePermission_DisplayedByDefault() throws Exception{
        // WHEN a user with Jenkins.MANAGE permission render the config form
        setPermissionToEveryone(Jenkins.MANAGE);
        HtmlForm form = j.createWebClient().goTo("configure").getFormByName("config");

        //THEN the field is displayed
        //This case is not restricted against user with Manage by default
        HtmlInput input = form.getInputByName("_.exampleInput");
        assertNotNull("_.exampleInput input is displayed with Manage permission", input);
    }

    @Issue("JENKINS-60266")
    @Test
    public void testGlobalConfigDescriptor_withManagePermission_CanNotBeModifiedByDefault() throws Exception {
        // WHEN a user with Jenkins.MANAGE permission try to save the changes

        //the form is rendered with Administer permission, to get a form with all the fields
        setPermissionToEveryone(Jenkins.ADMINISTER);
        assertNull("default input value", GlobalConfigurationTestDescriptor.getInputValue());
        HtmlForm form = j.createWebClient().goTo("configure").getFormByName("config");
        setInputValue(form, "_.globalExampleInput", "blabla");

        //removes the permission: this simulates a user editting the form or generating a custom POST without permission
        setPermissionToEveryone(Jenkins.MANAGE);
        HtmlPage page = j.submit(form);
        //The submission is accepted
        j.assertGoodStatus(page);

        //THEN the changes are NOT saved.
        //This case is authorized only to Administer by default
        assertNotEquals("value has been setted", GlobalConfigurationTestDescriptor.getInputValue(), "blabla");
    }

    @Issue("JENKINS-60266")
    @Test
    public void testConfigDescriptor_withManagePermission_CanBeModifiedByDefault() throws Exception{
        // WHEN a user with Jenkins.MANAGE permission only try to save the changes

        //the form is rendered with Administer permission, to get a form with all the fields
        setPermissionToEveryone(Jenkins.ADMINISTER);
        assertNull("default input value", TestDescriptor.getInputValue());
        HtmlForm form = j.createWebClient().goTo("configure").getFormByName("config");
        setInputValue(form, "_.exampleInput", "blebleble");

        //removes the permission: this simulates a user editting the form or generating a custom POST without permission
        setPermissionToEveryone(Jenkins.MANAGE);
        HtmlPage page = j.submit(form);
        //The submission is accepted
        j.assertGoodStatus(page);

        //THEN the changes are saved.
        //This case is not restricted against user with Manage by default
        assertEquals("Value has been setted", TestDescriptor.getInputValue(), "blebleble");
    }

    @TestExtension({"testGlobalConfigDescriptor_withManagePermission_CanNotBeModifiedByDefault",
                    "testGlobalConfigDescriptor_withManagePermission_NotDisplayedByDefault"})
    @Extension(ordinal=300) @Symbol("globalConfigurationTestDescriptor")
    public static final class GlobalConfigurationTestDescriptor extends GlobalConfiguration {
        private String exampleInput;
        public String getExampleInput() { return exampleInput; }
        public void setExampleInput(String exampleInput) { this.exampleInput = exampleInput; }
        static String getInputValue(){
            GlobalConfigurationTestDescriptor inputNonGlobalDescriptor = Jenkins.getInstance().getDescriptorByType(
                    GlobalConfigurationTestDescriptor.class);
            return inputNonGlobalDescriptor.getExampleInput();
        }
    }

    @TestExtension({"testConfigDescriptor_withManagePermission_CanBeModifiedByDefault",
                    "testConfigDescriptor_withManagePermission_DisplayedByDefault"})
    @Extension(ordinal=301) @Symbol("testDescriptor")
    public static class TestDescriptor extends Descriptor<TestDescriptor>
            implements ExtensionPoint, Describable<TestDescriptor>  {

        private String exampleInput;
        public TestDescriptor() {
            super(self());
        }
        public String getExampleInput() { return exampleInput; }
        public final Descriptor<TestDescriptor> getDescriptor() {
            return this;
        }
        @Override
        public boolean configure(StaplerRequest req, JSONObject json) {
            this.exampleInput = json.getString("exampleInput");
            return true;
        }

        static String getInputValue(){
            TestDescriptor inputNonGlobalDescriptor =
                    Jenkins.getInstanceOrNull().getDescriptorByType(TestDescriptor.class);
            return inputNonGlobalDescriptor.exampleInput;
        }
    }

    private void setInputValue(HtmlForm form, String inputName, String value)  {
        HtmlInput input = form.getInputByName(inputName);
        assertNotNull(inputName, input);
        input.setValueAttribute(value);
    }

    private void setPermissionToEveryone(Permission administer) {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(administer, Jenkins.READ).everywhere().toEveryone());
    }

}
