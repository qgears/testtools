# testtools

## hu.qgears.eclipse.testtools
Eclipse plugin with the following functionality:
* allows automatically starting a launch configuration at startup, by specifying its name as a system property
* allows execution of an Eclipse command, by specifying the id of the command, after the launch config has finished or before the launch config has started.
* declares commands for:
 * creating a test report in JUnit XML format of the results of the last running RCPTT test suite or simple test
 * running a predefined RCPTT AUT configuration

See the source code for the documentation of the system properties!

## Running RCPTT test cases with testtools plugin

1. Package the application (feature set) under test as a runnable RCP application
1. Prepare a workspace that contains the RCP TT unit test files (referred as host-workspace), and an RCPTT application configuration that starts AUT.
1. Optionally prepare a workspace for application under test, that contains test resources (test-workspace)
1. Define a maven job that does the followings:
  * Copies host and test workspaces to a temporary directory, that will be used during tests (optional).
  * Starts an eclipse that contains the RCPTT features, and the testtool project. The eclipse must be initailized on the predefined temporary host-workspace.
    * The startup hook in testtol project should locate and run RCPTT AUT configuration automatically. The prepared AUT should be started on temporary test-workspace.
    * If the AUT has been started, the startup hook executes the specified RCPTT test launch configuration.
    * After RCPTT test launch is finished, the startup hook executes the eclipse command, that will export test results as JUnit.xml
    * If everything is ready the RCPTT instance will be closed
  
### Maven build job details
Parameters:
 * Indigo P2 repository URL
 * RCPTT P2 repository URL
 * Our testtool plugin packaged as P2 repository
 * Host-workspace path
 * Test-workspace path 
 * AUT path : the folder where the application under test can be found
Output:
 * JUnit test report in xml format.

Note : this build is not platform independent, as it uses bash scripts, and other LINUX programs.

#### Initial setup
1. Download RCPTT tool from here (our build works with 1.5.4) : http://eclipse.org/rcptt/download/
1. Package RCPTT as a P2 repository
  * See this page for an example:  http://maksim.sorokin.dk/it/2010/11/26/creating-a-p2-repository-from-features-and-plugins/
1. Download and build testtool project and package as P2 repository
  * You should use the buildjobs in this GIT repository
1. Search a proper P2 repository URL for eclipse indigo (and optionally you should create a local mirror from this repository)
1. Create an empty plugin-project, without any source folders (Only MANIFEST.MF, and build.properties is important). This project will be the test runner project.
1. Create a new pom.xml, that configures tycho build, and set packaging type to eclipse-test-plugin

The new folder structure looks like this:
 * rcptt.runner
   * META-INF
     * MANIFEST.MF
   * build.properties
   * pom.xml
  
Initial pom.xml
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd" xmlns="http://maven.apache.org/POM/4.0.0"
xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">

	<modelVersion>4.0.0</modelVersion>
		<groupId>test</groupId>
		<artifactId>rcptt.runner</artifactId>
		<version>1.0.0</version>
	<packaging>eclipse-test-plugin</packaging>

	<!-- Parent project with proper tycho configuration -->
	<parent>
		<groupId>test</groupId>
		<artifactId>parent-pom</artifactId>
		<version>0.9.9</version>
		<relativePath>../parent-pom</relativePath>
	</parent>
	<properties>
		<host-workspace>${project.build.directory}/host-workspace</host-workspace>
		<test-workspace>${project.build.directory}/test-workspace</test-workspace>
		<aut-location>${project.basedir}/applications/application-under-test</aut-location>
		<!-- Repository URL-s -->
		<indigoRepoUrl>http://buildserver/indigo</indigoRepoUrl>
		<rcpttRepoUrl>http://buildserver/rcptt</rcpttRepoUrl>
		<qgearsTesttoolsRepoUrl>http://buildserver/qgears-testtols</qgearsTesttoolsRepoUrl>
		<!-- Where to put JUnit report -->
		<report-path>${project.build.directory}/surefire-reports/TEST-rcptt</report-path>	
	</properties>	
