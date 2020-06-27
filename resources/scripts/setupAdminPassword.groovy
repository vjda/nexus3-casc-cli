class SetupAdminPassword extends ScriptBaseClass {

    SetupAdminPassword(context) {
        super(context)
    }

    String execute() {
        String adminUser = config.nexus?.defaults?.adminUser ?: 'admin'
        String adminPassword = config.nexus?.adminPassword ?: 'admin123'

        def action = scriptResult.newAction(adminUser: adminUser)

        try {
            security.getSecuritySystem().changePassword(adminUser, adminPassword)
            log.info('Password changed for the user {}', adminUser)
            scriptResult.addActionDetails(action, ScriptResult.Status.UPDATED)

        } catch (Exception e) {
            log.error('Could not change password for user {}: {}', adminUser, e.toString())
            scriptResult.addActionDetails(action, e)
        }

        return sendResponse()
    }
}

return new SetupAdminPassword(this).execute()
