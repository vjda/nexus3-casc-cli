class SyncBlobStores extends ScriptBaseClass {

    SyncBlobStores(context) {
        super(context)
    }

    void catchErrorOnCreation(String name, Map action, Closure body) {
        try {
            body()
            log.info('Blobstore {} created', name)
            scriptResult.addActionDetails(action, ScriptResult.Status.CREATED)

        } catch (Exception e) {
            log.error('Could not create blobstore {}: {}', name, e.toString())
            scriptResult.addActionDetails(action, e)
        }
    }

    void deleteBlobStore(String name, Map action) {
        try {
            blobStore.getBlobStoreManager().delete(name)
            log.info('Blobstore {} deleted', name)
            scriptResult.addActionDetails(action, ScriptResult.Status.DELETED)

        } catch (Exception e) {
            log.error('Could not delete blobstore {}: {}', name, e.toString())
            scriptResult.addActionDetails(action, e)
        }
    }

    String execute() {
        Boolean deleteUnknownBlobStores = (config?.nexus?.deleteUnknownItems?.blobstores) ?: false
        List<Map<String, Serializable>> blobStoresDefs = (config?.blobStores as List<Map<String, Serializable>>) ?: []

        // Delete unknown existing blob stores
        if (deleteUnknownBlobStores) {
            blobStore.getBlobStoreManager().browse().each { blobStore ->
                String name = blobStore.getBlobStoreConfiguration().getName()
                String type = blobStore.getBlobStoreConfiguration().getType()

                def action = scriptResult.newAction(name: name, type: type)

                Boolean found = blobStoresDefs.any { it.name == name }

                if (found) {
                    log.info('Blobstore {} found. Left untouched', name)

                } else {
                    deleteBlobStore(name, action)
                }
            }
        }

        // Create or update blob stores
        blobStoresDefs.each { blobStoreDef ->
            String name = blobStoreDef.name
            String type = blobStoreDef.get('type', 'File')

            def conf = blobStoreDef.config as Map<String, String>

            def action = scriptResult.newAction(name: name, type: type)

            def existingBlobStore = blobStore.getBlobStoreManager().exists(name)

            if (existingBlobStore) {
                log.info('Blobstore {} already exists. Left untouched', name)
                scriptResult.addActionDetails(action, ScriptResult.Status.UNCHANGED)

            } else {
                catchErrorOnCreation(name, action, {
                    if (type.toLowerCase() == 's3') {
                        blobStore.createS3BlobStore(name, conf)
                    } else if (type.toLowerCase() == 'file') {
                        blobStore.createFileBlobStore(name, blobStoreDef.path ?: name as String)
                    }
                })
            }
        }

        return sendResponse()
    }
}

return new SyncBlobStores(this).execute()