</project>
```
#### Starting host RCPTT during build

The tycho-eclipserun-plugin is able to run an Eclispe instance during a maven build. The eclipse configuration is created dynamically, by specifying required top-bundles and features. Tycho resolves all dependencies recursively and starts the eclipse with this configuration.

In our case an RCPTT instance must be executed, and the testtools plugin must also installed. The RCPTT instance should be opened on prepared host-workspace, which contains the test cases, the test launch configuration, and an RCPTT AUT, representing the eclipse application under test. The startup hook in testtools plugin will start teh. The following pom.xml snippet shows how to do that:

```xml
<plugin>
	<groupId>org.eclipse.tycho.extras</groupId>
	<artifactId>tycho-eclipserun-plugin</artifactId>
	<version>${tycho-version}</version>
	<executions>
		<execution>
			<id>RCPTT editor integration test</id> 
			<configuration>
				<argLine>-DstartLaunchConfigs=MyRCPTTTestSuiteLaunchConfig -DexecuteCommandBeforeLaunchCfg=hu.qgears.eclipse.testtools.ReloadAut -DexecuteCommandAfterLaunchCfg=hu.qgears.eclipse.testtools.RCPTTReportGeneration -DexitAfterLaunchCfgFinished=true -Drcptt.report.filepathprefix=${report-path} -DlauchConfigStartupDelayMs=5000</argLine>
				<appArgLine>-product org.eclipse.rcptt.platform.product -data ${host-workspace} -consoleLog</appArgLine>
				<dependencies>
					<!-- startup hook project -->
					<dependency>
						<artifactId>hu.qgears.eclipse.testtools</artifactId>
						<type>eclipse-plugin</type>
					</dependency>
					<!-- RCPTT features (all of them)-->
					<dependency>
						<artifactId>org.eclipse.rcptt.ecl.core</artifactId>
						<type>eclipse-feature</type>
					</dependency>
					<dependency>
						<artifactId>org.eclipse.rcptt.ecl.ide</artifactId>
						<type>eclipse-feature</type>
					</dependency>
					<dependency>
						<artifactId>org.eclipse.rcptt.ecl.server</artifactId>
						<type>eclipse-feature</type>
					</dependency>
					<dependency>
						<artifactId>org.eclipse.rcptt.module.nebula</artifactId>
						<type>eclipse-feature</type>
					</dependency>
					<dependency>
						<artifactId>org.eclipse.rcptt.tesla</artifactId>
						<type>eclipse-feature</type>
					</dependency>
					<dependency>
						<artifactId>org.eclipse.rcptt.tesla.ecl</artifactId>
						<type>eclipse-feature</type>
					</dependency>
					<dependency>
						<artifactId>org.eclipse.rcptt.updates</artifactId>
						<type>eclipse-feature</type>
					</dependency>

					<dependency>
						<artifactId>org.eclipse.rcptt.watson</artifactId>
						<type>eclipse-feature</type>
					</dependency>
					<dependency>
						<artifactId>org.eclipse.rcptt.ecl.platform</artifactId>
						<type>eclipse-feature</type>
					</dependency>
					<dependency>
						<artifactId>org.eclipse.rcptt.ecl.platform.ui</artifactId>
						<type>eclipse-feature</type>
					</dependency>
					<dependency>
						<artifactId>org.eclipse.rcptt.platform</artifactId>
						<type>eclipse-feature</type>
					</dependency>
				</dependencies>
				<repositories>
					<repository>
						<id>RCPTT P2 repository</id>
						<layout>p2</layout>
						<url>${rcpttRepoUrl}</url>
					</repository>
					<repository>
						<id>eclipse-indigo</id>
						<layout>p2</layout>
						<url>${indigoRepoUrl}</url>
					</repository>
					<repository>
						<id>QGears testtools repo</id>
						<layout>p2</layout>
						<url>${qgearsTesttoolsRepoUrl}</url>
					</repository>
				</repositories>
			</configuration>

			<goals>
				<goal>eclipse-run</goal>
			</goals>

			<phase>integration-test</phase>
		</execution>
	</executions>
</plugin>
```
#### Running tests on a background display
Maven builds usually run in headless environment. For supporting this use-case we defined a maven profile that initializes a background X-server for host eclipse and application under test.

Note that this build configuration is platform dependent, and requires some preinstalled packages from ubuntu repository.

Build steps:
 * pre-integration-test : run a shell script, that starts X server asynchronously , and saves the PID of forked process and the id of the background display into a standard Java property file. See start-xvfb.sh for details.
 * pre-integration-test : read property file, and export the properties as maven properties
 * integration-test : pass display id to eclipserun  plugin as environment variable. The environment variables are inherited to forked processes, so the AUT started by RCPTT will be initialized also on speified display. Relevant part from eclipserun-plugin's configuration:
```xml
<environmentVariables>
  <DISPLAY>${bgxserver-display}</DISPLAY>
</environmentVariables>
```
 * post-integration-test : kill X-server process (the pid is read from properties file)

Profile configuration:

```xml
 <profile>
	<!--  Profile for starting background x server -->
	<id>xserver-start</id>
	<properties>
		<!-- PID and display id will be saved here -->
		<xserver-starter-props>${project.build.directory}/xserver.props</xserver-starter-props>
		<xserver-start-scriptpath>start-xvfb.sh</xserver-start-scriptpath>
	</properties>
	<build>
		<plugins>
			<plugin>
				<artifactId>maven-antrun-plugin</artifactId>
				<version>1.6</version>
				<executions>
					<execution>
						<id>Starting X server</id>
						<phase>pre-integration-test</phase>
						<configuration>
							<target>
								<exec executable="${xserver-start-scriptpath}" spawn="false">
									<arg value="${xserver-starter-props}" /> 
								</exec>
							</target>
						</configuration>
						<goals>
							<goal>run</goal>
						</goals>
					</execution>
				</executions>
			</plugin>
			<plugin>
				<!-- This plugin reads xserver properties from file (generated by xserver 
					startscript), and set them as maven properties -->
				<groupId>org.codehaus.mojo</groupId>
				<artifactId>properties-maven-plugin</artifactId>
				<version>1.0-alpha-2</version>
				<executions>
					<execution>
						<goals>
							<goal>read-project-properties</goal>
						</goals>
						<configuration>
							<files>
								<file>${xserver-starter-props}</file>
							</files>
						</configuration>
						<phase>pre-integration-test</phase>
					</execution>
				</executions>
			</plugin>
			<plugin>
				<artifactId>exec-maven-plugin</artifactId>
				<groupId>org.codehaus.mojo</groupId>
				<version>1.3.2</version>
				<configuration>
					<workingDirectory></workingDirectory>
				</configuration>
				<executions>
					<execution>
						<id>Killing X server</id>
						<phase>post-integration-test</phase>
						<goals>
							<goal>exec</goal>
						</goals>
						<configuration>
							<executable>kill</executable>
							<commandlineArgs>-9 ${bgxserver-pid}</commandlineArgs>
						</configuration>
					</execution>
				</executions>
			</plugin>
		</plugins>
	</build>
</profile>
```
