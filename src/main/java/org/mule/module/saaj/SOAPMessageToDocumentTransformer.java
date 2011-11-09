package org.mule.module.saaj;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Iterator;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.soap.DetailEntry;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPFault;
import javax.xml.soap.SOAPHeaderElement;
import javax.xml.soap.SOAPMessage;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.mule.api.MuleMessage;
import org.mule.api.MuleRuntimeException;
import org.mule.api.transformer.TransformerException;
import org.mule.module.saaj.i18n.SaajMessages;
import org.mule.transformer.AbstractMessageTransformer;
import org.mule.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 * <code>SOAPBodyToDocumentTransformer</code> Transform the payload of a <code>SOAPBody</code>,
 * from a <code>SOAPMessage</code>,to a <code>org.w3c.dom.Document</code>.  Headers in the SOAP
 * message are propgating as properties on the <code>MuleMessage</code>.
 */
public class SOAPMessageToDocumentTransformer extends AbstractMessageTransformer {

    private boolean throwExceptionOnFault = true;

    DocumentBuilder builder;
    MessageFactory messageFactory;

    public SOAPMessageToDocumentTransformer() throws SOAPException {
        super();
        try {
            builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            messageFactory = MessageFactory.newInstance();
        } catch (ParserConfigurationException e) {
            throw new MuleRuntimeException(SaajMessages.failedToCreateDocumentBuilder(), e);
        }
    }

    public void setThrowExceptionOnFault(boolean throwExceptionOnFault) {
        this.throwExceptionOnFault = throwExceptionOnFault;
    }

    public Object transformMessage(MuleMessage muleMessage, String s) throws TransformerException {

        InputStream inputStream;

        if (muleMessage.getPayload() instanceof byte[]) {
            byte[] in = (byte[]) muleMessage.getPayload();
            inputStream = new ByteArrayInputStream(in);
        } else if (muleMessage.getPayload() instanceof InputStream) {
            inputStream = (InputStream) muleMessage.getPayload();
        } else {
            throw new MuleRuntimeException(SaajMessages.failedToExtractSoapBody());
        }

        SOAPMessage soapMessage = SaajUtils.buildSOAPMessage(inputStream, messageFactory);

        if (logger.isDebugEnabled()) {
            logger.debug("SOAPMessage: \n " + SaajUtils.getSOAPMessageAsString(soapMessage));
        }

        if (throwExceptionOnFault && SaajUtils.containsFault(soapMessage)) {
            throwFaultException(soapMessage);
        }

        Document result = builder.newDocument();
        Node soapBody;
        try {
            soapBody = result.importNode(SaajUtils.getBodyContent(soapMessage.getSOAPBody()), true);
        } catch (SOAPException e) {
            throw new MuleRuntimeException(SaajMessages.failedToExtractSoapBody(), e);
        }
        result.appendChild(soapBody);
        populateHeaders(soapMessage, muleMessage);
//        TransformerFactory transFactory = TransformerFactory.newInstance();
//        Transformer transformer;
//		try {
//			transformer = transFactory.newTransformer();
//			 StringWriter buffer = new StringWriter();
//		        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
//		        transformer.transform(new DOMSource(result),
//		              new StreamResult(buffer));
//		        String str = buffer.toString();
//		        logger.info(str);
//		        return str;
//		} catch (Exception e) {
//            throw new MuleRuntimeException(SaajMessages.failedToCreateDocumentBuilder(), e);
//
//		}
        return result;
       
    }

    private void populateHeaders(SOAPMessage soapMessage, MuleMessage muleMessage) {
        try {
            if (soapMessage.getSOAPHeader() != null) {
                Iterator elements = soapMessage.getSOAPHeader().getChildElements();
                while (elements.hasNext()) {
                    SOAPHeaderElement header = (SOAPHeaderElement) elements.next();
                    String headerName = header.getLocalName();
                    String headerValue = header.getValue();
                    if(StringUtils.isNotEmpty(headerName) && StringUtils.isNotEmpty(headerValue)){
	                    logger.debug(String.format("Adding \"%s\" message property with value \"%s\" from the SOAP header",
	                            headerName, headerValue));
	                    muleMessage.setOutboundProperty(headerName, headerValue);
                    }
                }
            }
        } catch (SOAPException e) {
            logger.warn("Could not add SOAP header");
        }
    }

    private void throwFaultException(SOAPMessage soapMessage) {
        SOAPFault fault;
        try {
            fault = soapMessage.getSOAPBody().getFault();
        } catch (SOAPException ex) {
            throw new MuleRuntimeException(SaajMessages.failedToEvaluateFault(), ex);
        }
        String faultCode = fault.getFaultCode();
        String faultString = fault.getFaultString();
        String faultActor = fault.getFaultActor();
        String faultDetails = serializeFaultDetails(fault);

        throw new MuleRuntimeException((SaajMessages.soapFault(faultCode, faultString,
                faultActor, faultDetails)));
    }

    private String serializeFaultDetails(SOAPFault fault) {
        String result = "none";
        if (fault.getDetail() != null) {
            StringBuilder faultDetails = new StringBuilder();
            Iterator detailEntries = fault.getDetail().getDetailEntries();
            while (detailEntries.hasNext()) {
                DetailEntry detail = (DetailEntry) detailEntries.next();
                faultDetails.append(detail.getTextContent());
            }
            result = faultDetails.toString();
        }
        return result;
    }


}
