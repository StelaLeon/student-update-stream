import com.typesafe.config.ConfigFactory

/**
 * Created by sleon on 8/15/16.
 */
object ConfigurationService {
  /**
   * configuration factory to solve the scattered configuration and have only 1 entry point for getting the config
   */
  val config = ConfigFactory.load()

  def getHttpInterface: String ={
    config.getString("http.interface")
  }

  def getHttpPort: Int = {
    config.getInt("http.port")
  }

  def getSourceHostname:String={
    config.getString("endpoints.sources.hostname")
  }

  def getSourcePort:Int={
    config.getInt("endpoints.sources.port")
  }

  def getEntityEndpoint: String ={
    config.getString("endpoints.target")
  }

  def getTargetPathPrefix():String = {
    config.getString("endpoints.targets.path.prefix")
  }
}
