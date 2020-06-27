import org.sonatype.nexus.cleanup.storage.CleanupPolicy
import org.sonatype.nexus.cleanup.storage.CleanupPolicyReleaseType
import org.sonatype.nexus.cleanup.storage.CleanupPolicyStorage

import java.util.concurrent.TimeUnit

import static org.sonatype.nexus.repository.search.DefaultComponentMetadataProducer.*

class SyncCleanupPolicies extends ScriptBaseClass {

    private final CleanupPolicyStorage cleanupPolicyStorage

    SyncCleanupPolicies(context) {
        super(context)
        this.cleanupPolicyStorage = context.container.lookup(CleanupPolicyStorage)
    }

    Map<String, String> createCriteria(Map policyDef) {
        Map<String, String> criteriaMap = [:]

        if (policyDef.criteria.lastBlobUpdated != null) {
            criteriaMap.put(LAST_BLOB_UPDATED_KEY, asStringSeconds(policyDef.criteria.lastBlobUpdated))
        }

        if (policyDef.criteria.lastDownloaded != null) {
            criteriaMap.put(LAST_DOWNLOADED_KEY, asStringSeconds(policyDef.criteria.lastDownloaded))
        }

        if (policyDef.criteria.preRelease != null) {
            Boolean isPrerelease = policyDef.criteria.preRelease == CleanupPolicyReleaseType.PRERELEASES.name()
            criteriaMap.put(IS_PRERELEASE_KEY, isPrerelease as String)
        }

        if (policyDef.criteria.regexKey != null) {
            criteriaMap.put(REGEX_KEY, policyDef.criteria.regexKey as String)
        }

        log.info('Using criteriaMap: {}', criteriaMap.toString())

        return criteriaMap
    }

    Boolean isPolicyEqual(CleanupPolicy existingPolicy, Map policyDef) {
        Boolean equals = true

        def currentCriteria = createCriteria(policyDef)

        equals &= existingPolicy.getNotes() == policyDef.notes
        equals &= existingPolicy.getFormat() == policyDef.format
        equals &= (currentCriteria == existingPolicy.getCriteria())

        return equals
    }

    @SuppressWarnings('GrMethodMayBeStatic')
    Long asSeconds(Integer days) {
        return days * TimeUnit.DAYS.toSeconds(1)
    }

    String asStringSeconds(Integer daysInt) {
        return String.valueOf(asSeconds(daysInt))
    }

    void setValues(CleanupPolicy currentPolicy, Map policyDef) {
        String name = policyDef.name
        String notes = (policyDef.notes) ?: ''
        String format = ((policyDef.format as String).toUpperCase() == 'ALL') ?
                CleanupPolicy.ALL_CLEANUP_POLICY_FORMAT : policyDef.format
        String mode = (policyDef.mode) ?: 'delete'

        currentPolicy.with {
            setName(name)
            setNotes(notes)
            setFormat(format)
            setMode(mode)
            setCriteria(createCriteria(policyDef))
        }
    }

    void createCleanupPolicy(Map policyDef, Map action) {
        String name = policyDef.name

        try {
            def cleanupPolicy = cleanupPolicyStorage.newCleanupPolicy()
            setValues(cleanupPolicy, policyDef)
            cleanupPolicyStorage.add(cleanupPolicy)
            log.info('Cleanup policy {} created', name)
            scriptResult.addActionDetails(action, ScriptResult.Status.CREATED)

        } catch (Exception e) {
            log.error('Cleanup policy {} could not be created: {}', name, e.toString())
            scriptResult.addActionDetails(action, e)
        }
    }

    void updateCleanupPolicy(Map policyDef, Map action) {
        String name = policyDef.name

        try {
            def existingPolicy = cleanupPolicyStorage.get(name)

            if (isPolicyEqual(existingPolicy, policyDef)) {
                log.info('No change Cleanup Policy {}', name)
                scriptResult.addActionDetails(action, ScriptResult.Status.UNCHANGED)

            } else {
                setValues(existingPolicy, policyDef)
                cleanupPolicyStorage.update(existingPolicy)
                log.info('Cleanup policy {} updated', name)
                scriptResult.addActionDetails(action, ScriptResult.Status.UPDATED)
            }

        } catch (Exception e) {
            log.error('Cleanup policy {} could not be updated: {}', name, e.toString())
            scriptResult.addActionDetails(action, e)
        }
    }

    void deleteCleanupPolicy(String name, Map action) {
        try {
            def existingPolicy = cleanupPolicyStorage.get(name)
            cleanupPolicyStorage.remove(existingPolicy)
            log.info('Cleanup policy {} deleted', name)
            scriptResult.addActionDetails(action, ScriptResult.Status.DELETED)

        } catch (Exception e) {
            log.error('Cleanup policy {} could not be deleted: {}', name, e.toString())
            scriptResult.addActionDetails(action, e)
        }
    }

    String execute() {
        Boolean deleteUnknownCleanupPolicies = (config?.nexus?.deleteUnknownItems?.cleanupPolicies) ?: false
        List<Map<String, Object>> cleanupPoliciesDefs = (config?.cleanupPolicies as List<Map<String, Object>>) ?: []

        // Delete unknown existing policies
        if (deleteUnknownCleanupPolicies) {
            cleanupPolicyStorage.getAll().each { existingPolicy ->
                String name = existingPolicy.getName()
                Boolean policyInDef = cleanupPoliciesDefs.any {
                    (it.name as String).toLowerCase() == name.toLowerCase()
                }

                if (policyInDef) {
                    log.info('Cleanup policy {} found. Left untouched', name)

                } else {
                    def action = scriptResult.newAction(
                            name: name,
                            format: existingPolicy.getFormat(),
                            mode: existingPolicy.getMode())
                    deleteCleanupPolicy(name, action)
                }
            }
        }

        // Create or update policies
        cleanupPoliciesDefs.each { policyDef ->
            String name = policyDef.name

            def action = scriptResult.newAction(
                    name: name,
                    format: policyDef.format as String,
                    mode: policyDef.mode as String)

            if (cleanupPolicyStorage.exists(name)) {
                updateCleanupPolicy(policyDef, action)
            } else {
                createCleanupPolicy(policyDef, action)
            }
        }

        return sendResponse()
    }
}

return new SyncCleanupPolicies(this).execute()
