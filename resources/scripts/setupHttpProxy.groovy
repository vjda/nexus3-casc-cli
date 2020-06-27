class SetupHttpProxy extends ScriptBaseClass {

    SetupHttpProxy(context) {
        super(context)
    }

    String execute() {
        Map<String, Serializable> httpClientDef = (config.httpClient as Map<String, Serializable>) ?: [:]

        def action = scriptResult.newAction()

        try {
            core.connectionRetryAttempts(httpClientDef.retries as Integer ?: 2)
            core.connectionTimeout(httpClientDef.timeout as Integer ?: 20)
            core.userAgentCustomization(httpClientDef.userAgentSuffix as String ?: '')

            if (httpClientDef.httpProxy) {
                def proxyCnf = httpClientDef.httpProxy
                String host = proxyCnf.host
                Integer port = proxyCnf.port
                String username = proxyCnf.authentication.username
                String password = proxyCnf.authentication.password
                String ntlmHost = proxyCnf.authentication.ntlmHost
                String ntlmDomain = proxyCnf.authentication.ntlmDomain

                if (proxyCnf.authentication?.ntlmHost) {
                    core.httpProxyWithNTLMAuth(host, port, username, password, ntlmHost, ntlmDomain)

                } else if (proxyCnf.authentication?.username) {
                    core.httpProxyWithBasicAuth(host, port, username, password)

                } else {
                    core.httpProxy(host, port)
                }

                log.info('Http proxy set for {}:{}', host, port)

            } else {
                log.info('Http proxy configuration deleted')
                core.removeHTTPProxy()
            }

            if (httpClientDef.httpsProxy) {
                def proxyCnf = httpClientDef.httpsProxy
                String host = proxyCnf.host
                Integer port = proxyCnf.port
                String username = proxyCnf.authentication.username
                String password = proxyCnf.authentication.password
                String ntlmHost = proxyCnf.authentication.ntlmHost
                String ntlmDomain = proxyCnf.authentication.ntlmDomain

                if (proxyCnf.authentication?.ntlmHost) {
                    core.httpsProxyWithNTLMAuth(host, port, username, password, ntlmHost, ntlmDomain)

                } else if (proxyCnf.authentication?.username) {
                    core.httpsProxyWithBasicAuth(host, port, username, password)

                } else {
                    core.httpsProxy(host, port)
                }

                log.info('Https proxy set for {}:{}', host, port)

            } else {
                log.info('Https proxy configuration deleted')
                core.removeHTTPSProxy()
            }

            if (httpClientDef.httpProxy || httpClientDef.httpsProxy) {
                core.nonProxyHosts()
                core.nonProxyHosts(httpClientDef.excludeHosts as String[])
            }

            scriptResult.addActionDetails(action, ScriptResult.Status.UPDATED)

        } catch (Exception e) {
            log.error('Could not set proxy configuration: {}', e.toString())
            scriptResult.addActionDetails(action, e)
        }

        return sendResponse()
    }
}

return new SetupHttpProxy(this).execute()
