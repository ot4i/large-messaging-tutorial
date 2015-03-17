/*
 * Sample program for use with IBM Integration Bus
 * © Copyright International Business Machines Corporation 2005, 2015
 * Licensed Materials - Property of IBM
*/

import com.ibm.broker.javacompute.MbJavaComputeNode;
import com.ibm.broker.plugin.MbElement;
import com.ibm.broker.plugin.MbException;
import com.ibm.broker.plugin.MbMessage;
import com.ibm.broker.plugin.MbMessageAssembly;
import com.ibm.broker.plugin.MbOutputTerminal;
import com.ibm.broker.plugin.MbUserException;
import com.ibm.broker.plugin.MbXMLNSC;
import com.ibm.broker.plugin.MbTimestamp;

public class LargeMessages extends
		MbJavaComputeNode {
	
	// constant variables
	final String COMPLETION_ROOT = "SlicingReport";
	
	final String ROOT_LEVEL = "SaleEnvelope";
	final String HEADER = "Header";
	final String REPEATING_ELEMENT_COUNT = "SaleListCount";
	final String REPEATING_ELEMENT = "SaleList";
	
	// global variables
	private int intNumberOfSaleListsDeclared = 0;
	private int intNumberOfSaleListsFound = 0;
	private MbMessage inMessage = null;

	// int j = 0;
	
	public void evaluate(MbMessageAssembly inAssembly) throws MbException {
		MbOutputTerminal out = getOutputTerminal("out");
		MbOutputTerminal alt = getOutputTerminal("alternate");

		MbMessage inMessage = inAssembly.getMessage();

		// Add user code below

		ProcessLargeMessageToProduceIndividualMessages(inAssembly, out, inMessage);
		ProduceProcessingCompleteNotification(inAssembly, alt);
			
		// End of user code
			
		// The following should only be changed
		// if not propagating message to the 'out' terminal
		//out.propagate(outAssembly);
	}
	
    /**
    ============================================================================================
      > Declare variables
      > Find first instance of the element to process
      > For each instance found
        1> Release memory used to store information about the previous instance (if appropriate)
        2> Call a procedure to produce a single message the current instance
        3> Look for a following instance         
    ============================================================================================
    
	final String ROOT_LEVEL = "SaleEnvelope";
	final String HEADER = "Header";
	final String REPEATING_ELEMENT_COUNT = "SaleListCount";
	final String REPEATING_ELEMENT = "SaleList";
     */
	public void ProcessLargeMessageToProduceIndividualMessages(MbMessageAssembly inAssembly, MbOutputTerminal out, MbMessage inMessage) throws MbException {
		//create a reference of the input message
		this.inMessage = inMessage;
		// copy the input message
		MbMessage copyMessage = new MbMessage();
        MbElement xmlnsc = copyMessage.getRootElement();
        xmlnsc.addAsLastChild(inMessage.getRootElement().getFirstElementByPath("XMLNSC").copy());
        xmlnsc = xmlnsc.getFirstElementByPath("XMLNSC");
        // get the number of SaleList elements in the message
        intNumberOfSaleListsDeclared = Integer.parseInt((String)xmlnsc.getFirstElementByPath(ROOT_LEVEL + "/" + HEADER + "/" + REPEATING_ELEMENT_COUNT).getValue());
        
        // find the repeating elements and call ProduceIndividualSaleListMessage on them.
        // delete the already used messages
        MbElement refEnvironmentSaleList = xmlnsc.getFirstElementByPath(ROOT_LEVEL + "/" + REPEATING_ELEMENT);
        while (refEnvironmentSaleList != null){
        	intNumberOfSaleListsFound++;
        	if (intNumberOfSaleListsFound > 1) refEnvironmentSaleList.getPreviousSibling().delete();
        	
        	ProduceIndividualSaleListMessage(inAssembly, out, refEnvironmentSaleList);
        	
        	// find the next REPEATING_ELEMENT
        	do {
        		refEnvironmentSaleList = refEnvironmentSaleList.getNextSibling();
        	}
        	while(refEnvironmentSaleList != null && !refEnvironmentSaleList.getName().equals(REPEATING_ELEMENT));
        }
	}
	
	public void ProduceIndividualSaleListMessage(MbMessageAssembly inAssembly, MbOutputTerminal out, MbElement inSaleList) throws MbException {
		// creates a message containing a SaleList element and it's Number
		MbMessage outMessage = new MbMessage();
		copyMessageHeaders(outMessage);
		
		MbElement outElement = outMessage.getRootElement().createElementAsLastChild("XMLNSC");
		
		MbElement outRoot = outElement.createElementAsLastChild(MbXMLNSC.FOLDER, ROOT_LEVEL, null);
		outRoot.createElementAsLastChild(MbXMLNSC.FOLDER, "Number", "" + intNumberOfSaleListsFound);
		outRoot.createElementAsLastChild(MbXMLNSC.FOLDER, REPEATING_ELEMENT, null).copyElementTree(inSaleList);
		
		// propagate the message
		MbMessageAssembly outAssembly = new MbMessageAssembly(inAssembly, outMessage);
		
		// setting the number of the outputs, so they are all in a separate file
		MbMessage localEnv = outAssembly.getLocalEnvironment();
		MbElement filenameWildCard = localEnv.getRootElement().getFirstElementByPath("/Wildcard/WildcardMatch");
		filenameWildCard.setValue(intNumberOfSaleListsFound);

		//TODO error handling...
		
        out.propagate(outAssembly);
	}
	
	public void ProduceProcessingCompleteNotification(MbMessageAssembly inAssembly, MbOutputTerminal alt) throws MbException {
		// outputs the final statement of the work done
		MbMessage outMessage = new MbMessage();
		copyMessageHeaders(outMessage);
		
		MbElement outElement = outMessage.getRootElement().createElementAsLastChild("XMLNSC");
		MbElement outRoot = outElement.createElementAsLastChild(MbXMLNSC.FOLDER, COMPLETION_ROOT, null);
		MbElement counts = outRoot.createElementAsLastChild(MbXMLNSC.FOLDER, "Counts", null);
		counts.createElementAsLastChild(MbXMLNSC.FOLDER, "Declared", "" + intNumberOfSaleListsDeclared);
		counts.createElementAsLastChild(MbXMLNSC.FOLDER, "Actual", "" + intNumberOfSaleListsFound);
		outRoot.createElementAsLastChild(MbXMLNSC.FOLDER, "Completed", new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS").format(new java.util.Date()));
		
		// propagate the message
		MbMessageAssembly outAssembly = new MbMessageAssembly(inAssembly, outMessage);
		
        alt.propagate(outAssembly);
	}

	public void copyMessageHeaders(MbMessage outMessage) throws MbException
	{
		MbElement outRoot = outMessage.getRootElement();
	    MbElement header = inMessage.getRootElement().getFirstChild();

	    while(header != null && header.getNextSibling() != null)
	    {
	        outRoot.addAsLastChild(header.copy());
	        header = header.getNextSibling();
	    }
	}

}
