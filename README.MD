# ADB Deployer

Naive APK deployer that watch on shared directory changes for new APK and deploy it over emulators
or devices running/connected in the same PCs this lib is running on.
 
Usage
----

``` java

    gradle build
    
    java -jar cifs-apk-deployer <shared-dir> <android-file> <package-name>
    
```

and then in `android-sample` project run

``` gradle
 
gradle parallelDeploy

```






