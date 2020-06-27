import org.sonatype.nexus.security.authz.AuthorizationManager
import org.sonatype.nexus.security.privilege.Privilege
import org.sonatype.nexus.security.user.UserManager

class SyncCustomPrivileges extends ScriptBaseClass {

    private final AuthorizationManager authManager

    SyncCustomPrivileges(context) {
        super(context)
        this.authManager = context.security.getSecuritySystem().getAuthorizationManager(UserManager.DEFAULT_SOURCE)
    }

    @SuppressWarnings('GrMethodMayBeStatic')
    Boolean isPrivilegeEqual(Privilege existingPrivilege, Map<String, Object> privilegeDef) {
        Boolean equals = true

        equals &= existingPrivilege.name == privilegeDef.name
        equals &= existingPrivilege.description == privilegeDef.description
        equals &= existingPrivilege.type == privilegeDef.type
        equals &= existingPrivilege.properties == [
                'format'         : privilegeDef.format,
                'contentSelector': privilegeDef.contentSelector,
                'repository'     : privilegeDef.repository,
                'pattern'        : privilegeDef.pattern,
                'domain'         : privilegeDef.domain,
                'name'           : privilegeDef.scriptName,
                'actions'        : privilegeDef.actions?.join(',')
        ] as Map<String, String>

        return equals
    }

    @SuppressWarnings('GrMethodMayBeStatic')
    void setValues(Privilege currentPrivilege, Map<String, Object> privilegeDef) {
        currentPrivilege.id = privilegeDef.name as String
        currentPrivilege.name = privilegeDef.name as String
        currentPrivilege.description = privilegeDef.description as String ?: ''
        currentPrivilege.type = (privilegeDef.type as String)
        currentPrivilege.readOnly = false
        currentPrivilege.properties = [
                'format'         : privilegeDef.format,
                'contentSelector': privilegeDef.contentSelector,
                'repository'     : privilegeDef.repository,
                'pattern'        : privilegeDef.pattern,
                'domain'         : privilegeDef.domain,
                'name'           : privilegeDef.scriptName,
                'actions'        : privilegeDef.actions?.join(',')
        ] as Map<String, String>
    }

    void createPrivilege(Map privilegeDef, Map action) {
        String name = privilegeDef.name

        try {
            def newPrivilege = new Privilege()
            setValues(newPrivilege, privilegeDef)
            authManager.addPrivilege(newPrivilege)
            log.info('Privilege {} created', name)
            scriptResult.addActionDetails(action, ScriptResult.Status.CREATED)

        } catch (Exception e) {
            log.error('Privilege {} could not be created: {}', name, e.toString())
            scriptResult.addActionDetails(action, e)
        }
    }

    void updatePrivilege(Map privilegeDef, Map action) {
        String name = privilegeDef.name

        try {
            def existingPrivilege = authManager.listPrivileges().find { it.name.toLowerCase() == name.toLowerCase() }

            if (isPrivilegeEqual(existingPrivilege, privilegeDef)) {
                log.info('No change privilege {}', name)
                scriptResult.addActionDetails(action, ScriptResult.Status.UNCHANGED)

            } else {
                setValues(existingPrivilege, privilegeDef)
                authManager.updatePrivilege(existingPrivilege)
                log.info('Update Privilege {}', name)
                scriptResult.addActionDetails(action, ScriptResult.Status.UPDATED)
            }

        } catch (Exception e) {
            log.error('Privilege {} could not be updated: {}', name, e.toString())
            scriptResult.addActionDetails(action, e)
        }
    }

    void deletePrivilege(String name, Map action) {
        try {
            def existingPrivilege = authManager.listPrivileges().find { it.name.toLowerCase() == name.toLowerCase() }
            authManager.deletePrivilege(existingPrivilege.id)
            log.info('Privilege {} deleted', name)
            scriptResult.addActionDetails(action, ScriptResult.Status.DELETED)

        } catch (Exception e) {
            log.error('Privilege {} could not be deleted: {}', name, e.toString())
            scriptResult.addActionDetails(action, e)
        }
    }

    String execute() {
        Boolean deleteUnknownPrivileges = (config?.nexus?.deleteUnknownItems?.customPrivileges) ?: false
        List<Map<String, Object>> privilegesDefs = (config?.customPrivileges as List<Map<String, Object>>) ?: []

        // Delete unknown existing privileges
        if (deleteUnknownPrivileges) {
            // Get privileges with readOnly == false
            authManager.listPrivileges().findAll { !it.readOnly }.each { existingPrivilege ->
                String name = existingPrivilege.name
                Boolean privilegeInDef = privilegesDefs.any {
                    (it.name as String).toLowerCase() == name.toLowerCase()
                }

                if (privilegeInDef) {
                    log.info('Privilege {} found. Left untouched', name)
                } else {
                    def action = scriptResult.newAction(name: name, type: existingPrivilege.type)
                    deletePrivilege(name, action)
                }
            }
        }

        // Create or update privileges
        privilegesDefs.each { privilegeDef ->
            String name = privilegeDef.name

            def action = scriptResult.newAction(name: name, type: privilegeDef.type as String)

            Privilege existingPrivilege = authManager.listPrivileges().find { privilege ->
                privilege.name.toLowerCase() == name.toLowerCase()
            }

            if (existingPrivilege && existingPrivilege.readOnly) {
                log.info('Privilege {} is read only. Left untouched', existingPrivilege.name)

            } else if (existingPrivilege) {
                updatePrivilege(privilegeDef, action)

            } else {
                createPrivilege(privilegeDef, action)
            }
        }

        return sendResponse()
    }
}

return new SyncCustomPrivileges(this).execute()
