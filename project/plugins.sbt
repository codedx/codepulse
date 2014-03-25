//webplugin
addSbtPlugin("com.earldouglas" % "xsbt-web-plugin" % "0.5.0")

//eclipse plugin
addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "2.1.1")

//IntelliJ IDEA plugin
addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.6.0")

//sbt-dependency-graph
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.7.4")

// Resolver for the sbt-assembly plugin
resolvers += Resolver.url("artifactory", url("http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases"))(Resolver.ivyStylePatterns)

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.8.5")