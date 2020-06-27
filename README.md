# Nexus3CasC: Configuration As Code for Nexus 3

CLI to inject the configuration stored in YAML(s) into [Sonatype Nexus repository manager 3](https://help.sonatype.com/repomanager3).

## What it is for

Nexus3CasC provides you a way to configure a Nexus 3 server to perform the following operations:

* Changing admin password
* Enabling or disabling...
  * anonymous access
  * realms
* Setting HTTP(s) proxies
* Setting a SMTP connection
* Creating or updating...
  * blob stores to store objects locally or in AWS S3
  * content selectors
  * cleanup policies
  * routing rules
  * repositories for all formats (_maven2, npm, docker_, etc) and types (_hosted, remote_ and _group_)
  * custom internal users
  * custom roles
  * custom privileges
  * LDAP connections
* Deleting any unknown configuration for items like...
  * blob stores
  * content selectors
  * clean up policies
  * routing rules
  * repositories
  * LDAP connections (not available yet but in a near future :sunglasses: )
  * local users (_anonymous_ and _admin_ users will be ignored)
  * custom roles (built-in roles will be ignored)
  * custom privileges (built-in privileges will be ignored)

## How it works

Nexus 3 provides a powerful [scripting API](https://help.sonatype.com/repomanager3/rest-and-integration-api/script-api) to simplify provisioning and executing other complex tasks. These scripts are written in Groovy language.

Nexus3CasC takes advantage of this capability to inject some scripts in Nexus 3 using its API rest. It also reads the configuration from YAML files, connect to the Nexus 3 server and uses its API to invoke every script sending the configuration values as input arguments.

The CLI is written in python 3.8 and the scripts for Nexus 3 are written in Groovy.

### Configuration items

As said before, you can use YAML files to define your custom configuration. You can place this YAML where you want and pass it as command argument to Nexus3CasC CLI.

#### Default values

There is a YAML with default values at [config/nexus_defaults.yaml](config/nexus_defaults.yaml). It defines a set of keys that are used in case of they are not defined in your YAML file.

```yaml
# config/nexus_defaults.yaml

nexus:
  baseUrl: http://localhost:8081 # URL to reach Nexus 3 UI
  anonymousAccessEnabled: false  # If anonymous users can access to Nexus 3
  defaults:
    adminUser: admin             # Default admin user
    adminPassword: admin123      # Default admin password
  deleteUnknownItems:            # To delete existing items in Nexus but not in YAML
    blobstores: false
    contentSelectors: false
    cleanupPolicies: false
    routingRules: false
    repos: false
    ldapConnections: false
    customLocalUsers: false
    customRoles: false
    customPrivileges: false

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

# Create new local and LDAP users
customUsers:
  local: []
  # ldap: [] # Not yet available

# Create new custom roles
customRoles: []

# Create new custom privileges
customPrivileges: []
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

Security realms are used for authentication and authorization of different types of users. You can enable or disable as follows:

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

The list of realms could be different depending of the Nexus 3 version you use. If you include a non-existent realm it will be ignored.

> Be carefull disabling some realms such as _Local Authenticating Realm_ and _Local Authorizing Realm_. If one of them is disabled, you will not be able to login as an internal user!

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
      username: proxyuser       # required: if authentication is set
      password: pr0xyp@ss       # required: if authentication is set
      ntlmHost: ntlm.acme.com   # optional
      ntlmDomain: acme.com      # optional
  httpsProxy:
    host: proxy.acme.com
    port: 8443
    authentication:
      username: proxyuser       # required: if authentication is set
      password: pr0xyp@ss       # required: if authentication is set
      ntlmHost: ntlm.acme.com   # optional
      ntlmDomain: acme.com      # optional
```

#### `blobStores`

A blob store is a location where to store the _blob objects_ for binary assets.

```yaml
blobStores:
- name: default             # required
  type: file                # optional (defaults: file)
  path: default             # optional: path can be either a relative or absolute path (defaults: `name` value)
- name: local
- name: remote
  type: file
  path: /tmp/custom/remote
  type: s3                                                      # Use `type: s3` to store blobs in AWS S3 bucket
  config:                                                       # required if`type: s3`
    bucket: s3-blobstore                                        # required if`type: s3`
    accessKeyId: AKIAIOSFODNN7EXAMPLE                           # required if`type: s3`
    secretAccessKey: wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY   # required if`type: s3`
```

#### `cleanupPolicies`

The cleanup feature is a way of removing components from your repositories using policies customized to suit your needs.

```yaml
cleanupPolicies:
- name: purge-snapshots                               # required
  format: maven2                                      # required
  notes: "Delete maven snapshots downloaded > 7 days" # optional
  mode: delete                                        # optional (defaults: delete)
  criteria:                                           # required
    lastBlobUpdated: 30                               # optional: must be a number > 0
    lastDownloaded: 7                                 # optional: must be a number > 0
    preRelease: PRERELEASES                           # optional: RELEASES or PRERELEASES
    regexKey: (org|com)/.*                            # optional: must be a regex pattern
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
- name: helm-all-selector                       # required
  format: csel                                  # optional (defaults: csel)
  description: Search for all helm artifacts    # optional
  expression: format == "helm"                  # required
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
- name: helm-releases                                 # required
  format: helm                                        # required: any type which supports hosted repositories
  type: hosted                                        # required: must be "hosted"
  online: true                                        # optional (defaults: false)
  storage:                                            # required
    blobStoreName: local                              # required
    strictContentTypeValidation: true                 # required: true or false
    writePolicy: ALLOW_ONCE                           # required: ALLOW, ALLOW_ONCE or DENY
  cleanup:                                            # optional
    policyNames:
    - remove-old-helm-artifacts
  apt:                                                # required: if `format: apt`
    distribution: bionic                              # required
  aptSigning:                                         # required: if `format: apt`
    keypair: 515F58C16D58E682E91ACEFF17B5C97F9A816AD7 # required
    passphrase: keep my account safe                  # required
  docker:                                             # required: if `format: docker`
    v1Enabled: false                                  # optional (defaults: false)
    forceBasicAuth: true                              # optional (defaults: false)
    httpPort: 5001                                    # optional
    httpsPort: 5000                                   # optional
  maven:                                              # required: if `format: maven2`
    versionPolicy: SNAPSHOT                           # required: RELEASE, SNAPSHOT or MIXED
    layoutPolicy: PERMISSIVE                          # required: STRICT or PERMISSIVE
  yum:                                                # required: if `format: yum`
    repodataDepth: 5                                  # required
    deployPolicy: STRICT                              # required

# Remote repositories
- name: yum-proxy                               # required
  format: yum                                   # required: any type which supports proxy repositories
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
      retries:                                  # optional
      userAgentSuffix:                          # optional
      timeout: 30                               # optional
      enableCircularRedirects: false            # optional (defaults: false)
      enableCookies: false                      # optional (defaults: false)
      useTrustStore: true                       # optional (defaults: false)
    authentication:                             # optional (defaults: {})
      username: proxyuser                       # required: if authentication is set
      password: pr0xyp@ss                       # required: if authentication is set
      ntlmHost: ntlm.acme.com                   # optional
      ntlmDomain: acme.com                      # optional
  bower:                                        # required: if `format: bower`
    rewritePackageUrls: true                    # required
  nugetProxy:                                   # required: if `format: nuget`
    queryCacheItemMaxAge: 1440                  # required
  docker:                                       # required: if `format: docker`
    v1Enabled: false                            # optional (defaults: false)
    forceBasicAuth: true                        # optional (defaults: false)
    httpPort: 5001                              # optional
    httpsPort: 5000                             # optional
  dockerProxy:                                  # required: if `format: docker`
    indexType: CUSTOM                           # required: HUB, REGISTRY or CUSTOM
    indexUrl: https://index.docker.io/          # required: if `dockerProxy.indextype: CUSTOM`
    foreignLayerUrlWhitelist:                   # optional: must be a regex
    - https?://go.microsoft.com/.*
    - https?://.*\.azurecr\.io/.*

# Group repositories
- name: npm-group                      # required
  format: npm                          # required: any type which supports group repositories
  type: group                          # required: must be "group"
  online: true                         # optional (defaults: false)
  storage:                             # required
    blobStoreName: group               # required
    strictContentTypeValidation: true  # required: true or false
  group:                               # required
    memberNames:                       # required: at least 1 repository for the same type
    - npm-hosted
    - npm-proxy
  docker:                              # required: if `type: docker`
    v1Enabled: false                   # optional (defaults: false)
    forceBasicAuth: true               # optional (defaults: false)
    httpPort: 5001                     # optional: must be a valid port
    httpsPort: 5000                    # optional: must be a valid port
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

#### `ldapConnections`

Not available yet.

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
    status: disabled                  # optional: active, locked, disabled or changepassword (defaults: disabled)
    roles:                            # required
    - my-admin-role
  - userId: anonymous
    password:
    firstName: Anonymous
    lastName: User
    emailAddress: anonymous@example.org
    status: active
    roles:
    - nx-anonymous
  - userId: admin
    firstName: Administrator
    lastName: User
    emailAddress: admin@example.org
    status: active
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

Privileges control access to specific functionality of the repository manager and can be grouped as a role and assigned to a specific users.

```yaml
customPrivileges:

# These are privileges that use patterns to group other privileges
- type: wildcard                              # required: wildcard, application, repository-admin, repository-view, repository-content-selector or script
  name: my-wildcard-all                       # required
  description: All permissions                # optional
  pattern: "nexus:*"                          # required: if `type: wildcard`

# These are privileges related to a specific domain in the repository manager
- type: application
  name: my-app-analytics-all
  description: All permissions for Analytics
  actions:                                    # required: if not `type: wildcard`
  - ALL                                       # required: ADD, BROWSE, CREATE, DELETE, EDIT, READ, UPDATE OR ALL
  domain: analytics                           # required

# These are privileges related to the administration and configuration of a specific repository
- type: repository-admin
  name: my-maven-repository-admin
  description: All privileges for all maven repositories
  actions:
  - ALL
  format: maven2                              # required: if `type: repository-admin`
  repository: "*"                             # required: can be any repository name or "*" for all repositories

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
  repository: "*-raw"                         # required: *-raw selects all repositories names which end with -raw
  contentSelector: raw-selector               # required

# These are privileges related to the execution and management of scripts
- type: script
  name: my-script-*-add
  description: Add permission for Scripts
  actions:
  - ADD
  - READ
  scriptName: "*"                             # required: can be a script name or "*" for all scripts
```

## Run the CLI

First, you have to install dependencies with [`pipenv`](https://github.com/pypa/pipenv):

```sh
pipenv install
```

After that, you can run the CLI with:

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

Nexus3CasC is published on [Dockerhub](https://hub.docker.com/r/vjda/nexus3casc-cli). You can use it as docker image executing the following command:

```sh
docker run -v /path/to/directory:/tmp/config vjda/nexus3casc-cli:0.1.0 from-path --config /tmp/config/nexus.yaml
```

Or, you can set `NEXUS3_CASC_CONFIG_PATH` instead of passing `--config /tmp/config/nexus.yaml`

```sh
docker run -v /path/to/directory:/tmp/config -e NEXUS3_CASC_CONFIG_PATH=/tmp/config/nexus.yaml vjda/nexus3casc-cli:0.1.0 from-path
```

> You must use `-v` to mount a volume so that the YAML file is available inside the docker container. Otherwise, it cannot access to it and the command will fail.

## Inject the configuration into Nexus 3 running on kubernetes

Another possibility is to execute the CLI from your local environment to inject the configuration into a Nexus 3 server deployed on a kubernetes cluster.

First, you create as many configmaps and/or secrets as you want to store the configuration in YAML format. This is how it looks like:

```yaml
apiVersion: v1
kind: Secret
data:
  nexus-secrets.yaml: bmV4dXM6CiAgICBhZG1pblBhc3N3b3JkOiBteVN1cDNyUEBzc3cwcmQ=
metadata:
  labels:
    app: sonatype-nexus
    nexus3casc: active
  name: nexus-casc-secrets
  namespace: nexus
type: Opaque
---
apiVersion: v1
kind: ConfigMap
metadata:
  labels:
    nexus3casc: active
    another-label: another-value
  name: sonatype-nexus-casc
  namespace: nexus
data:
  nexus.yaml: |
    nexus:
      anonymousAccessEnabled: false
    realms:
      - name: Docker Bearer Token Realm
        enabled: true
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

You have to put your configuration in keys ending with a valid YAML file extension (`.yaml` or `.yml`). In the above example only `nexus-secrets.yaml` and `nexus.yaml` will be consider. Also, notice that the label `nexus3casc` exists in both manifests. You can put the label name (required) and the label value (optional) you want.

For instance, you can execute it as follows:

```sh
pipenv run nexus3casc from-k8s --namespace nexus --label nexus3casc --label-value active --resource both --local --watch --refresh-period 10
```

This is what each argument does:

* `--local`: search for your `KUBECONFIG` location (by default at `~/.kube/config`) and get the current k8s cluster connection
* `--resource both --namespace nexus`: find secrets and configmaps in namespace `nexus`
* `--label nexus3casc --label-value active`: filter secrets and configmpas which have the label `nexus3casc: active`
* `--watch`: watch for changes in those resources. If one of their yaml contents are updated, then it will configure Nexus again
* `--refresh-period 10`: search for changes every 10 seconds

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
