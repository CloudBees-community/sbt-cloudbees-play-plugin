CloudBees Run@Cloud Play2 SBT Plugin
------------------------------

Integration for SBT that lets you deploy Play 2 apps to the CloudBees RUN@Cloud PaaS.

This plugin is a fork of Tim Perret's sbt-cloudbees-plugin with play2 specific additions added.

Usage
-----

Firstly you need to add the plugin to your ~/.sbt/user.sbt or to your regular project build.sbt. You can do that with the following:

<pre><code>resolvers += "sonatype snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

addSbtPlugin("com.cloudbees.deploy.play" %% "sbt-cloudbees-play-plugin" % "0.3-SNAPSHOT")
</code></pre>

Don't forget to export the settings so they are included by SBT:

<pre><code>seq(cloudBeesSettings :_*)</code></pre>

With those in place, the next thing you'll need to do is head over to grandcentral.cloudbees.com and pickup your API key and secret. These should look like: 

![Grand Central Keys](https://github.com/timperrett/sbt-cloudbees-plugin/raw/master/notes/img/beehive-keys.jpg)

Take these values and apply them in your user.sbt (or regular build file):

<pre><code>seq(cloudBeesSettings :_*)

CloudBees.apiKey := Some("FXXXXXXXXXXX")

CloudBees.apiSecret := Some("AAAAAAAAAAAAAAAAAAAA=")
</code></pre>

These of course are global settings per-machine, so the only application specific settings you need to define are the application and user in your project file. Alternativly, you could also define the username globally too:

<pre><code>CloudBees.username := Some("youruser")

CloudBees.applicationId := Some("yourapp")

// Optional: use to pass in properties to play. Can pass in a url for application.conf (see docs)
// Cloudbees.jvmProps := "-DapplyEvolutions=true -Dmykey=false"
</code></pre>

Now your all configured and good to go, there are two commands you can run with this plugin:

* Get a list of your configured applications: <code>cloudbees-applications</code>
* Deploy your application <code>cloudbees-deploy</code>
* Open the application in your default web browser: <code>cloudbees-open</code>
