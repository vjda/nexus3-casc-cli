import org.sonatype.nexus.repository.Repository
import org.sonatype.nexus.repository.config.Configuration
import org.sonatype.nexus.repository.manager.RepositoryManager
import org.sonatype.nexus.repository.routing.RoutingRuleStore

class SyncRepositories extends ScriptBaseClass {

    SyncRepositories(context) {
        super(context)
    }

    @SuppressWarnings('GrMethodMayBeStatic')
    Boolean findRepositoryInConfig(Repository repo, List<Map<String, Object>> repositories) {
        return repositories.any { repoConfig ->
            return repo.getType().getValue() == (repoConfig.type as String) &&
                    repo.getFormat().getValue() == (repoConfig.format as String) &&
                    repo.getName() == (repoConfig.name as String)
        }
    }

    Configuration newConfiguration(Map map) {
        RepositoryManager repositoryManager = repository.getRepositoryManager()
        Configuration config

        try {
            config = repositoryManager.newConfiguration()
        } catch (ignored) {
            // Compatibility with nexus versions older than 3.21
            config = Configuration.newInstance()
        }

        config.with {
            repositoryName = map.repositoryName
            recipeName = map.recipeName
            online = Boolean.parseBoolean(map.online as String)
            attributes = map.attributes as Map
        }

        return config
    }

    @SuppressWarnings('GrMethodMayBeStatic')
    boolean configurationEquals(Configuration currentConfig, Configuration configDef) {
        if (currentConfig.attributes.httpclient) {
            if (currentConfig.attributes.httpclient.authentication == [:]) {
                currentConfig.attributes.httpclient.authentication = null
            }
            if (currentConfig.attributes.httpclient.connection == [:]) {
                currentConfig.attributes.httpclient.connection = null
            }
            if (currentConfig.attributes.httpclient == [:]) {
                currentConfig.attributes.httpclient = null
            }
        }

        if (currentConfig.attributes.maven == [:]) {
            currentConfig.attributes.remove('maven')
        }

        return currentConfig.properties == configDef.properties
    }

