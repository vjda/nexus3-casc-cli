import org.sonatype.nexus.security.realm.RealmManager

class SetupRealms extends ScriptBaseClass {
    private final RealmManager realmManager

    SetupRealms(context) {
        super(context)
        this.realmManager = context.container.lookup(RealmManager)
    }

    String execute() {
        def realmsDef = (config.realms as List<Map<String, Serializable>>) ?: []

        realmsDef.each { realmDef ->
            String name = realmDef.name
            Boolean enabled = Boolean.parseBoolean(realmDef.enabled as String)

            def action = scriptResult.newAction(name: name, enabled: enabled)

            try {
                def existingRealm = realmManager.availableRealms.find { it.name.toLowerCase() == name.toLowerCase() }

                if (name in ['Local Authenticating Realm', 'Local Authorizing Realm']) {
                    // Avoid changing these realms because if one of them is disabled, no user could authenticate
                    log.warn('Changing the realm {} is not allowed', name)
                    scriptResult.addActionDetails(action, ScriptResult.Status.FORBIDDEN)

                } else if (existingRealm) {
                    if (realmManager.isRealmEnabled(existingRealm.id) == enabled) {
                        log.info('Realm {} untouched', name)
                        scriptResult.addActionDetails(action, ScriptResult.Status.UNCHANGED)

                    } else {
                        realmManager.enableRealm(existingRealm.id, enabled)

                        log.info('Realm {} {}', name, (enabled) ? 'enabled' : 'disabled')
                        scriptResult.addActionDetails(action, ScriptResult.Status.UPDATED)
                    }

                } else {
                    throw new RuntimeException('Realm not available')
                }

            } catch (Exception e) {
                log.error('Could not set realm {}: {}', name, e.toString())
                scriptResult.addActionDetails(action, e)
            }
        }

        return sendResponse()
    }
}

return new SetupRealms(this).execute()
