package cloudbees

import sbt._
import Keys._
import com.cloudbees.api.{BeesClient,HashWriteProgress}
import edu.stanford.ejalbert.{BrowserLauncher => BL}
import scala.collection.JavaConversions._
import scala.collection.JavaConverters._  
import java.io.FileInputStream
import com.typesafe.config.ConfigFactory
import sbt.complete.Parser

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
    val apiSecret     = SettingKey[Option[String]]("cloudbees-api-secret", "Your CloudBees API secret")
    val applicationId = SettingKey[Option[String]]("cloudbees-application-id", "The application identifier of the deploying project")
    val client        = SettingKey[Client]("cloudbees-client")
    val deployParams  = SettingKey[Map[String, String]]("cloudbees-deploy-params", "Pass in extra options to the Cloudbees API client. Not normally need.")
    val jvmProps      = SettingKey[String]("cloudbees-jvm-properties", "Extra JVM properties to pass to the application. For example -DapplyEvolutions.default=true")
    val verbose       = SettingKey[Boolean]("cloudbees-client-verbose", "Set the Cloudbees API Client to verbose mode (default: false)")
    val beesConfig    = SettingKey[String]("cloudbees-bees-config")
    // tasks
    val applications  = TaskKey[Unit]("cloudbees-applications")
    val deploy        = TaskKey[Unit]("cloudbees-deploy")
    val deployConfig  = InputKey[Unit](
      "cloudbees-deploy-config", 
      "Deploy a configuration of your app to a Run@Cloud app id. Arguments are:\n" +
      "  (1) the base name of a conf file in your project's conf directory, defaulting to \"application\"/\n" + 
      "  (2) Optional. The application id to which this configuration should deploy. You can omit this\n" + 
      "      arg if you have either set cloudbees.applicationId in the config file from the first\n" +
      "      arg or have set the project ID in your PlayProject.\n\n" +
      "  Example usage: `> cloudbees-deploy-config live`, where live.conf exists in the project's conf/\n" + 
      "  directory and contains a key cloudbees.applicationId."
      )
    val open          = TaskKey[Unit]("cloudbees-open", "Open the application in your default web browser")
    
    private[Plugin] val deployHelper = SettingKey[DeployHelper]("_private_deploy")
    private[Plugin] val propsHelper = SettingKey[Map[String, String]]("_private_helper")
    private[Plugin] val playConfigFilesHelper = SettingKey[Seq[File]]("_private_play_config_files")
  }  

  val cloudBeesSettings: Seq[Setting[_]] = Seq(
    host <<= propsHelper(_.get("bees.api.url").getOrElse("https://api.cloudbees.com/api")),
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
    deployConfig <<= deployConfigTask,
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
    }),
    playConfigFilesHelper <<= (PlayKeys.confDirectory)(conf => conf * ("*.conf" | "*.json" | "*.properties") get)
  )

  /***** tasks ******/
  def applicationsTask = (client, streams) map { (client,s) =>
    client().applicationList.getApplications.asScala.foreach(
      a => s.log.info("+ %s - %s".format(a.getTitle, a.getUrls.head)))
  }
  
  def deployTask = ((PlayProject.dist in Compile), deployHelper, streams) map {
    (archive, deployHelper, s) =>
      import deployHelper.app
      
      performDeploy(archive, require(app, applicationId), deployHelper, s)
  }

  def deployConfigTask = InputTask(playConfigsParser) { (argTask: TaskKey[(Option[String], Option[String])]) =>
    (argTask, (PlayProject.dist in Compile), PlayKeys.confDirectory, name, deployHelper, streams) map { 
      (args, archive, configFileDir, projName, deployHelper, s) =>
        import deployHelper.{user => maybeBeesAccount, app => maybeSbtBeesAppId, jvmProps}

        // Snag the active configuration file from the first cli argument, and the target Run@Cloud deployment
        // app id either (1) in the second argument, (2) from the file specified in arg 1, and
        // finally (3) from the sbt setting.        
        val configFileResource = args._1.getOrElse("application")

        // Get the app id from the config file (though hey it might not be there)        
        val configFileResourcePath = configFileDir / configFileResource
        val playConfig = ConfigFactory.parseFileAnySyntax(configFileResourcePath)
        if (playConfig.isEmpty) sys.error(
          "No file named " + configFileResource + 
          ".conf, .json, or .properties was found in " + configFileDir
        )
        
        val beesAccount = require(maybeBeesAccount, username)
        
        val maybeConfigBeesAppId = if (playConfig.hasPath(appIdConfigKey)) {
          Some(playConfig.getString(appIdConfigKey)) 
        } else {
          None
        }
 
        // Choose target app based on presence in one of our three sources
        val beesAppId = args._2
          .orElse(maybeConfigBeesAppId)
          .orElse(maybeSbtBeesAppId)
          .getOrElse(sys.error(noDeploymentTargetError))

        // Deploy
        val configResourceWithExtension = file(playConfig.origin.filename).name
        val newDeployHelper = deployHelper.copy(
          app=Some(beesAppId),
          jvmProps = "-Dconfig.resource=" + configResourceWithExtension + " " + jvmProps
        )
        
        performDeploy(archive, beesAppId, newDeployHelper, s, configName=Some(configFileResource))        
    }
  }
 
  // can't figure out a way to do this without mapping
  def openTask = (username, applicationId) map { (user, app) => for {
      u <- user
      a <- app
    } BrowserLauncher.openURLinBrowser("http://" + a + "." + u + ".cloudbees.net")
  }

  /***** internal *****/

  /** Encodes the following grammar: cloudbees-deploy-config Option[<config_file_base>] Option[<bees_app_id>] */
  private def playConfigsParser = {
    (playConfigFilesHelper) { configFiles =>
      (state: State) => {
        import sbt.complete.DefaultParsers._
        // Extract the configuration filenames sans extension from their Files.
        val configNames = configFiles.map(_.name.reverse.dropWhile(_ != '.').tail.reverse)

        // Sweet sweet tab-complete
        val configParser = Space ~> token(NotSpace examples configNames.toSet)
        val appIdParser = Space ~> NotSpace
        
        configParser.? ~ appIdParser.?
      }
    }
  }

  private def targetAppId(username: String, appId: String) = appId.split("/").toList match {
    case a :: Nil => username+"/"+a
    case _ => appId
  }
  private def require[T](value: Option[String], setting: SettingKey[Option[String]]) =
    value.getOrElse {
      sys.error("%s setting is required".format(setting.key.label))
    }
  private val appIdConfigKey = "cloudbees.applicationId"
  private val noDeploymentTargetError = "No application ID found.\n" + 
    "Please specify a Run@Cloud application ID in at least one of the following\n" + 
    "places (ordered by precedence): \n" + 
    "  * As the second argument to this command \n" + 
    "  * in the specified configuration file (\"cloudbees.applicationId\")\n" + 
    "  * directly on the project with the sbt setting \"applicationId\""

  private def performDeploy(    
    archive: File,
    appId: String,
    deployHelper: DeployHelper,
    s: TaskStreams,
    configName: Option[String]=None    
  ) {
    import deployHelper.{user, name, version, client, deployParams, jvmProps, open}

    if (archive.exists) {

      val targetApp = targetAppId(require(user, username), appId)
      
      val parameters = Map(
        "containerType" -> "java",
        "runtime.class" -> "play.core.server.NettyServer",
        "runtime.classpath" -> "%s-%s/lib/*".format(name, version),
        "runtime.JAVA_OPTS" -> ("-Dhttp.port=$app_port " + jvmProps).trim, 
        "runtime.args" -> "$app_dir/app/%s-%s/".format(name, version) 
      ) ++ deployParams      
      
      val appDescription = name + "-" + version + configName.map(cfg => "[config=" + cfg + "]").getOrElse("")
      
      s.log.info("Deploying %s to Run@Cloud/%s".format(appDescription, targetApp))
      
      val result = client().applicationDeployArchive(
        targetApp, 
        null, // environment
        appDescription,
        archive.asFile,
        null, // source file
        "zip",
        true,  // delta deploy
        parameters,
        new HashWriteProgress
      )
      
      s.log.info("Application available at %s".format(result.getUrl))
      
      if (open) {
        BrowserLauncher.openURLinBrowser(result.getUrl)
      }
    } else {
      sys.error("There was a problem locating the archive file for this project")
    }
  }
}
