package com.secdec.codepulse

import org.slf4j.LoggerFactory
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.Level

object CodePulseLogging {

  private def setLoggerLevel(packageName: String, level: Option[Level]): Unit = {
    if (level == None) return
    val logger = LoggerFactory.getLogger(packageName).asInstanceOf[Logger]
    logger.setLevel(level.get)
  }

  def init(): Unit = {
    setLoggerLevel("com.secdec", userSettings.secdecLoggingLevel)
    setLoggerLevel("com.codedx", userSettings.codedxLoggingLevel)
    setLoggerLevel("bootstrap", userSettings.bootstrapLoggingLevel)
    setLoggerLevel("net.liftweb", userSettings.liftwebLoggingLevel)
    setLoggerLevel(org.slf4j.Logger.ROOT_LOGGER_NAME, userSettings.rootLoggingLevel)
  }
}
