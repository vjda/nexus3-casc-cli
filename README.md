# Nexus3CasC: Configuration As Code for Nexus 3

[![Docker Repository on Quay](https://quay.io/repository/vjda/nexus3casc-cli/status "Docker Repository on Quay")](https://quay.io/repository/vjda/nexus3casc-cli)

CLI to inject the configuration stored in YAML(s) into [Sonatype Nexus repository manager 3](https://help.sonatype.com/repomanager3).

## What it is for

Nexus3CasC provides you a way to configure a Nexus 3 server to perform the following operations:

* Changing admin password
* Enabling or disabling...
  * anonymous access
  * realms
* Setting HTTP(s) proxies
* Setting a SMTP connection
* Importing certificates from...
  * server addresses
  * PEM format
* Creating or updating...
  * blob stores to store objects locally or in AWS S3
  * content selectors
  * cleanup policies
  * routing rules
  * repositories for all formats (_maven2, npm, docker_, etc) and types (_hosted, remote_ and _group_)
  * internal users
  * custom roles
  * custom privileges
  * scheduled tasks
  * LDAP connections
* Deleting any unknown configuration for items like...
  * blob stores
  * content selectors
  * cleanup policies
  * routing rules
  * repositories
  * internal users (_anonymous_ and _admin_ users will be ignored)
  * custom roles (built-in roles will be ignored)
  * custom privileges (built-in privileges will be ignored)
  * scheduled tasks
  * LDAP connections

## How it works

Nexus 3 provides a powerful [scripting API](https://help.sonatype.com/repomanager3/rest-and-integration-api/script-api) to simplify provisioning and executing other complex tasks. These scripts are written in Groovy language.

Nexus3CasC takes advantage of this capability to inject some scripts in Nexus 3 using its API rest. It also reads the configuration from YAML files, connects to the Nexus 3 server and uses its API to invoke every script sending the configuration values as input arguments.

The CLI is written in python 3.8 and the scripts for Nexus 3 are written in Groovy.

### Configuration items

As said before, you can use YAML files to define your custom configuration. You can place this YAML where you want and pass it as command argument to Nexus3CasC CLI.

#### Default values

There is a YAML with default values at [resources/config/nexus_defaults.yaml](resources/config/nexus_defaults.yaml). It defines a set of keys that are used in case of they are not defined in your YAML file.

```yaml
# config/nexus_defaults.yaml

nexus:
  baseUrl: http://localhost:8081  # URL to reach Nexus 3 UI
  anonymousAccessEnabled: false   # If anonymous users can access to Nexus 3
  defaults:
    adminUser: admin              # Default admin user
    adminPassword: admin123       # Default admin password
  deleteUnknownItems:             # To delete existing items in Nexus but not in YAML
    blobstores: false
    contentSelectors: false
    cleanupPolicies: false
    routingRules: false
    repos: false
    ldapConnections: false
    customLocalUsers: false
    customRoles: false
    customPrivileges: false
    tasks: false

# Enable/disable Nexus realms
realms: []

# Set http proxy global configuration
httpClient: {}

# Create new blob stores to store binary objects for the repositories
blobStores: []

# Create new policies to clean up artifacts
cleanupPolicies: []

# Create new routing rules to either allow or block access to artifacts
routingRules: []

# Create new content selector to select specific content
contentSelectors: []  

# Create new repositories to store artifacts from different sources
repositories: []

# Set a connection to a SMTP server
smtpConnection:
  enabled: false

# Create new connections to LDAP servers
ldapConnections: []

# Create new internal users and/or assign roles to LDAP users
customUsers:
  local: []
  ldap: []

# Create new custom roles
customRoles: []

# Create new custom privileges
customPrivileges: []

# Add certificates to the trust store
certificates: []

# Create new scheduled tasks
tasks: []
```

> Notice that these default values are merged with the values set in your YAML files, so if you do not define one of them it will use the default one.

#### `nexus`

This section has the global values which affect to the Nexus installation.

Here are the minimal required values:

```yaml
nexus:
  adminPassword: this_is_awes0me! # Set the password for the user 'admin'
```

You can override any of the above [default values](#default-values).

#### `realms`

Security realms are used for authentication and authorization of different types of users. You can enable or disable them as follows:

```yaml
realms:
  - name: Conan Bearer Token Realm
    enabled: false
  - name: Default Role Realm
    enabled: false
  - name: Docker Bearer Token Realm
    enabled: false
  - name: LDAP Realm
    enabled: false
  - name: Local Authenticating Realm
    enabled: true
  - name: Local Authorizing Realm
    enabled: true
  - name: npm Bearer Token Realm
    enabled: false
  - name: NuGet API-Key Realm
    enabled: false
  - name: Rut Auth Realm
    enabled: false
```

The list of realms could be different depending on the Nexus 3 version you use. If you include a non-existent realm it will be ignored.

> Be careful disabling some realms such as _Local Authenticating Realm_ and _Local Authorizing Realm_. If one of them is disabled, you will not be able to login as an internal user!

#### `httpClient`

Manage outbound HTTP/HTTPS configuration.

```yaml
httpClient:
  userAgentSuffix: NRXM3-client
  timeout: 30
  retries: 2
  excludeHosts:
    - localhost
    - example.com
    - domain.com
  httpProxy:
    host: proxy.acme.com
    port: 8080
    authentication:
      username: proxyuser      # required: if `httpProxy.authentication` is set
      password: pr0xyp@ss      # required: if `httpProxy.authentication` is set
      ntlmHost: ntlm.acme.com  # optional
      ntlmDomain: acme.com     # optional
  httpsProxy:
    host: proxy.acme.com
    port: 8443
    authentication:
      username: proxyuser      # required: if `httpsProxy.authentication` is set
      password: pr0xyp@ss      # required: if `httpsProxy.authentication` is set
      ntlmHost: ntlm.acme.com  # optional
      ntlmDomain: acme.com     # optional
```

#### `blobStores`

A blob store is a location where to store the _blob objects_ for binary assets.

```yaml
blobStores:
  - name: default  # required
    type: file     # optional (defaults: file)
    path: default  # optional: a relative or absolute path (defaults: `name` value)
  - name: local
  - name: remote
    type: file
    path: /tmp/custom/remote
  - name: s3-blobstore
    type: s3                                                     # Use `type: s3` to store blobs in AWS S3 bucket
    config:                                                      # required if`type: s3`
      bucket: s3-blobstore                                       # required if`type: s3`
      accessKeyId: AKIAIOSFODNN7EXAMPLE                          # required if`type: s3`
      secretAccessKey: wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY  # required if`type: s3`
```

#### `cleanupPolicies`

The cleanup feature is a way of removing components from your repositories using policies customized to suit your needs.

```yaml
cleanupPolicies:
  - name: purge-snapshots                                # required
    format: maven2                                       # required
    notes: "Delete maven snapshots downloaded > 7 days"  # optional
    mode: delete                                         # optional (defaults: delete)
    criteria:                                            # required
      lastBlobUpdated: 30                                # optional: must be a number > 0
      lastDownloaded: 7                                  # optional: must be a number > 0
      preRelease: PRERELEASES                            # optional: RELEASES or PRERELEASES
      regexKey: (org|com)/.*                             # optional: must be a regex pattern
  - name: purge-helm-artifacts-with-one-year
    format: helm
    notes: Delete helm artifacts older than 1 year
    mode: delete
    criteria:
      lastBlobUpdated: 365
      regexKey: "(org|com)/company/.*"
```

#### `routingRules`

Routing rules are used to prevent or allow it from making certain requests to upstream repositories.

```yaml
routingRules:
  - name: block-com-company-department                # required
    description: Blocks artifacts with specific path  # optional
    mode: BLOCK                                       # required: ALLOW or BLOCK
    matchers:                                         # required
    - ^com/company/department/.*                      # required: at least 1 matcher, must be a regex
  - name: allow-some
    mode: allow
    description: Allow artifacts from specific paths
    matchers:
    - company/department2/.*
    - .*/department1/.*
    - ^com/.*/engineering/.*
```

#### `contentSelectors`

Content selectors provide a way to select specific content from all of your content.

```yaml
contentSelectors:
  - name: helm-all-selector                     # required
    format: csel                                # optional (defaults: csel)
    description: Search for all helm artifacts  # optional
    expression: format == "helm"                # required
  - name: raw-selector
    format: csel
    description: Search for raw artifacts in specific path
    expression: format == "maven2" and path =^ "/org/sonatype/nexus"
```

#### `repositories`

Create repositories to store artifacts for different formats and sources.

```yaml
repositories:

  # Hosted repositories
  - name: helm-releases                                  # required
    format: helm                                         # required: any format which supports hosted repositories
    type: hosted                                         # required: must be "hosted"
    online: true                                         # optional (defaults: false)
    storage:                                             # required
      blobStoreName: local                               # required
      strictContentTypeValidation: true                  # required
      writePolicy: ALLOW_ONCE                            # required: ALLOW, ALLOW_ONCE or DENY
    cleanup:                                             # optional
      policyNames:
      - remove-old-helm-artifacts
    apt:                                                 # required: if `format: apt`
      distribution: bionic                               # required
    aptSigning:                                          # required: if `format: apt`
      keypair: 515F58C16D58E682E91ACEFF17B5C97F9A816AD7  # required
      passphrase: keep my account safe                   # required
    docker:                                              # required: if `format: docker`
      v1Enabled: false                                   # optional (defaults: false)
      forceBasicAuth: true                               # optional (defaults: false)
      httpPort: 5001                                     # optional
      httpsPort: 5000                                    # optional
    maven:                                               # required: if `format: maven2`
      versionPolicy: SNAPSHOT                            # required: RELEASE, SNAPSHOT or MIXED
      layoutPolicy: PERMISSIVE                           # required: STRICT or PERMISSIVE
    yum:                                                 # required: if `format: yum`
      repodataDepth: 5                                   # required
      deployPolicy: STRICT                               # required

  # Remote repositories
  - name: yum-proxy                               # required
    format: yum                                   # required: any format which supports proxy repositories
    type: proxy                                   # required: must be "proxy"
    online: true                                  # optional (defaults: false)
    storage:                                      # required
      blobStoreName: remote                       # required
      strictContentTypeValidation: true           # required
    proxy:                                        # required
      remoteUrl: http://mirror.centos.org/centos  # required
      contentMaxAge: 1440                         # optional (defaults: 1440)
      metadataMaxAge: 1440                        # optional (defaults: 1440)
    negativeCache:                                # optional
      enabled: true                               # required
      timeToLive: 1440                            # required
    cleanup:                                      # optional
      policyNames:
      - remove-old-packages
    routingRuleName: allow-some                   # optional
    httpClient:                                   # required
      blocked: false                              # optional (defaults: false)
      autoBlock: true                             # optional (defaults: false)
      connection:                                 # optional (defaults: {})
        retries: 3                                # optional
        userAgentSuffix: "NXRM3-client"           # optional
        timeout: 30                               # optional
        enableCircularRedirects: false            # optional (defaults: false)
        enableCookies: false                      # optional (defaults: false)
        useTrustStore: true                       # optional (defaults: false)
      authentication:                             # optional (defaults: {})
        username: proxyuser                       # required: if `httpClient.authentication` is set
        password: pr0xyp@ss                       # required: if `httpClient.authentication` is set
        ntlmHost: ntlm.acme.com                   # optional
        ntlmDomain: acme.com                      # optional
    bower:                                        # required: if `format: bower`
      rewritePackageUrls: true                    # required
    nugetProxy:                                   # required: if `format: nuget`
      queryCacheItemMaxAge: 1440                  # required
    docker:                                       # required: if `format: docker`
      v1Enabled: false                            # optional (defaults: false)
      forceBasicAuth: true                        # optional (defaults: false)
      httpPort: 5001                              # optional: must be a valid port number
      httpsPort: 5000                             # optional: must be a valid port number
    dockerProxy:                                  # required: if `format: docker`
      indexType: CUSTOM                           # required: HUB, REGISTRY or CUSTOM
      indexUrl: https://index.docker.io/          # required: if `dockerProxy.indextype: CUSTOM`
      foreignLayerUrlWhitelist:                   # optional: must be a regex
      - https?://go.microsoft.com/.*
      - https?://.*\.azurecr\.io/.*

  # Group repositories
  - name: npm-group                      # required
    format: npm                          # required: any format which supports group repositories
    type: group                          # required: must be "group"
    online: true                         # optional (defaults: false)
    storage:                             # required
      blobStoreName: group               # required
      strictContentTypeValidation: true  # required
    group:                               # required
      memberNames:                       # required: at least 1 repository for the same format
      - npm-hosted
      - npm-proxy
    docker:                              # required: if `type: docker`
      v1Enabled: false                   # optional (defaults: false)
      forceBasicAuth: true               # optional (defaults: false)
      httpPort: 5001                     # optional: must be a valid port number
      httpsPort: 5000                    # optional: must be a valid port number
```

#### `smtpConnection`

Connect to SMTP server to send emails to users.

```yaml
smtpConnection:
  enabled: true                         # optional (defaults: false)
  host: my-smtp-host                    # required
  port: 587                             # required: must be a valid port number
  username: emailuser                   # required
  password: p@assw0rd                   # required
  fromAddress: nexus@example.org        # required
  subjectPrefix: "[NEXUS] "             # optional
  startTlsEnabled: true                 # optional (defaults: false)
  startTlsRequired: true                # optional (defaults: false)
  sslOnConnectEnabled: false            # optional (defaults: false)
  sslServerIdentityCheckEnabled: false  # optional (defaults: false)
  nexusTrustStoreEnabled: false         # optional (defaults: false)
```

#### `customUsers`

Add additional users to log in Nexus 3.

```yaml
customUsers:
  local:
    - userId: jamesbrown                # required
      password: Th15_1s_Aw3s0m3         # required: it should be provided unless being anonymous or admin
      firstName: James                  # required
      lastName: Brown                   # required
      emailAddress: jbrown@example.com  # required
      status: DISABLED                  # optional: ACTIVE, LOCKED, DISABLED or CHANGEPASSWORD (defaults: DISABLED)
      roles:                            # required
        - my-admin-role
    - userId: anonymous
      password:                         # can be null for anonymous user
      firstName: Anonymous
      lastName: User
      emailAddress: anonymous@example.org
      status: ACTIVE
      roles:
        - nx-anonymous
    - userId: admin
      firstName: Administrator
      lastName: User
      emailAddress: admin@example.org
      status: ACTIVE
      roles:
        - nx-admin
  ```

#### `customRoles`

Roles aggregate privileges into a related context and can, in turn, be grouped to create more complex roles.

```yaml
customRoles:
  - id: my-admin-role                       # required
    name: my-admin-role                     # required
    source: default                         # optional (defaults: default)
    description: Custom Administrator Role  # optional
    privileges: []                          # optional
    roles:                                  # optional
      - nx-admin
  - id: custom-maven-developer-role
    name: Maven Developer
    source: default
    description: Maven Developer Role
    privileges:
      - nx-repository-view-maven2-*-browse
      - nx-repository-view-maven2-*-add
      - nx-repository-view-maven2-*-read
    roles: []
  ```

#### `customPrivileges`

Privileges control access to specific functionality of the repository manager and can be grouped as a role and assigned to a specific user.

```yaml
customPrivileges:

  # These are privileges that use patterns to group other privileges
  - type: wildcard                # required: wildcard, application, repository-admin, repository-view, repository-content-selector or script
    name: my-wildcard-all         # required
    description: All permissions  # optional
    pattern: "nexus:*"            # required: if `type: wildcard`

  # These are privileges related to a specific domain in the repository manager
  - type: application
    name: my-app-analytics-all
    description: All permissions for Analytics
    actions:                      # required: if not `type: wildcard`
      - ALL                       # required: ADD, BROWSE, CREATE, DELETE, EDIT, READ, UPDATE OR ALL
    domain: analytics             # required

  # These are privileges related to the administration and configuration of a specific repository
  - type: repository-admin
    name: my-maven-repository-admin
    description: All privileges for all maven repositories
    actions:
      - ALL
    format: maven2                # required: if `type: repository-admin`
    repository: "*"               # required: can be any repository name or "*" for all repositories

  # These are privileges controlling access to the content of a specific repository
  - type: repository-view
    name: my-nuget-repository-view-add
    description: Add permission for all nuget repository views
    actions:
      - BROWSE
    format: nuget
    repository: nuget-hosted

  # These are privileges attributed to filtered content within a format, evaluated against a content selector
  - type: repository-content-selector
    name: my-content-selectors-all
    description: Add permission for content selector for raw repositories  
    actions:
      - BROWSE
      - ADD
      - CREATE
      - EDIT
      - UPDATE
    repository: "*-raw"            # required: *-raw selects all repositories which their names end with -raw
    contentSelector: raw-selector  # required

  # These are privileges related to the execution and management of scripts
  - type: script
    name: my-script-*-add
    description: Add permission for Scripts
    actions:
      - ADD
      - READ
    scriptName: "*"                # required: can be a script name or "*" for all scripts
```

#### `certificates`

Add SSL certificates of remote proxy repositories to marked as trusted.

```yaml
certificates:
  servers:                              # optional: all below formats are accepted
    - cdn.cocoapods.org                 # host
    - conan.bintray.com:443             # host + port
    - https://cran.r-project.org        # protocol + host
    - https://download.eclipse.org:443  # protocol + host + port
  certs:                                # optional
    - pem: |-                           # must be `pem: <PEM_CERTIFICATE_CONTENT>`
        -----BEGIN CERTIFICATE-----
        MIIHTjCCBjagAwIBAgIQa5LMK51h2Z4IAAAAAEZzsTANBgkqhkiG9w0BAQsFADBC
        MQswCQYDVQQGEwJVUzEeMBwGA1UEChMVR29vZ2xlIFRydXN0IFNlcnZpY2VzMRMw
        EQYDVQQDEwpHVFMgQ0EgMU8xMB4XDTIwMDYxMDA5MTYyNVoXDTIwMDkwMjA5MTYy
        NVowcjELMAkGA1UEBhMCVVMxEzARBgNVBAgTCkNhbGlmb3JuaWExFjAUBgNVBAcT
        DU1vdW50YWluIFZpZXcxEzARBgNVBAoTCkdvb2dsZSBMTEMxITAfBgNVBAMMGCou
        c3RvcmFnZS5nb29nbGVhcGlzLmNvbTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCC
        AQoCggEBALEeUhptbYTwwjAZbI+acwYyUgNgc6dejR6Lq58rh36KKT9bEnvp5GXa
        /veW/7HLEd6yhe46biQKELTAwefXKA885u+wQeMem7PrNyEVTgff+wjCnEz/QLBs
        qu4UzvBPcMQDleOP8kTsNBQNN/lUArRF2ESwTIUzkw1bgkvud4hbQTHIdLELjkCl
        L5ufb1AaEF8dBT2DijOwLy3Fm+XlNg6ztCj7K+QAAUqhIqN2kd+mJ3GxguBgCH3Q
        SbRYywgMoVH3wQz5mlWIs4jQckbgmog5L3m+l4zk/eJpD5egGiOYzSj8IUb/zdjN
        lYZMgkF8w5Wz/LAFPpXtraHE83oX5MMCAwEAAaOCBA4wggQKMA4GA1UdDwEB/wQE
        AwIFoDATBgNVHSUEDDAKBggrBgEFBQcDATAMBgNVHRMBAf8EAjAAMB0GA1UdDgQW
        BBQUDn/0bKh4/6eM+K0VeuTeq8bEVzAfBgNVHSMEGDAWgBSY0fhuEOvPm+xgnxiQ
        G6DrfQn9KzBoBggrBgEFBQcBAQRcMFowKwYIKwYBBQUHMAGGH2h0dHA6Ly9vY3Nw
        LnBraS5nb29nL2d0czFvMWNvcmUwKwYIKwYBBQUHMAKGH2h0dHA6Ly9wa2kuZ29v
        Zy9nc3IyL0dUUzFPMS5jcnQwggHIBgNVHREEggG/MIIBu4IYKi5zdG9yYWdlLmdv
        b2dsZWFwaXMuY29tgiQqLmFwcHNwb3QuY29tLnN0b3JhZ2UuZ29vZ2xlYXBpcy5j
        b22CIiouY29tbW9uZGF0YXN0b3JhZ2UuZ29vZ2xlYXBpcy5jb22CKSouY29udGVu
        dC1zdG9yYWdlLWRvd25sb2FkLmdvb2dsZWFwaXMuY29tgicqLmNvbnRlbnQtc3Rv
        cmFnZS11cGxvYWQuZ29vZ2xlYXBpcy5jb22CICouY29udGVudC1zdG9yYWdlLmdv
        b2dsZWFwaXMuY29tghAqLmdvb2dsZWFwaXMuY29tgiEqLnN0b3JhZ2UtZG93bmxv
        YWQuZ29vZ2xlYXBpcy5jb22CHyouc3RvcmFnZS11cGxvYWQuZ29vZ2xlYXBpcy5j
        b22CHyouc3RvcmFnZS5zZWxlY3QuZ29vZ2xlYXBpcy5jb22CIGNvbW1vbmRhdGFz
        dG9yYWdlLmdvb2dsZWFwaXMuY29tghZzdG9yYWdlLmdvb2dsZWFwaXMuY29tgh1z
        dG9yYWdlLnNlbGVjdC5nb29nbGVhcGlzLmNvbYIPdW5maWx0ZXJlZC5uZXdzMCEG
        A1UdIAQaMBgwCAYGZ4EMAQICMAwGCisGAQQB1nkCBQMwMwYDVR0fBCwwKjAooCag
        JIYiaHR0cDovL2NybC5wa2kuZ29vZy9HVFMxTzFjb3JlLmNybDCCAQUGCisGAQQB
        1nkCBAIEgfYEgfMA8QB2ALIeBcyLos2KIE6HZvkruYolIGdr2vpw57JJUy3vi5Be
        AAABcp27Ko8AAAQDAEcwRQIgd6MMtKRRF7G18hpdGog3NINQsuW9vdk7gdqLRDCV
        /M4CIQCY6NQeRvf5NgmihDWzmpOMPLNmqNFsrdEpgjP6uYY1UAB3AMZSoOxIzrP8
        qxcJksQ6h0EzCegAZaJiUkAbozYqF8VlAAABcp27KqgAAAQDAEgwRgIhANJ58DxN
        zyg9Sjenwhl7VMZfdwPPY5k1L/Pwji5S8s41AiEAjrCdS19KqwBBaxagrT/rPsZH
        PYkBJVCzPLIm0jGeMq8wDQYJKoZIhvcNAQELBQADggEBAMqOLe5vNl4/ThmSFlku
        WX6iLXmPh6faHRJrVTVO0NNDMI/TTmwBEMYTf2VHEiWlGbe0k7fopkUKvSTGroUT
        efDMZLL5WsipIG87tfqyOV104e7I/OmJqx7/s9Zq4sild+2iMH8fhkzZqhaPzVn8
        e526u2i7vo8xPOiHeQsYq7yWf4PPK3wS4xO+0Ov2aG0m2HkeSTkgommoYiGNrMVv
        xBs5jrz5aMTtocwBPTADl17r5x13nsHhVorlebVHaxRO6clpUxOkhty2bgDP7TWB
        JRn+L9BHMi+7EfgTJQVIwvjKgsjikfnM2JwLhmCnWFidIL8XW6noCVIaFJgf03Zb
        vew=
        -----END CERTIFICATE-----
```

If you want to extract PEM certificates from your servers, you can do this:

```sh
$ openssl s_client -showcerts -connect registry.npmjs.org:443 </dev/null 2>/dev/null | openssl x509 -outform PEM
-----BEGIN CERTIFICATE-----
MIIErjCCBFSgAwIBAgIQI6p7Y0Yf7HnyY71zvmeEAzAKBggqhkjOPQQDAjCBkjEL
MAkGA1UEBhMCR0IxGzAZBgNVBAgTEkdyZWF0ZXIgTWFuY2hlc3RlcjEQMA4GA1UE
BxMHU2FsZm9yZDEaMBgGA1UEChMRQ09NT0RPIENBIExpbWl0ZWQxODA2BgNVBAMT
L0NPTU9ETyBFQ0MgRG9tYWluIFZhbGlkYXRpb24gU2VjdXJlIFNlcnZlciBDQSAy
MB4XDTIwMDUxOTAwMDAwMFoXDTIwMTEyNTIzNTk1OVowJjEkMCIGA1UEAxMbc3Ns
ODkxNzM4LmNsb3VkZmxhcmVzc2wuY29tMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcD
QgAEILTZBNtaXXiIlSOn5kmRf65xdN2dLZdP8sm9eEhDirlvKcM2pdTxSQQ8LIhS
+SI0aRXjVaDPniTobjgsjEfu6qOCAvUwggLxMB8GA1UdIwQYMBaAFEAJYWfwvINx
T94SCCxv1NQrdj2WMB0GA1UdDgQWBBSNu13h63VwbslDXq7fjTD0kqbaADAOBgNV
HQ8BAf8EBAMCB4AwDAYDVR0TAQH/BAIwADAdBgNVHSUEFjAUBggrBgEFBQcDAQYI
KwYBBQUHAwIwSQYDVR0gBEIwQDA0BgsrBgEEAbIxAQICBzAlMCMGCCsGAQUFBwIB
FhdodHRwczovL3NlY3RpZ28uY29tL0NQUzAIBgZngQwBAgEwVgYDVR0fBE8wTTBL
oEmgR4ZFaHR0cDovL2NybC5jb21vZG9jYTQuY29tL0NPTU9ET0VDQ0RvbWFpblZh
bGlkYXRpb25TZWN1cmVTZXJ2ZXJDQTIuY3JsMIGIBggrBgEFBQcBAQR8MHowUQYI
KwYBBQUHMAKGRWh0dHA6Ly9jcnQuY29tb2RvY2E0LmNvbS9DT01PRE9FQ0NEb21h
aW5WYWxpZGF0aW9uU2VjdXJlU2VydmVyQ0EyLmNydDAlBggrBgEFBQcwAYYZaHR0
cDovL29jc3AuY29tb2RvY2E0LmNvbTCCAQIGCisGAQQB1nkCBAIEgfMEgfAA7gB1
AAe3XBvlfWj/8bDGHSMVx7rmV3xXlLdq7rxhOhpp06IcAAABci8TnYIAAAQDAEYw
RAIgCYYujZHAhbAfFgfAoe5uPZu1o4y1CsP3gcJ1Al6DXoYCIESFxPvIlnZlbLUT
kzgF18/GHgoR3CrdKhMvPLf+4/GXAHUA5xLysDd+GmL7jskMYYTx6ns3y1YdESZb
8+DzS/JBVG4AAAFyLxOdsQAABAMARjBEAiARAo67jHvrN3WU+FWJWjYV2ckoBWeZ
ZCyVLR0EInHggwIgTURNZuUQy+PkK6KAFQ0tCVTN/gF/h6bodBqpc+n/FVwwPgYD
VR0RBDcwNYIbc3NsODkxNzM4LmNsb3VkZmxhcmVzc2wuY29tggsqLm5wbWpzLm9y
Z4IJbnBtanMub3JnMAoGCCqGSM49BAMCA0gAMEUCICkFeKYaIIioz9VmbzhK7WgD
VwwmUUmXvLqEqVYcOdYFAiEAyEiUrpxFk3WQrUSdcFh40OdwHhdeUt1pTUHls7Pl
ZBg=
-----END CERTIFICATE-----
```

Copy the content and paste it as a new entry at `certificates.certs[].pem`

### tasks

Create tasks to schedule the execution of maintenance steps that will be applied to all repositories or to specific repositories on a configurable schedule or simply perform other system maintenance.

```yaml
tasks:
  - name: Task name                          # required
    typeId: repository.yum.rebuild.metadata  # required: any of the available task ids
    alertEmail: mymail@company.com           # optional
    notificationCondition: FAILURE           # optional: FAILURE or SUCCESS_FAILURE (defaults: FAILURE)
    properties:                              # optional: it can be required if `typeId` needs additional conf keys (see below examples)

      ## if `typeId: repository.yum.rebuild.metadata` set this:
      # yumMetadataCaching: true             # optional (defaults: false)
      # repositoryName: yum-hosted           # required: yum hosted repository name

      ## if `typeId: repository.docker.gc` set this
      # repositoryName: "*"                  # required: a docker repo name or "*" for all docker repos

      ## if `typeId: repository.npm.reindex` set this:
      # repositoryName: npm-proxy            # required: a npm repo name or "*" for all npm repos

      ## if `typeId: repository.purge-unused` set this:
      # repositoryName: "*"                  # required: a repo name or "*" for all
      # lastUsed: 30                         # required

      ## if `typeId: repository.rebuild-index` set this:
      # repositoryName: "*"                  # required: a repo name or "*" for all

      ## if `typeId: repository.maven.purge-unused-snapshots` set this:
      # repositoryName: "*"                  # required: a maven repo name or "*" for all maven repos
      # lastUsed: 30                         # required

      ## if `typeId: repository.maven.remove-snapshots` set this:
      # repositoryName: maven-central        # required: a maven repo name or "*" for all maven repos
      # minimumRetained: 3                   # required
      # snapshotRetentionDays: 30            # required
      # gracePeriodInDays: 2                 # optional (defaults: 0)
      # removeIfReleased: true               # optional (defaults: false)

      ## if `typeId: repository.maven.publish-dotindex` set this:
      # repositoryName: maven-releases       # required: a maven repo name or "*" for all maven repos

      ## if `typeId: repository.maven.unpublish-dotindex` set this:
      # blobstoreName: default               # required
      # repositoryName: maven-releases       # required: a maven repo name or "*" for all maven repos

      ## if `typeId: repository.maven.rebuild-metadata` set this:
      # repositoryName: "*"                  # required: can be a maven repo name or "*" for all maven repos
      # rebuildChecksums: true               # optional (defaults: false)
      # groupId: com.company.dpto            # optional
      # artifactId: hello-service            # optional
      # baseVersion: 1.2.3                   # optional

      ## if `typeId: script` set this:
      # language: groovy                     # optional
      # source: |-                           # required
      #   log.info('hello world');
      #   return 'hello world'

      ## if `typeId: create.browse.nodes` set this:
      # repositoryName: "apt-proxy,helm-releases,maven-central"   # required: a list of hosted and proxy repositories or "*" for all

      ## if `typeId: blobstore.compact` set this:
      # blobstoreName: default               # required

      ## if `typeId: blobstore.rebuildComponentDB` set this:
      # dryRun: true                         # optional (defaults: false)
      # restoreBlobs: true                   # optional (defaults: true)
      # undeleteBlobs: true                  # optional (defaults: true)
      # integrityCheck: true                 # optional (defaults: true)
      # blobstoreName: local                 # required

      ## if `typeId: db.backup` set this:
      # location: /tmp/                      # required

    schedule:
      type: NOW                              # required: NOW, ONCE, HOURLY, DAILY, WEEKLY, CRON or MANUAL
      startDateTime: '2021-03-04T01:15:24'   # required: if `schedule.type` is ONCE, HOURLY, DAILY or WEEKLY. Valid format: "yyyy-MM-dd'T'HH:mm:ss"
      weeklyDays:                            # required: if `schedule.type: WEEKLY`
        - MON
        - TUE
        - WED
        - THU
        - FRI
        - SAT
        - SUN
      cron: 5 4 * * * ?                      # required: if `schedule.type: CRON`

  # A valid task looks like this
  - name: Delete SNAPSHOTS
    typeId: repository.maven.remove-snapshots
    enabled: true
    alertEmail: mymail@company.com
    notificationCondition: FAILURE
    properties:
      repositoryName: maven-central
      minimumRetained: 3
      snapshotRetentionDays: 30
      gracePeriodInDays: 2
      removeIfReleased: true
    schedule:
      type: weekly
      startDateTime: '2020-10-04T08:00:00'
      weeklyDays:
        - MON
        - WED
        - FRI
        - SUN
  
```

`typeId` and task-specific `properties` can be guessed either:

* from the java type hierarchy of `org.sonatype.nexus.scheduling.TaskDescriptorSupport`
* by inspecting the task creation html form in your browser
* from peeking at the browser AJAX requests while manually configuring a task

Also, you can see the type ids available at [Types of Tasks and When to Use Them](https://help.sonatype.com/repomanager3/system-configuration/tasks#Tasks-TypesofTasksandWhentoUseThem) from the Nexus official documentation.

Check out [examples/config/yaml/nexus.yaml](examples/config/yaml/nexus.yaml) for more examples.

#### `ldapConnections`

You can use the Lightweight Directory Access Protocol (LDAP) for authentication via external systems providing LDAP support such as Microsoft Exchange/Active Directory, OpenLDAP, ApacheDS and others.

```yaml
ldapConnections:
  - name: jumpcloud                     # required
    protocol: LDAPS                     # required: LDAP or LDAPS
    useTrustStore: true                 # optional (defaults: false)
    host: ldap.jumpcloud.com            # required
    port: 636                           # required: must be a valid port number
    searchBase: ou=Users,o=5f0ded99b3a9ef1305a22b22,dc=jumpcloud,dc=com   # required
    authScheme: SIMPLE                  # required: SIMPLE, DIGEST-MD5, CRAM-MD5 or NONE
    authRealm: example.com              # optional: if `authScheme` is DIGEST-MD5 or CRAM-MD5
    authUsername: ou=Users,o=5f0ded99b3a9ef1305a22b22,dc=jumpcloud,dc=com   # required if `authScheme` is not NONE
    authPassword: ldap1234              # required: if `authScheme` is not NONE
    connectionTimeoutSeconds: 15        # optional (defaults: 30)
    connectionRetryDelaySeconds: 100    # optional (defaults: 300)
    maxIncidentsCount: 2                # optional (defaults: 3)
    userBaseDn:                         # optional
    userSubtree: true                   # optional (defaults: false)
    userObjectClass: inetOrgPerson      # required
    userLdapFilter:                     # optional
    userIdAttribute: uid                # required
    userRealNameAttribute: cn           # required
    userEmailAddressAttribute: mail     # required
    userPasswordAttribute:              # optional
    ldapGroupsAsRoles: true             # optional (defaults: false)
    groupType: STATIC                   # required: if `ldapGroupsAsRoles` is true. It can be STATIC or DYNAMIC
    groupBaseDn:                        # optional: only set if `groupType` is STATIC
    groupSubtree: true                  # optional: only set if `groupType` is STATIC (defaults: false)
    groupObjectClass: groupOfNames      # optional: only set if `groupType` is STATIC
    groupIdAttribute: cn                # optional: only set if `groupType` is STATIC
    groupMemberAttribute: member        # optional: only set if `groupType` is STATIC
    groupMemberFormat: uid=${username},ou=Users,o=5f0ded99b3a9ef1305a22b22,dc=jumpcloud,dc=com   # optional: only set if `groupType` is STATIC
    userMemberOfAttribute: memberOf     # optional: only set if `groupType` is DYNAMIC
```

## Run the CLI

First, you have to install dependencies with [`pipenv`](https://github.com/pypa/pipenv):

```sh
pipenv install
```

After that, you have two ways to run the CLI:

```text
# Activate the shell to load the virtualenv
$ pipenv shell

# Run the CLI with:
$ ./nexus3casc.py
```

Or, you also can do this:

```text
# Use pipenv run to load the virtualenv and execute the CLI
$ pipenv run nexus3casc
```

If you run it without passing any argument, the help section will be displayed:

```text
$ pipenv run nexus3casc
Usage: nexus3casc.py [OPTIONS] COMMAND [ARGS]...

Options:
  --log-level [ERROR|INFO|DEBUG]  Set logging level  [env var:
                                  NEXUS3_CASC_LOG_LEVEL; default: INFO]

  --install-completion            Install completion for the current shell.
  --show-completion               Show completion for the current shell, to
                                  copy it or customize the installation.

  --help                          Show this message and exit.

Commands:
  from-k8s   Fetch config from either configmaps, secrets or both in a...
  from-path  Read config from YAML file(s) and inject it into a Nexus 3...
```

If you want to see the help text for a subcommand you can do:

```sh
pipenv run nexus3casc [COMAND] --help
```

For instance:

```text
$ pipenv run nexus3casc from-path --help
Usage: nexus3casc.py from-path [OPTIONS]

  Read config from YAML file(s) and inject it into a Nexus 3 instance
  server.

  CONFIG can be an absolute or relative path to a YAML file or a directory.
  If it is a directory every YAML file found in that path will be merged.

Options:
  --config PATH  Path or directory to load YAML file(s)  [env var:
                 NEXUS3_CASC_CONFIG_PATH; required]

  --help         Show this message and exit.
```

## Run the CLI as docker container

Nexus3CasC is published on [quay.io](https://quay.io/repository/vjda/nexus3casc-cli). You can use it as docker image executing the following command:

```sh
docker run -v /path/to/directory:/tmp/config quay.io/vjda/nexus3casc-cli:latest from-path --config /tmp/config/nexus.yaml
```

Or, you can set `NEXUS3_CASC_CONFIG_PATH` instead of passing `--config /tmp/config/nexus.yaml`

```sh
docker run -v /path/to/directory:/tmp/config -e NEXUS3_CASC_CONFIG_PATH=/tmp/config/nexus.yaml quay.io/vjda/nexus3casc-cli:latest from-path
```

> You must use `-v` to mount a volume so that the YAML file is available inside the docker container. Otherwise, it cannot access to it and the command will fail.

## Inject the configuration into Nexus 3 running on kubernetes

Another possibility is to execute the CLI from your local environment to inject the configuration into a Nexus 3 server deployed on a kubernetes cluster.

First, you create as many configmaps and/or secrets as you want to store the configuration in YAML format. This is how it looks like:

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: nexus-casc-secrets
  namespace: nexus
  labels:
    app: sonatype-nexus
    nexus3casc: active
type: Opaque
data:
  nexus-secrets.yaml: bmV4dXM6CiAgICBhZG1pblBhc3N3b3JkOiBteVN1cDNyUEBzc3cwcmQ=
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: sonatype-nexus-casc
  namespace: nexus
  labels:
    another-label: another-value
    nexus3casc: active
data:
  nexus.yml: |
    nexus:
      anonymousAccessEnabled: false
    realms:
      - name: Docker Bearer Token Realm
        enabled: true
      - name: npm Bearer Token Realm
        enabled: true
    blobStores:
    - name: local
      type: file
      path: local
  foo: bar
  greeting.txt: hello world
```

You have to put your configuration in keys ending with a valid YAML file extension (`.yaml` or `.yml`). In the above example only `nexus-secrets.yaml` and `nexus.yml` will be considered. Also, notice that the label `nexus3casc` exists in both manifests. You can put the label name (required) and the label value (optional) you want.

For instance, you can execute it as follows:

```sh
pipenv run nexus3casc from-k8s --namespace nexus --label nexus3casc --label-value active --resource both --local --watch --refresh-period 10
```

This is what each argument does:

* `--local`: searches for your `KUBECONFIG` location (by default at `~/.kube/config`) and gets the current k8s cluster connection
* `--resource both --namespace nexus`: find secrets and configmaps in namespace `nexus`
* `--label nexus3casc --label-value active`: filter secrets and configmaps which have the label `nexus3casc: active`
* `--watch`: watches for changes in those resources. If one of their yaml contents are updated, then it will configure Nexus again
* `--refresh-period 10`: searches for changes every 10 seconds

You can type `pipenv run nexus3casc --help` to get help.

### Use Nexus3CasC as sidecar on Kubernetes

One of the capabilities of this CLI is to be used as a container inside a kubernetes pod. This is useful if you want to create a sidecar injector for your Nexus 3 configuration and reload changes when the configuration changes.

You can do that by performing the following actions:

* Create a new `ServiceAccount` inside a Kubernetes namespace or use an existing one
* Create a new `Role` and `RoleBinding` to authorize the service account to get, watch and list configmaps and secrets for the same namespace.
* Apply a `Deployment` using the docker image, with the arguments to watch for changes either configmaps, secrets or both.

See [examples/kubernetes](examples/kubernetes) to see how to do it.

<!-- ## Development

TBD -->

<!-- ## Contributions

TBD -->

## Special thanks

This project has been inspired by many others. Big thanks to:

* <https://github.com/sonatype-nexus-community/nexus-scripting-examples>
* <https://github.com/ansible-ThoTeam/nexus3-oss>
* <https://github.com/cinhtau/sonatype-nexus-waffle>
* <https://github.com/cloudogu/nexus-claim>
* <https://github.com/rgl/nexus-vagrant>
* <https://github.com/samrocketman/nexus3-config-as-code>

You guys really rock! :clap: :metal:
