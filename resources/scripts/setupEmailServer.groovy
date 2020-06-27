import org.sonatype.nexus.email.EmailManager

class SetupEmailServer extends ScriptBaseClass {

    private final EmailManager emailManager

    SetupEmailServer(context) {
        super(context)
        this.emailManager = context.container.lookup(EmailManager)
    }

    String execute() {
        Map<String, Object> emailServerDef = (config?.smtpConnection as Map<String, Object>) ?: [:]

        def action = scriptResult.newAction(
                host: emailServerDef.host as String,
                port: emailServerDef.port as Integer,
                enabled: Boolean.parseBoolean(emailServerDef.enabled as String))

        try {
            def emailServerConf = emailManager.getConfiguration()

            def isEnabled = Boolean.parseBoolean(emailServerDef.enabled as String)

            if (isEnabled) {
                emailServerConf.with {
                    enabled = isEnabled
                    host = emailServerDef.host
                    port = emailServerDef.port as Integer
                    username = emailServerDef.username
                    password = emailServerDef.password
                    fromAddress = emailServerDef.fromAddress
                    subjectPrefix = emailServerDef.subjectPrefix
                    startTlsEnabled = Boolean.parseBoolean(emailServerDef.startTlsEnabled as String)
                    startTlsRequired = Boolean.parseBoolean(emailServerDef.startTlsRequired as String)
                    sslOnConnectEnabled = Boolean.parseBoolean(emailServerDef.sslOnConnectEnabled as String)
                    sslCheckServerIdentityEnabled = Boolean.parseBoolean(emailServerDef.sslServerIdentityCheckEnabled as String)
                    nexusTrustStoreEnabled = Boolean.parseBoolean(emailServerDef.nexusTrustStoreEnabled as String)
                }
            } else {
                emailServerConf.with {
                    enabled = isEnabled
                }
            }

            emailManager.setConfiguration(emailServerConf)

            log.info('Email server connection configured to {}', emailServerDef.host)
            scriptResult.addActionDetails(action, ScriptResult.Status.UPDATED)

        } catch (Exception e) {
            log.error('Could not configure email server connection for {}: {}', emailServerDef.host, e.toString())
            scriptResult.addActionDetails(action, e)
        }

        return sendResponse()
    }
}

return new SetupEmailServer(this).execute()
