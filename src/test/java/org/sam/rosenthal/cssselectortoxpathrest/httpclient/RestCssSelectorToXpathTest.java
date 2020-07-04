package org.sam.rosenthal.cssselectortoxpathrest.httpclient;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.Supplier;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpMethod;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.sam.rosenthal.cssselectortoxpath.utilities.CssElementCombinatorPairsToXpath;
import org.sam.rosenthal.cssselectortoxpath.utilities.CssSelectorToXPathConverterException;
import org.sam.rosenthal.cssselectortoxpath.utilities.basetestcases.BaseCssSelectorToXpathTestCase;
import org.sam.rosenthal.cssselectortoxpathrest.restservice.CssSelectorIn;
import org.sam.rosenthal.cssselectortoxpathrest.restservice.InvalidCssSelector;
import org.sam.rosenthal.cssselectortoxpathrest.restservice.XpathOut;

import com.google.appengine.repackaged.com.google.gson.Gson;

public class RestCssSelectorToXpathTest {
	private static Process restAppProcess;
	private Gson gson = new Gson();
	private	HttpClient httpClient;

	
	@BeforeClass
	public static void startRestApplicationProcess() throws IOException
	{
		System.out.println("IN BEEFORE CLASS");
		String classPath = System.getProperty("java.class.path");
		String javaHome = System.getProperty("java.home")+"/bin/java";
		System.out.println("classPath="+classPath+" javaHome="+javaHome);

		ProcessBuilder builder = new ProcessBuilder(new String[]{javaHome,"-cp",classPath,"org.sam.rosenthal.cssselectortoxpathrest.CssSelectorToXpathRestApplication"});
		builder.redirectOutput(Redirect.INHERIT);
		builder.redirectError(Redirect.INHERIT);
		restAppProcess = builder.start();
		for(int i=0; i<10; ++i)
		{
			try {
				Thread.sleep(1000L);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			System.out.println(i+" restAppProcess="+restAppProcess+" isAlive="+restAppProcess.isAlive());
		}
		System.out.println("OUT BEFORE CLASS");
	}
	
	@AfterClass
	public static void endRestApplicationProcess()
	{
		if(restAppProcess!=null)
		{
			restAppProcess.destroy();
			restAppProcess=null;
		}
	}
	
	@Test
	public void testBasicTestCases() throws CssSelectorToXPathConverterException, IOException, InterruptedException {
		System.out.println("In test basic");
		List<BaseCssSelectorToXpathTestCase> baseCases=BaseCssSelectorToXpathTestCase.getBaseCssSelectorToXpathTestCases(true);
		test(true, baseCases, BaseCssSelectorToXpathTestCase::getCssSelector, BaseCssSelectorToXpathTestCase::getExpectedXpath, 200, this::getXpathFromXpathOutJsonString );
	}
	
	@Test
	public void testExceptionTestCases() throws CssSelectorToXPathConverterException, IOException, InterruptedException {
		Map<String,String> baseExceptions=BaseCssSelectorToXpathTestCase.getBaseCssSelectorToXpathExceptionTestCases();
		test(false, baseExceptions.entrySet(), Entry<String, String>::getKey, Entry<String, String>::getValue, 400, this::getXpathFromInvalidCssSelectorJsonString);
	}
	
	@Test
	public void testVersion() throws CssSelectorToXPathConverterException, IOException, InterruptedException {
		ContentResponse response = getResponse("version");
		assertEquals(200,  response.getStatus());
		assertEquals(new CssElementCombinatorPairsToXpath().getVersionNumber(), response.getContentAsString());
	}
	
	public <T> void test(boolean isBasic, Collection<T> testCaseCollection, 
			Function<T, String> cssSelectorFunction,
			Function<T, String> xpathFunction,
			int expectedStatusCode,
			Function<String, String> jsonToXpath) throws CssSelectorToXPathConverterException, IOException, InterruptedException
	{
		for(T testCase : testCaseCollection)
		{
			String cssSelector = cssSelectorFunction.apply(testCase); 
			String expectedXpath = xpathFunction.apply(testCase); 
			assertJsonAndStatus(cssSelector, expectedXpath, expectedStatusCode, jsonToXpath);
		}
	}
	
	protected ContentResponse postResponse(String cssSelector) {
		return sendRequest(()->{
			String cssJson = gson.toJson(new CssSelectorIn(cssSelector));
			Request request = httpClient.newRequest("http://localhost:8888/cssSelectorToXpath/convert");
			request.content(new StringContentProvider(cssJson), "application/json");
			request.method(HttpMethod.POST);
			return request;
		});
	}
	
	protected ContentResponse getResponse(String endpoint) {
		return sendRequest(()->httpClient.newRequest("http://localhost:8888/cssSelectorToXpath/"+endpoint));
	}
	
	protected ContentResponse sendRequest(Supplier<Request> requestSupplier) {
		httpClient = new HttpClient();
		try {
			httpClient.start();
			return requestSupplier.get().send();
		} 
		catch (Exception e) 
		{
			throw new RuntimeException(e);
		}
		finally
		{
			try 
			{
				httpClient.stop();
			} 
			catch (Exception e) 
			{
				throw new RuntimeException(e);
			}			
		}
	}
	 
	protected void assertJsonAndStatus(String cssSelector, String expectedXpath, int expectedStatusCode, Function<String, String> jsonToXpath ) {
		System.out.println(cssSelector+" Expected="+expectedXpath);
		ContentResponse response = postResponse(cssSelector);
		String jsonXml = response.getContentAsString();
		int actualStatusCode = response.getStatus();
		String actualXpath = jsonToXpath.apply(jsonXml);
		System.out.println(cssSelector+" Actual="+ actualXpath);
		assertEquals(expectedStatusCode, actualStatusCode);
		assertEquals("CSS Selector="+cssSelector, expectedXpath, actualXpath);		
	}
	
	protected String getXpathFromXpathOutJsonString(String jsonXml)
	{
		XpathOut actualXpath = gson.fromJson(jsonXml, XpathOut.class);
		return actualXpath.getXpath();
	}
	
	protected String getXpathFromInvalidCssSelectorJsonString(String jsonXml)
	{
		InvalidCssSelector invalidCssSelector = gson.fromJson(jsonXml, InvalidCssSelector.class);
		return invalidCssSelector.getMessage();
	}
}