    void createOrUpdateRepositories(List<Map<String, Serializable>> repos) {
        RepositoryManager repositoryManager = repository.getRepositoryManager()

        repos.each { repoDef ->
            String name = repoDef.name
            String format = repoDef.format
            String type = repoDef.type
            String recipeName = String.format('%s-%s', format, type)
            Configuration configuration

            def action = scriptResult.newAction(name: name, format: format, type: type)

            Repository existingRepo = repository.getRepositoryManager().get(name)

            try {
                if (existingRepo == null) {
                    log.info('Creating configuration for new repo {} (Format: {}, Type: {})', name, format, type)

                    // If the blob store does not exist, it may corrupt the Nexus installation.
                    // So, in order to avoid a critical state due to a bad configuration, it will throw an exception
                    // if the blob store does not found in the current configuration.
                    def blobStoreName = repoDef.storage.blobStoreName
                    if(!blobStore.getBlobStoreManager().exists(blobStoreName)) {
                        throw new IllegalArgumentException("Blobstore ${blobStoreName} not found")
                    }

                    // Default and/or immutable values
                    configuration = newConfiguration(
                            repositoryName: name,
                            recipeName: recipeName,
                            online: repoDef.online,
                            attributes: [
                                    storage: repoDef.storage
                            ]
                    )
                } else {
                    log.info('Loading configuration for existing repo {} (Format: {}, Type: {})', name, format, type)
                    // Load existing repository configuration
                    configuration = existingRepo.getConfiguration().copy()
                    configuration.online = repoDef.online
                    configuration.attributes.put('storage', repoDef.get('storage') as Map)
                }

                if (type == 'proxy' && repoDef.routingRuleName) {
                    configuration.routingRuleId = container.lookup(RoutingRuleStore)
                            .getByName(repoDef.routingRuleName as String)?.id()
                } else {
                    configuration.routingRuleId = null
                }

                if (type == 'proxy' || type == 'hosted') {
                    def cleanupPolicies = repoDef.cleanup?.policyNames as Set<String>
                    if (cleanupPolicies) {
                        // Due to misspelling in cleanup.policyNames key
                        configuration.attributes.put('cleanup', [policyName: cleanupPolicies])
                    } else {
                        configuration.attributes.remove('cleanup')
                    }
                }

                // Extra config allowed
                List<Tuple2<String, Boolean>> attributeKeys = [
                        new Tuple2<String, Boolean>(format, format != 'maven2'),
                        new Tuple2<String, Boolean>(type, type != 'hosted'),
                        new Tuple2<String, Boolean>('aptSigning', format == 'apt'),
                        new Tuple2<String, Boolean>('dockerProxy', format == 'docker' && type == 'proxy'),
                        new Tuple2<String, Boolean>('httpClient', type != 'hosted'),
                        new Tuple2<String, Boolean>('maven', format == 'maven2'),
                        new Tuple2<String, Boolean>('negativeCache', type == 'proxy'),
                        new Tuple2<String, Boolean>('nugetProxy', format == 'nuget' && type == 'proxy'),
                ]

                attributeKeys.findAll { it.second }.each { tuple ->
                    String key = tuple.first
                    // Due to misspelling in httpClient key
                    String confKey = (key == 'httpClient') ? key.toLowerCase() : key

                    if (repoDef.get(key) != null) {
                        configuration.attributes.put(confKey, repoDef.get(key) as Map)
                    } else if (configuration.attributes.get(confKey) != null) {
                        configuration.attributes.remove(key)
                    }
                }

                if (existingRepo == null) {
                    repositoryManager.create(configuration)
                    log.info('Configuration for repo {} created', name)
                    scriptResult.addActionDetails(action, ScriptResult.Status.CREATED)

                } else {
                    if (!configurationEquals(existingRepo.configuration, configuration)) {
                        repositoryManager.update(configuration)
                        log.info('Configuration for repo {} updated', name)
                        scriptResult.addActionDetails(action, ScriptResult.Status.UPDATED)
                    } else {
                        log.info('Configuration for repo {} not changed', name)
                        scriptResult.addActionDetails(action, ScriptResult.Status.UNCHANGED)
                    }
                }

            } catch (Exception e) {
                log.error('Configuration for repo {} could not be saved: {}', name, e.toString())
                scriptResult.addActionDetails(action, e)
            }
        }
    }

    void deleteRepository(String name, Map action) {
        try {
            repository.getRepositoryManager().delete(name)
            log.info('Repository {} deleted', name)
            scriptResult.addActionDetails(action, ScriptResult.Status.DELETED)

        } catch (Exception e) {
            log.error('Could not delete repository {}: {}', name, e.toString())
            scriptResult.addActionDetails(action, e)
        }
    }

    String execute() {
        Boolean deleteUnknownRepos = (config?.nexus?.deleteUnknownItems?.repos) ?: false
        List<Map<String, Object>> reposDefs = (config?.repositories as List<Map<String, Object>>) ?: []

        // Delete unknown existing repositories
        if (deleteUnknownRepos) {
            repository.getRepositoryManager().browse().each { repo ->
                String name = repo.getName()

                if (findRepositoryInConfig(repo, reposDefs)) {
                    log.info('Repository {} found. Left untouched', name)
                } else {
                    def currentResult = scriptResult.newAction(
                            name: name, format: repo.getFormat().getValue(), type: repo.getType().getValue())
                    deleteRepository(name, currentResult)
                }
            }
        }

        // Create or update repositories in the following order hosted, proxy and group
        List hostedRepos = reposDefs.findAll { repo -> repo.type == 'hosted' }
        List proxyRepos = reposDefs.findAll { repo -> repo.type == 'proxy' }
        List groupRepos = reposDefs.findAll { repo -> repo.type == 'group' }
        createOrUpdateRepositories(hostedRepos + proxyRepos + groupRepos)

        return sendResponse()
    }
}

return new SyncRepositories(this).execute()
