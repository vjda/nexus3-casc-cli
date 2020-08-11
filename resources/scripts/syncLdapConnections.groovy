import org.sonatype.nexus.ldap.persist.LdapConfigurationManager
import org.sonatype.nexus.ldap.persist.entity.Connection
import org.sonatype.nexus.ldap.persist.entity.LdapConfiguration
import org.sonatype.nexus.ldap.persist.entity.Mapping

class SyncLdapConnections extends ScriptBaseClass {

    private final LdapConfigurationManager ldapConfigurationManager

    SyncLdapConnections(context) {
        super(context)
        this.ldapConfigurationManager = container.lookup(LdapConfigurationManager)
    }

    @SuppressWarnings('GrMethodMayBeStatic')
    private Connection configConnection(Map<String, String> ldapConfDef, Connection connection = new Connection()) {
        Connection.Host connHost = new Connection.Host(
                Connection.Protocol.valueOf((ldapConfDef.protocol).toLowerCase()),
                ldapConfDef.host,
                Integer.valueOf(ldapConfDef.port))

        connection.setHost(connHost)

        String authScheme = (ldapConfDef.authScheme).toLowerCase()
        connection.setAuthScheme(authScheme)

        if (authScheme == 'simple') {
            connection.setSystemUsername(ldapConfDef.authUsername)
            connection.setSystemPassword(ldapConfDef.authPassword)
        } else if (authScheme in ['digest-md5', 'cram-md5']) {
            connection.setSystemUsername(ldapConfDef.authUsername)
            connection.setSystemPassword(ldapConfDef.authPassword)
            connection.setSaslRealm(ldapConfDef.authRealm)

        } else if ((ldapConfDef.authScheme).toLowerCase() == 'none') {
            connection.setAuthScheme('none')
        }

        connection.setSearchBase(ldapConfDef.searchBase)
        connection.setConnectionTimeout(Integer.parseInt(ldapConfDef.get('connectionTimeoutSeconds', '30') as String))
        connection.setConnectionRetryDelay(Integer.parseInt(ldapConfDef.get('connectionRetryDelaySeconds', '300') as String))
        connection.setMaxIncidentsCount(Integer.parseInt(ldapConfDef.get('maxIncidentsCount', '3') as String))
        connection.setUseTrustStore(Boolean.valueOf(ldapConfDef.useTrustStore))

        return connection
    }

    @SuppressWarnings('GrMethodMayBeStatic')
    private Mapping configMapping(Map<String, String> ldapConfDef, Mapping mapping = new Mapping()) {
        // User attributes
        mapping.setUserBaseDn(ldapConfDef.userBaseDn)
        mapping.setLdapFilter(ldapConfDef.userLdapFilter)
        mapping.setUserObjectClass(ldapConfDef.userObjectClass)
        mapping.setUserIdAttribute(ldapConfDef.userIdAttribute)
        mapping.setUserRealNameAttribute(ldapConfDef.userRealNameAttribute)
        mapping.setEmailAddressAttribute(ldapConfDef.userEmailAddressAttribute)
        mapping.setUserPasswordAttribute(ldapConfDef.userPasswordAttribute)

        // Group attributes
        mapping.setLdapGroupsAsRoles(Boolean.parseBoolean(ldapConfDef.ldapGroupsAsRoles as String))

        if (ldapConfDef.groupType?.toLowerCase() == 'dynamic') {
            mapping.setUserMemberOfAttribute(ldapConfDef.userMemberOfAttribute)
        } else if (ldapConfDef.groupType?.toLowerCase() == 'static') {
            mapping.setGroupBaseDn(ldapConfDef.groupBaseDn)
            mapping.setGroupObjectClass(ldapConfDef.groupObjectClass)
            mapping.setGroupIdAttribute(ldapConfDef.groupIdAttribute)
            mapping.setGroupMemberAttribute(ldapConfDef.groupMemberAttribute)
            mapping.setGroupMemberFormat(ldapConfDef.groupMemberFormat)
        }

        // Other attributes
        mapping.setUserSubtree(Boolean.parseBoolean(ldapConfDef.userSubtree as String))
        mapping.setGroupSubtree(Boolean.parseBoolean(ldapConfDef.groupSubtree as String))

        return mapping
    }

