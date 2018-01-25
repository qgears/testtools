package hu.qgears.eclipse.testtools;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.Map.Entry;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.rcptt.internal.launching.Q7LaunchManager;
import org.eclipse.rcptt.internal.launching.reporting.ReportMaker;
import org.eclipse.rcptt.launching.IExecutionSession;
import org.eclipse.rcptt.reporting.core.IReportRenderer.IContentFactory;
import org.eclipse.rcptt.reporting.util.FileContentFactory;
import org.eclipse.rcptt.reporting.util.JUnitFileReportGenerator;
import org.eclipse.rcptt.reporting.util.Q7ReportIterator;
import org.eclipse.rcptt.sherlock.core.streams.SherlockReportOutputStream;
import org.osgi.framework.BundleContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Generates a JUnit report on an RCPTT test run, without user interaction.  
 * 
 * @see #SYSPROP_NAME_OUTPUT_FILE_PREFIX
 * @author chreex
 */
public class RCPTTReportGeneration extends AbstractHandler {
	
	/**
	 * System property to specify the output path, more precisely an output
	 * directory and a file name for the generated test report, without the
	 * extension. When a report is generated, the file name will be postfixed 
	 * with the string ".junit.xml". If this system property is not specified, 
	 * the the test report output path and file will be defaulted to 
	 * "./rcptt-report", resulting in creating a file called 
	 * rcptt-report.junit.xml in the current working directory.
	 */
	private static final String SYSPROP_NAME_OUTPUT_FILE_PREFIX =
			"rcptt.report.filepathprefix";
	
	private File outputFilePrefix = new File(System.getProperty(
			SYSPROP_NAME_OUTPUT_FILE_PREFIX, "rcptt-report"));
	
	private void log(final IStatus status) {
		final BundleContext bundleContext = Activator.getContext();
		final ILog log = Platform.getLog(bundleContext.getBundle());
		
		log.log(status);
	}
	
	/**
	 * Generates a report in a custom report format into a temporary file, from
	 * the results of the first {@link IExecutionSession} enumerated by
	 * {@link Q7LaunchManager#getExecutionSessions()}. Note that this temporary
	 * file will be deleted after the JVM exits, as {@link File#deleteOnExit()}
	 * is called on it. If the report file generation fails, log messages will
	 * be emitted. 
	 * @return the temporary file containing the test report in a custom format,
	 * or null if creation fails with an observable error (note that not all
	 * errors are observable, as the {@link SherlockReportOutputStream#close()}
	 * called swallows exceptions and emits log messages)
	 */
	private File generateTempReportFile() {
		File rawReportFile = null;
		try {
			rawReportFile = File.createTempFile("RCPTTTestReport", null);
			
			rawReportFile.deleteOnExit();
		} catch (final Exception e) {
			log(new Status(IStatus.ERROR, Activator.PLUGIN_ID, 
					"Could not create temporary file for raw test report ", e));
		}
			
		if (rawReportFile != null) {
			final IExecutionSession[] executionSessions = 
					Q7LaunchManager.getInstance().getExecutionSessions();
			
			if (executionSessions == null || executionSessions.length == 0) {
				log(new Status(IStatus.ERROR, Activator.PLUGIN_ID, 
						"No test executions results found from which reports" +
						"could be generated"));
				
				return null;
			} else {
				final IExecutionSession executionSession = executionSessions[0];
				SherlockReportOutputStream out = null;
				
				try {
					out = new SherlockReportOutputStream(new BufferedOutputStream(
							new FileOutputStream(rawReportFile)));
					new ReportMaker(out).make(executionSession, new NullProgressMonitor());
				} catch (final FileNotFoundException e) {
					log(new Status(IStatus.ERROR, Activator.PLUGIN_ID, 
							"Could not write raw test report to temp file", e));
					
					return null;
				} finally {
					if (out != null) {
						out.close();
					}
				}
			}
		}
		
		return rawReportFile;
	}
	
	@Override
	public Object execute(final ExecutionEvent event) throws ExecutionException {
		final File tempReportFile = generateTempReportFile();

		if (tempReportFile != null) {
			final Q7ReportIterator report = new Q7ReportIterator(tempReportFile);
			final JUnitFileReportGenerator reportRenderer = new JUnitFileReportGenerator();
			final File outputDir = outputFilePrefix.getParentFile();
			final String reportFileName = outputFilePrefix.getName();
			final IContentFactory contentFactory = new FileContentFactory(outputDir);
			reportRenderer.generateReport(contentFactory, reportFileName, 
					report);
			for (String reportFile : reportRenderer.getGeneratedFileNames(reportFileName)){
				try {
					addSystemProps(new File (outputDir,reportFile));
				} catch (Exception e) {
					throw new ExecutionException("Cannot append system properties to report", e);
				}
			}
		}
		
		return null;
	}

	private void addSystemProps(File file) throws Exception {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = dbf.newDocumentBuilder();
		Document d = db.parse(file);
		NodeList suites = d.getElementsByTagName("testsuite");
		for (int i = 0 ;i < suites.getLength(); i++){
			Node suite = suites.item(i);
			Element pse = d.createElement("properties");
			appendSystemProperties(d,pse);
			suite.appendChild(pse);
		}
		Transformer transformer = TransformerFactory.newInstance().newTransformer();
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		Result output = new StreamResult(file);
		Source input = new DOMSource(d);
		transformer.transform(input, output);
	}
	

	private void appendSystemProperties(Document d, Element pse) throws XMLStreamException {
		Properties ps = System.getProperties();
		for (Entry<Object, Object> e : ps.entrySet()){
			Element p = d.createElement("property");
			p.setAttribute("name",""+e.getKey());
			p.setAttribute("value",""+e.getValue());
			pse.appendChild(p);
		}
	}
}

