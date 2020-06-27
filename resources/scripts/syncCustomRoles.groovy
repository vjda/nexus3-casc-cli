import org.sonatype.nexus.security.authz.AuthorizationManager
import org.sonatype.nexus.security.internal.AuthorizationManagerImpl
import org.sonatype.nexus.security.role.Role
import org.sonatype.nexus.security.user.UserManager

class SyncCustomRoles extends ScriptBaseClass {

    private final AuthorizationManager authManager

    SyncCustomRoles(context) {
        super(context)
        this.authManager = context.security.getSecuritySystem().getAuthorizationManager(UserManager.DEFAULT_SOURCE)
    }

    @SuppressWarnings('GrMethodMayBeStatic')
    Boolean isRoleEqual(Role existingRole, Map<String, Object> roleDef) {
        Boolean equals = true

        equals &= existingRole.roleId == roleDef.id
        equals &= existingRole.name == roleDef.name
        equals &= existingRole.description == roleDef.description
        equals &= existingRole.source == ((roleDef.source as String)?.toUpperCase() ?: AuthorizationManagerImpl.SOURCE)
        equals &= existingRole.roles == roleDef.roles as Set<String>
        equals &= existingRole.privileges == roleDef.privileges as Set<String>

        return equals
    }

    @SuppressWarnings('GrMethodMayBeStatic')
    void setValues(Role currentRole, Map<String, Object> roleDef) {
        currentRole.roleId = roleDef.id as String
        currentRole.name = roleDef.name as String
        currentRole.description = roleDef.description as String ?: ''
        currentRole.source = (roleDef.source as String)?.toUpperCase() ?: AuthorizationManagerImpl.SOURCE
        currentRole.readOnly = false
        currentRole.roles = roleDef.roles as Set<String>
        currentRole.privileges = roleDef.privileges as Set<String>
    }

    void createRole(Map roleDef, Map action) {
        String id = roleDef.id

        try {
            def newRole = new Role()
            setValues(newRole, roleDef)
            authManager.addRole(newRole)
            log.info('Role {} created', id)
            scriptResult.addActionDetails(action, ScriptResult.Status.CREATED)

        } catch (Exception e) {
            log.error('Role {} could not be created: {}', id, e.toString())
            scriptResult.addActionDetails(action, e)
        }
    }

    void updateRole(Map roleDef, Map action) {
        String id = roleDef.id

        try {
            def existingRole = authManager.getRole(roleDef.id as String)

            if (isRoleEqual(existingRole, roleDef)) {
                log.info('No change role {}', id)
                scriptResult.addActionDetails(action, ScriptResult.Status.UNCHANGED)

            } else {
                setValues(existingRole, roleDef)
                authManager.updateRole(existingRole)
                log.info('Role {} updated', id)
                scriptResult.addActionDetails(action, ScriptResult.Status.UPDATED)
            }

        } catch (Exception e) {
            log.error('Role {} could not be updated: {}', id, e.toString())
            scriptResult.addActionDetails(action, e)
        }
    }

    void deleteRole(String id, Map action) {
        try {
            authManager.deleteRole(id)
            log.info('Role {} deleted', id)
            scriptResult.addActionDetails(action, ScriptResult.Status.DELETED)

        } catch (Exception e) {
            log.error('Role {} could not be deleted: {}', id, e.toString())
            scriptResult.addActionDetails(action, e)
        }
    }

    String execute() {
        Boolean deleteUnknownRoles = (config?.nexus?.deleteUnknownItems?.customRoles) ?: false
        List<Map<String, Object>> rolesDefs = (config?.customRoles as List<Map<String, Object>>) ?: []

        // Delete unknown existing roles
        if (deleteUnknownRoles) {
            // Get roles with readOnly == false
            authManager.listRoles().findAll { !it.readOnly }.each { existingRole ->
                String id = existingRole.roleId
                Boolean roleInDef = rolesDefs.any { it.id as String == id }

                if (roleInDef) {
                    log.info('Role {} found. Left untouched', id)
                } else {
                    def action = scriptResult.newAction(id: id, name: existingRole.name, source: existingRole.source)
                    deleteRole(id, action)
                }
            }
        }

        // Create or update roles
        rolesDefs.each { roleDef ->
            String id = roleDef.id
            String name = roleDef.name
            String source = (roleDef.source as String)?.toUpperCase() ?: AuthorizationManagerImpl.SOURCE

            def action = scriptResult.newAction(id: id, name: name, source: source)
            def existingRole = authManager.listRoles().find { role -> role.roleId == id }

            if (existingRole && existingRole.readOnly) {
                log.info('Role {} is read only. Left untouched', existingRole.name)
            } else if (existingRole) {
                updateRole(roleDef, action)
            } else {
                createRole(roleDef, action)
            }
        }

        return sendResponse()
    }
}

return new SyncCustomRoles(this).execute()
