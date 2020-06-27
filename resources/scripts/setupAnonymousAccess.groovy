class SetupAnonymousAccess extends ScriptBaseClass {

    SetupAnonymousAccess(context) {
        super(context)
    }

    String execute() {
        Boolean enabled = Boolean.parseBoolean(config?.nexus?.anonymousAccessEnabled as String)

        def action = scriptResult.newAction(enabled: enabled)

        if (security.getAnonymousManager().getConfiguration().isEnabled() == enabled) {
            log.info('Anonymous access configuration has not changed')
            scriptResult.addActionDetails(action, ScriptResult.Status.UNCHANGED)

        } else {
            security.setAnonymousAccess(enabled)
            log.info('Anonymous access {}', (enabled) ? 'enabled' : 'disabled')
            scriptResult.addActionDetails(action, ScriptResult.Status.UPDATED)
        }

        return sendResponse()
    }
}

return new SetupAnonymousAccess(this).execute()
