# Hacking with Grails Issue 12460

When upgrading to Grails 5.1.6 with Spring 5.3.18, there was a error intruduced, it may be related with `groovyPagesTemplateEngine` in the `grails-gsp` plugin.
Because of [Spring Framework RCE](https://spring.io/blog/2022/03/31/spring-framework-rce-early-announcement), many Grails and Spring apps are impacted. [This demo](https://github.com/rainboyan/grails-issue-12460-demo) report the error, and give a workaround to solve the problem.

```
Caused by: org.springframework.beans.factory.BeanCreationException: Error creating bean with name 'groovyPagesTemplateEngine': Error setting property values; nested exception is org.springframework.beans.NotWritablePropertyException: Invalid property 'classLoader' of bean class [org.grails.gsp.GroovyPagesTemplateEngine]: Bean property 'classLoader' is not writable or has an invalid setter method. Does the parameter type of the setter match the return type of the getter?
```

## How to Fix it, we need a Workaround

I read a lot the `grails-gsp` source code, found that `groovyPagesTemplateEngine` need a `GroovyPageClassLoader` to compile gsp, the `ref("classLoader")` is the parent class loader, so I think it may be the wrong with Spring Framework 5.3.18. Because `groovyPagesTemplateEngine` implements `BeanClassLoaderAware`, so there is no need to set classLoader property declaratively, spring will do this via the `setBeanClassLoader`. I've already submit a [PR](https://github.com/grails/grails-gsp/pull/257) for `grails-gsp`.

```
    // Setup the main templateEngine used to render GSPs
    groovyPagesTemplateEngine(GroovyPagesTemplateEngine) { bean ->
        classLoader = ref("classLoader")
        groovyPageLocator = groovyPageLocator
        if (enableReload) {
            reloadEnabled = enableReload
        }
        tagLibraryLookup = gspTagLibraryLookup
        jspTagLibraryResolver = jspTagLibraryResolver
        cacheResources = enableCacheResources
    }
```

Before `grails-gsp` new version to fix the issue, there is a workaround to do, we could add the `GrailsIssue12460PostProcessor` to remove `classLoader` from the propertyValues of bean `groovyPagesTemplateEngine` at the runtime,

```
class GrailsIssue12460PostProcessor implements MergedBeanDefinitionPostProcessor {
    @Override
    void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, Class<?> beanType, String beanName) {
        if (beanName == 'groovyPagesTemplateEngine') {
            
            // Grails issue : https://github.com/grails/grails-core/issues/12460'
            
            beanDefinition.getPropertyValues().removePropertyValue("classLoader")
        }
    }
}
```

## Upgrade Tomcat to 9.0.62

Since Apache Tomcat 9.0.62 was released yet, and it's also fixed the class loader issue, so we should update it.  

```
Effectively disable the WebappClassLoaderBase.getResources() method as it is not used and if something accidently exposes the class loader this method can be used to gain access to Tomcat internals.
```

In `gradle.properties`, add `tomcat.version`:

```
tomcat.version=9.0.62
```

Then check the tomcat version, `./gradlew dependencies --configuration runtimeClasspath | grep org.apache.tomcat`

```
|    +--- org.apache.tomcat.embed:tomcat-embed-el:9.0.58 -> 9.0.62
|    |    |    |    |    \--- org.apache.tomcat:tomcat-jdbc:10.0.8 -> 9.0.62 (c)
|    +--- org.apache.tomcat.embed:tomcat-embed-core:9.0.58 -> 9.0.62
|    +--- org.apache.tomcat.embed:tomcat-embed-el:9.0.58 -> 9.0.62
|    \--- org.apache.tomcat.embed:tomcat-embed-websocket:9.0.58 -> 9.0.62
|         \--- org.apache.tomcat.embed:tomcat-embed-core:9.0.62
|    |    \--- org.apache.tomcat.embed:tomcat-embed-logging-log4j:8.5.2
+--- org.apache.tomcat:tomcat-jdbc -> 9.0.62
|    \--- org.apache.tomcat:tomcat-juli:9.0.62
```

## What about Grails 4?

Unfortunately, Grails 4 since last release 4.0.13 still use Spring Boot 2.1.x and Spring Framework 5.1.x, which are all [End of Support](https://spring.io/projects/spring-boot#support), so I created a [Grails 4 demo](https://github.com/rainboyan/grails4-upgrade-spring-demo) to show you how to Upgrade Spring to 5.3.18. I hope this demo will help you, but you should **Notice** that, Spring Framework 5.3.x changed lot and may not work with old Grails 4 plugins, please give a hard test and make sure it works.

## Links
- [Spring Framework RCE](https://spring.io/blog/2022/03/31/spring-framework-rce-early-announcement)
- [Spring Framework RCE, Mitigation Alternative](https://spring.io/blog/2022/04/01/spring-framework-rce-mitigation-alternative)
- [Spring 5.3.18 issue#28261, Restrict access to property paths on Class references](https://github.com/spring-projects/spring-framework/issues/28261)
- [Apache Tomcat 9.0.62 released](https://tomcat.apache.org/tomcat-9.0-doc/changelog.html#Tomcat_9.0.62_(remm))
- [Grails 5.1.6: integration tests, bootRun, bootWar are broken](https://github.com/grails/grails-core/issues/12460)
- [Grails GSP - Fixed Grails issue #12460](https://github.com/grails/grails-gsp/pull/257)
- [Grails 4 upgrade Spring 5.3.18 demo](https://github.com/rainboyan/grails4-upgrade-spring-demo)
- [Will Dormann twitter status](https://twitter.com/wdormann/status/1509372145394200579)