# Hacking with Grails Issue 12460

When upgrading to Grails 5.1.6 with Spring 5.3.18, there was a error intruduced, it may be related with `groovyPagesTemplateEngine` in the `grails-gsp` plugin.
Because of [Spring Framework RCE](https://spring.io/blog/2022/03/31/spring-framework-rce-early-announcement), many Grails and Spring apps are impacted. [This demo](https://github.com/rainboyan/grails-issue-12460-demo) report the error, and give a workaround to solve the problem.

```
Caused by: org.springframework.beans.factory.BeanCreationException: Error creating bean with name 'groovyPagesTemplateEngine': Error setting property values; nested exception is org.springframework.beans.NotWritablePropertyException: Invalid property 'classLoader' of bean class [org.grails.gsp.GroovyPagesTemplateEngine]: Bean property 'classLoader' is not writable or has an invalid setter method. Does the parameter type of the setter match the return type of the getter?
```

`groovyPagesTemplateEngine` need a `GroovyPageClassLoader`, not `ref("classLoader")`, so I think it may be wrong, we can remove the line or set it **null**.

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

The workaround is, add the `GrailsIssue12460PostProcessor` to remove `classLoader` from the propertyValues of bean `groovyPagesTemplateEngine`,

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

## Links
- [Spring Framework RCE](https://spring.io/blog/2022/03/31/spring-framework-rce-early-announcement)
- [Grails 5.1.6: integration tests, bootRun, bootWar are broken](https://github.com/grails/grails-core/issues/12460)
