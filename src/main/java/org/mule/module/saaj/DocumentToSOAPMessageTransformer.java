package org.mule.module.saaj;

import java.io.ByteArrayOutputStream;
import java.security.InvalidParameterException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.Name;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPFactory;
import javax.xml.soap.SOAPHeader;
import javax.xml.soap.SOAPHeaderElement;
import javax.xml.soap.SOAPMessage;

import org.apache.tools.ant.filters.StringInputStream;
import org.mule.api.MuleMessage;
import org.mule.api.transformer.TransformerException;
import org.mule.module.saaj.i18n.SaajMessages;
import org.mule.transformer.AbstractMessageTransformer;
import org.mule.transformer.types.DataTypeFactory;
import org.w3c.dom.Document;

/**
 * <code>DocumentToSOAPMessageTransformer</code> transforms a org.w3c.dom.Document to a SOAP message using
 * SAAJ. The transformed result is serialized as a byte array.
 */
public class DocumentToSOAPMessageTransformer extends AbstractMessageTransformer {

    private boolean propagateHeaders = true;
    private String headerURI = "http://www.mulesource.org/schema/mule/saaj/2.2";
    private String headerPrefix = "mule-saaj";

    private SOAPFactory soapFactory;
    private MessageFactory messageFactory;

    public DocumentToSOAPMessageTransformer() throws Exception {
        soapFactory = SOAPFactory.newInstance();
        messageFactory = MessageFactory.newInstance();
        registerSourceType(DataTypeFactory.STRING);
        registerSourceType(DataTypeFactory.create(Document.class));
        registerSourceType(DataTypeFactory.BYTE_ARRAY);
        //Workaround for this issue https://issues.apache.org/jira/browse/XERCESJ-1234
        System.setProperty("javax.xml.soap.SOAPFactory", "org.apache.axis.soap.SOAPFactoryImpl");
        System.setProperty("javax.xml.soap.MessageFactory","org.apache.axis.soap.MessageFactoryImpl");
        System.setProperty("javax.xml.soap.SOAPConnectionFactory","org.apache.axis.soap.SOAPConnectionFactoryImpl");
    }

    public void setPropagateHeaders(boolean propagateHeaders) {
        this.propagateHeaders = propagateHeaders;
    }

    public void setHeaderURI(String headerURI) {
        this.headerURI = headerURI;
    }

    public void setHeaderPrefix(String headerPrefix) {
        this.headerPrefix = headerPrefix;
    }

    @Override
    public Object transformMessage(MuleMessage muleMessage, String s) throws TransformerException {
    	Object src = muleMessage.getPayload();
    	Document document;
    	DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
    	
    	if (src instanceof String)
         {
    		try {
    			
				document = documentBuilderFactory.newDocumentBuilder().parse(new StringInputStream((String)src));
				
			} catch (Exception e) {
				 throw new TransformerException(SaajMessages.failedToBuildSOAPMessage(),e);
			} 
         }
    	else if (src instanceof ByteArrayOutputStream)
        {
    		ByteArrayOutputStream baos = (ByteArrayOutputStream)src;
    		try {
				document = DocumentBuilderFactory
						.newInstance()
						.newDocumentBuilder()
						.parse(new StringInputStream(baos.toString()));
			} catch (Exception e) {
				throw new TransformerException(SaajMessages.failedToBuildSOAPMessage(),e);
			} 
			
        }
    	 else if (src instanceof Document)
         {
    		 document = (Document) src;
         } else {
        	 throw new TransformerException(SaajMessages.failedToBuildSOAPMessage(), new InvalidParameterException());
         }
        SOAPMessage soapMessage;

        try {
            soapMessage = messageFactory.createMessage();
            SOAPBody body = soapMessage.getSOAPBody();
            body.addDocument(document);
            if (propagateHeaders) {
                propagateHeaders(muleMessage, soapMessage);
            }
            soapMessage.saveChanges();
        } catch (SOAPException ex) {
            throw new TransformerException(SaajMessages.failedToBuildSOAPMessage());
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Transformation result: " + SaajUtils.getSOAPMessageAsString(soapMessage));
        }

        return SaajUtils.getSOAPMessageAsBytes(soapMessage);
    }

    void propagateHeaders(MuleMessage muleMessage, SOAPMessage soapMessage) throws SOAPException {
        for (Object n : muleMessage.getInboundPropertyNames()) {
            String propertyName = (String) n;
            SOAPHeader header = soapMessage.getSOAPHeader();

            Name name = soapFactory.createName(propertyName, headerPrefix, headerURI);
            SOAPHeaderElement headerElement = header.addHeaderElement(name);
            headerElement.addTextNode(muleMessage.getInboundProperty(propertyName).toString());
        }
    }

}
