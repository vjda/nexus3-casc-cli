import org.apache.shiro.authc.UsernamePasswordToken
import org.apache.shiro.mgt.RealmSecurityManager
import org.sonatype.nexus.security.SecuritySystem
import org.sonatype.nexus.security.authz.AuthorizationManager
import org.sonatype.nexus.security.internal.AuthorizationManagerImpl
import org.sonatype.nexus.security.role.RoleIdentifier
import org.sonatype.nexus.security.user.User
import org.sonatype.nexus.security.user.UserManager
import org.sonatype.nexus.security.user.UserSearchCriteria
import org.sonatype.nexus.security.user.UserStatus

class SyncCustomLocalUsers extends ScriptBaseClass {

    private final AuthorizationManager authManager
    private final SecuritySystem securitySystem

    SyncCustomLocalUsers(context) {
        super(context)
        this.authManager = context.security.getSecuritySystem().getAuthorizationManager(UserManager.DEFAULT_SOURCE)
        this.securitySystem = context.security.securitySystem
    }

    @SuppressWarnings('GrMethodMayBeStatic')
    Boolean isUserEqual(User existingUser, Map<String, Object> userDef) {
        Boolean equals = true

        equals &= existingUser.userId == userDef.userId
        equals &= existingUser.firstName == userDef.firstName
        equals &= existingUser.lastName == userDef.lastName
        equals &= existingUser.emailAddress == userDef.emailAddress
        equals &= existingUser.source == AuthorizationManagerImpl.SOURCE.toLowerCase()
        equals &= existingUser.status == (userDef?.status?.toLowerCase() as UserStatus ?: UserStatus.disabled)
        equals &= !existingUser.readOnly

        // Check if roles are different
        Set<String> existingRoles = []
        existingUser.roles.each { RoleIdentifier roleIdentifier ->
            existingRoles.add(roleIdentifier.roleId)
        }

        equals &= existingRoles.sort() == (userDef.roles as Set<String>).sort()

        // Check if password has changed
        try {
            String userDefPassword = userDef.password as String
            UsernamePasswordToken authenticationToken = new UsernamePasswordToken(existingUser.userId, userDefPassword)
            container.lookup(RealmSecurityManager).authenticate(authenticationToken) == null
        } catch (ignored) {
            equals = false
        }

        return equals
    }

    @SuppressWarnings('GrMethodMayBeStatic')
    void setValues(User currentUser, Map<String, Object> userDef) {
        currentUser.setUserId(userDef.userId as String)
        currentUser.setFirstName(userDef.firstName as String)
        currentUser.setLastName(userDef.lastName as String)
        currentUser.setEmailAddress(userDef.emailAddress as String)
        currentUser.setSource(AuthorizationManagerImpl.SOURCE.toLowerCase())
        currentUser.setStatus(userDef?.status?.toLowerCase() as UserStatus ?: UserStatus.disabled)
        currentUser.setReadOnly(false)

        Set<RoleIdentifier> definedRoles = []
        (userDef.roles as Set<String>)?.each { String roleDef ->
            RoleIdentifier role = new RoleIdentifier(AuthorizationManagerImpl.SOURCE.toLowerCase(),
                    authManager.getRole(roleDef).roleId)
            definedRoles.add(role)
        }

        currentUser.setRoles(definedRoles)
    }

    void createUser(Map userDef, Map action) {
        String userId = userDef.userId

        try {
            def newUser = new User()
            setValues(newUser, userDef)
            securitySystem.addUser(newUser, userDef.password as String)
            log.info('User {} created', userId)
            scriptResult.addActionDetails(action, ScriptResult.Status.CREATED)

        } catch (Exception e) {
            log.error('User {} could not be created: {}', userId, e.toString())
            scriptResult.addActionDetails(action, e)
        }
    }

    void updateUser(Map userDef, Map action) {
        String id = userDef.id

        try {
            def existingUser = securitySystem.getUser(userDef.userId as String, AuthorizationManagerImpl.SOURCE)

            if (isUserEqual(existingUser, userDef)) {
                log.info('No change user {}', id)
                scriptResult.addActionDetails(action, ScriptResult.Status.UNCHANGED)

            } else {
                setValues(existingUser, userDef)
                securitySystem.updateUser(existingUser)
                if (userDef.password?.trim()) {
                    securitySystem.changePassword(existingUser.userId, userDef.password as String)
                }
                log.info('User {} updated', id)
                scriptResult.addActionDetails(action, ScriptResult.Status.UPDATED)
            }

        } catch (Exception e) {
            log.error('User {} could not be updated: {}', id, e.toString())
            scriptResult.addActionDetails(action, e)
        }
    }

    void deleteUser(String userId, Map action) {
        try {
            securitySystem.deleteUser(userId, AuthorizationManagerImpl.SOURCE)
            log.info('UserId {} deleted', userId)
            scriptResult.addActionDetails(action, ScriptResult.Status.DELETED)

        } catch (Exception e) {
            log.error('UserId {} could not be deleted: {}', userId, e.toString())
            scriptResult.addActionDetails(action, e)
        }
    }

    String execute() {
        Boolean deleteUnknownLocalUsers = (config?.nexus?.deleteUnknownItems?.customLocalUsers) ?: false
        List<Map<String, Object>> usersDefs = (config?.customUsers?.local as List<Map<String, Object>>) ?: []

        // Delete unknown existing local users
        if (deleteUnknownLocalUsers) {
            Set<String> existingUserIds = securitySystem.
                    getUserManager(AuthorizationManagerImpl.SOURCE.toLowerCase()).listUserIds()

            Set<String> knownUserIds = usersDefs*.userId as Set<String>

            // Get unknown users. User admin and anonymous cannot be deleted, they will be ignored
            existingUserIds.removeAll(knownUserIds)
            existingUserIds.removeAll(['admin', 'anonymous'])
            Set<String> unknownUsers = existingUserIds

            // Get users with readOnly == false and local source
            unknownUsers.findAll { userId ->
                def existingUser = securitySystem.getUser(userId)
                !(existingUser.readOnly) && existingUser.source == AuthorizationManagerImpl.SOURCE.toLowerCase()
            }.each { userId ->
                def action = scriptResult.newAction(userId: userId, source: AuthorizationManagerImpl.SOURCE.toLowerCase())
                deleteUser(userId, action)
            }
        }

        // Create or update users
        usersDefs.each { userDef ->
            String userId = userDef.userId
            String source = (userDef.source as String)?.toLowerCase() ?: AuthorizationManagerImpl.SOURCE.toLowerCase()

            def action = scriptResult.newAction(userId: userId, source: source)

            UserSearchCriteria userSearchCriteria = new UserSearchCriteria(userId)
            userSearchCriteria.source = source

            def existingUser = securitySystem.searchUsers(userSearchCriteria)

            if (existingUser && existingUser.first().readOnly) {
                log.info('User {} is read only. Left untouched', existingUser.first().userId)
                scriptResult.addActionDetails(action, ScriptResult.Status.UNCHANGED)
            } else if (existingUser && existingUser?.first()) {
                updateUser(userDef, action)
            } else {
                createUser(userDef, action)
            }
        }

        return sendResponse()
    }
}

return new SyncCustomLocalUsers(this).execute()
