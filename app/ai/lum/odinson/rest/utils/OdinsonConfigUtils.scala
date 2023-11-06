package ai.lum.odinson.rest.utils

import ai.lum.common.ConfigFactory
import scala.collection.JavaConverters._
import com.typesafe.config.{ Config, ConfigRenderOptions, ConfigValueFactory }
import ai.lum.common.ConfigUtils._
import ai.lum.common.FileUtils._
import ai.lum.odinson.{ Document => OdinsonDocument, StringField => OdinsonStringField }
import ai.lum.odinson.utils.exceptions.OdinsonException
import com.typesafe.config.Config
import java.io.File

object OdinsonConfigUtils {

  /**
    * Replaces odinson.compiler.allTokenFields with env var's ODINSON_TOKEN_ATTRIBUTES (if set) 
    *
    * @param config
    */
  def injectTokenAttributes(config: Config): Config = {
    val ODINSON_TOKEN_ATTRIBUTES = "ODINSON_TOKEN_ATTRIBUTES"
    val TARGET_CONFIG_PROPERTY = "odinson.compiler.allTokenFields"
    //println(s"Looking for ${ODINSON_TOKEN_ATTRIBUTES}")
    //println(config.getValue(TARGET_CONFIG_PROPERTY))
    sys.env.get(ODINSON_TOKEN_ATTRIBUTES) match {
      case None => 
        println(s"${ODINSON_TOKEN_ATTRIBUTES} not set.  Using defaults")
        config
      case Some(envVar) =>
        val tokenAttributes = envVar.split(",").toList.asJava
        //println(s"overriding ${TARGET_CONFIG_PROPERTY} with ${ODINSON_TOKEN_ATTRIBUTES}=${envVar}")
        config.withValue(
          TARGET_CONFIG_PROPERTY,
          ConfigValueFactory.fromIterable(tokenAttributes)
        )
    }
  }
}
