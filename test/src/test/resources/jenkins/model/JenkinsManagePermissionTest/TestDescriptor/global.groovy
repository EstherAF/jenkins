package jenkins.model.JenkinsManagePermissionTest.TestDescriptorNotGlobalConfiguration


def f=namespace(lib.FormTagLib)

f.entry(title:_("Non Global Example input"), field:"exampleInput") {
        f.textbox()
}
