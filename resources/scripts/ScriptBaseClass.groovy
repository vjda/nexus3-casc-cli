import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.slf4j.Logger
import org.sonatype.nexus.BlobStoreApi
import org.sonatype.nexus.CoreApi
import org.sonatype.nexus.common.app.GlobalComponentLookupHelper
import org.sonatype.nexus.script.plugin.RepositoryApi
import org.sonatype.nexus.security.SecurityApi

abstract class ScriptBaseClass {

    protected final Logger log
    protected final SecurityApi security
    protected final CoreApi core
    protected final RepositoryApi repository
    protected final BlobStoreApi blobStore
    protected final GlobalComponentLookupHelper container
    protected final Map<String, Serializable> config
    protected final ScriptResult scriptResult

    ScriptBaseClass(context) {
        log = context.log
        security = context.security
        core = context.core
        repository = context.repository
        blobStore = context.blobStore
        container = context.container
        scriptResult = new ScriptResult(false, false)
        config = new JsonSlurper().parseText(context.args as String) as Map<String, Serializable>
    }

    String sendResponse() {
        return JsonOutput.toJson(scriptResult)
    }

    abstract String execute()

    class ScriptResult implements Serializable {
        Boolean changed
        Boolean error
        List<Map<String, Serializable>> actionDetails

        ScriptResult(changed, error) {
            this.changed = changed
            this.error = error
            actionDetails = new ArrayList<>()
        }

        void hasChanged() {
            this.changed = true
        }

        void hasError() {
            this.error = true
        }

        Map<String, Serializable> newAction(Map initValues = [:]) {
            return new HashMap<String, Serializable>(initValues)
        }

        void addActionDetails(Map<String, Serializable> action, Throwable error) {
            action.put('errorMsg', error.toString())
            hasError()
            addActionDetails(action, Status.ERROR)
        }

        void addActionDetails(Map<String, Serializable> action, Status status) {
            action.put('status', status.toString().toLowerCase())

            if (status.isChanged) {
                hasChanged()
            }

            actionDetails.add(action)
        }

        enum Status implements Serializable {
            CREATED(true),
            CHANGED(true),
            UPDATED(true),
            DELETED(true),
            UNCHANGED(false),
            FORBIDDEN(false),
            ERROR(false)

            protected Boolean isChanged

            Status(Boolean isChanged) {
                this.isChanged = isChanged
            }
        }
    }
}
