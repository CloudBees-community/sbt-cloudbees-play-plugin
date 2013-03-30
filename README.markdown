CloudBees Run@Cloud Play2 SBT Plugin
------------------------------

Integration for SBT that lets you deploy Play 2 apps to the CloudBees RUN@Cloud PaaS.

This plugin is a fork of Tim Perret's sbt-cloudbees-plugin with play2 specific additions added.

Usage
-----

Firstly you need to add the to project/plugins.sbt. You can do that with the following:

<pre><code>resolvers += "Sonatype snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/"

addSbtPlugin("com.cloudbees.deploy.play" % "sbt-cloudbees-play-plugin" % "0.5-SNAPSHOT")
</code></pre>

Next you need to add the following to project/Build.scala

<pre><code>import cloudbees.Plugin._
//...
//...
val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA) 
    .settings(cloudBeesSettings :_*)
    .settings(
  CloudBees.applicationId := Some("<applicationname>")
)
</code></pre>

And finally you need to add the following to ~/.bees/bees.config (or if on windows %USERPROFILE%\bees\bees.config).
This file will already exist if you use the CloudBees SDK

<pre><code>bees.api.secret=XXXXXXXXXXXXXX=
bees.api.url=https\://api.cloudbees.com/api
bees.api.key=XXXXXXXXXXXXXXX
bees.project.app.domain=<accountname>
</code></pre>
Now your all configured and good to go, there are two commands you can run with this plugin:

* Get a list of your configured applications: <code>cloudbees-applications</code>
* Deploy your application <code>cloudbees-deploy</code>
* Open the application in your default web browser: <code>cloudbees-open</code>

Release Notes
--------------

#### 0.4 - 2012/10/20

* Support for multiple application configuration files with `cloudbees-deploy-config`
* Auto-completion of configuration 
* Bug fix on default api url.

#### 0.3

* Forked from Tim Perret's general CloudBees Plugin to provide features for Playframework.
* Support for play 2.0.2+