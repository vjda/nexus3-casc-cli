import com.sonatype.nexus.ssl.plugin.internal.CertificateRetriever
import org.apache.http.client.utils.URIBuilder
import org.sonatype.nexus.ssl.KeyNotFoundException
import org.sonatype.nexus.ssl.TrustStore
import sun.security.x509.X509CertImpl

import java.security.KeyStoreException
import java.security.cert.CertificateFactory

class SyncTrustStore extends ScriptBaseClass {

    private final CertificateRetriever certificateRetriever
    private final TrustStore trustStore

    SyncTrustStore(context) {
        super(context)
        this.certificateRetriever = container.lookup(CertificateRetriever)
        this.trustStore = context.container.lookup(TrustStore)
    }

    private static String getFingerPrint(X509CertImpl certificate) {
        // get fingerprint with colon characters every 2 positions
        return certificate.getFingerprint('SHA-1').toList().collate(2)*.join().join(':')
    }

    private void importCertificate(X509CertImpl certificate, Map<String, Serializable> action) {
        def alias = getFingerPrint(certificate)
        action.put('alias', alias)

        try {
            // removing certificate if it is already imported
            trustStore.removeTrustCertificate(alias)
        } catch (KeyNotFoundException ignored) {
            log.warn('Certificate with alias {} not found', alias)
        }

        try {
            // importing certificate
            trustStore.importTrustCertificate(certificate, alias)
            log.info('Certificate with alias {} imported', alias)
            scriptResult.addActionDetails(action, ScriptResult.Status.CREATED)
        } catch (KeyStoreException kse) {
            log.error('Cannot import certificate with alias {}: {}', alias, kse.getMessage())
            scriptResult.addActionDetails(action, kse)
        }
    }

    private void importFromServer(String server) {
        def action = scriptResult.newAction(mode: 'server')

        try {
            def serverUrl = server.contains('https://') ? server.trim() : 'https://'.concat(server.trim())
            def url = new URIBuilder(serverUrl)
            def host = url.getHost()
            def port = url.getPort() != -1 ? url.getPort() : 443

            action.put('name', host)

            def certificates = certificateRetriever.retrieveCertificates(host, port)
            def cert = certificates.first() as X509CertImpl
            importCertificate(cert, action)
        } catch (Exception ex) {
            log.error('Cannot retrieve certificate for {}: {}', server, ex.getMessage())
            scriptResult.addActionDetails(action, ex)
        }
    }

    private void importFromPem(Map<String, Object> pemDef) {
        String pem = pemDef.content
        def action = scriptResult.newAction(mode: 'pem')

        // pem to cert
        try {
            InputStream stream = new ByteArrayInputStream(pem.getBytes())
            CertificateFactory cf = CertificateFactory.getInstance('X.509')
            def cert = cf.generateCertificate(stream) as X509CertImpl
            def server = cert.getSubjectDN().getName().tokenize(',').find { it =~ /CN=/ }.tokenize('=').get(1)
            action.put('name', server)
            importCertificate(cert, action)
        } catch (Exception ex) {
            log.error('Cannot convert PEM to X509 cert: {}', ex.getMessage())
            scriptResult.addActionDetails(action, ex)
        }
    }

    String execute() {
        List<String> serverDefs = (config?.certificates?.servers as List<String>) ?: []
        List<Map<String, Object>> pemDefs = (config?.certificates?.pemTexts as List<Map<String, Object>>) ?: []

        // Import certificates from servers
        serverDefs.each { importFromServer(it) }

        // Import certificates from PEM text
        pemDefs.each { importFromPem(it) }

        return sendResponse()
    }
}

return new SyncTrustStore(this).execute()