    private void createConnection(Map ldapConfDef, Map action) {
        String name = ldapConfDef.name
        String id = name

        try {
            Connection connection = configConnection(ldapConfDef as Map<String, String>)
            Mapping mapping = configMapping(ldapConfDef as Map<String, String>)

            // Create a new configuration with the given name
            //LdapConfiguration ldapConfig = new LdapConfiguration(id, name, connection, mapping)
            LdapConfiguration ldapConfig = ldapConfigurationManager.newConfiguration()
            ldapConfig.setName(name)
            ldapConfig.setConnection(connection)
            ldapConfig.setMapping(mapping)
            ldapConfigurationManager.addLdapServerConfiguration(ldapConfig)

            log.info('LDAP connection {} created', name)
            scriptResult.addActionDetails(action, ScriptResult.Status.CREATED)

        } catch (Exception e) {
            log.error('LDAP connection {} could not be created: {}', name, e.toString())
            scriptResult.addActionDetails(action, e)
        }
    }

    private void updateConnection(LdapConfiguration configuration, Map ldapConfDef, Map action) {
        String name = ldapConfDef.name

        try {
            Connection connection = configConnection(ldapConfDef, configuration.getConnection())
            Mapping mapping = configMapping(ldapConfDef, configuration.getMapping())

            // Update the configuration with its connection and mapping values
            configuration.setConnection(connection)
            configuration.setMapping(mapping)
            ldapConfigurationManager.updateLdapServerConfiguration(configuration)

            log.info('LDAP connection {} updated', name)
            scriptResult.addActionDetails(action, ScriptResult.Status.UPDATED)

        } catch (Exception e) {
            log.error('LDAP connection {} could not be updated: {}', name, e.toString())
            scriptResult.addActionDetails(action, e)
        }
    }

    private void deleteConnection(LdapConfiguration ldapConf, Map action) {
        try {
            ldapConfigurationManager.deleteLdapServerConfiguration(ldapConf.id)
            log.info('LDAP connection {} deleted', ldapConf.name)
            scriptResult.addActionDetails(action, ScriptResult.Status.DELETED)

        } catch (Exception e) {
            log.error('LDAP connection {} could not be deleted: {}', ldapConf.name, e.toString())
            scriptResult.addActionDetails(action, e)
        }
    }

    String execute() {
        Boolean deleteUnknownLdapConnections = (config?.nexus?.deleteUnknownItems?.ldapConnections) ?: false
        List<Map<String, Object>> ldapConfDefs = (config?.ldapConnections as List<Map<String, Object>>) ?: []

        // Delete unknown existing connections
        if (deleteUnknownLdapConnections) {
            ldapConfigurationManager.listLdapServerConfigurations().each { ldapConf ->
                String name = ldapConf.name
                Boolean connInDef = ldapConfDefs.any {
                    (it.name as String).toLowerCase() == name.toLowerCase()
                }

                if (connInDef) {
                    log.info('LDAP connection {} found. Left untouched', name)
                } else {
                    def action = scriptResult.newAction(name: name, host: ldapConf.connection.host.hostName)
                    deleteConnection(ldapConf, action)
                }
            }
        }

        ldapConfDefs.each { ldapConfDef ->
            String name = ldapConfDef.name
            String host = ldapConfDef.host

            def action = scriptResult.newAction(name: name, host: host)

            // Search for existing connection
            LdapConfiguration configuration = ldapConfigurationManager.listLdapServerConfigurations()
                    .find { conf -> conf.name == name as String }

            if (configuration) {
                updateConnection(configuration, ldapConfDef, action)
            } else {
                createConnection(ldapConfDef, action)
            }
        }

        return sendResponse()
    }
}

return new SyncLdapConnections(this).execute()
