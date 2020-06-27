import org.sonatype.nexus.selector.CselSelector
import org.sonatype.nexus.selector.SelectorConfiguration
import org.sonatype.nexus.selector.SelectorManager

class SyncContentSelectors extends ScriptBaseClass {

    private final SelectorManager selectorManager

    SyncContentSelectors(context) {
        super(context)
        this.selectorManager = context.container.lookup(SelectorManager)
    }

    @SuppressWarnings('GrMethodMayBeStatic')
    Boolean isSelectorEqual(SelectorConfiguration existingSelector, Map selectorDef) {
        Boolean equals = true

        equals &= existingSelector.name == selectorDef.name
        equals &= existingSelector.description == selectorDef.description
        equals &= existingSelector.type == ((selectorDef.type) ?: CselSelector.TYPE)
        equals &= existingSelector.attributes?.get('expression') == selectorDef.expression

        return equals
    }

    @SuppressWarnings('GrMethodMayBeStatic')
    void setValues(SelectorConfiguration currentSelector, Map selectorDef) {
        currentSelector.with {
            name = selectorDef.name as String
            type = (selectorDef.type) ?: CselSelector.TYPE
            description = selectorDef.description as String ?: ''
            attributes = [
                    expression: selectorDef.expression
            ]
        }
    }

    void createContentSelector(Map selectorDef, Map action) {
        String name = selectorDef.name

        try {
            def newContentSelector = selectorManager.newSelectorConfiguration()
            setValues(newContentSelector, selectorDef)
            selectorManager.create(newContentSelector)
            log.info('Content selector {} created', name)
            scriptResult.addActionDetails(action, ScriptResult.Status.CREATED)

        } catch (Exception e) {
            log.error('Content selector {} could not be created: {}', name, e.toString())
            scriptResult.addActionDetails(action, e)
        }
    }

    void updateContentSelector(SelectorConfiguration existingSelector, Map selectorDef, Map action) {
        String name = selectorDef.name

        try {
            if (isSelectorEqual(existingSelector, selectorDef)) {
                log.info('No change content selector {}', name)
                scriptResult.addActionDetails(action, ScriptResult.Status.UNCHANGED)

            } else {
                setValues(existingSelector, selectorDef)
                selectorManager.update(existingSelector)
                log.info('Update Content selector {}', name)
                scriptResult.addActionDetails(action, ScriptResult.Status.UPDATED)
            }

        } catch (Exception e) {
            log.error('Content selector {} could not be updated: {}', name, e.toString())
            scriptResult.addActionDetails(action, e)
        }
    }

    void deleteContentSelector(String name, Map action) {
        try {
            def existingSelector = selectorManager.browse().find { it.name.toLowerCase() == name.toLowerCase() }
            selectorManager.delete(existingSelector)
            log.info('Content selector {} deleted', name)
            scriptResult.addActionDetails(action, ScriptResult.Status.DELETED)

        } catch (Exception e) {
            log.error('Content selector {} could not be deleted: {}', name, e.toString())
            scriptResult.addActionDetails(action, e)
        }
    }

    String execute() {
        Boolean deleteUnknownContentSelectors = (config?.nexus?.deleteUnknownItems?.contentSelectors) ?: false
        List<Map<String, Object>> contentSelectorsDefs = (config?.contentSelectors as List<Map<String, Object>>) ?: []

        // Delete unknown existing selectors
        if (deleteUnknownContentSelectors) {
            selectorManager.browse().each { existingSelector ->
                String name = existingSelector.name
                Boolean selectorInDef = contentSelectorsDefs.any {
                    (it.name as String).toLowerCase() == name.toLowerCase()
                }

                if (selectorInDef) {
                    log.info('Content selector {} found. Left untouched', name)

                } else {
                    def action = scriptResult.newAction(name: name, type: existingSelector.type)
                    deleteContentSelector(name, action)
                }
            }
        }

        // Create or update rules
        contentSelectorsDefs.each { contentSelectorDef ->
            String name = contentSelectorDef.name
            String format = (contentSelectorDef.format) ?: CselSelector.TYPE

            def action = scriptResult.newAction(name: name, type: format)

            def existingSelector = selectorManager.browse().find { contentSelector ->
                contentSelector.name.toLowerCase() == name.toLowerCase()
            }

            if (existingSelector) {
                updateContentSelector(existingSelector, contentSelectorDef, action)
            } else {
                createContentSelector(contentSelectorDef, action)
            }
        }

        return sendResponse()
    }
}

return new SyncContentSelectors(this).execute()
