package cloudbees

import sbt._
import Keys._
import com.cloudbees.api.{BeesClient,HashWriteProgress}
import edu.stanford.ejalbert.{BrowserLauncher => BL}
import scala.collection.JavaConversions._
import scala.collection.JavaConverters._  
import java.io.FileInputStream

object BrowserLauncher extends BL()

object Plugin extends Plugin {
  import CloudBees._

  case class Client(host: String, key: Option[String], secret: Option[String], verbose: Boolean){
    def apply() = {
      val k = require(key, apiKey)
      val s = require(secret, apiSecret)
      val client = new BeesClient(host, k, s, "xml", "1.0")
      client.setVerbose(verbose)
      client
    }
  }
  
  private case class DeployHelper(client: Client, user: Option[String], app: Option[String], delta: Boolean, open: Boolean, name: String, version: String, deployParams: Map[String, String], jvmProps: String)
  
  object CloudBees {
    // settings
    val host          = SettingKey[String]("cloudbees-host", "Host URL of the CloudBees API")
    val useDelta      = SettingKey[Boolean]("cloudbees-use-delta", "Deploy only a delta-archive to CloudBees (default: true)")
    val openOnUpload  = SettingKey[Boolean]("cloudbees-open-on-upload", "Open the application in your default web browser after upload (default: true)")
    val username      = SettingKey[Option[String]]("cloudbees-username", "Your CloudBees username")
    val apiKey        = SettingKey[Option[String]]("cloudbees-api-key", "Your CloudBees API key")
    val apiSecret     = SettingKey[Option[String]]("cloudbees-api-secrect", "Your CloudBees API secret")
    val applicationId = SettingKey[Option[String]]("cloudbees-application-id", "The application identifier of the deploying project")
    val client        = SettingKey[Client]("cloudbees-client")
    val deployParams  = SettingKey[Map[String, String]]("cloudbees-deploy-params", "Pass in extra options to the Cloudbees API client. Not normally need.")
    val jvmProps      = SettingKey[String]("cloudbees-jvm-properties", "Extra JVM properties to pass to the application. For example -DapplyEvolutions.default=true")
    val verbose       = SettingKey[Boolean]("cloudbees-client-verbose", "Set the Cloudbees API Client to verbose mode (default: false)")
    val beesConfig    = SettingKey[String]("cloudbees-bees-config")
    // tasks
    val applications  = TaskKey[Unit]("cloudbees-applications")
    val deploy        = TaskKey[Unit]("cloudbees-deploy")
    val open          = TaskKey[Unit]("cloudbees-open", "Open the application in your default web browser")
    
    private[Plugin] val deployHelper = SettingKey[DeployHelper]("_private_deploy")
    private[Plugin] val propsHelper = SettingKey[Map[String, String]]("_private_helper")
  }

  
  val cloudBeesSettings: Seq[Setting[_]] = Seq(
    host <<= propsHelper(_.get("bees.api.url").getOrElse("htps://api.cloudbees.com/api")),
    beesConfig := System.getProperty("user.home") + "/.bees/bees.config",
    useDelta := true,
    openOnUpload := true,
    username <<= propsHelper(_.get("bees.project.app.domain")),
    apiKey <<= propsHelper(_.get("bees.api.key")),
    apiSecret <<= propsHelper(_.get("bees.api.secret")),
    applicationId := None,
    client <<= (host, apiKey, apiSecret, verbose)(Client),
    deployParams := Map[String, String](),
    jvmProps := "",
    verbose := false,
    applications <<= applicationsTask,
    deploy <<= deployTask,
    open <<= openTask,
    deployHelper <<= (client, username, applicationId, useDelta, openOnUpload, normalizedName, version, deployParams, jvmProps)(DeployHelper),
    propsHelper <<= (beesConfig).apply(s => {
      val properties = new java.util.Properties
      val f = file(s)
      if(f.exists()){
        properties.load(new FileInputStream(f.asFile))
        propertiesAsScalaMap(properties).toMap
      } else {
        Map()
      }
    })
  ) 
  
 
  /***** tasks ******/
  def applicationsTask = (client, streams) map { (client,s) =>
    client().applicationList.getApplications.asScala.foreach(
      a => s.log.info("+ %s - %s".format(a.getTitle, a.getUrls.head)))
  }
  
  
  def deployTask = ((PlayProject.dist in Compile), deployHelper, streams) map {
    (archive, deployHelper, s) =>
      import deployHelper._
      
      if (archive.exists) {
        
        val to = targetAppId(require(user, username), require(app, applicationId))
        s.log.info("Deploying application '%s' to Run@Cloud".format(to))
        
        
        val parameters = Map(
        		"containerType" -> "java",
        		"runtime.class" -> "play.core.server.NettyServer",
        		"runtime.classpath" -> "%s-%s/lib/*".format(name, version),
        		"runtime.JAVA_OPTS" -> ("-Dhttp.port=$app_port " + deployHelper.jvmProps).trim, 
        		"runtime.args" -> "$app_dir/app/%s-%s/".format(name, version) 
        ) ++ deployParams
        
        val result = client().applicationDeployArchive(to, null, null, archive.asFile, null, "zip", true, parameters, new HashWriteProgress)
        
        s.log.info("Application avalible at %s".format(result.getUrl))
        
        if (open) {
          BrowserLauncher.openURLinBrowser(result.getUrl)
        }	
      } else sys.error("There was a problem locating the archive file for this project")
  }
  
 
  // can't figure out a way to do this without mapping
  def openTask = (username, applicationId) map { (user, app) => for {
      u <- user
      a <- app
    } BrowserLauncher.openURLinBrowser("http://" + a + "." + u + ".cloudbees.net")
  }

  /***** internal *****/
  private def targetAppId(username: String, appId: String) = appId.split("/").toList match {
    case a :: Nil => username+"/"+a
    case _ => appId
  }
  private def require[T](value: Option[String], setting: SettingKey[Option[String]]) =
    value.getOrElse {
      sys.error("%s setting is required".format(setting.key.label))
    }
}