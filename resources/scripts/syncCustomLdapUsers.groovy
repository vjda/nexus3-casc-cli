import org.sonatype.nexus.security.SecuritySystem
import org.sonatype.nexus.security.role.RoleIdentifier
import org.sonatype.nexus.security.user.User
import org.sonatype.nexus.security.user.UserManager
import org.sonatype.nexus.security.user.UserSearchCriteria

class SyncCustomLdapUsers extends ScriptBaseClass {

    private final SecuritySystem securitySystem
    private static final String LDAP_SOURCE = 'LDAP'

    SyncCustomLdapUsers(context) {
        super(context)
        this.securitySystem = security.securitySystem
    }

    @SuppressWarnings('GrMethodMayBeStatic')
    private Boolean hasInternalRoles(User user) {
        return user.source == LDAP_SOURCE && user.roles.any { it.source == UserManager.DEFAULT_SOURCE }
    }

    private void updateRoles(Map userDef) {
        String userId = userDef.username as String
        Set<String> roles = userDef.roles as Set<String>

        def action = scriptResult.newAction(userId: userId, roles: roles)

        try {
            User existingUser = securitySystem.getUser(userId, LDAP_SOURCE)
            Set<RoleIdentifier> targetRoles = roles.collect { new RoleIdentifier(UserManager.DEFAULT_SOURCE, it) }
            securitySystem.setUsersRoles(existingUser.getUserId(), LDAP_SOURCE, targetRoles)
            log.info('Roles [user {}] updated: {}', userId, roles?.join(','))
            scriptResult.addActionDetails(action, ScriptResult.Status.UPDATED)

        } catch (Exception e) {
            log.error('Roles [user {}] could not be updated: {}', userId, e.toString())
            scriptResult.addActionDetails(action, e)
        }
    }

    private void unsetRoles(User user) {
        String userId = user.userId
        Set<RoleIdentifier> internalRoles = user.roles.findAll { it.source == UserManager.DEFAULT_SOURCE }
        String roles = internalRoles*.roleId

        def action = scriptResult.newAction(userId: userId, roles: roles)

        try {
            securitySystem.setUsersRoles(userId, LDAP_SOURCE, Collections.emptySet())

            log.info('Roles [user {}] unset: {}', userId, roles?.join(','))
            scriptResult.addActionDetails(action, ScriptResult.Status.DELETED)

        } catch (Exception e) {
            log.error('Roles [user {}] could not be unset: {}', userId, e.toString())
            scriptResult.addActionDetails(action, e)
        }
    }

    String execute() {
        List<Map<String, Object>> usersDefs = (config?.customUsers?.ldap as List<Map<String, Object>>) ?: []

        // Update roles for users in list
        usersDefs.each { updateRoles(it) }

        // Remove roles for LDAP users which have internal roles set and are not in the list
        def usernames = usersDefs*.username as Set<String>

        UserSearchCriteria userSearchCriteria = new UserSearchCriteria()
        def ldapUsersWithRoles = securitySystem.searchUsers(userSearchCriteria)
                .findAll { !usernames.contains(it.userId) && hasInternalRoles(it) }

        ldapUsersWithRoles.each { unsetRoles(it) }

        return sendResponse()
    }
}

return new SyncCustomLdapUsers(this).execute()
