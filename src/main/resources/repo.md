
### Install jar to local repo

```
mvn install:install-file -Dfile=~/jenkins/hashicorp-vault-plugin-2.2.1-NIBR.jar -DgroupId=com.datapipe.jenkins.plugins -DartifactId=hashicorp-vault-plugin -Dversion=2.2.1-NIBR -Dpackaging=jar -DlocalRepositoryPath=~/jenkins/hashicorp-vault-pipeline-plugin/src/main/resources/repo
```

### pom.xml

