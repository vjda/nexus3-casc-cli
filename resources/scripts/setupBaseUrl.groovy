class SetupBaseUrl extends ScriptBaseClass {

    SetupBaseUrl(context) {
        super(context)
    }

    String execute() {
        String baseUrl = (config.nexus?.baseUrl) ?: 'http://localhost:8081'

        def action = scriptResult.newAction(baseUrl: baseUrl)

        try {
            core.baseUrl(baseUrl)
            log.info('Base url changed to {}', baseUrl)
            scriptResult.addActionDetails(action, ScriptResult.Status.UPDATED)

        } catch (Exception e) {
            log.error('Could not change base url to {}: {}', baseUrl, e.toString())
            scriptResult.addActionDetails(action, e)
        }

        return sendResponse()
    }
}

return new SetupBaseUrl(this).execute()
