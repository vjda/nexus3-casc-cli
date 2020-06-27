import org.sonatype.nexus.repository.routing.RoutingMode
import org.sonatype.nexus.repository.routing.RoutingRule
import org.sonatype.nexus.repository.routing.RoutingRuleStore

class SyncRoutingRules extends ScriptBaseClass {

    private final RoutingRuleStore routingRuleStore

    SyncRoutingRules(context) {
        super(context)
        this.routingRuleStore = context.container.lookup(RoutingRuleStore)
    }

    @SuppressWarnings('GrMethodMayBeStatic')
    Boolean isRuleEqual(RoutingRule existingRule, Map<String, Object> ruleDef) {
        Boolean equals = true

        equals &= existingRule.name() == ruleDef.name
        equals &= existingRule.description() == ruleDef.description
        equals &= existingRule.mode() == (ruleDef.mode as String).toUpperCase() as RoutingMode
        equals &= existingRule.matchers() == ruleDef.matchers as List<String>

        return equals
    }

    @SuppressWarnings('GrMethodMayBeStatic')
    void setValues(RoutingRule currentRule, Map<String, Object> ruleDef) {
        currentRule.name(ruleDef.name as String)
        currentRule.description(ruleDef.description as String ?: '')
        currentRule.mode((ruleDef.mode as String).toUpperCase() as RoutingMode)
        currentRule.matchers(ruleDef.matchers as List<String>)
    }

    void createRoutingRule(Map ruleDef, Map action) {
        String name = ruleDef.name

        try {
            def newRoutingRule = routingRuleStore.newRoutingRule()
            setValues(newRoutingRule, ruleDef)
            routingRuleStore.create(newRoutingRule)
            log.info('Routing rule {} created', name)
            scriptResult.addActionDetails(action, ScriptResult.Status.CREATED)

        } catch (Exception e) {
            log.error('Routing rule {} could not be created: {}', name, e.toString())
            scriptResult.addActionDetails(action, e)
        }
    }

    void updateRoutingRule(Map routingRuleDef, Map action) {
        String name = routingRuleDef.name

        try {
            def existingRule = routingRuleStore.list().find { it.name().toLowerCase() == name.toLowerCase() }

            if (isRuleEqual(existingRule, routingRuleDef)) {
                log.info('No change routing rule {}', name)
                scriptResult.addActionDetails(action, ScriptResult.Status.UNCHANGED)

            } else {
                setValues(existingRule, routingRuleDef)
                routingRuleStore.update(existingRule)
                log.info('Update Routing rule {}', name)
                scriptResult.addActionDetails(action, ScriptResult.Status.UPDATED)
            }

        } catch (Exception e) {
            log.error('Routing rule {} could not be updated: {}', name, e.toString())
            scriptResult.addActionDetails(action, e)
        }
    }

    void deleteRoutingRule(String name, Map action) {
        try {
            def existingRule = routingRuleStore.list().find { it.name().toLowerCase() == name.toLowerCase() }
            routingRuleStore.delete(existingRule)
            log.info('Routing rule {} deleted', name)
            scriptResult.addActionDetails(action, ScriptResult.Status.DELETED)

        } catch (Exception e) {
            log.error('Routing rule {} could not be deleted: {}', name, e.toString())
            scriptResult.addActionDetails(action, e)
        }
    }

    String execute() {
        Boolean deleteUnknownRoutingRules = (config?.nexus?.deleteUnknownItems?.routingRules) ?: false
        List<Map<String, Object>> routingRulesDefs = (config?.routingRules as List<Map<String, Object>>) ?: []

        // Delete unknown existing rules
        if (deleteUnknownRoutingRules) {
            routingRuleStore.list().each { existingRule ->
                String name = existingRule.name()
                Boolean ruleInDef = routingRulesDefs.any {
                    (it.name as String).toLowerCase() == name.toLowerCase()
                }

                if (ruleInDef) {
                    log.info('Routing rule {} found. Left untouched', name)
                } else {
                    def action = scriptResult.newAction(name: name, mode: existingRule.mode().name())
                    deleteRoutingRule(name, action)
                }
            }
        }

        // Create or update rules
        routingRulesDefs.each { routingRuleDef ->
            String name = routingRuleDef.name
            String mode = routingRuleDef.mode

            def action = scriptResult.newAction(name: name, mode: mode)

            Boolean ruleExists = routingRuleStore.list().any { routingRule ->
                routingRule.name().toLowerCase() == name.toLowerCase()
            }

            if (ruleExists) {
                updateRoutingRule(routingRuleDef, action)
            } else {
                createRoutingRule(routingRuleDef, action)
            }
        }

        return sendResponse()
    }
}

return new SyncRoutingRules(this).execute()
