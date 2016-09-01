package engineering.hansen.pianobot

import java.nio.file.{Paths, Files}
import java.io.{InputStream, PrintWriter}
import org.apache.logging.log4j.{Logger, LogManager}
import scala.io._

object Environment {
  private def printerr(x: String) = System.err.println(x)

  private def getHomedir() = {
    sys.props.get("user.home") match {
      case None => {
        printerr("user.home is not defined.  Aborting.")
        System.exit(-1)
        ""
      }
      case Some(x) => {
        Files.exists(Paths.get(x)) match {
          case false => {
          printerr("user.home does not exist. Aborting.")
            System.exit(-1)
            ""
          }
          case true => Files.isDirectory(Paths.get(x)) match {
            case false => {
              printerr("user.home is not a directory.  Aborting.")
              System.exit(-1)
              ""
            }
            case true => {
              x + java.io.File.separator
            }
          }
        }
      }
    }
  }
  
  val homedir = getHomedir()

  val appdir = homedir + ".pianobot" + java.io.File.separator

  private val appdirPath = Paths.get(appdir)
  Files.exists(appdirPath) match {
    case false => {
      try {
        Files.createDirectory(appdirPath)
      }
      catch {
        case _ : Throwable => {
          printerr("Could not create " + appdir + ".  Aborting.")
          System.exit(-1)
        }
      }
    }
    case true => Files.isDirectory(appdirPath) match {
      case true => ;
      case false => {
        printerr("Your ~/.pianobot is a file, not a directory.  Aborting.")
        System.exit(-1)
      }
    }
  }

  val conffile = appdir + "pianobot.conf"
  private val conffilePath = Paths.get(conffile)

  Files.exists(conffilePath) match {
    case false => {
      var istream : InputStream = null
      try {
        istream = getClass.getResourceAsStream("/pianobotconf.sample")
        Files.copy(istream, conffilePath)
      }
      catch {
        case _ : Throwable => {
          printerr("Error copying default pianobot configuration file.")
          printerr("Aborting.")
          System.exit(-1)
        }
      }
      finally {
        istream close
      }
    }
    case true => ;
  }

  val log4jfile = appdir + "log4j2.xml"
  val log4jfilePath = Paths.get(log4jfile)

  Files.exists(log4jfilePath) match {
    case false =>
      var istream : InputStream = null
      var pw : PrintWriter = null
      try {
        istream = getClass.getResourceAsStream("/log4jconf.sample")
        pw = new PrintWriter(log4jfile)
        for (i <- Source.fromInputStream(istream).getLines())
          pw.println(i.replace("LOG4JCONF_FILE", appdir + "pianobot.log"))
      }
      catch {
        case _ : Throwable => {
          printerr("Error copying default log4j configuration file.")
          printerr("Aborting.")
          System.exit(-1)
        }
      }
      finally {
        if (istream != null) istream.close()
        if (pw != null) pw.close()
      }

    case true => ;
  }

  sys.props.get("log4j.configurationFile") match {
    case None => sys.props("log4j.configurationFile") = log4jfile
    case Some(x) => ;
  }

  private val logger = LogManager.getLogger(getClass())
  logger.info("Successfully finished startup sequence.")

  def HW() = { System.err.println(homedir) }
}
