/*
 * Copyright 2018 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package htsstride.driver

import java.net.URL
import java.util.concurrent.TimeUnit

import cats.instances.string._
import cats.syntax.either._
import cats.syntax.eq._
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.{ChromeDriver, ChromeOptions}
import org.openqa.selenium.firefox.{FirefoxDriver, FirefoxOptions}
import org.openqa.selenium.remote.{DesiredCapabilities, RemoteWebDriver}
import htsstride.utils.Configuration.environment
import htsstride.utils.Environment.Local

import scala.io.Source

object Driver extends Driver

class Driver {

  private val systemProperties = System.getProperties

  def newWebDriver(): Either[String, WebDriver] = {
    val selectedDriver: Either[String, WebDriver] = Option(systemProperties.getProperty("marcusbrowser")).map(_.toLowerCase) match {
      case Some("firefox") ⇒ Right(new FirefoxDriver())
      case Some("chrome") ⇒
        environment match {
          case Local ⇒
            Right(createChromeDriver(false))
          case _ ⇒ Right(new ChromeDriver())
        }
      case Some("remote-chrome")  ⇒ Right(createRemoteChrome)
      case Some("remote-firefox") ⇒ Right(createRemoteFirefox)
      case Some("browserstack")   ⇒ Right(createBrowserStackDriver)
      case Some(other)            ⇒ Left(s"Unrecognised browser: $other")
      case None                   ⇒ Left("No browser set")
    }

    selectedDriver.foreach { driver ⇒
      val (_, _) = (sys.addShutdownHook(driver.quit()),
        driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS)
      )
    }
    selectedDriver
  }

  def createRemoteChrome: WebDriver = {
    new RemoteWebDriver(new URL(s"http://localhost:4444/wd/hub"), new ChromeOptions())
  }

  def createRemoteFirefox: WebDriver = {
    new RemoteWebDriver(new URL(s"http://localhost:4444/wd/hub"), new FirefoxOptions())
  }

  private val os: String =
    Option(systemProperties.getProperty("os.name")).getOrElse(sys.error("Could not read OS name"))

  private val isMac: Boolean = os.startsWith("Mac")

  private val isLinux: Boolean = os.startsWith("Linux")

  private val linuxArch: String =
    Option(systemProperties.getProperty("os.arch")).getOrElse(sys.error("Could not read OS arch"))

  private val isJsEnabled: Boolean = true

  private val driverDirectory: String = Option(systemProperties.getProperty("drivers")).getOrElse("/usr/local/bin")

  private def setChromeDriver() = {
    if (Option(systemProperties.getProperty("webdriver.driver")).isEmpty) {
      if (isMac) {
        systemProperties.setProperty("webdriver.driver", driverDirectory + "/chromedriver_mac")
      } else if (isLinux && linuxArch === "amd32") {
        systemProperties.setProperty("webdriver.driver", driverDirectory + "/chromedriver_linux32")
      } else if (isLinux) {
        systemProperties.setProperty("webdriver.driver", driverDirectory + "/chromedriver")
      } else {
        systemProperties.setProperty("webdriver.driver", driverDirectory + "/chromedriver.exe")
      }
    }
  }

  private def createChromeDriver(headless: Boolean): WebDriver = {
    setChromeDriver()

    val capabilities = DesiredCapabilities.chrome()
    val options = new ChromeOptions()
    options.addArguments("test-type")
    options.addArguments("--disable-gpu")
    if (headless) options.addArguments("--headless")
    capabilities.setJavascriptEnabled(isJsEnabled)
    capabilities.setCapability(ChromeOptions.CAPABILITY, options)
    new ChromeDriver(capabilities)
  }

  private def createBrowserStackDriver: WebDriver = {
    import scala.collection.JavaConverters._

    val bsCaps = getBrowserStackCapabilities
    val desiredCaps = new DesiredCapabilities(bsCaps.asJava)
    desiredCaps.setCapability("browserstack.debug", "true")
    desiredCaps.setCapability("browserstack.local", "true")
    desiredCaps.setCapability("acceptSslCerts", "true")
    desiredCaps.setCapability("project", "HTS Stride")
    desiredCaps.setCapability("build", "Local")

    List("browserstack.os",
      "browserstack.os_version",
      "browserstack.browser",
      "browserstack.device",
      "browserstack.browser_version",
      "browserstack.real_mobile")
      .map(k ⇒ (k, sys.props.get(k)))
      .collect({ case (k, Some(v)) ⇒ (k, v) })
      .foreach(x ⇒ desiredCaps.setCapability(x._1.replace("browserstack.", ""), x._2.replace("_", " ")))

    val username = systemProperties.getProperty("username")
    val automateKey = systemProperties.getProperty("key")
    val url = s"http://$username:$automateKey@hub.browserstack.com/wd/hub"

    new RemoteWebDriver(new URL(url), desiredCaps)
  }

  def getBrowserStackCapabilities: Map[String, Object] = {
    val testDevice = System.getProperty("testDevice", "BS_Win10_Chrome_55")
    val resourceUrl = s"/browserstackdata/$testDevice.json"
    val cfgJsonString = Source.fromURL(getClass.getResource(resourceUrl)).mkString
    val mapper = new ObjectMapper() with ScalaObjectMapper
    mapper.registerModule(DefaultScalaModule)
    mapper.readValue[Map[String, Object]](cfgJsonString)
  }
}
