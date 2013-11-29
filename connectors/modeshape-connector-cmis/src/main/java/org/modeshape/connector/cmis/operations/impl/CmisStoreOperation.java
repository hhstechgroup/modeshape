package org.modeshape.connector.cmis.operations.impl;

import org.apache.chemistry.opencmis.client.api.CmisObject;
import org.apache.chemistry.opencmis.client.api.Session;
import org.apache.chemistry.opencmis.commons.data.ContentStream;
import org.apache.chemistry.opencmis.commons.definitions.PropertyDefinition;
import org.infinispan.schematic.document.Document;
import org.modeshape.connector.cmis.mapping.LocalTypeManager;
import org.modeshape.connector.cmis.mapping.MappedCustomType;
import org.modeshape.connector.cmis.ObjectId;
import org.modeshape.connector.cmis.operations.BinaryContentProducerInterface;

import java.util.HashMap;
import java.util.Map;

import static org.modeshape.connector.cmis.operations.impl.CmisOperationCommons.*;


public class CmisStoreOperation extends CmisOperation {

    private boolean ignoreEmptyPropertiesOnCreate;

    public CmisStoreOperation(Session session, LocalTypeManager localTypeManager, boolean ignoreEmptyPropertiesOnCreate) {
        super(session, localTypeManager);
        this.ignoreEmptyPropertiesOnCreate = ignoreEmptyPropertiesOnCreate;
    }

    public void storeDocument(Document document, BinaryContentProducerInterface binaryProducer) {
        // object id is a composite key which holds information about
        // unique object identifier and about its type
        ObjectId objectId = ObjectId.valueOf(document.getString("key"));

        // this action depends from object type
        switch (objectId.getType()) {
            case REPOSITORY_INFO:
                // repository information is ready only
                return;
            case CONTENT:
                // in the jcr domain content is represented by child node of
                // the nt:file node while in cmis domain it is a property of
                // the cmis:document object. so to perform this operation we need
                // to restore identifier of the original cmis:document. it is easy
                String cmisId = objectId.getIdentifier();

                // now let's get the reference to this object
                CmisObject cmisObject = session.getObject(cmisId);

                // object exists?
                if (cmisObject == null) {
                    // object does not exist. propably was deleted by from cmis domain
                    // we don't know how to handle such case yet, thus TODO
                    return;
                }

                // original object is here so converting binary value and
                // updating original cmis:document
                ContentStream stream = binaryProducer.jcrBinaryContent(document);
                if (stream != null) {
                    if (isVersioned(cmisObject)) CmisOperationCommons.updateVersionedDoc(session, cmisObject, null, stream);
                    else asDocument(cmisObject).setContentStream(stream, true, true);
                }
                break;
            case OBJECT:
                // extract node properties from the document view
                Document jcrProperties = document.getDocument("properties");

                // check that we have jcr properties to store in the cmis repo
                if (jcrProperties == null) {
                    // nothing to store
                    return;
                }

                // if node has properties we need to pickup cmis object from
                // cmis repository, convert properties from jcr domain to cmis
                // and update properties
                cmisObject = null;
                try {
                    cmisObject = session.getObject(objectId.getIdentifier());
                } catch (Exception e) {
                    debug("Not found object with id - " + objectId.getIdentifier());
                }

                // unknown object?
                if (cmisObject == null) {
                    // exit silently
                    return;
                }

                // Prepare store for the cmis properties
                Map<String, Object> updateProperties = new HashMap<String, Object>();

                // ask cmis repository to get property definitions
                // we will use these definitions for correct conversation
                Map<String, PropertyDefinition<?>> propDefs = cmisObject.getBaseType().getPropertyDefinitions();
                propDefs.putAll(cmisObject.getType().getPropertyDefinitions());
                MappedCustomType mapping = localTypeManager.getMappedTypes().findByExtName(cmisObject.getType().getId());
                // jcr properties are grouped by namespace uri
                // we will travers over all namespaces (ie group of properties)

                for (Document.Field field : jcrProperties.fields()) {
                    // field has namespace uri as field's name and properties
                    // as value
                    // getting namespace uri and properties
                    String namespaceUri = field.getName();
                    Document props = field.getValueAsDocument();

                    // namespace uri uniquily defines prefix for the property name
                    String prefix = localTypeManager.getPrefixes().value(namespaceUri);

                    // now scroll over properties
                    for (Document.Field property : props.fields()) {
                        // getting jcr fully qualified name of property
                        // then determine the the name of this property
                        // in the cmis domain
                        String jcrPropertyName = prefix != null ? prefix + ":" + property.getName() : property.getName();
                        String cmisPropertyName = localTypeManager.getPropertyUtils().findCmisName(jcrPropertyName);
                        // correlate with custom mapping
                        cmisPropertyName = mapping.toExtProperty(cmisPropertyName);
                        // now we need to convert value, we will use
                        // property definition from the original cmis repo for this step
                        PropertyDefinition<?> pdef = propDefs.get(cmisPropertyName);

                        // unknown property?
                        if (pdef == null) {
                            // ignore
                            continue;
                        }

                        // make conversation for the value

                        Object cmisValue = null;
                        try {
                            cmisValue = localTypeManager.getPropertyUtils().cmisValue(pdef, property.getName(), props);
                        } catch (Exception e) {
                            debug(e.getMessage());
                        }
                        // store properties for update
                        // incorrect value won't be parsed so cmisValue will have null which may overwrite default value for required property
                        // consider not to put empty values while store ??
                        if (ignoreEmptyPropertiesOnCreate && pdef.isRequired() && (cmisValue == null || "".equals(cmisValue.toString()))) {
                            continue;
                        }
                        // add property
                        updateProperties.put(cmisPropertyName, cmisValue);
                    }

                }
                // finally execute update action
                if (!updateProperties.isEmpty()) {
                    if (isDocument(cmisObject) && isVersioned(cmisObject)) {
                        CmisOperationCommons.updateVersionedDoc(session, cmisObject, updateProperties, null);
                    } else {
                        cmisObject.updateProperties(updateProperties);
                    }
                }
                break;
        }
    }
}
