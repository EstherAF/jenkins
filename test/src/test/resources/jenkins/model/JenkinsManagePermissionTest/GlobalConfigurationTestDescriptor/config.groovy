package jenkins.model.JenkinsManagePermissionTest.TestDescriptor

import hudson.Functions
import jenkins.model.Jenkins

def f=namespace(lib.FormTagLib)

f.entry(title:_("Example input"), field:"globalExampleInput") {
        f.textbox()
}
